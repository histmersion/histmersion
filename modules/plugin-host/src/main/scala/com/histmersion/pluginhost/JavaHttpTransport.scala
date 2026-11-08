package com.histmersion
package pluginhost

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.Locale
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.CoreQuota
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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

  def get(url: HttpTransport.Url, headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]): Either[HttpTransport.Problem, HttpAnswer] =
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
          .flatMap((k, vs) => vs.asScala.headOption.map(v => CoreQuota.HeaderName(k.toLowerCase(Locale.ROOT)) -> CoreQuota.HeaderValue(v)))
        HttpAnswer(HttpAnswer.Code(resp.statusCode()), hs, HttpAnswer.Body(resp.body()))
      }
      .toEither
      .left
      .map(e => HttpTransport.Problem(s"${url.value} unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}"))

object JavaHttpTransport:
  type ConnectSeconds = ConnectSeconds.T
  object ConnectSeconds extends RefinedType[Int, Pure]
