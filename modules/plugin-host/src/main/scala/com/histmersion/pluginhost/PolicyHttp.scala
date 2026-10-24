package com.histmersion.pluginhost

import com.histmersion.core.{AppConfig, HttpPolicy}
import com.histmersion.plugin.PluginHttp
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.Uri

import java.util.Locale
import scala.concurrent.duration.*

/** A bot-protection CHALLENGE (Cloudflare's "Just a moment…" interstitial and its kin) rather than an
  * answer. Such a host is treated as having no copy: it is neither retried nor worked around. */
object Challenge:
  def isChallenge(code: Int, headers: Map[String, String], body: Array[Byte]): Boolean =
    val mitigated = headers.exists((k, v) => k.equalsIgnoreCase("cf-mitigated") && v.toLowerCase.contains("challenge"))
    val page = new String(body.take(4096), "ISO-8859-1").toLowerCase
    mitigated || isAnubis(body) || ((code == 403 || code == 429 || code == 503) &&
      (page.contains("<title>just a moment") || page.contains("challenge-platform") ||
        page.contains("<title>attention required")))

  /** An Anubis page (OAPEN's file server runs one): its proof-of-work challenge or its refusal, whatever
    * the status (200 by default, 401/403 as configured). Its markers are its own — the challenge's
    * `anubis_challenge` script, its assets under `/.within.website/x/` — and a file never starts with
    * them, so any status counts; a PDF is never read as one. */
  private def isAnubis(body: Array[Byte]): Boolean =
    val head = new String(body.take(32768), "ISO-8859-1").toLowerCase
    !head.startsWith("%pdf") &&
      (head.contains("id=\"anubis_challenge\"") || (head.contains("/.within.website/x/") && head.contains("anubis")))

/** The acquisition client: every request a source fetcher (a plugin's through [[PluginHttp]], or a
  * built-in one) and the metadata lookups make, over one [[HttpTransport]].
  *
  * Every request follows [[HttpPolicy]], the one outbound policy: the product's `User-Agent` (no
  * e-mail — Crossref and OpenAlex take the contact as their `mailto` parameter), a slow-down answer
  * (429, 502/503/504) asked again after its `Retry-After` or 1, 2, 4 … s, a host that asks for more
  * than `maxRetryAfterSeconds` left alone. An unreachable host is asked again on the same schedule.
  * A bot challenge ends the request ([[Challenge]]). `tries` counts the first request; `sleep` and
  * `clock` are parameters so a test does not wait. */
final class PolicyHttp(userAgent: String, transport: HttpTransport, tries: Int = 4, maxRetryAfterSeconds: Int = 60,
    sleep: Long => Unit = ms => Thread.sleep(ms), clock: () => java.time.Instant = () => java.time.Instant.now(),
    log: HostLog = HostLog.scribe):

  private val maxRetries = (tries - 1).max(0)

  /** One GET: the body of a 2xx answer, or why there is none. A caller's own `User-Agent` replaces the
    * configured one; either goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail. */
  def bytes(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]] =
    val ua   = headers.collectFirst { case (k, v) if k.equalsIgnoreCase(PolicyHttp.UserAgent) => v }
    val sent = headers.filterNot(_._1.equalsIgnoreCase(PolicyHttp.UserAgent)) +
      (PolicyHttp.UserAgent -> HttpPolicy.userAgent(ua.getOrElse(userAgent)))
    def again(n: Int, ms: Long, why: String): Either[String, Array[Byte]] =
      log.warn(s"acquire: $why — retrying $url in ${ms}ms")
      sleep(ms)
      attempt(n + 1)
    def attempt(n: Int): Either[String, Array[Byte]] =
      transport.get(url, sent) match
        case Left(unreachable) =>
          if n < maxRetries then again(n, HttpPolicy.backoffMs(n), unreachable) else Left(unreachable)
        case Right(answer) =>
          val code = answer.code
          if Challenge.isChallenge(code, answer.headers, answer.body) then
            Left(s"$url: HTTP $code bot-protection challenge — treated as no copy")
          else
            HttpPolicy.decide(code, answer.header("Retry-After"), n, maxRetries, maxRetryAfterSeconds, clock()) match
              case HttpPolicy.Step.Accept        => Right(answer.body)
              case HttpPolicy.Step.Wait(ms, why) => again(n, ms, why)
              case HttpPolicy.Step.GiveUp(why) if HttpPolicy.Throttled(code) => Left(s"$url failed: $why")
              case HttpPolicy.Step.GiveUp(_)     => Left(s"$url: HTTP $code ${new String(answer.body.take(200), "UTF-8")}")
    attempt(0)

  /** The client as the plugin SPI lends it: plugins get bytes, and parse what they asked for. */
  def plugin: PluginHttp = new PluginHttp:
    def get(url: String, headers: Map[String, String]) = bytes(url, headers)

object PolicyHttp:
  val UserAgent = "User-Agent"

  /** The acquisition client of a configuration — the ONE client every plugin's `ctx.http`, every
    * built-in fetcher and every metadata lookup of a generation share: `acquire.user-agent`, and the
    * media politeness's patience with `Retry-After`. */
  def acquisition(cfg: AppConfig, transport: HttpTransport, sleep: Long => Unit = ms => Thread.sleep(ms),
      log: HostLog = HostLog.scribe): PolicyHttp =
    PolicyHttp(cfg.acquire.userAgent, transport, maxRetryAfterSeconds = cfg.media.maxRetryAfterSeconds, sleep = sleep,
      log = log)

  /** The transport the acquisition client sends on: `acquire.timeout-seconds`, `acquire.tls`'s trust. */
  def acquisitionTransport(cfg: AppConfig): HttpTransport =
    SttpTransport(cfg.acquire.timeoutSeconds, OutboundTls.configure(cfg.acquire.tls).map(_._1))

/** The acquisition transport: java.net.http through sttp, which follows redirects. `tls` is the trust
  * `acquire.tls` configured ([[OutboundTls.configure]]); `None` keeps the client's default, the JDK's roots. */
final class SttpTransport(timeoutSeconds: Int, tls: Option[javax.net.ssl.SSLContext] = None) extends HttpTransport:
  // HTTP/1.1 on every request: java.net.http negotiates HTTP/2 by ALPN, and some hosts that offer h2
  // then drop the stream — Europe PMC's www.ebi.ac.uk answered every h2 request with "EOF reached while
  // reading" while HTTP/1.1 got a 200. One download per request gains nothing from h2's multiplexing.
  private val backend = tls.fold(HttpClientSyncBackend(customizeRequest = SttpTransport.http11)) { ctx =>
    // the client sttp would build (no redirects of its own — sttp follows them — and its connect
    // timeout), with the configured trust
    HttpClientSyncBackend.usingClient(
      java.net.http.HttpClient.newBuilder()
        .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
        .connectTimeout(java.time.Duration.ofMillis(BackendOptions.Default.connectionTimeout.toMillis))
        .sslContext(ctx)
        .build(),
      customizeRequest = SttpTransport.http11)
  }

  def get(url: String, headers: Map[String, String]): Either[String, HttpAnswer] =
    val req = basicRequest
      .get(Uri.unsafeParse(url))
      .headers(headers)
      .readTimeout(timeoutSeconds.seconds)
      .response(asByteArrayAlways)
    scala.util.Try(req.send(backend)).toEither.left.map(e => s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}")
      .map(resp => HttpAnswer(resp.code.code,
        resp.headers.reverse.map(h => h.name.toLowerCase(Locale.ROOT) -> h.value).toMap, resp.body))

object SttpTransport:
  /** The same request, pinned to HTTP/1.1 (see the backend above). */
  val http11: java.net.http.HttpRequest => java.net.http.HttpRequest = r =>
    java.net.http.HttpRequest.newBuilder(r, (_, _) => true).version(java.net.http.HttpClient.Version.HTTP_1_1).build()
