package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** Runs `f` at most once per `intervalMs`: the first call runs at once, calls inside the window
  * collapse into one trailing run with the latest arguments. Unlike a debounce it keeps firing
  * during a continuous drag, so live views update while the pointer moves.
  *
  * `now` and `later` are injectable so the timing can be tested without a browser clock. */
final class Throttle[A](
    intervalMs: Int,
    f: A => Unit,
    now: () => Double = () => js.Date.now(),
    later: (Double, () => Unit) => Int = (ms, g) => dom.window.setTimeout(() => g(), ms),
    cancel: Int => Unit = id => dom.window.clearTimeout(id)
):
  private var lastRun: Double       = Double.NegativeInfinity
  private var pending: Option[A]    = None
  private var timer: Int            = 0

  def apply(a: A): Unit =
    val t = now()
    val wait = lastRun + intervalMs - t
    if wait <= 0 && pending.isEmpty then run(a, t)
    else
      val scheduled = pending.isDefined
      pending = Some(a)
      if !scheduled then
        timer = later(wait.max(0), () => pending.foreach(p => run(p, now())))

  /** Drop a scheduled trailing run. */
  def cancelPending(): Unit =
    if pending.isDefined then cancel(timer)
    pending = None

  private def run(a: A, t: Double): Unit =
    pending = None
    lastRun = t
    f(a)

/** One request in flight, and the NEWEST wish waiting behind it: `apply(a)` starts `run(a, done)` at
  * once when nothing runs; while one runs, further calls only replace the waiting value, and when the
  * running one calls `done` the newest waiting value (if any) starts. Values in between are never
  * fetched — a playback ticking 60 times a second against a 20 ms answer asks ~ 50 times, not 60, and
  * a slow answer never piles up a queue of stale ones behind it. */
final class LatestOnly[A](run: (A, () => Unit) => Unit):
  // `var`s: whether a run is in flight, and the newest value waiting for it
  private var busy: Boolean         = false
  private var waiting: Option[A]    = None

  def apply(a: A): Unit =
    if busy then waiting = Some(a)
    else start(a)

  /** Forget the waiting value (playback stopped): the run in flight finishes, nothing follows it. */
  def drop(): Unit = waiting = None

  private def start(a: A): Unit =
    busy = true
    run(a, () => {
      busy = false
      val next = waiting
      waiting = None
      next.foreach(start)
    })
