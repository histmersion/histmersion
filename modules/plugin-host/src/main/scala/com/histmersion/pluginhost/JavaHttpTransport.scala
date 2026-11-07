package com.histmersion.pluginhost

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.Locale
import scala.jdk.CollectionConverters.*

/** The real client: `java.net.http`, following redirects, the URL used exactly as built (the provider URLs are already
  * percent-encoded). `timeoutSeconds` bounds the wait for an answer, `connectSeconds` (when given; else the same) the
  * connection.
  */
final class JavaHttpTransport(timeoutSeconds: => Int, connectSeconds: Option[JavaHttpTransport.ConnectSeconds] = None)
    extends HttpTransport: // by name: read per request
  private val client = HttpClient
    .newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(connectSeconds.getOrElse(timeoutSeconds).toLong.max(1)))
    .version(HttpClient.Version.HTTP_1_1)
    .build()

  def get(url: com.histmersion.pluginhost.HttpTransport.Url, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.pluginhost.JavaHttpTransport.GetHeadersPart2]): Either[com.histmersion.pluginhost.JavaHttpTransport.Problem, HttpAnswer] =
    scala.util
      .Try {
        val b = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(timeoutSeconds.toLong.max(1)))
        headers.foreach((k, v) => b.header(k, v))
        val resp = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
        val hs = resp
          .headers()
          .map()
          .asScala
          .toMap
          .flatMap((k, vs) => vs.asScala.headOption.map(v => k.toLowerCase(Locale.ROOT) -> v))
        HttpAnswer(resp.statusCode(), hs, resp.body())
      }
      .toEither
      .left
      .map(e => s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}")

object JavaHttpTransport:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConnectSeconds = ConnectSeconds.T
  object ConnectSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type GetHeadersPart2 = GetHeadersPart2.T
  object GetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
