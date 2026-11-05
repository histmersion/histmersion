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
      url: com.histmersion.pluginhost.HostAiHttp.PostUrl,
      headers: Map[com.histmersion.pluginhost.HostAiHttp.PostHeadersPart1, com.histmersion.pluginhost.HostAiHttp.PostHeadersPart2],
      body: com.histmersion.pluginhost.HostAiHttp.PostBody,
      timeoutSeconds: com.histmersion.pluginhost.HostAiHttp.PostTimeoutSeconds
  ): Either[com.histmersion.pluginhost.HostAiHttp.PostResultPart1, AiHttp.Response] =
    send(
      url,
      headers,
      timeoutSeconds,
      _.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
    )

  def get(url: com.histmersion.pluginhost.HostAiHttp.GetUrl, headers: Map[com.histmersion.pluginhost.HostAiHttp.GetHeadersPart1, com.histmersion.pluginhost.HostAiHttp.GetHeadersPart2], timeoutSeconds: com.histmersion.pluginhost.HostAiHttp.GetTimeoutSeconds): Either[com.histmersion.pluginhost.HostAiHttp.GetResultPart1, AiHttp.Response] =
    send(url, headers, timeoutSeconds, _.GET())

  private def send(
      url: com.histmersion.pluginhost.HostAiHttp.SendUrl,
      headers: Map[com.histmersion.pluginhost.HostAiHttp.SendHeadersPart1, com.histmersion.pluginhost.HostAiHttp.SendHeadersPart2],
      timeoutSeconds: com.histmersion.pluginhost.HostAiHttp.SendTimeoutSeconds,
      method: java.net.http.HttpRequest.Builder => java.net.http.HttpRequest.Builder
  ): Either[com.histmersion.pluginhost.HostAiHttp.SendResultPart1, AiHttp.Response] =
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
  type PostResultPart1 = PostResultPart1.T
  object PostResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostUrl = PostUrl.T
  object PostUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostHeadersPart1 = PostHeadersPart1.T
  object PostHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostHeadersPart2 = PostHeadersPart2.T
  object PostHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostBody = PostBody.T
  object PostBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PostTimeoutSeconds = PostTimeoutSeconds.T
  object PostTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetResultPart1 = GetResultPart1.T
  object GetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetUrl = GetUrl.T
  object GetUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart1 = GetHeadersPart1.T
  object GetHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart2 = GetHeadersPart2.T
  object GetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetTimeoutSeconds = GetTimeoutSeconds.T
  object GetTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendResultPart1 = SendResultPart1.T
  object SendResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendUrl = SendUrl.T
  object SendUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendHeadersPart1 = SendHeadersPart1.T
  object SendHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendHeadersPart2 = SendHeadersPart2.T
  object SendHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SendTimeoutSeconds = SendTimeoutSeconds.T
  object SendTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
