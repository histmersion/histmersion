package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

/** A hover that waits: `enter(k)` fires `k` only after `delayMs` without a `leave(k)` or another `enter`, so sweeping
  * the pointer across twenty polygons fetches nothing and the one it rests on fetches once. `later`/`cancel` are
  * injectable so the timing is a unit test, like [[Throttle]].
  */
final class HoverIntent[K](
    delayMs: HoverIntent.DelayMs,
    fire: K => Unit,
    later: (HoverIntent.LaterArg1, () => Unit) => HoverIntent.LaterResult = (ms, g) => dom.window.setTimeout(() => g(), ms),
    cancel: HoverIntent.CancelArg1 => Unit = id => dom.window.clearTimeout(id)
):
  // `var`: the one pending hover and its timer
  private var pending: Option[(K, Int)] = None

  def isPending(k: K): Boolean = pending.exists(_._1 == k)

  def enter(k: K): Unit =
    if !isPending(k) then
      cancelPending()
      val id = later(delayMs.toDouble, () => { pending = None; fire(k) })
      pending = Some(k -> id)

  /** The pointer left `k`: its pending card is dropped (a pending card for another target stays). */
  def leave(k: K): Unit = if isPending(k) then cancelPending()

  def cancelPending(): Unit =
    pending.foreach((_, id) => cancel(id))
    pending = None

object HoverIntent:
  type DelayMs = DelayMs.T
  object DelayMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LaterArg1 = LaterArg1.T
  object LaterArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LaterResult = LaterResult.T
  object LaterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CancelArg1 = CancelArg1.T
  object CancelArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

