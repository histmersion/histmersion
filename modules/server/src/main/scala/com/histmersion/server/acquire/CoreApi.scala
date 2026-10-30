package com.histmersion.server.acquire

import com.histmersion.config.{AcquireConfig, CoreServiceConfig}
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.{HttpAnswer, Url}

import io.circe.Json

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import scala.util.control.NonFatal

/** Where the CORE budget is kept between restarts. */
trait CoreQuotaStore:
  def load(): Option[CoreQuota.State]
  def save(s: CoreQuota.State): Unit

object CoreQuotaStore:
  /** The `upstream_quota` row `core`. */
  def db(ds: DataSource): CoreQuotaStore = new CoreQuotaStore:
    def load() = com.histmersion.db.UpstreamQuotaRepo.load(ds, CoreBudget.Upstream)
    def save(s: CoreQuota.State) = com.histmersion.db.UpstreamQuotaRepo.save(ds, CoreBudget.Upstream, s)

/** The server's ONE budget for CORE ([[CoreQuota]]): the acquisition chain's `core` step and the new-source search's
  * `core` index both call CORE through it, across every plugin generation and config reload of the process — so a
  * reload does not reset it, and a restart reads it back from the store it was given ([[persistTo]]). `clock` and
  * `sleep` are parameters so a test does not wait.
  */
final class CoreBudget(clock: () => Instant = () => Instant.now(), sleep: Long => Unit = ms => Thread.sleep(ms)):

  private val lock = Object()
  private val current = AtomicReference(CoreQuota.State.fresh(CoreQuota.day(clock())))
  private val store = AtomicReference[Option[CoreQuotaStore]](None)

  /** The state now. */
  def state: CoreQuota.State = current.get

  /** Keep the budget in `s` from now on, starting from what it holds: the same day's spending and a block that has not
    * ended are kept (the larger count, the later block, when both sides have one).
    */
  def persistTo(s: CoreQuotaStore): Unit = lock.synchronized {
    val now = clock()
    val saved = attempt("read")(s.load()).flatten.map(CoreQuota.onDay(_, now))
    val mine = CoreQuota.onDay(current.get, now)
    val merged = saved.fold(mine) { o =>
      mine.copy(
        lookupTokens = mine.lookupTokens.max(o.lookupTokens),
        searchTokens = mine.searchTokens.max(o.searchTokens),
        block = (mine.block.toList ++ o.block.toList).maxByOption(_.until.toEpochMilli),
        window = mine.window.orElse(o.window)
      )
    }
    current.set(merged)
    store.set(Some(s))
    // nothing to keep until CORE was first asked: `about` shows the budget from then on
    if saved.isDefined || merged.spent > 0 || merged.block.isDefined then save(merged)
  }

  /** Why a call for `purpose` would not be made now — CORE asked to wait, or the day's budget is spent; `None` when it
    * may go (pacing aside: a slot comes within a minute). Nothing is counted.
    */
  def unavailable(limits: CoreQuota.Limits, purpose: CoreQuota.Purpose): Option[CoreQuota.Refusal] =
    CoreQuota.reserve(current.get, limits, purpose, clock()).left.toOption.filter {
      case CoreQuota.Refusal.Paced(_) => false
      case _                          => true
    }

  /** Make one call for `purpose` through the budget: `Left` when it is not made; else what `send` answered, which the
    * budget reads (a 429, CORE's rate-limit headers). The call is counted before it is sent, and waits its slot under
    * the per-minute pace first.
    */
  def call(limits: CoreQuota.Limits, purpose: CoreQuota.Purpose)(
      send: => Either[String, HttpAnswer]
  ): Either[CoreQuota.Refusal, Either[String, HttpAnswer]] =
    val granted = lock.synchronized {
      CoreQuota.reserve(current.get, limits, purpose, clock()).map { g =>
        current.set(g.state)
        save(g.state)
        g.waitMs
      }
    }
    granted.map { waitMs =>
      if waitMs > 0 then sleep(waitMs)
      val answer = send
      answer.foreach(a =>
        lock.synchronized {
          val next = CoreQuota.observe(current.get, limits, a.code, a.headers, clock())
          current.set(next)
          save(next)
        }
      )
      answer
    }

  private def save(s: CoreQuota.State): Unit =
    store.get.foreach(st => attempt("write")(st.save(s)))

  private def attempt[A](what: String)(a: => A): Option[A] =
    try Some(a)
    catch
      case NonFatal(e) =>
        scribe.warn(s"CORE budget: could not $what its stored state (${e.getMessage}) — it is kept in memory")
        None

object CoreBudget:
  /** Its row in `upstream_quota`. */
  val Upstream = "core"

  /** The limits `acquire.core.*` set. */
  def limits(c: CoreServiceConfig): CoreQuota.Limits =
    CoreQuota.Limits(
      c.perMinute,
      c.dailyTokens,
      c.searchDailyTokens,
      c.lookupCost,
      c.searchCost,
      c.exhaustedPauseSeconds,
      c.maxWaitSeconds
    )

/** Why a CORE call gave nothing. */
enum CoreFailure:
  /** Not asked, or refused for its rate: the budget or CORE itself said to wait. */
  case Skipped(why: String)

  /** Asked, and it failed (unreachable, an error answer, unparseable JSON). */
  case Failed(why: String)

  def message: String = this match
    case Skipped(why) => s"CORE not asked: $why"
    case Failed(why)  => why

/** CORE's `search/works` with a key, through the shared [[CoreBudget]] — the one way Histmersion calls CORE. Asked
  * once, never retried: the budget decides when CORE is asked again.
  */
final class CoreApi(cfg: AcquireConfig, http: Http, budget: CoreBudget):

  def limits: CoreQuota.Limits = CoreBudget.limits(cfg.core)

  /** Why `purpose` would not be asked now (CORE's block, the day's budget), if it would not. */
  def unavailable(purpose: CoreQuota.Purpose): Option[String] = budget.unavailable(limits, purpose).map(_.describe)

  def searchWorks(key: String, query: String, limit: Int, purpose: CoreQuota.Purpose): Either[CoreFailure, Json] =
    val url = Url(cfg.core.url, "/search/works/", "q" -> query, "limit" -> limit.toString)
    budget.call(limits, purpose)(http.once(url, Map("Authorization" -> s"Bearer $key"))) match
      case Left(refusal)                    => Left(CoreFailure.Skipped(refusal.describe))
      case Right(Left(e))                   => Left(CoreFailure.Failed(e))
      case Right(Right(a)) if a.code == 429 =>
        Left(
          CoreFailure.Skipped(
            budget.state.block.fold("CORE answered HTTP 429")(b =>
              s"CORE answered HTTP 429; not asked until ${b.until}"
            )
          )
        )
      case Right(Right(a)) if a.code >= 400 =>
        Left(CoreFailure.Failed(s"$url: HTTP ${a.code} ${new String(a.body.take(200), "UTF-8")}"))
      case Right(Right(a)) =>
        io.circe.parser
          .parse(new String(a.body, "UTF-8"))
          .left
          .map(e => CoreFailure.Failed(s"$url returned unparseable JSON: ${e.getMessage}"))
