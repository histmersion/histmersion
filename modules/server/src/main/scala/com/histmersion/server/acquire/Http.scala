package com.histmersion.server.acquire

import com.histmersion.core.{AcquireConfig, HttpPolicy}

import io.circe.Json
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.Uri

import scala.concurrent.duration.*

/** The ONE seam between the acquisition toolset and the network.
  *
  * Every upstream client is written against this trait, so a test supplies a fake that answers from a
  * captured fixture and never opens a socket. It is deliberately tiny — three verbs — because the
  * interesting logic is in the pure parsers, not here.
  *
  * Failures are `Left(message)`, never exceptions: an unreachable API is an expected condition for a
  * tool whose whole job is talking to flaky third parties.
  */
trait Http:
  def json(url: String, headers: Map[String, String] = Map.empty): Either[String, Json]
  def text(url: String, headers: Map[String, String] = Map.empty): Either[String, String]
  def bytes(url: String, headers: Map[String, String] = Map.empty): Either[String, Array[Byte]]

object Http:
  /** A caller's own `User-Agent` (the PMC hosts get the project's public identity) replaces the
    * acquisition one; either goes through [[HttpPolicy.userAgent]], so neither can carry an e-mail. */
  val UserAgent = "User-Agent"

  /** A bot-protection CHALLENGE (Cloudflare's "Just a moment…" interstitial and its kin) rather than
    * an answer. Such a host is treated as having no copy: it is neither retried nor worked around. */
  def isChallenge(code: Int, headers: Map[String, String], body: Array[Byte]): Boolean =
    val mitigated = headers.exists((k, v) => k.equalsIgnoreCase("cf-mitigated") && v.toLowerCase.contains("challenge"))
    val page = new String(body.take(4096), "ISO-8859-1").toLowerCase
    mitigated || ((code == 403 || code == 429 || code == 503) &&
      (page.contains("<title>just a moment") || page.contains("challenge-platform") ||
        page.contains("<title>attention required")))

/** URL assembly that keeps query values escaped. `Uri`'s interpolator would try to parse the value as
  * a URI; building the query from pairs is what makes `q=sea peoples & co` safe. */
object Url:
  def apply(base: String, path: String = "", params: (String, String)*): String =
    val u = Uri.unsafeParse(base + path)
    u.addParams(params.filter(_._2.nonEmpty)*).toString

/** The real client: java.net.http through sttp (the same backend `cli/Client.scala` uses, so the
  * native image needs no new reachability metadata beyond what the CLI already captures).
  *
  * Every request follows [[HttpPolicy]], the one outbound policy: the product's `User-Agent` (no
  * e-mail — Crossref and OpenAlex take the contact as their `mailto` parameter), a slow-down answer
  * (429, 502/503/504) asked again after its `Retry-After` or 1, 2, 4 … s, a host that asks for more
  * than `maxRetryAfterSeconds` left alone. An unreachable host is asked again on the same schedule.
  * `tries` counts the first request; `sleep` and `clock` are parameters so a test does not wait.
  * `tls` is the trust `acquire.tls` configured ([[OutboundTls.configure]]); `None` keeps the client's
  * default, the JDK's roots.
  */
final class SttpHttp(cfg: AcquireConfig, tries: Int = 4, maxRetryAfterSeconds: Int = 60,
    sleep: Long => Unit = ms => Thread.sleep(ms), clock: () => java.time.Instant = () => java.time.Instant.now(),
    tls: Option[javax.net.ssl.SSLContext] = None)
    extends Http:
  // HTTP/1.1 on every request (#1523): java.net.http negotiates HTTP/2 by ALPN, and some hosts that
  // offer h2 then drop the stream — Europe PMC's www.ebi.ac.uk answered every h2 request with
  // "EOF reached while reading" (a ReadException after four retries) while HTTP/1.1 got a 200.
  // One download per request gains nothing from h2's multiplexing.
  private val backend = tls.fold(HttpClientSyncBackend(customizeRequest = SttpHttp.http11)) { ctx =>
    // the client sttp would build (no redirects of its own — sttp follows them — and its connect
    // timeout), with the configured trust
    HttpClientSyncBackend.usingClient(
      java.net.http.HttpClient.newBuilder()
        .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
        .connectTimeout(java.time.Duration.ofMillis(BackendOptions.Default.connectionTimeout.toMillis))
        .sslContext(ctx)
        .build(),
      customizeRequest = SttpHttp.http11)
  }

  private val maxRetries = (tries - 1).max(0)

  private def send(url: String, headers: Map[String, String]): Either[String, Array[Byte]] =
    def attempt(n: Int): Either[String, Array[Byte]] =
      val ua  = headers.collectFirst { case (k, v) if k.equalsIgnoreCase(Http.UserAgent) => v }
      val req = basicRequest
        .get(Uri.unsafeParse(url))
        .header(Http.UserAgent, HttpPolicy.userAgent(ua.getOrElse(cfg.userAgent)))
        .headers(headers.filterNot(_._1.equalsIgnoreCase(Http.UserAgent)))
        .readTimeout(cfg.timeoutSeconds.seconds)
        .response(asByteArrayAlways)
      def again(ms: Long, why: String): Either[String, Array[Byte]] =
        scribe.warn(s"acquire: $why — retrying $url in ${ms}ms")
        sleep(ms)
        attempt(n + 1)

      scala.util.Try(req.send(backend)) match
        case scala.util.Failure(e) =>
          if n < maxRetries then again(HttpPolicy.backoffMs(n), e.getMessage)
          else Left(s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}")
        case scala.util.Success(resp) =>
          val code = resp.code.code
          if Http.isChallenge(code, resp.headers.map(h => h.name -> h.value).toMap, resp.body) then
            Left(s"$url: HTTP $code bot-protection challenge — treated as no copy")
          else
            HttpPolicy.decide(code, resp.header("Retry-After"), n, maxRetries, maxRetryAfterSeconds, clock()) match
              case HttpPolicy.Step.Accept        => Right(resp.body)
              case HttpPolicy.Step.Wait(ms, why) => again(ms, why)
              case HttpPolicy.Step.GiveUp(why) if HttpPolicy.Throttled(code) => Left(s"$url failed: $why")
              case HttpPolicy.Step.GiveUp(_)     => Left(s"$url: HTTP $code ${new String(resp.body.take(200), "UTF-8")}")
    attempt(0)

  def bytes(url: String, headers: Map[String, String]): Either[String, Array[Byte]] = send(url, headers)

  def text(url: String, headers: Map[String, String]): Either[String, String] =
    send(url, headers).map(new String(_, "UTF-8"))

  def json(url: String, headers: Map[String, String]): Either[String, Json] =
    text(url, headers).flatMap(s =>
      io.circe.parser.parse(s).left.map(e => s"$url returned unparseable JSON: ${e.getMessage}"))

object SttpHttp:
  /** The same request, pinned to HTTP/1.1 (see the backend above). */
  val http11: java.net.http.HttpRequest => java.net.http.HttpRequest = r =>
    java.net.http.HttpRequest.newBuilder(r, (_, _) => true).version(java.net.http.HttpClient.Version.HTTP_1_1).build()
