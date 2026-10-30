package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

/** The pure rules of a pinned place card (#1441). */
object PinRules:
  /** Does this key close a pinned card? */
  def dismisses(key: String): Boolean = key == "Escape" || key == "Esc"

  /** Does this key pin a focused place's card (and, on the map, choose the place)? */
  def activates(key: String): Boolean = key == "Enter" || key == " "

  /** After a click (or Enter) on a map place that leaves it chosen as the filter or not: its card is pinned while it is
    * chosen; the click that unchooses it closes the card too.
    */
  def pinAfterClick(chosenNow: Boolean): Boolean = chosenNow

  /** Pure: a point in the map container (`x`, `y`, from its top-left) as a viewport point, when it is inside the
    * container's `w`×`h` box at (`left`, `top`) — `None` while it is panned out of view.
    */
  def inView(x: Double, y: Double, left: Double, top: Double, w: Double, h: Double): Option[(Double, Double)] =
    Option.when(x >= 0 && y >= 0 && x <= w && y <= h)((left + x, top + y))
