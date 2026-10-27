package com.histmersion.web

/** Where the timeline overview's two years go — the selection's "from" and "to" under its handles — so
  * they never overlap and never leave the bar. Pure, so the placement is a unit test and
  * [[TimelineView]] only applies the answer.
  *
  *   - inside the selection (each next to its own handle, facing the other) while both fit there;
  *   - outside it when the handles are too close — "from" left of the left handle, "to" right of the
  *     right one — pushed back into the bar at its ends (the other year then moves on past it, so
  *     "from" stays left of "to");
  *   - one label "from – to" centred on the selection (kept inside the bar) when even that does not fit. */
object RangeLabels:

  /** The gap between a year and its handle, and the least gap between the two years. */
  val Pad = 3.0
  val Gap = 6.0

  /** The width of `text` at the overview's 8 px label font (tabular digits; a rough upper bound). */
  def width(text: String): Double = text.length * 5.0

  /** One label: its left edge `x` and width `w` (drawn with `text-anchor: start` at `x`). */
  final case class Label(text: String, x: Double, w: Double):
    def end: Double = x + w

  enum Placement:
    case Inside(from: Label, to: Label)
    case Outside(from: Label, to: Label)
    case Joined(label: Label)

  /** The placement for a selection from `x0` to `x1` (px, `x0 ≤ x1`) in a bar `barW` wide. */
  def place(fromText: String, toText: String, x0: Double, x1: Double, barW: Double): Placement =
    val wf = width(fromText)
    val wt = width(toText)
    if x0 + Pad + wf + Gap <= x1 - Pad - wt then
      Placement.Inside(Label(fromText, x0 + Pad, wf), Label(toText, x1 - Pad - wt, wt))
    else
      // outside the handles, each pushed back into the bar at its end — and the other one pushed on
      // past it (still on its own side) when that end pushed them together
      val fromX0 = math.max(0.0, x0 - Pad - wf)
      val toX0   = math.min(barW - wt, x1 + Pad)
      val (fromX, toX) =
        if fromX0 + wf + Gap <= toX0 then (fromX0, toX0)
        else if fromX0 == 0.0 then (fromX0, fromX0 + wf + Gap)
        else (toX0 - Gap - wf, toX0)
      if fromX >= 0.0 && toX + wt <= barW && fromX + wf + Gap <= toX then
        Placement.Outside(Label(fromText, fromX, wf), Label(toText, toX, wt))
      else
        val text = s"$fromText – $toText"
        val w    = width(text)
        val x    = ((x0 + x1) / 2 - w / 2).max(0.0).min(math.max(0.0, barW - w))
        Placement.Joined(Label(text, x, w))
