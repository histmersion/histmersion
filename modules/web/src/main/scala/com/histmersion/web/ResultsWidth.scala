package com.histmersion.web

import com.histmersion.web.ui.Layout

/** The width of Explore's results column: wide enough by default for its five tabs and their counts, dragged or moved
  * with the arrow keys, kept by this browser, and never narrower than [[min]] nor wider than [[max]] of the window.
  * Pure, but for [[stored]] and [[keep]].
  */
object ResultsWidth:
  type KeyName = KeyName.T
  object KeyName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShiftHeld = ShiftHeld.T
  object ShiftHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViewportWidth = ViewportWidth.T
  object ViewportWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ClampViewport = _root_.com.histmersion.web.ResultsWidth.ViewportWidth
  val ClampViewport: _root_.com.histmersion.web.ResultsWidth.ViewportWidth.type = _root_.com.histmersion.web.ResultsWidth.ViewportWidth
  type StepViewport = _root_.com.histmersion.web.ResultsWidth.ViewportWidth
  val StepViewport: _root_.com.histmersion.web.ResultsWidth.ViewportWidth.type = _root_.com.histmersion.web.ResultsWidth.ViewportWidth

  val StorageKey = "histmersion.resultsWidth"

  def min: com.histmersion.web.ResultsWidth.Width = Width(Layout.sidebarMinWidth.value.value)

  /** The widest: `sidebarMaxWidth`, and never more than 60 % of the window, so the map keeps the rest. */
  def max(viewport: com.histmersion.web.ResultsWidth.ViewportWidth): com.histmersion.web.ResultsWidth.Width = Width(math.max(min.value, math.min(Layout.sidebarMaxWidth.value.value, (viewport.value * 0.6).toInt)))

  def clamp(w: com.histmersion.web.ResultsWidth.Width, viewport: com.histmersion.web.ResultsWidth.ClampViewport): com.histmersion.web.ResultsWidth.Width = Width(math.max(min.value, math.min(max(viewport).value, w.value)))

  /** What one key press does: ← widens (the column is on the right), → narrows, by 16 px (64 with Shift); Home the
    * narrowest, End the widest. None for any other key.
    */
  def step(w: com.histmersion.web.ResultsWidth.Width, key: com.histmersion.web.ResultsWidth.KeyName, shift: com.histmersion.web.ResultsWidth.ShiftHeld, viewport: com.histmersion.web.ResultsWidth.StepViewport): Option[com.histmersion.web.ResultsWidth.Width] =
    val by = if shift.value then 64 else 16
    key.value match
      case "ArrowLeft"  => Some(clamp(Width(w.value + by), viewport))
      case "ArrowRight" => Some(clamp(Width(w.value - by), viewport))
      case "Home"       => Some(min)
      case "End"        => Some(max(viewport))
      case _            => None

  def decode(raw: Option[String]): Option[com.histmersion.web.ResultsWidth.Width] = raw.flatMap(_.trim.toIntOption).filter(_ > 0).map(Width(_))

  def stored: Option[com.histmersion.web.ResultsWidth.Width] = decode(Store.Browser.get(StorageKey))
  def keep(w: com.histmersion.web.ResultsWidth.Width): Unit = Store.Browser.put(StorageKey, w.value.toString)
