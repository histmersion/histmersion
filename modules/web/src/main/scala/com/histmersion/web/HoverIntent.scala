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
    later: (HoverIntent.DelayMs, () => Unit) => HoverIntent.TimerId = (ms, g) => HoverIntent.TimerId(dom.window.setTimeout(() => g(), ms.value.toDouble)),
    cancel: HoverIntent.TimerId => Unit = id => dom.window.clearTimeout(id.value)
):
  // `var`: the one pending hover and its timer
  private var pending: Option[(K, HoverIntent.TimerId)] = None

  def isPending(k: K): Boolean = pending.exists(_._1 == k)

  def enter(k: K): Unit =
    if !isPending(k) then
      cancelPending()
      val id = later(delayMs, () => { pending = None; fire(k) })
      pending = Some(k -> id)

  /** The pointer left `k`: its pending card is dropped (a pending card for another target stays). */
  def leave(k: K): Unit = if isPending(k) then cancelPending()

  def cancelPending(): Unit =
    pending.foreach((_, id) => cancel(id))
    pending = None

object HoverIntent:
  type DelayMs = DelayMs.T
  object DelayMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimerId = TimerId.T
  object TimerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

