package com.histmersion
package pluginhost
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence
import com.histmersion.plugin.Plugin
import com.histmersion.domain.HttpPolicy
import com.histmersion.config.MediaConfig

import java.net.URI
import java.time.Instant
import scala.jdk.CollectionConverters.*
import com.histmersion.domain.CoreQuota
import io.github.iltotore.iron.constraint.any.Pure

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
  def intervalMs(h: Polite.Host): Polite.IntervalMs =
    Polite.IntervalMs(cfg.requestIntervalMs.value.toLong * slowdown.getOrElse(h, 1))

  /** Requests sent so far (retries included). */
  def requests: Polite.RequestCount = Polite.RequestCount(count)

  private def host(url: HttpTransport.Url): Polite.Host =
    Polite.Host(scala.util.Try(URI.create(url.value).getHost).toOption.flatMap(Option(_)).getOrElse(url.value))

  private def pace(h: Polite.Host): Unit =
    last.get(h).foreach { t =>
      val wait = t.value + intervalMs(h).value - clock().value
      if wait > 0 then sleep(Polite.IntervalMs(wait))
    }
    last(h) = clock()

  /** One GET, paced and retried. `headers` are sent as given, except the User-Agent: always the configured one (a
    * plugin's request too).
    */
  def get(
      url: HttpTransport.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Plugin.Http.Failure, HttpAnswer] =
    val h = host(url)
    val sent =
      headers.filterNot(_._1.value.equalsIgnoreCase("User-Agent")) +
        (CoreQuota.HeaderName("User-Agent") ->
          CoreQuota.HeaderValue(HttpPolicy.userAgent(HttpPolicy.UserAgent(cfg.userAgent.value)).value))
    def attempt(n: HttpPolicy.Attempt, refusals: Int): Either[Plugin.Http.Failure, HttpAnswer] =
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
            log.info(HostLog.Line(s"media: ${why.sentence} from ${h.value} — waiting ${ms.value} ms"))
            sleep(Polite.IntervalMs(ms.value))
            attempt(HttpPolicy.Attempt(n.value + 1), refusals)
          case HttpPolicy.Step.GiveUp(why) if a.code.value == 403 && refusals < refusalBackoffMs.size =>
            val ms = refusalBackoffMs(refusals)
            slowdown(h) = (slowdown.getOrElse(h, 1) * 2).min(Polite.MaxSlowdown)
            log.warn(
              HostLog.Line(
                s"media: ${why.sentence} from ${h.value} — taken as rate limiting: waiting ${ms.value} ms, then asking at " +
                  s"${intervalMs(h).value} ms between requests"
              )
            )
            sleep(Polite.IntervalMs(ms.value))
            attempt(n, refusals + 1)
          case HttpPolicy.Step.GiveUp(why) =>
            val waited = if a.code.value == 403 && refusals > 0 then
              s" (still, after waiting ${refusalBackoffMs.take(refusals).map(_.value).sum / 1000} s)"
            else ""
            Left(
              Plugin.Http.Failure.Status(
                a.code.status,
                Problem.Unclassified(Legacy.Text(s"${h.value}: ${why.sentence}$waited"))
              )
            )
      }
    attempt(HttpPolicy.Attempt(0), 0)

  /** One GET answered as JSON. `headers` as for [[get]] — a key-gated API's key travels in one, never in the URL (which
    * an error line or a log may show).
    */
  def json(
      url: HttpTransport.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue] = Map.empty
  ): Either[Plugin.Http.Failure, Json] =
    get(url, headers).flatMap(a =>
      RawJson
        .parse(a.text.value)
        .left
        .map(e =>
          Plugin.Http.Failure
            .Transport(Problem.Unclassified(Legacy.Text(s"${host(url).value}: unparseable JSON ($e)")))
        )
    )

  /** The bytes of a file and its type (the answer's Content-Type, else `fallbackMime`). */
  def file(
      url: HttpTransport.Url,
      fallbackMime: Polite.Mime
  ): Either[Plugin.Http.Failure, (HttpAnswer.Body, Polite.Mime)] =
    get(url).map(a =>
      (
        a.body,
        Polite.Mime(
          a.header(CoreQuota.HeaderName("content-type"))
            .map(_.value.split(";").head.trim)
            .filter(_.nonEmpty)
            .getOrElse(fallbackMime.value)
        )
      )
    )

object Polite:
  type Host = Host.T
  object Host extends Newtype[String, Pure]
  type IntervalMs = IntervalMs.T
  object IntervalMs extends Newtype[Long, Pure]
  type EpochMs = EpochMs.T
  object EpochMs extends Newtype[Long, Pure]
  type Mime = Mime.T
  object Mime extends Newtype[String, Pure]
  type RequestCount = RequestCount.T
  object RequestCount extends Newtype[Int, Pure]
  type RefusalBackoffMs = RefusalBackoffMs.T
  object RefusalBackoffMs extends Newtype[Long, Pure]

  /** A host is never asked more than this many times slower than its configured pace. */
  val MaxSlowdown = 16
