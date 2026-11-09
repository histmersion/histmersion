package com.histmersion
package pluginhost

import com.histmersion.api.instance.Version
import com.histmersion.domain.HttpPolicy
import com.histmersion.plugin.*

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** The host's [[AiHttp]]: java.net.http over HTTP/1.1 (as every client of the server), the host's User-Agent (no e-mail
  * can reach it, [[HttpPolicy.userAgent]]), a timeout per call, and nothing sent or received logged.
  */
final class HostAiHttp extends AiHttp:
  /** Built on the first call (an `HttpClient` starts a selector thread): a server that never calls a plugin provider
    * starts none.
    */
  private val built = java.util.concurrent.atomic.AtomicReference[java.net.http.HttpClient]()

  private def client: java.net.http.HttpClient =
    Option(built.get).getOrElse {
      val c = java.net.http.HttpClient
        .newBuilder()
        .version(java.net.http.HttpClient.Version.HTTP_1_1)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()
      if built.compareAndSet(null, c) then c else built.get
    }

  private def userAgent = HttpPolicy.userAgent(HttpPolicy.UserAgent(s"Histmersion/${Version.version} (agents)"))

  def post(
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value],
      body: AiHttp.PostBody,
      timeoutSeconds: AiHttp.TimeoutSeconds
  ): Either[AiHttp.Failure, AiHttp.Response] =
    send(
      url.value,
      headers.map((k, v) => k.value -> v.value),
      timeoutSeconds.value,
      _.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.value, StandardCharsets.UTF_8))
    )

  def get(
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value],
      timeoutSeconds: AiHttp.TimeoutSeconds
  ): Either[AiHttp.Failure, AiHttp.Response] =
    send(url.value, headers.map((k, v) => k.value -> v.value), timeoutSeconds.value, _.GET())

  private def send(
      url: String,
      headers: Map[String, String],
      timeoutSeconds: Int,
      method: java.net.http.HttpRequest.Builder => java.net.http.HttpRequest.Builder
  ): Either[AiHttp.Failure, AiHttp.Response] =
    try
      val b = java.net.http.HttpRequest
        .newBuilder(java.net.URI.create(url))
        .timeout(java.time.Duration.ofSeconds(math.max(1, timeoutSeconds).toLong))
      headers.foreach((k, v) => if !k.equalsIgnoreCase("user-agent") then { val _ = b.header(k, v) })
      val _ = b.header("User-Agent", userAgent.value)
      val r = client.send(method(b).build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      Right(
        AiHttp.Response(
          AiHttp.Response.Status(r.statusCode),
          AiHttp.Response.Body(r.body),
          r.headers.map.asScala.toMap.collect {
            case (k, v) if !v.isEmpty =>
              Header.Name(k.toLowerCase(java.util.Locale.ROOT)) -> Header.Value(v.get(0))
          }
        )
      )
    catch
      case e: InterruptedException => throw e
      // the message only: never the request, which may carry a key in a header
      case NonFatal(e) => Left(failureOf(e))

  /** What a failed send was, from the type of the exception; its text is `<exception>: <message>`, cut at 300. */
  def failureOf(e: Throwable): AiHttp.Failure =
    val reported = Problem(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))
    e match
      case _: java.net.ConnectException | _: java.net.UnknownHostException | _: java.net.MalformedURLException |
          _: java.net.NoRouteToHostException | _: java.net.PortUnreachableException | _: java.net.ProtocolException |
          _: java.net.URISyntaxException =>
        AiHttp.Failure.Connect(reported)
      case _: java.util.concurrent.TimeoutException | _: java.net.http.HttpTimeoutException |
          _: java.net.SocketTimeoutException =>
        AiHttp.Failure.TimedOut(reported)
      case _: IllegalArgumentException | _: IllegalStateException | _: SecurityException |
          _: UnsupportedOperationException | _: NullPointerException =>
        AiHttp.Failure.Refused(reported)
      case _: Exception => AiHttp.Failure.Read(reported)
      case _            => AiHttp.Failure.Refused(reported)
