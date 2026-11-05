package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** The polling schedule of every polled window (jobs, agent runs, a manager): a quick first look, then slower while
  * nothing changes, and none once nothing is live. Pure.
  */
object Poll:
  type NextResult = NextResult.T
  object NextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextPrevious = NextPrevious.T
  object NextPrevious extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextLive = NextLive.T
  object NextLive extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextChanged = NextChanged.T
  object NextChanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val FirstMs = 1000
  val MaxMs = 15000
  val Factor = 1.6

  /** The delay before the next poll, or `None` to stop: nothing left to wait for. A change (a row moved on, or one was
    * started from this page) goes back to the quick delay.
    */
  def next(previous: Option[com.histmersion.web.Poll.NextPrevious], live: com.histmersion.web.Poll.NextLive, changed: com.histmersion.web.Poll.NextChanged): Option[com.histmersion.web.Poll.NextResult] =
    if !live then None
    else if changed then Some(FirstMs)
    else Some(previous.fold(FirstMs)(p => math.min(MaxMs, math.round(p * Factor).toInt)))
