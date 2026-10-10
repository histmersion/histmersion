package com.histmersion.server.media

import com.histmersion.core.MediaConfig

import io.circe.Json

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Duration, Instant, ZonedDateTime}
import java.time.format.DateTimeFormatter
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
final class JavaMediaHttp(timeoutSeconds: Int) extends MediaHttp:
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

/** What to do with an answer: take it, wait and ask again, or give up on this request. Pure. */
object Politeness:

  enum Step:
    case Accept
    case Wait(ms: Long, why: String)
    case GiveUp(why: String)

  /** Does the answer say "slow down"? A 429, a 503/502/504, or MediaWiki's maxlag refusal — which
    * comes back as HTTP 200 with a `MediaWiki-API-Error: maxlag` header and `error.code = maxlag`. */
  def isMaxlag(a: HttpAnswer): Boolean =
    a.header("mediawiki-api-error").exists(_.trim.equalsIgnoreCase("maxlag")) ||
      (a.body.length < 4096 && a.text.contains("\"code\":\"maxlag\""))

  private val Throttled = Set(429, 502, 503, 504)

  /** Pure: the next step after the `attempt`-th answer (0-based). A slow-down waits for the answer's
    * Retry-After (else 1, 2, 4 … s), at most `maxRetries` times and never longer than
    * `maxRetryAfterSeconds` at once — a provider that asks for more is left alone for this run. */
  def decide(a: HttpAnswer, attempt: Int, maxRetries: Int, maxRetryAfterSeconds: Int, now: Instant): Step =
    val lag = isMaxlag(a)
    if Throttled(a.code) || lag then
      val why  = if lag then "maxlag" else s"HTTP ${a.code}"
      val wait = retryAfterMs(a.header("retry-after"), now).getOrElse((1L << attempt.min(10)) * 1000L).max(0L)
      if attempt >= maxRetries then Step.GiveUp(s"$why after ${maxRetries} retries")
      else if wait > maxRetryAfterSeconds.toLong * 1000L then Step.GiveUp(s"$why: asked to wait ${wait / 1000} s")
      else Step.Wait(wait, why)
    else if a.code >= 400 then Step.GiveUp(s"HTTP ${a.code}")
    else Step.Accept

  /** Pure: a Retry-After value — delay seconds or an HTTP date — as milliseconds from `now`. */
  def retryAfterMs(v: Option[String], now: Instant): Option[Long] =
    v.map(_.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLongOption.map(_ * 1000L).orElse(
        scala.util.Try(ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant).toOption
          .map(t => (t.toEpochMilli - now.toEpochMilli).max(0L)))
    }

/** A polite client of one run: requests go one at a time, at least `request-interval-ms` apart per
  * host; every request carries the configured User-Agent (the project and its repository — no
  * e-mail, no personal data); slow-down answers are honoured ([[Politeness]]). `sleep` and `clock` are
  * parameters so a test runs without waiting. */
final class Polite(http: MediaHttp, cfg: MediaConfig, sleep: Long => Unit = ms => Thread.sleep(ms),
    clock: () => Long = () => System.currentTimeMillis()):

  // `var`/mutable: the time of the last request per host and the request count — a run's own state
  private val last = scala.collection.mutable.Map.empty[String, Long]
  private var count = 0

  /** Requests sent so far (retries included). */
  def requests: Int = count

  private def host(url: String): String =
    scala.util.Try(URI.create(url).getHost).toOption.flatMap(Option(_)).getOrElse(url)

  private def pace(h: String): Unit =
    last.get(h).foreach { t =>
      val wait = t + cfg.requestIntervalMs.toLong - clock()
      if wait > 0 then sleep(wait)
    }
    last(h) = clock()

  def get(url: String): Either[String, HttpAnswer] =
    val h = host(url)
    def attempt(n: Int): Either[String, HttpAnswer] =
      pace(h)
      count += 1
      http.get(url, Map("User-Agent" -> cfg.userAgent)).flatMap { a =>
        Politeness.decide(a, n, cfg.maxRetries, cfg.maxRetryAfterSeconds, Instant.ofEpochMilli(clock())) match
          case Politeness.Step.Accept => Right(a)
          case Politeness.Step.Wait(ms, why) =>
            scribe.info(s"media: $why from $h — waiting ${ms} ms")
            sleep(ms)
            attempt(n + 1)
          case Politeness.Step.GiveUp(why) => Left(s"$h: $why")
      }
    attempt(0)

  def json(url: String): Either[String, Json] =
    get(url).flatMap(a => io.circe.parser.parse(a.text).left.map(e => s"${host(url)}: unparseable JSON (${e.getMessage})"))

  /** The bytes of a file and its type (the answer's Content-Type, else `fallbackMime`). */
  def file(url: String, fallbackMime: String): Either[String, (Array[Byte], String)] =
    get(url).map(a => (a.body, a.header("content-type").map(_.split(";").head.trim).filter(_.nonEmpty)
      .getOrElse(fallbackMime)))
