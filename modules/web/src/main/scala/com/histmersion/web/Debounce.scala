package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** Runs `f` once the calls have stopped for `quietMs`, with the latest arguments — for work too heavy to repeat while a
  * drag goes on (a whole range's boundaries: megabytes per answer), where [[Throttle]] would still fire every interval.
  * `later`/`cancel` are injectable for a test.
  */
final class Debounce[A](
    quietMs: Debounce.QuietMs,
    f: A => Unit,
    later: (Debounce.LaterArg1, () => Unit) => Debounce.LaterResult = (ms, g) => dom.window.setTimeout(() => g(), ms),
    cancel: Debounce.CancelArg1 => Unit = id => dom.window.clearTimeout(id)
):
  // `var`s: the one pending run and its timer
  private var pending: Option[A] = None
  private var timer: Int = 0

  def apply(a: A): Unit =
    if pending.isDefined then cancel(timer)
    pending = Some(a)
    timer = later(quietMs, () => { val p = pending; pending = None; p.foreach(f) })

  /** Drop the pending run. */
  def cancelPending(): Unit =
    if pending.isDefined then cancel(timer)
    pending = None

object Debounce:
  type QuietMs = QuietMs.T
  object QuietMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LaterArg1 = LaterArg1.T
  object LaterArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LaterResult = LaterResult.T
  object LaterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CancelArg1 = CancelArg1.T
  object CancelArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

