package com.histmersion.web

import com.histmersion.web.ui.CssClass

/** The fade of what the map adds and removes by key (a point, a name): new members fade in, vanished ones fade out and
  * are then removed, kept ones are restyled in place — so a layer never blinks when the filter or the time span moves.
  */
object FadingLayer:

  type PlanBefore = PlanBefore.T
  object PlanBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanAfter = PlanAfter.T
  object PlanAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Plan(entering: Set[Plan.Entering], staying: Set[Plan.Staying], exiting: Set[Plan.Exiting])

  object Plan:
    type Entering = Entering.T
    object Entering extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Staying = Staying.T
    object Staying extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Exiting = Exiting.T
    object Exiting extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: what enters, stays and leaves going from `before` to `after`. */
  private[web] def plan(before: Set[com.histmersion.web.FadingLayer.PlanBefore], after: Set[com.histmersion.web.FadingLayer.PlanAfter]): Plan =
    Plan(entering = after -- before, staying = after & before, exiting = before -- after)

  val fadeIn: CssClass = CssClass("hd-fade-in")
  val fadeOut: CssClass = CssClass("hd-fade-out")
