package com.histmersion.pluginhost

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

  private def userAgent = HttpPolicy.userAgent(s"Histmersion/${Version.version} (agents)")

  def post(
      url: com.histmersion.pluginhost.HttpTransport.Url,
      headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.pluginhost.HostAiHttp.PostHeadersPart2],
      body: com.histmersion.pluginhost.HostAiHttp.RequestBody,
      timeoutSeconds: com.histmersion.pluginhost.HttpTransport.TimeoutSeconds
  ): Either[com.histmersion.pluginhost.HostAiHttp.Problem, AiHttp.Response] =
    send(
      url,
      headers,
      timeoutSeconds,
      _.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
    )

  def get(url: com.histmersion.pluginhost.HttpTransport.Url, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.pluginhost.HostAiHttp.GetHeadersPart2], timeoutSeconds: com.histmersion.pluginhost.HttpTransport.TimeoutSeconds): Either[com.histmersion.pluginhost.HostAiHttp.Problem, AiHttp.Response] =
    send(url, headers, timeoutSeconds, _.GET())

  private def send(
      url: com.histmersion.pluginhost.HttpTransport.Url,
      headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.pluginhost.HostAiHttp.SendHeadersPart2],
      timeoutSeconds: com.histmersion.pluginhost.HttpTransport.TimeoutSeconds,
      method: java.net.http.HttpRequest.Builder => java.net.http.HttpRequest.Builder
  ): Either[com.histmersion.pluginhost.HostAiHttp.Problem, AiHttp.Response] =
    try
      val b = java.net.http.HttpRequest
        .newBuilder(java.net.URI.create(url))
        .timeout(java.time.Duration.ofSeconds(math.max(1, timeoutSeconds).toLong))
      headers.foreach((k, v) => if !k.equalsIgnoreCase("user-agent") then { val _ = b.header(k, v) })
      val _ = b.header("User-Agent", userAgent)
      val r = client.send(method(b).build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      Right(
        AiHttp.Response(
          r.statusCode,
          r.body,
          r.headers.map.asScala.toMap.collect {
            case (k, v) if !v.isEmpty => k.toLowerCase(java.util.Locale.ROOT) -> v.get(0)
          }
        )
      )
    catch
      case e: InterruptedException => throw e
      // the message only: never the request, which may carry a key in a header
      case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))

object HostAiHttp:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestBody = RequestBody.T
  object RequestBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostHeadersPart2 = PostHeadersPart2.T
  object PostHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart2 = GetHeadersPart2.T
  object GetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendHeadersPart2 = SendHeadersPart2.T
  object SendHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
