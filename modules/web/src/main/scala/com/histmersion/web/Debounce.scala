package com.histmersion
package web

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Runs `f` once the calls have stopped for `quietMs`, with the latest arguments — for work too heavy to repeat while a
  * drag goes on (a whole range's boundaries: megabytes per answer), where [[Throttle]] would still fire every interval.
  * `later`/`cancel` are injectable for a test.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final class Debounce[A](
    quietMs: Debounce.QuietMs,
    f: A => Unit,
    later: (Debounce.Delay, () => Unit) => Debounce.TimerId = (ms, g) =>
      Debounce.TimerId(dom.window.setTimeout(() => g(), ms.value)),
    cancel: Debounce.TimerId => Unit = id => dom.window.clearTimeout(id.value)
):
  // `var`s: the one pending run and its timer
  private var pending: Option[A] = None
  private var timer: Debounce.TimerId = Debounce.TimerId(0)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(a: A): Unit =
    if pending.isDefined then cancel(timer)
    pending = Some(a)
    timer = later(Debounce.Delay(quietMs.value.toDouble), () => { val p = pending; pending = None; p.foreach(f) })

  /** Drop the pending run. */
  def cancelPending(): Unit =
    if pending.isDefined then cancel(timer)
    pending = None

object Debounce:
  type QuietMs = QuietMs.T
  object QuietMs extends Newtype[Int, Pure]
  type Delay = Delay.T
  object Delay extends Newtype[Double, Pure]
  type TimerId = TimerId.T
  object TimerId extends Newtype[Int, Pure]
