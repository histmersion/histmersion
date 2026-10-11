package com.histmersion.server.acquire

import com.histmersion.core.AcquireConfig

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
  /** A caller's own `User-Agent` (the PMC hosts get the project's public identity, never the contact
    * e-mail) replaces the polite-pool one. */
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
  * Politeness is the point of the headers: Crossref, OpenAlex and Unpaywall put requests that
  * identify themselves into a faster pool, and the identification they ask for is a contact e-mail in
  * the `User-Agent`. Rate limiting (429) and upstream hiccups (5xx) are retried with exponential
  * backoff, exactly as `lib.get_json` did.
  */
final class SttpHttp(cfg: AcquireConfig, tries: Int = 4) extends Http:
  // HTTP/1.1 on every request (#1523): java.net.http negotiates HTTP/2 by ALPN, and some hosts that
  // offer h2 then drop the stream — Europe PMC's www.ebi.ac.uk answered every h2 request with
  // "EOF reached while reading" (a ReadException after four retries) while HTTP/1.1 got a 200.
  // One download per request gains nothing from h2's multiplexing.
  private val backend = HttpClientSyncBackend(customizeRequest = SttpHttp.http11)

  private def send(url: String, headers: Map[String, String]): Either[String, Array[Byte]] =
    def attempt(n: Int): Either[String, Array[Byte]] =
      val ua  = headers.collectFirst { case (k, v) if k.equalsIgnoreCase(Http.UserAgent) => v }
      val req = basicRequest
        .get(Uri.unsafeParse(url))
        .header(Http.UserAgent, ua.getOrElse(cfg.politeUserAgent))
        .headers(headers.filterNot(_._1.equalsIgnoreCase(Http.UserAgent)))
        .readTimeout(cfg.timeoutSeconds.seconds)
        .response(asByteArrayAlways)
      def retryOr(why: String, giveUp: => String): Either[String, Array[Byte]] =
        if n + 1 < tries then
          backoff(n, url, why)
          attempt(n + 1)
        else Left(giveUp)

      scala.util.Try(req.send(backend)) match
        case scala.util.Failure(e) =>
          retryOr(e.getMessage, s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}")
        case scala.util.Success(resp) =>
          val code = resp.code.code
          if Http.isChallenge(code, resp.headers.map(h => h.name -> h.value).toMap, resp.body) then
            Left(s"$url: HTTP $code bot-protection challenge — treated as no copy")
          else if code == 429 || code >= 500 then
            retryOr(s"HTTP $code", s"$url failed after $tries attempts: HTTP $code")
          else if code >= 400 then Left(s"$url: HTTP $code ${new String(resp.body.take(200), "UTF-8")}")
          else Right(resp.body)
    attempt(0)

  private def backoff(n: Int, url: String, why: String): Unit =
    val wait = (1L << n) * 1000
    scribe.warn(s"acquire: $why — retrying $url in ${wait}ms")
    Thread.sleep(wait)

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
