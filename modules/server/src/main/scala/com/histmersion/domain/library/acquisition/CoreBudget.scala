package com.histmersion.domain.library.acquisition

import com.histmersion.config.CoreServiceConfig
import com.histmersion.domain.CoreQuota

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal

/** The server's ONE budget for CORE ([[CoreQuota]]): the acquisition chain's `core` step and the new-source search's
  * `core` index both call CORE through it, across every plugin generation and config reload of the process — so a
  * reload does not reset it, and a restart reads it back from the store it was given ([[persistTo]]). `clock` and
  * `sleep` are parameters so a test does not wait.
  */
final class CoreBudget(clock: () => Instant, sleep: Long => Unit = ms => Thread.sleep(ms)):

  private val lock = Object()
  private val current = AtomicReference(CoreQuota.State.fresh(CoreQuota.day(clock())))
  private val store = AtomicReference[Option[CoreQuotaStore]](None)

  /** The state now. */
  def state: CoreQuota.State = current.get

  /** This budget as the plugin SPI lends it (`ctx.quota("core")`), under `limits`. */
  def lend(limits: CoreQuota.Limits): com.histmersion.plugin.UpstreamQuota =
    com.histmersion.domain.library.acquisition.CoreQuotaLend(this, limits)

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

  /** Take one call's share for `purpose`: `Left` when the call is not made (nothing is counted then). The call is
    * counted before it is sent, and waits its slot under the per-minute pace first; [[observe]] then reads what CORE
    * answered.
    */
  def take(limits: CoreQuota.Limits, purpose: CoreQuota.Purpose): Either[CoreQuota.Refusal, Unit] =
    val granted = lock.synchronized {
      CoreQuota.reserve(current.get, limits, purpose, clock()).map { g =>
        current.set(g.state)
        save(g.state)
        g.waitMs
      }
    }
    granted.map(waitMs => if waitMs > 0 then sleep(waitMs))

  /** What CORE answered a call taken: a 429 and its rate-limit headers (names in lower case) block or pace it. */
  def observe(limits: CoreQuota.Limits, code: com.histmersion.domain.library.acquisition.CoreBudget.ObserveCode, headers: Map[com.histmersion.domain.library.acquisition.CoreBudget.ObserveHeadersPart1, com.histmersion.domain.library.acquisition.CoreBudget.ObserveHeadersPart2]): Unit =
    lock.synchronized {
      val next = CoreQuota.observe(current.get, limits, code, headers, clock())
      current.set(next)
      save(next)
    }

  private def save(s: CoreQuota.State): Unit =
    store.get.foreach(st => attempt("write")(st.save(s)))

  private def attempt[A](what: com.histmersion.domain.library.acquisition.CoreBudget.AttemptWhat)(a: => A): Option[A] =
    try Some(a)
    catch
      case NonFatal(e) =>
        scribe.warn(s"CORE budget: could not $what its stored state (${e.getMessage}) — it is kept in memory")
        None

object CoreBudget:
  /** Its row in `upstream_quota`, and the upstream's name plugins ask the host's quota by. */
  type ObserveCode = ObserveCode.T
  object ObserveCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ObserveHeadersPart1 = ObserveHeadersPart1.T
  object ObserveHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ObserveHeadersPart2 = ObserveHeadersPart2.T
  object ObserveHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptWhat = AttemptWhat.T
  object AttemptWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotasResultArg1 = QuotasResultArg1.T
  object QuotasResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Upstream = "core"

  /** The budgets the host lends plugins by upstream name: CORE's, `core` under `limits`; any other is unlimited. */
  def quotas(core: CoreBudget, limits: CoreQuota.Limits): com.histmersion.domain.library.acquisition.CoreBudget.QuotasResultArg1 => com.histmersion.plugin.UpstreamQuota =
    name => if name == Upstream then core.lend(limits) else com.histmersion.plugin.UpstreamQuota.Unlimited

  /** The limits `acquire.core.*` set. */
  def limits(c: CoreServiceConfig): CoreQuota.Limits =
    CoreQuota.Limits(
      c.perMinute.value,
      c.dailyTokens.value,
      c.searchDailyTokens.value,
      c.lookupCost.value,
      c.searchCost.value,
      c.exhaustedPauseSeconds.value,
      c.maxWaitSeconds.value
    )
