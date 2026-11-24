package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.plugin.Plugin
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.Locale
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.CoreQuota
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The real client: `java.net.http`, following redirects, the URL used exactly as built (the provider URLs are already
  * percent-encoded). `timeoutSeconds` bounds the wait for an answer, `connectSeconds` (when given; else the same) the
  * connection.
  */
@atBoundary(BoundaryKind.Http, "sends HTTP requests with java.net.http")
final class JavaHttpTransport(
    timeoutSeconds: => HttpTransport.TimeoutSeconds,
    connectSeconds: Option[JavaHttpTransport.ConnectSeconds] = None
) extends HttpTransport: // by name: read per request
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private val client = HttpClient
    .newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(connectSeconds.map(_.value).getOrElse(timeoutSeconds.value).toLong.max(1)))
    .version(HttpClient.Version.HTTP_1_1)
    .build()

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def get(
      url: HttpTransport.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
  ): Either[Problem, HttpAnswer] =
    scala.util
      .Try {
        val b = HttpRequest
          .newBuilder(URI.create(url.value))
          .GET()
          .timeout(Duration.ofSeconds(timeoutSeconds.value.toLong.max(1)))
        headers.foreach((k, v) => b.header(k.value, v.value))
        val resp = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
        val hs = resp
          .headers()
          .map()
          .asScala
          .toMap
          .flatMap((k, vs) =>
            vs.asScala.headOption.map(v => CoreQuota.HeaderName(k.toLowerCase(Locale.ROOT)) -> CoreQuota.HeaderValue(v))
          )
        HttpAnswer(HttpAnswer.Code(resp.statusCode()), hs, HttpAnswer.Body(resp.body()))
      }
      .toEither
      .left
      .map(e =>
        Problem.Upstream.Unreachable.here(
          Problem.Upstream
            .Name(scala.util.Try(URI.create(url.value).getHost).toOption.flatMap(Option(_)).getOrElse(s"$url")),
          Problem.Upstream.Url(s"$url"),
          External.Reported.of(e)
        )
      )

object JavaHttpTransport:
  type ConnectSeconds = ConnectSeconds.T
  object ConnectSeconds extends Newtype[Int, Pure]
