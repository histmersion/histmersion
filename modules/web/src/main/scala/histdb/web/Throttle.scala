package histdb.web

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
