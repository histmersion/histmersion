package com.histmersion
package web

import com.histmersion.web.ui.CssClass
import io.github.iltotore.iron.constraint.any.Pure

/** The fade of what the map adds and removes by key (a point, a name): new members fade in, vanished ones fade out and
  * are then removed, kept ones are restyled in place — so a layer never blinks when the filter or the time span moves.
  */
object FadingLayer:
  type Key = Key.T
  object Key extends Newtype[String, Pure]


  final case class Plan(entering: Set[Key], staying: Set[Key], exiting: Set[Key])

  /** Pure: what enters, stays and leaves going from `before` to `after`. */
  private[web] def plan(before: Set[Key], after: Set[Key]): Plan =
    Plan(entering = after -- before, staying = after & before, exiting = before -- after)

  val fadeIn: CssClass = CssClass("hd-fade-in")
  val fadeOut: CssClass = CssClass("hd-fade-out")
