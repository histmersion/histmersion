package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent
import com.histmersion.api.log.LogEvent.What
import sttp.model.{Header, StatusCode}
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.*
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

import RequestLog.{LabelHeader, TraceHeader, Unmatched}

/** Every HTTP request — REST, MCP, pages — opens a trace: its steps (provider calls, problems, the database's writes)
  * are recorded under it, its answer carries it (`Histmersion-Trace`), and once answered it is one `RequestAnswered`
  * event with the route template that matched, the status, the time, the client's address and the search text. A
  * client's own label (`Histmersion-Trace-Label`) is kept beside the trace. Map tiles, static files and the health
  * check (`log.counted`) are counted per minute instead. The netty-sync interpreter runs a request's whole logic on its
  * own thread, which is what lets the trace scope and the matched route ride on it.
  */
final class RequestLog(writer: LogWriter, settings: () => LogConfig) extends RequestInterceptor[Identity]:
  private val matched = ThreadLocal[LogEvent.Operation | Null]()

  private val routes: EndpointInterceptor[Identity] = new EndpointInterceptor[Identity]:
    def apply[B](responder: Responder[Identity, B], next: EndpointHandler[Identity, B]): EndpointHandler[Identity, B] =
      new EndpointHandler[Identity, B]:
        def onDecodeSuccess[A, U, I](ctx: DecodeSuccessContext[Identity, A, U, I])(using
            monad: MonadError[Identity],
            body: BodyListener[Identity, B]
        ): Identity[ServerResponse[B]] =
          matched.set(LogEvent.Operation.of(ctx.endpoint))
          next.onDecodeSuccess(ctx)
        def onSecurityFailure[A](ctx: SecurityFailureContext[Identity, A])(using
            monad: MonadError[Identity],
            body: BodyListener[Identity, B]
        ): Identity[ServerResponse[B]] =
          matched.set(LogEvent.Operation.of(ctx.endpoint))
          next.onSecurityFailure(ctx)
        def onDecodeFailure(ctx: DecodeFailureContext)(using
            monad: MonadError[Identity],
            body: BodyListener[Identity, B]
        ): Identity[Option[ServerResponse[B]]] =
          val answer = next.onDecodeFailure(ctx)
          if answer.isDefined then matched.set(LogEvent.Operation.of(ctx.endpoint))
          answer

  def apply[R, B](
      responder: Responder[Identity, B],
      requestHandler: EndpointInterceptor[Identity] => RequestHandler[Identity, R, B]
  ): RequestHandler[Identity, R, B] =
    val next = requestHandler(routes)
    new RequestHandler[Identity, R, B]:
      def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, Identity]])(using
          monad: MonadError[Identity]
      ): RequestResult[B] =
        val method = LogEvent.Method.of(request.method)
        val path = request.uri.pathSegments.segments.map(_.v).mkString("/", "/", "")
        val counted = method.reads && settings().countsPath(path)
        val scope = TraceScope(
          writer.mintTrace(),
          request.header(LabelHeader).flatMap(LogEvent.Label.fromHeader),
          LogEvent.Actor.Client,
          writer
        )
        val started = System.nanoTime()
        matched.remove()
        val result = scope.within(next(request, endpoints))
        val operation = Option(matched.get()).getOrElse(Unmatched)
        matched.remove()
        val status = result match
          case RequestResult.Response(response, _) => LogEvent.Status.of(response.code)
          case _: RequestResult.Failure            => LogEvent.Status.of(StatusCode.NotFound)
        if counted then writer.count(operation.by(method), status.serverError)
        else
          scope.record(
            Vector(scope.trace.request),
            What.RequestAnswered(
              operation,
              method,
              status,
              LogEvent.Millis.between(started, System.nanoTime()),
              request.connectionInfo.remote.map(a => LogEvent.Client(a.getAddress.getHostAddress)),
              request.queryParameters.get("q").flatMap(LogEvent.Search.fromQuery)
            )
          )
        result match
          case RequestResult.Response(response, source) =>
            RequestResult.Response(response.addHeaders(List(Header(TraceHeader, s"${scope.trace}"))), source)
          case failure => failure

object RequestLog:
  /** The answer's header that names its trace. */
  val TraceHeader = "Histmersion-Trace"

  /** The header a client names its own calls with (an outside agent session grouping its calls). */
  val LabelHeader = "Histmersion-Trace-Label"

  /** The operation of a request no route matched. */
  val Unmatched: LogEvent.Operation = LogEvent.Operation("(no route)")
