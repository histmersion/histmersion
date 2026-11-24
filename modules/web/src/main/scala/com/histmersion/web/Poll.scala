package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The polling schedule of every polled window (jobs, agent runs, a manager): a quick first look, then slower while
  * nothing changes, and none once nothing is live. Pure.
  */
object Poll:
  type DataChanged = DataChanged.T
  object DataChanged extends Newtype[Boolean, Pure]
  type LiveMode = LiveMode.T
  object LiveMode extends Newtype[Boolean, Pure]
  type Revision = Revision.T
  object Revision extends Newtype[Int, Pure]

  val FirstMs = 1000
  val MaxMs = 15000
  val Factor = 1.6

  /** The delay before the next poll, or `None` to stop: nothing left to wait for. A change (a row moved on, or one was
    * started from this page) goes back to the quick delay.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def next(previous: Option[Revision], live: LiveMode, changed: DataChanged): Option[Revision] =
    if !live.value then None
    else if changed.value then Some(Revision(FirstMs))
    else Some(Revision(previous.fold(FirstMs)(p => math.min(MaxMs, math.round(p.value * Factor).toInt))))
