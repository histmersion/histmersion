package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

/** The pure rules of a pinned place card (#1441). */
object PinRules:
  /** Does this key close a pinned card? */
  type DismissesResult = DismissesResult.T
  object DismissesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DismissesKey = DismissesKey.T
  object DismissesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivatesResult = ActivatesResult.T
  object ActivatesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActivatesKey = ActivatesKey.T
  object ActivatesKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PinAfterClickResult = PinAfterClickResult.T
  object PinAfterClickResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PinAfterClickChosenNow = PinAfterClickChosenNow.T
  object PinAfterClickChosenNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewResultPart1 = InViewResultPart1.T
  object InViewResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewResultPart2 = InViewResultPart2.T
  object InViewResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewX = InViewX.T
  object InViewX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewY = InViewY.T
  object InViewY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewLeft = InViewLeft.T
  object InViewLeft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewTop = InViewTop.T
  object InViewTop extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewW = InViewW.T
  object InViewW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InViewH = InViewH.T
  object InViewH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def dismisses(key: com.histmersion.web.PinRules.DismissesKey): com.histmersion.web.PinRules.DismissesResult = key == "Escape" || key == "Esc"

  /** Does this key pin a focused place's card (and, on the map, choose the place)? */
  def activates(key: com.histmersion.web.PinRules.ActivatesKey): com.histmersion.web.PinRules.ActivatesResult = key == "Enter" || key == " "

  /** After a click (or Enter) on a map place that leaves it chosen as the filter or not: its card is pinned while it is
    * chosen; the click that unchooses it closes the card too.
    */
  def pinAfterClick(chosenNow: com.histmersion.web.PinRules.PinAfterClickChosenNow): com.histmersion.web.PinRules.PinAfterClickResult = chosenNow

  /** Pure: a point in the map container (`x`, `y`, from its top-left) as a viewport point, when it is inside the
    * container's `w`×`h` box at (`left`, `top`) — `None` while it is panned out of view.
    */
  def inView(x: com.histmersion.web.PinRules.InViewX, y: com.histmersion.web.PinRules.InViewY, left: com.histmersion.web.PinRules.InViewLeft, top: com.histmersion.web.PinRules.InViewTop, w: com.histmersion.web.PinRules.InViewW, h: com.histmersion.web.PinRules.InViewH): Option[(com.histmersion.web.PinRules.InViewResultPart1, com.histmersion.web.PinRules.InViewResultPart2)] =
    Option.when(x >= 0 && y >= 0 && x <= w && y <= h)((left + x, top + y))
