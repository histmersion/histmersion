package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent
import com.histmersion.api.log.LogEvent.What
import com.histmersion.config.LogConfig
import sttp.model.Header
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.*
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

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
          matched.set(RequestLog.template(ctx.endpoint))
          next.onDecodeSuccess(ctx)
        def onSecurityFailure[A](ctx: SecurityFailureContext[Identity, A])(using
            monad: MonadError[Identity],
            body: BodyListener[Identity, B]
        ): Identity[ServerResponse[B]] =
          matched.set(RequestLog.template(ctx.endpoint))
          next.onSecurityFailure(ctx)
        def onDecodeFailure(ctx: DecodeFailureContext)(using
            monad: MonadError[Identity],
            body: BodyListener[Identity, B]
        ): Identity[Option[ServerResponse[B]]] =
          val answer = next.onDecodeFailure(ctx)
          if answer.isDefined then matched.set(RequestLog.template(ctx.endpoint))
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
        val method = request.method.method
        val path = request.uri.pathSegments.segments.map(_.v).mkString("/", "/", "")
        val counted = (method == "GET" || method == "HEAD") &&
          settings().countedPrefixes.exists(p => path == p.value.stripSuffix("/") || path.startsWith(p.value))
        val scope = TraceScope(
          writer.mintTrace(),
          request.header(RequestLog.LabelHeader).map(_.trim).filter(_.nonEmpty).map(l => LogEvent.Label(l.take(200))),
          LogEvent.Actor.Client,
          writer
        )
        val started = System.nanoTime()
        matched.remove()
        val result = Tracing.within(scope)(next(request, endpoints))
        val operation = Option(matched.get()).getOrElse(LogEvent.Operation(RequestLog.Unmatched))
        matched.remove()
        val status = result match
          case RequestResult.Response(response, _) => response.code.code
          case _: RequestResult.Failure            => 404
        if counted then writer.count(LogEvent.Operation(s"$method ${operation.value}"), status >= 500)
        else
          scope.record(
            Vector(LogEvent.Subject.Request(LogEvent.Key(scope.trace.value))),
            What.RequestAnswered(
              operation,
              LogEvent.Method(method),
              LogEvent.Status(status),
              LogEvent.Millis((System.nanoTime() - started) / 1000000L),
              request.connectionInfo.remote.map(a => LogEvent.Client(a.getAddress.getHostAddress)),
              request.queryParameters.get("q").map(_.trim).filter(_.nonEmpty).map(q => LogEvent.Search(q.take(500)))
            )
          )
        result match
          case RequestResult.Response(response, source) =>
            RequestResult.Response(response.addHeaders(List(Header(RequestLog.TraceHeader, scope.trace.value))), source)
          case failure => failure

object RequestLog:
  /** The answer's header that names its trace. */
  val TraceHeader = "Histmersion-Trace"

  /** The header a client names its own calls with (an outside agent session grouping its calls). */
  val LabelHeader = "Histmersion-Trace-Label"

  /** The operation of a request no route matched. */
  val Unmatched = "(no route)"

  /** A route's template: its path with its parameters named (`/api/sources/{citekey}`), never the query. */
  def template(e: Endpoint[?, ?, ?, ?, ?]): LogEvent.Operation =
    LogEvent.Operation(e.showPathTemplate(showQueryParam = None))
