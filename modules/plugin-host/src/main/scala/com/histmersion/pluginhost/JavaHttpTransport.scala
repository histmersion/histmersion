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
final class JavaHttpTransport(timeoutSeconds: => HttpTransport.TimeoutSeconds, connectSeconds: Option[JavaHttpTransport.ConnectSeconds] = None)
    extends HttpTransport: // by name: read per request
  private val client = HttpClient
    .newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(connectSeconds.map(_.value).getOrElse(timeoutSeconds.value).toLong.max(1)))
    .version(HttpClient.Version.HTTP_1_1)
    .build()

  def get(url: HttpTransport.Url, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.domain.CoreQuota.HeaderValue]): Either[HttpTransport.Problem, HttpAnswer] =
    scala.util
      .Try {
        val b = HttpRequest.newBuilder(URI.create(url.value)).GET().timeout(Duration.ofSeconds(timeoutSeconds.value.toLong.max(1)))
        headers.foreach((k, v) => b.header(k.value, v.value))
        val resp = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
        val hs = resp
          .headers()
          .map()
          .asScala
          .toMap
          .flatMap((k, vs) => vs.asScala.headOption.map(v => com.histmersion.domain.CoreQuota.HeaderName(k.toLowerCase(Locale.ROOT)) -> com.histmersion.domain.CoreQuota.HeaderValue(v)))
        HttpAnswer(HttpAnswer.Code(resp.statusCode()), hs, HttpAnswer.Body(resp.body()))
      }
      .toEither
      .left
      .map(e => HttpTransport.Problem(s"${url.value} unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}"))

object JavaHttpTransport:
  type ConnectSeconds = ConnectSeconds.T
  object ConnectSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
