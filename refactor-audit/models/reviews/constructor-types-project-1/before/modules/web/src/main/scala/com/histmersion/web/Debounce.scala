package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** Runs `f` once the calls have stopped for `quietMs`, with the latest arguments — for work too heavy to repeat while a
  * drag goes on (a whole range's boundaries: megabytes per answer), where [[Throttle]] would still fire every interval.
  * `later`/`cancel` are injectable for a test.
  */
final class Debounce[A](
    quietMs: Int,
    f: A => Unit,
    later: (Double, () => Unit) => Int = (ms, g) => dom.window.setTimeout(() => g(), ms),
    cancel: Int => Unit = id => dom.window.clearTimeout(id)
):
  // `var`s: the one pending run and its timer
  private var pending: Option[A] = None
  private var timer: com.histmersion.web.Debounce.Timer = 0

  def apply(a: A): Unit =
    if pending.isDefined then cancel(timer)
    pending = Some(a)
    timer = later(quietMs, () => { val p = pending; pending = None; p.foreach(f) })

  /** Drop the pending run. */
  def cancelPending(): Unit =
    if pending.isDefined then cancel(timer)
    pending = None

object Debounce:
  type Timer = Timer.T
  object Timer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
