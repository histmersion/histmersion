package com.histmersion.pluginhost

import com.histmersion.domain.HttpPolicy
import com.histmersion.config.MediaConfig

import io.circe.Json

import java.net.URI
import java.time.Instant
import scala.jdk.CollectionConverters.*

/** A polite client of one run: requests go one at a time, at least `request-interval-ms` apart per host; every request
  * carries the configured User-Agent (the project and its repository — no e-mail, no personal data); slow-down answers
  * are honoured ([[Politeness]]). `sleep` and `clock` are parameters so a test runs without waiting.
  *
  * `refusalBackoffMs` (#2561, a collection's `refusal-backoff-seconds`): a host that refuses (HTTP 403) after answering
  * is taken as rate limiting, not a ban — the request waits each step in turn and is asked again, and from the first
  * refusal the host is asked at half the pace (each further refusal halves it again, down to [[MaxSlowdown]] times
  * slower) for the rest of the run. When the steps run out the 403 stands: the caller stops. Empty (the default): a 403
  * is final at once.
  */
final class Polite(
    http: HttpTransport,
    cfg: MediaConfig,
    sleep: Long => Unit = ms => Thread.sleep(ms),
    clock: () => Long = () => System.currentTimeMillis(),
    refusalBackoffMs: List[Long] = Nil,
    log: HostLog = HostLog.scribe
):

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

  /** One GET, paced and retried. `headers` are sent as given, except the User-Agent: always the configured one (a
    * plugin's request too).
    */
  def get(url: String, headers: Map[String, String] = Map.empty): Either[String, HttpAnswer] =
    val h = host(url)
    val sent =
      headers.filterNot(_._1.equalsIgnoreCase("User-Agent")) + ("User-Agent" -> HttpPolicy.userAgent(cfg.userAgent))
    def attempt(n: Int, refusals: Int): Either[String, HttpAnswer] =
      pace(h)
      count += 1
      http.get(url, sent).flatMap { a =>
        Politeness.decide(a, n, cfg.maxRetries, cfg.maxRetryAfterSeconds, Instant.ofEpochMilli(clock())) match
          case Politeness.Step.Accept        => Right(a)
          case Politeness.Step.Wait(ms, why) =>
            log.info(s"media: $why from $h — waiting $ms ms")
            sleep(ms)
            attempt(n + 1, refusals)
          case Politeness.Step.GiveUp(why) if a.code == 403 && refusals < refusalBackoffMs.size =>
            val ms = refusalBackoffMs(refusals)
            slowdown(h) = (slowdown.getOrElse(h, 1) * 2).min(Polite.MaxSlowdown)
            log.warn(
              s"media: $why from $h — taken as rate limiting: waiting $ms ms, then asking at " +
                s"${intervalMs(h)} ms between requests"
            )
            sleep(ms)
            attempt(n, refusals + 1)
          case Politeness.Step.GiveUp(why) =>
            val waited = if a.code == 403 && refusals > 0 then
              s" (still, after waiting ${refusalBackoffMs.take(refusals).sum / 1000} s)"
            else ""
            Left(s"$h: $why$waited")
      }
    attempt(0, 0)

  /** One GET answered as JSON. `headers` as for [[get]] — a key-gated API's key travels in one, never in the URL (which
    * an error line or a log may show).
    */
  def json(url: String, headers: Map[String, String] = Map.empty): Either[String, Json] =
    get(url, headers).flatMap(a =>
      io.circe.parser.parse(a.text).left.map(e => s"${host(url)}: unparseable JSON (${e.getMessage})")
    )

  /** The bytes of a file and its type (the answer's Content-Type, else `fallbackMime`). */
  def file(url: String, fallbackMime: String): Either[String, (Array[Byte], String)] =
    get(url).map(a =>
      (
        a.body,
        a.header("content-type")
          .map(_.split(";").head.trim)
          .filter(_.nonEmpty)
          .getOrElse(fallbackMime)
      )
    )

object Polite:
  /** A host is never asked more than this many times slower than its configured pace. */
  val MaxSlowdown = 16
