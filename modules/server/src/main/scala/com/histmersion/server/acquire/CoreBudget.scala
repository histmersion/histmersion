package com.histmersion.server.acquire

import com.histmersion.config.CoreServiceConfig
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.HttpAnswer

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal

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
      c.perMinute.value,
      c.dailyTokens.value,
      c.searchDailyTokens.value,
      c.lookupCost.value,
      c.searchCost.value,
      c.exhaustedPauseSeconds.value,
      c.maxWaitSeconds.value
    )
