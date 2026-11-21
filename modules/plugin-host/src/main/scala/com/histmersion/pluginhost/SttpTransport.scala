package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.plugin.Plugin
import com.histmersion.api.log.LogEvent
import com.histmersion.util.log.TraceScope
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.Uri

import java.util.Locale
import scala.concurrent.duration.*
import com.histmersion.domain.CoreQuota
import io.github.iltotore.iron.constraint.any.Pure

/** The acquisition transport: java.net.http through sttp, which follows redirects. `tls` is the trust `acquire.tls`
  * configured ([[OutboundTls.configure]]); `None` keeps the client's default, the JDK's roots.
  */
final class SttpTransport(timeoutSeconds: SttpTransport.TimeoutSeconds, tls: Option[javax.net.ssl.SSLContext] = None)
    extends HttpTransport:
  // HTTP/1.1 on every request: java.net.http negotiates HTTP/2 by ALPN, and some hosts that offer h2
  // then drop the stream — Europe PMC's www.ebi.ac.uk answered every h2 request with "EOF reached while
  // reading" while HTTP/1.1 got a 200. One download per request gains nothing from h2's multiplexing.
  private val backend = tls.fold(HttpClientSyncBackend(customizeRequest = SttpTransport.http11)) { ctx =>
    // the client sttp would build (no redirects of its own — sttp follows them — and its connect
    // timeout), with the configured trust
    HttpClientSyncBackend.usingClient(
      java.net.http.HttpClient
        .newBuilder()
        .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
        .connectTimeout(java.time.Duration.ofMillis(BackendOptions.Default.connectionTimeout.toMillis))
        .sslContext(ctx)
        .build(),
      customizeRequest = SttpTransport.http11
    )
  }

  def get(
      url: HttpTransport.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
  ): Either[Problem, HttpAnswer] =
    val req = basicRequest
      .get(Uri.unsafeParse(url.value))
      .headers(headers.map((k, v) => k.value -> v.value))
      .readTimeout(timeoutSeconds.value.seconds)
      .response(asByteArrayAlways)
    val started = System.nanoTime()
    val sent = scala.util.Try(req.send(backend))
    TraceScope.record(
      LogEvent.What.ProviderCalled(
        LogEvent.Upstream.of(url.upstream),
        LogEvent.Host.of(url.upstream),
        sent.toOption.map(r => LogEvent.Status.of(r.code)),
        LogEvent.Millis.between(started, System.nanoTime())
      )
    )
    sent.toEither.left
      .map(e => Problem.Upstream.Unreachable.here(url.upstream, url.address, External.Reported.of(e)))
      .map(resp =>
        HttpAnswer(
          HttpAnswer.Code(resp.code.code),
          resp.headers.reverse
            .map(h => CoreQuota.HeaderName(h.name.toLowerCase(Locale.ROOT)) -> CoreQuota.HeaderValue(h.value))
            .toMap,
          HttpAnswer.Body(resp.body)
        )
      )

object SttpTransport:
  /** The same request, pinned to HTTP/1.1 (see the backend above). */
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]

  val http11: java.net.http.HttpRequest => java.net.http.HttpRequest = r =>
    java.net.http.HttpRequest.newBuilder(r, (_, _) => true).version(java.net.http.HttpClient.Version.HTTP_1_1).build()
