package com.histmersion
package domain.library.acquisition

import com.histmersion.config.CoreServiceConfig
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.HostServices

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal
import com.histmersion.plugin.UpstreamQuota
import com.histmersion.domain.library.acquisition.CoreQuotaLend
import com.histmersion.lint.{tolerated, LintRule}

/** The server's ONE budget for CORE ([[CoreQuota]]): the acquisition chain's `core` step and the new-source search's
  * `core` index both call CORE through it, across every plugin generation and config reload of the process — so a
  * reload does not reset it, and a restart reads it back from the store it was given ([[persistTo]]). `clock` and
  * `sleep` are parameters so a test does not wait.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final class CoreBudget(clock: () => Instant, sleep: CoreQuota.Grant.WaitMs => Unit = ms => Thread.sleep(ms.value)):

  private val lock = Object()
  private val current = AtomicReference(CoreQuota.State.fresh(CoreQuota.day(clock())))
  private val store = AtomicReference[Option[CoreQuotaStore]](None)

  /** The state now. */
  def state: CoreQuota.State = current.get

  /** This budget as the plugin SPI lends it (`ctx.quota("core")`), under `limits`. */
  def lend(limits: CoreQuota.Limits): UpstreamQuota =
    CoreQuotaLend(this, limits)

  /** Keep the budget in `s` from now on, starting from what it holds: the same day's spending and a block that has not
    * ended are kept (the larger count, the later block, when both sides have one).
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def persistTo(s: CoreQuotaStore): Unit = lock.synchronized {
    val now = clock()
    val saved = attempt("read")(s.load()).flatten.map(CoreQuota.onDay(_, now))
    val mine = CoreQuota.onDay(current.get, now)
    val merged = saved.fold(mine) { o =>
      mine.copy(
        lookupTokens = CoreQuota.State.LookupTokens(mine.lookupTokens.value.max(o.lookupTokens.value)),
        searchTokens = CoreQuota.State.SearchTokens(mine.searchTokens.value.max(o.searchTokens.value)),
        block = (mine.block.toList ++ o.block.toList).maxByOption(_.until.toEpochMilli),
        window = mine.window.orElse(o.window)
      )
    }
    current.set(merged)
    store.set(Some(s))
    // nothing to keep until CORE was first asked: `about` shows the budget from then on
    if saved.isDefined || merged.spent.value > 0 || merged.block.isDefined then save(merged)
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def take(limits: CoreQuota.Limits, purpose: CoreQuota.Purpose): Either[CoreQuota.Refusal, Unit] =
    val granted = lock.synchronized {
      CoreQuota.reserve(current.get, limits, purpose, clock()).map { g =>
        current.set(g.state)
        save(g.state)
        g.waitMs
      }
    }
    granted.map(waitMs => if waitMs.value > 0 then sleep(waitMs))

  /** What CORE answered a call taken: a 429 and its rate-limit headers (names in lower case) block or pace it. */
  def observe(
      limits: CoreQuota.Limits,
      code: CoreQuota.StatusCode,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
  ): Unit =
    lock.synchronized {
      val next = CoreQuota.observe(current.get, limits, code, headers, clock())
      current.set(next)
      save(next)
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
  /** Its row in `upstream_quota`, and the upstream's name plugins ask the host's quota by. */
  val Upstream = UpstreamQuotaService.Upstream("core")

  /** The budgets the host lends plugins by upstream name: CORE's, `core` under `limits`; any other is unlimited. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def quotas(core: CoreBudget, limits: CoreQuota.Limits): HostServices.Quotas =
    name => if name == Upstream.value then core.lend(limits) else UpstreamQuota.Unlimited

  /** The limits `acquire.core.*` set. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def limits(c: CoreServiceConfig): CoreQuota.Limits =
    CoreQuota.Limits(
      CoreQuota.Limits.PerMinute(c.perMinute.value),
      CoreQuota.Limits.DailyTokens(c.dailyTokens.value),
      CoreQuota.Limits.SearchDailyTokens(c.searchDailyTokens.value),
      CoreQuota.Limits.LookupCost(c.lookupCost.value),
      CoreQuota.Limits.SearchCost(c.searchCost.value),
      CoreQuota.Limits.ExhaustedPauseSeconds(c.exhaustedPauseSeconds.value),
      CoreQuota.Limits.MaxWaitSeconds(c.maxWaitSeconds.value)
    )
