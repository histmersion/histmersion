package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The polling schedule of every polled window (jobs, agent runs, a manager): a quick first look, then slower while
  * nothing changes, and none once nothing is live. Pure.
  */
object Poll:
  type DataChanged = DataChanged.T
  object DataChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LiveMode = LiveMode.T
  object LiveMode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Revision = Revision.T
  object Revision extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val FirstMs = 1000
  val MaxMs = 15000
  val Factor = 1.6

  /** The delay before the next poll, or `None` to stop: nothing left to wait for. A change (a row moved on, or one was
    * started from this page) goes back to the quick delay.
    */
  def next(previous: Option[com.histmersion.web.Poll.Revision], live: com.histmersion.web.Poll.LiveMode, changed: com.histmersion.web.Poll.DataChanged): Option[com.histmersion.web.Poll.Revision] =
    if !live.value then None
    else if changed.value then Some(Revision(FirstMs))
    else Some(Revision(previous.fold(FirstMs)(p => math.min(MaxMs, math.round(p.value * Factor).toInt))))
