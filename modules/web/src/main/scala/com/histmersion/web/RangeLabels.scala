package com.histmersion
package web

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.UiLabels

/** Where the timeline overview's two years go — the selection's "from" and "to" under its handles — so they never
  * overlap and never leave the bar. Pure, so the placement is a unit test and [[TimelineView]] only applies the answer.
  *
  *   - inside the selection (each next to its own handle, facing the other) while both fit there;
  *   - outside it when the handles are too close — "from" left of the left handle, "to" right of the right one — pushed
  *     back into the bar at its ends (the other year then moves on past it, so "from" stays left of "to");
  *   - one label "from – to" centred on the selection (kept inside the bar) when even that does not fit.
  */
object RangeLabels:
  type ElementSize = ElementSize.T
  object ElementSize extends Newtype[Double, Pure]
  type ScreenPixels = ScreenPixels.T
  object ScreenPixels extends Newtype[Double, Pure]

  /** The gap between a year and its handle, and the least gap between the two years. */

  val Pad = 3.0
  val Gap = 6.0

  /** The width of `text` at the overview's 8 px label font (tabular digits; a rough upper bound). */
  def width(text: UiLabels.Text): ElementSize = ElementSize(text.value.length * 5.0)

  /** One label: its left edge `x` and width `w` (drawn with `text-anchor: start` at `x`). */
  final case class Label(text: UiLabels.Text, x: ScreenPixels, w: ElementSize):
    def end: ScreenPixels = ScreenPixels(x.value + w.value)

  enum Placement:
    case Inside(from: Label, to: Label)
    case Outside(from: Label, to: Label)
    case Joined(label: Label)

  /** The placement for a selection from `x0` to `x1` (px, `x0 ≤ x1`) in a bar `barW` wide. */
  def place(fromText: UiLabels.Text, toText: UiLabels.Text, x0: ScreenPixels, x1: ScreenPixels, barW: ElementSize): Placement =
    val wf: Double = width(fromText).value
    val wt: Double = width(toText).value
    val a0: Double = x0.value
    val a1: Double = x1.value
    val bw: Double = barW.value
    if a0 + Pad + wf + Gap <= a1 - Pad - wt then
      Placement.Inside(Label(fromText, ScreenPixels(a0 + Pad), ElementSize(wf)), Label(toText, ScreenPixels(a1 - Pad - wt), ElementSize(wt)))
    else
      // outside the handles, each pushed back into the bar at its end — and the other one pushed on
      // past it (still on its own side) when that end pushed them together
      val fromX0 = math.max(0.0, a0 - Pad - wf)
      val toX0 = math.min(bw - wt, a1 + Pad)
      val (fromX, toX) =
        if fromX0 + wf + Gap <= toX0 then (fromX0, toX0)
        else if fromX0 == 0.0 then (fromX0, fromX0 + wf + Gap)
        else (toX0 - Gap - wf, toX0)
      if fromX >= 0.0 && toX + wt <= bw && fromX + wf + Gap <= toX then
        Placement.Outside(Label(fromText, ScreenPixels(fromX), ElementSize(wf)), Label(toText, ScreenPixels(toX), ElementSize(wt)))
      else
        val text = UiLabels.Text(s"$fromText – $toText")
        val w: Double = width(text).value
        val x = ((a0 + a1) / 2 - w / 2).max(0.0).min(math.max(0.0, bw - w))
        Placement.Joined(Label(text, ScreenPixels(x), ElementSize(w)))
