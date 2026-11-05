package com.histmersion.pluginhost

import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.Uri

import java.util.Locale
import scala.concurrent.duration.*

/** The acquisition transport: java.net.http through sttp, which follows redirects. `tls` is the trust `acquire.tls`
  * configured ([[OutboundTls.configure]]); `None` keeps the client's default, the JDK's roots.
  */
final class SttpTransport(timeoutSeconds: SttpTransport.TimeoutSeconds, tls: Option[javax.net.ssl.SSLContext] = None) extends HttpTransport:
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

  def get(url: com.histmersion.pluginhost.SttpTransport.GetUrl, headers: Map[com.histmersion.pluginhost.SttpTransport.GetHeadersPart1, com.histmersion.pluginhost.SttpTransport.GetHeadersPart2]): Either[com.histmersion.pluginhost.SttpTransport.GetResultPart1, HttpAnswer] =
    val req = basicRequest
      .get(Uri.unsafeParse(url))
      .headers(headers)
      .readTimeout(timeoutSeconds.seconds)
      .response(asByteArrayAlways)
    scala.util
      .Try(req.send(backend))
      .toEither
      .left
      .map(e => s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}")
      .map(resp =>
        HttpAnswer(
          resp.code.code,
          resp.headers.reverse.map(h => h.name.toLowerCase(Locale.ROOT) -> h.value).toMap,
          resp.body
        )
      )

object SttpTransport:
  /** The same request, pinned to HTTP/1.1 (see the backend above). */
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type GetResultPart1 = GetResultPart1.T
  object GetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetUrl = GetUrl.T
  object GetUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart1 = GetHeadersPart1.T
  object GetHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetHeadersPart2 = GetHeadersPart2.T
  object GetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val http11: java.net.http.HttpRequest => java.net.http.HttpRequest = r =>
    java.net.http.HttpRequest.newBuilder(r, (_, _) => true).version(java.net.http.HttpClient.Version.HTTP_1_1).build()
