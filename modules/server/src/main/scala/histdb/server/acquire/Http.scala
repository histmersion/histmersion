package histdb.server.acquire

import histdb.core.AcquireConfig

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
  private val backend = HttpClientSyncBackend()

  private def send(url: String, headers: Map[String, String]): Either[String, Array[Byte]] =
    def attempt(n: Int): Either[String, Array[Byte]] =
      val req = basicRequest
        .get(Uri.unsafeParse(url))
        .header("User-Agent", cfg.politeUserAgent)
        .headers(headers)
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
          if code == 429 || code >= 500 then
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
