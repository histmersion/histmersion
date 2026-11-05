package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

/** A hover that waits: `enter(k)` fires `k` only after `delayMs` without a `leave(k)` or another `enter`, so sweeping
  * the pointer across twenty polygons fetches nothing and the one it rests on fetches once. `later`/`cancel` are
  * injectable so the timing is a unit test, like [[Throttle]].
  */
final class HoverIntent[K](
    delayMs: Int,
    fire: K => Unit,
    later: (Double, () => Unit) => Int = (ms, g) => dom.window.setTimeout(() => g(), ms),
    cancel: Int => Unit = id => dom.window.clearTimeout(id)
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
