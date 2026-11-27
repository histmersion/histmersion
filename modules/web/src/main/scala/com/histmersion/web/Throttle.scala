package com.histmersion
package web

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Runs `f` at most once per `intervalMs`: the first call runs at once, calls inside the window collapse into one
  * trailing run with the latest arguments. Unlike a debounce it keeps firing during a continuous drag, so live views
  * update while the pointer moves.
  *
  * `now` and `later` are injectable so the timing can be tested without a browser clock.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final class Throttle[A](
    intervalMs: Throttle.IntervalMs,
    f: A => Unit,
    now: () => ResearchOverview.EpochMillis = () => ResearchOverview.EpochMillis(js.Date.now()),
    later: (Throttle.DelayMs, () => Unit) => Throttle.TimerId = (ms, g) =>
      Throttle.TimerId(dom.window.setTimeout(() => g(), ms.value)),
    cancel: Throttle.TimerId => Unit = id => dom.window.clearTimeout(id.value)
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private var lastRun: ResearchOverview.EpochMillis = ResearchOverview.EpochMillis(Double.NegativeInfinity)
  private var pending: Option[A] = None
  private var timer: Throttle.TimerId = Throttle.TimerId(0)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(a: A): Unit =
    val t = now()
    val wait = lastRun.value + intervalMs.value - t.value
    if wait <= 0 && pending.isEmpty then run(a, t)
    else
      val scheduled = pending.isDefined
      pending = Some(a)
      if !scheduled then timer = later(Throttle.DelayMs(wait.max(0)), () => pending.foreach(p => run(p, now())))

  /** Drop a scheduled trailing run. */
  def cancelPending(): Unit =
    if pending.isDefined then cancel(timer)
    pending = None

  private def run(a: A, t: ResearchOverview.EpochMillis): Unit =
    pending = None
    lastRun = t
    f(a)

object Throttle:
  type IntervalMs = IntervalMs.T
  object IntervalMs extends Newtype[Int, Pure]
  type DelayMs = DelayMs.T
  object DelayMs extends Newtype[Double, Pure]
  type TimerId = TimerId.T
  object TimerId extends Newtype[Int, Pure]
