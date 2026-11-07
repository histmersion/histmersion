package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** Runs `f` at most once per `intervalMs`: the first call runs at once, calls inside the window collapse into one
  * trailing run with the latest arguments. Unlike a debounce it keeps firing during a continuous drag, so live views
  * update while the pointer moves.
  *
  * `now` and `later` are injectable so the timing can be tested without a browser clock.
  */
final class Throttle[A](
    intervalMs: Throttle.IntervalMs,
    f: A => Unit,
    now: () => ResearchOverview.EpochMillis = () => ResearchOverview.EpochMillis(js.Date.now()),
    later: (Throttle.DelayMs, () => Unit) => Throttle.TimerId = (ms, g) =>
      Throttle.TimerId(dom.window.setTimeout(() => g(), ms.value)),
    cancel: Throttle.TimerId => Unit = id => dom.window.clearTimeout(id.value)
):
  private var lastRun: com.histmersion.web.ResearchOverview.EpochMillis = ResearchOverview.EpochMillis(Double.NegativeInfinity)
  private var pending: Option[A] = None
  private var timer: Throttle.TimerId = Throttle.TimerId(0)

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

  private def run(a: A, t: com.histmersion.web.ResearchOverview.EpochMillis): Unit =
    pending = None
    lastRun = t
    f(a)

object Throttle:
  type IntervalMs = IntervalMs.T
  object IntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DelayMs = DelayMs.T
  object DelayMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimerId = TimerId.T
  object TimerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

