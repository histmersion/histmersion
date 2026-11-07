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
    now: () => Throttle.NowResult = () => js.Date.now(),
    later: (Throttle.LaterArg1, () => Unit) => Throttle.LaterResult = (ms, g) => dom.window.setTimeout(() => g(), ms),
    cancel: Throttle.CancelArg1 => Unit = id => dom.window.clearTimeout(id)
):
  private var lastRun: com.histmersion.web.ResearchOverview.EpochMillis = Double.NegativeInfinity
  private var pending: Option[A] = None
  private var timer: Int = 0

  def apply(a: A): Unit =
    val t = now()
    val wait = lastRun + intervalMs - t
    if wait <= 0 && pending.isEmpty then run(a, t)
    else
      val scheduled = pending.isDefined
      pending = Some(a)
      if !scheduled then timer = later(wait.max(0), () => pending.foreach(p => run(p, now())))

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
  type NowResult = com.histmersion.web.ResearchOverview.EpochMillis
  val NowResult: _root_.com.histmersion.web.ResearchOverview.EpochMillis.type = _root_.com.histmersion.web.ResearchOverview.EpochMillis
  type LaterArg1 = com.histmersion.web.ResearchOverview.EpochMillis
  val LaterArg1: _root_.com.histmersion.web.ResearchOverview.EpochMillis.type = _root_.com.histmersion.web.ResearchOverview.EpochMillis
  type LaterResult = LaterResult.T
  object LaterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CancelArg1 = CancelArg1.T
  object CancelArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

