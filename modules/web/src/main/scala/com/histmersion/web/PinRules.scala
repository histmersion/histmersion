package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The pure rules of a pinned place card (#1441). */
object PinRules:
  type ChosenNow = ChosenNow.T
  object ChosenNow extends Newtype[Boolean, Pure]
  type ElementSize = ElementSize.T
  object ElementSize extends Newtype[Double, Pure]
  type KeyName = KeyName.T
  object KeyName extends Newtype[String, Pure]
  type ScreenPixels = ScreenPixels.T
  object ScreenPixels extends Newtype[Double, Pure]

  /** Does this key close a pinned card? */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def dismisses(key: KeyName): Boolean = key.value == "Escape" || key.value == "Esc"

  /** Does this key pin a focused place's card (and, on the map, choose the place)? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def activates(key: KeyName): Boolean = key.value == "Enter" || key.value == " "

  /** After a click (or Enter) on a map place that leaves it chosen as the filter or not: its card is pinned while it is
    * chosen; the click that unchooses it closes the card too.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pinAfterClick(chosenNow: ChosenNow): Boolean = chosenNow.value

  /** Pure: a point in the map container (`x`, `y`, from its top-left) as a viewport point, when it is inside the
    * container's `w`×`h` box at (`left`, `top`) — `None` while it is panned out of view.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def inView(
      x: ScreenPixels,
      y: ScreenPixels,
      left: ScreenPixels,
      top: ScreenPixels,
      w: ElementSize,
      h: ElementSize
  ): Option[(ScreenPixels, ScreenPixels)] =
    Option.when(x.value >= 0 && y.value >= 0 && x.value <= w.value && y.value <= h.value)(
      (ScreenPixels(left.value + x.value), ScreenPixels(top.value + y.value))
    )
