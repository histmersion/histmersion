package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

/** The pure rules of a pinned place card (#1441). */
object PinRules:
  type ChosenNow = ChosenNow.T
  object ChosenNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ElementSize = ElementSize.T
  object ElementSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyName = KeyName.T
  object KeyName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScreenPixels = ScreenPixels.T
  object ScreenPixels extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Does this key close a pinned card? */

  def dismisses(key: com.histmersion.web.PinRules.KeyName): Boolean = key == "Escape" || key == "Esc"

  /** Does this key pin a focused place's card (and, on the map, choose the place)? */
  def activates(key: com.histmersion.web.PinRules.KeyName): Boolean = key == "Enter" || key == " "

  /** After a click (or Enter) on a map place that leaves it chosen as the filter or not: its card is pinned while it is
    * chosen; the click that unchooses it closes the card too.
    */
  def pinAfterClick(chosenNow: com.histmersion.web.PinRules.ChosenNow): Boolean = chosenNow

  /** Pure: a point in the map container (`x`, `y`, from its top-left) as a viewport point, when it is inside the
    * container's `w`×`h` box at (`left`, `top`) — `None` while it is panned out of view.
    */
  def inView(x: com.histmersion.web.PinRules.ScreenPixels, y: com.histmersion.web.PinRules.ScreenPixels, left: com.histmersion.web.PinRules.ScreenPixels, top: com.histmersion.web.PinRules.ScreenPixels, w: com.histmersion.web.PinRules.ElementSize, h: com.histmersion.web.PinRules.ElementSize): Option[(com.histmersion.web.PinRules.ScreenPixels, com.histmersion.web.PinRules.ScreenPixels)] =
    Option.when(x >= 0 && y >= 0 && x <= w && y <= h)((left + x, top + y))
