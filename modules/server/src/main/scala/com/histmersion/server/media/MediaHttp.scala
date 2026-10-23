package com.histmersion.server.media

import com.histmersion.core.{HttpPolicy, MediaConfig}

import io.circe.Json

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Duration, Instant}
import java.util.Locale
import scala.jdk.CollectionConverters.*

/** One HTTP answer: the status, the headers (names in lower case, first value) and the body. */
final case class HttpAnswer(code: Int, headers: Map[String, String], body: Array[Byte]):
  def header(name: String): Option[String] = headers.get(name.toLowerCase(Locale.ROOT))
  def text: String                         = new String(body, UTF_8)

/** The ONE seam between the media providers and the network: a test supplies a fake that answers
  * from fixtures and never opens a socket. Failures to talk at all are `Left`; any HTTP answer, a 429
  * included, is a `Right` — [[Polite]] decides what it means. */
trait MediaHttp:
  def get(url: String, headers: Map[String, String]): Either[String, HttpAnswer]

/** The real client: `java.net.http`, following redirects, the URL used exactly as built (the provider
  * URLs are already percent-encoded). */
final class JavaMediaHttp(timeoutSeconds: => Int) extends MediaHttp: // by name: read per request
  private val client = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(timeoutSeconds.toLong.max(1)))
    .version(HttpClient.Version.HTTP_1_1)
    .build()

  def get(url: String, headers: Map[String, String]): Either[String, HttpAnswer] =
    scala.util.Try {
      val b = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(timeoutSeconds.toLong.max(1)))
      headers.foreach((k, v) => b.header(k, v))
      val resp = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
      val hs = resp.headers().map().asScala.toMap.flatMap((k, vs) =>
        vs.asScala.headOption.map(v => k.toLowerCase(Locale.ROOT) -> v))
      HttpAnswer(resp.statusCode(), hs, resp.body())
    }.toEither.left.map(e => s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}")

/** What to do with a media or catalogue answer: [[HttpPolicy]], the one outbound policy, plus
  * MediaWiki's `maxlag` refusal (a 200 that means "slow down"). Pure. */
object Politeness:

  type Step = HttpPolicy.Step
  val Step = HttpPolicy.Step

  /** Does the answer say "slow down"? A 429, a 503/502/504, or MediaWiki's maxlag refusal — which
    * comes back as HTTP 200 with a `MediaWiki-API-Error: maxlag` header and `error.code = maxlag`. */
  def isMaxlag(a: HttpAnswer): Boolean =
    a.header("mediawiki-api-error").exists(_.trim.equalsIgnoreCase("maxlag")) ||
      (a.body.length < 4096 && a.text.contains("\"code\":\"maxlag\""))

  /** Pure: the next step after the `attempt`-th answer (0-based) — [[HttpPolicy.decide]]. */
  def decide(a: HttpAnswer, attempt: Int, maxRetries: Int, maxRetryAfterSeconds: Int, now: Instant): Step =
    HttpPolicy.decide(a.code, a.header("retry-after"), attempt, maxRetries, maxRetryAfterSeconds, now,
      Option.when(isMaxlag(a))("maxlag"))

  /** Pure: a Retry-After value as milliseconds from `now` — [[HttpPolicy.retryAfterMs]]. */
  def retryAfterMs(v: Option[String], now: Instant): Option[Long] = HttpPolicy.retryAfterMs(v, now)

/** A polite client of one run: requests go one at a time, at least `request-interval-ms` apart per
  * host; every request carries the configured User-Agent (the project and its repository — no
  * e-mail, no personal data); slow-down answers are honoured ([[Politeness]]). `sleep` and `clock` are
  * parameters so a test runs without waiting.
  *
  * `refusalBackoffMs` (#2561, a collection's `refusal-backoff-seconds`): a host that refuses (HTTP 403)
  * after answering is taken as rate limiting, not a ban — the request waits each step in turn and is
  * asked again, and from the first refusal the host is asked at half the pace (each further refusal
  * halves it again, down to [[MaxSlowdown]] times slower) for the rest of the run. When the steps run
  * out the 403 stands: the caller stops. Empty (the default): a 403 is final at once. */
final class Polite(http: MediaHttp, cfg: MediaConfig, sleep: Long => Unit = ms => Thread.sleep(ms),
    clock: () => Long = () => System.currentTimeMillis(), refusalBackoffMs: List[Long] = Nil):

  // `var`/mutable: the time of the last request per host, each host's slow-down after refusals, and the
  // request count — a run's own state
  private val last = scala.collection.mutable.Map.empty[String, Long]
  private val slowdown = scala.collection.mutable.Map.empty[String, Int]
  private var count = 0

  /** The interval between two requests to `h` now, ms: the configured one, times its slow-down. */
  def intervalMs(h: String): Long = cfg.requestIntervalMs.toLong * slowdown.getOrElse(h, 1)

  /** Requests sent so far (retries included). */
  def requests: Int = count

  private def host(url: String): String =
    scala.util.Try(URI.create(url).getHost).toOption.flatMap(Option(_)).getOrElse(url)

  private def pace(h: String): Unit =
    last.get(h).foreach { t =>
      val wait = t + intervalMs(h) - clock()
      if wait > 0 then sleep(wait)
    }
    last(h) = clock()

  /** One GET, paced and retried. `headers` are sent as given, except the User-Agent: always the
    * configured one (a plugin's request too). */
  def get(url: String, headers: Map[String, String] = Map.empty): Either[String, HttpAnswer] =
    val h = host(url)
    val sent = headers.filterNot(_._1.equalsIgnoreCase("User-Agent")) + ("User-Agent" -> HttpPolicy.userAgent(cfg.userAgent))
    def attempt(n: Int, refusals: Int): Either[String, HttpAnswer] =
      pace(h)
      count += 1
      http.get(url, sent).flatMap { a =>
        Politeness.decide(a, n, cfg.maxRetries, cfg.maxRetryAfterSeconds, Instant.ofEpochMilli(clock())) match
          case Politeness.Step.Accept => Right(a)
          case Politeness.Step.Wait(ms, why) =>
            scribe.info(s"media: $why from $h — waiting ${ms} ms")
            sleep(ms)
            attempt(n + 1, refusals)
          case Politeness.Step.GiveUp(why) if a.code == 403 && refusals < refusalBackoffMs.size =>
            val ms = refusalBackoffMs(refusals)
            slowdown(h) = (slowdown.getOrElse(h, 1) * 2).min(Polite.MaxSlowdown)
            scribe.warn(s"media: $why from $h — taken as rate limiting: waiting ${ms} ms, then asking at " +
              s"${intervalMs(h)} ms between requests")
            sleep(ms)
            attempt(n, refusals + 1)
          case Politeness.Step.GiveUp(why) =>
            val waited = if a.code == 403 && refusals > 0 then s" (still, after waiting ${refusalBackoffMs.take(refusals).sum / 1000} s)" else ""
            Left(s"$h: $why$waited")
      }
    attempt(0, 0)

  def json(url: String): Either[String, Json] =
    get(url).flatMap(a => io.circe.parser.parse(a.text).left.map(e => s"${host(url)}: unparseable JSON (${e.getMessage})"))

  /** The bytes of a file and its type (the answer's Content-Type, else `fallbackMime`). */
  def file(url: String, fallbackMime: String): Either[String, (Array[Byte], String)] =
    get(url).map(a => (a.body, a.header("content-type").map(_.split(";").head.trim).filter(_.nonEmpty)
      .getOrElse(fallbackMime)))

object Polite:
  /** A host is never asked more than this many times slower than its configured pace. */
  val MaxSlowdown = 16
