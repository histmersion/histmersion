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
    sleep: Polite.IntervalMs => Unit = ms => Thread.sleep(ms.value),
    clock: () => Polite.EpochMs = () => Polite.EpochMs(System.currentTimeMillis()),
    refusalBackoffMs: List[Polite.RefusalBackoffMs] = Nil,
    log: HostLog = HostLog.scribe
):

  // `var`/mutable: the time of the last request per host, each host's slow-down after refusals, and the
  // request count — a run's own state
  private val last = scala.collection.mutable.Map.empty[Polite.Host, Polite.EpochMs]
  private val slowdown = scala.collection.mutable.Map.empty[Polite.Host, Int]
  private var count = 0

  /** The interval between two requests to `h` now, ms: the configured one, times its slow-down. */
  def intervalMs(h: com.histmersion.pluginhost.Polite.Host): com.histmersion.pluginhost.Polite.IntervalMs = Polite.IntervalMs(cfg.requestIntervalMs.value.toLong * slowdown.getOrElse(h, 1))

  /** Requests sent so far (retries included). */
  def requests: com.histmersion.pluginhost.Polite.RequestCount = Polite.RequestCount(count)

  private def host(url: com.histmersion.pluginhost.HttpTransport.Url): com.histmersion.pluginhost.Polite.Host =
    Polite.Host(scala.util.Try(URI.create(url.value).getHost).toOption.flatMap(Option(_)).getOrElse(url.value))

  private def pace(h: com.histmersion.pluginhost.Polite.Host): Unit =
    last.get(h).foreach { t =>
      val wait = t.value + intervalMs(h).value - clock().value
      if wait > 0 then sleep(Polite.IntervalMs(wait))
    }
    last(h) = clock()

  /** One GET, paced and retried. `headers` are sent as given, except the User-Agent: always the configured one (a
    * plugin's request too).
    */
  def get(url: com.histmersion.pluginhost.HttpTransport.Url, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, HttpTransport.GetHeadersPart2] = Map.empty): Either[HttpTransport.Problem, HttpAnswer] =
    val h = host(url)
    val sent =
      headers.filterNot(_._1.value.equalsIgnoreCase("User-Agent")) +
        (com.histmersion.domain.CoreQuota.HeaderName("User-Agent") ->
          HttpTransport.GetHeadersPart2(HttpPolicy.userAgent(HttpPolicy.UserAgent(cfg.userAgent.value)).value))
    def attempt(n: com.histmersion.domain.HttpPolicy.Attempt, refusals: Int): Either[HttpTransport.Problem, HttpAnswer] =
      pace(h)
      count += 1
      http.get(url, sent).flatMap { a =>
        Politeness.decide(
          a,
          n,
          HttpPolicy.MaxRetries(cfg.maxRetries.value),
          HttpPolicy.MaxRetryAfterSeconds(cfg.maxRetryAfterSeconds.value),
          Instant.ofEpochMilli(clock().value)
        ) match
          case HttpPolicy.Step.Accept        => Right(a)
          case HttpPolicy.Step.Wait(ms, why) =>
            log.info(HostLog.Line(s"media: ${why.value} from ${h.value} — waiting ${ms.value} ms"))
            sleep(Polite.IntervalMs(ms.value))
            attempt(HttpPolicy.Attempt(n.value + 1), refusals)
          case HttpPolicy.Step.GiveUp(why) if a.code.value == 403 && refusals < refusalBackoffMs.size =>
            val ms = refusalBackoffMs(refusals)
            slowdown(h) = (slowdown.getOrElse(h, 1) * 2).min(Polite.MaxSlowdown)
            log.warn(
              HostLog.Line(
                s"media: ${why.value} from ${h.value} — taken as rate limiting: waiting ${ms.value} ms, then asking at " +
                  s"${intervalMs(h).value} ms between requests"
              )
            )
            sleep(Polite.IntervalMs(ms.value))
            attempt(n, refusals + 1)
          case HttpPolicy.Step.GiveUp(why) =>
            val waited = if a.code.value == 403 && refusals > 0 then
              s" (still, after waiting ${refusalBackoffMs.take(refusals).map(_.value).sum / 1000} s)"
            else ""
            Left(HttpTransport.Problem(s"${h.value}: ${why.value}$waited"))
      }
    attempt(com.histmersion.domain.HttpPolicy.Attempt(0), 0)

  /** One GET answered as JSON. `headers` as for [[get]] — a key-gated API's key travels in one, never in the URL (which
    * an error line or a log may show).
    */
  def json(url: com.histmersion.pluginhost.HttpTransport.Url, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, HttpTransport.GetHeadersPart2] = Map.empty): Either[HttpTransport.Problem, Json] =
    get(url, headers).flatMap(a =>
      io.circe.parser.parse(a.text.value).left.map(e => HttpTransport.Problem(s"${host(url).value}: unparseable JSON (${e.getMessage})"))
    )

  /** The bytes of a file and its type (the answer's Content-Type, else `fallbackMime`). */
  def file(url: com.histmersion.pluginhost.HttpTransport.Url, fallbackMime: com.histmersion.pluginhost.Polite.Mime): Either[HttpTransport.Problem, (HttpAnswer.Body, com.histmersion.pluginhost.Polite.Mime)] =
    get(url).map(a =>
      (
        a.body,
        Polite.Mime(
          a.header(com.histmersion.domain.CoreQuota.HeaderName("content-type"))
            .map(_.value.split(";").head.trim)
            .filter(_.nonEmpty)
            .getOrElse(fallbackMime.value)
        )
      )
    )

object Polite:
  type Host = Host.T
  object Host extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IntervalMs = IntervalMs.T
  object IntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EpochMs = EpochMs.T
  object EpochMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mime = Mime.T
  object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestCount = RequestCount.T
  object RequestCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalBackoffMs = RefusalBackoffMs.T
  object RefusalBackoffMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A host is never asked more than this many times slower than its configured pace. */
  val MaxSlowdown = 16
