package com.histmersion.web

import com.histmersion.web.ui.Layout

/** The width of Explore's results column: wide enough by default for its five tabs and their counts, dragged or moved
  * with the arrow keys, kept by this browser, and never narrower than [[min]] nor wider than [[max]] of the window.
  * Pure, but for [[stored]] and [[keep]].
  */
object ResultsWidth:

  type MinResult = MinResult.T
  object MinResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxResult = MaxResult.T
  object MaxResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxViewport = MaxViewport.T
  object MaxViewport extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampResult = ClampResult.T
  object ClampResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampW = ClampW.T
  object ClampW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampViewport = ClampViewport.T
  object ClampViewport extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepResult = StepResult.T
  object StepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepW = StepW.T
  object StepW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepKey = StepKey.T
  object StepKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepShift = StepShift.T
  object StepShift extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepViewport = StepViewport.T
  object StepViewport extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeResult = DecodeResult.T
  object DecodeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeRaw = DecodeRaw.T
  object DecodeRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredResult = StoredResult.T
  object StoredResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeepW = KeepW.T
  object KeepW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val StorageKey = "histmersion.resultsWidth"

  def min: com.histmersion.web.ResultsWidth.MinResult = Layout.sidebarMinWidth.value

  /** The widest: `sidebarMaxWidth`, and never more than 60 % of the window, so the map keeps the rest. */
  def max(viewport: com.histmersion.web.ResultsWidth.MaxViewport): com.histmersion.web.ResultsWidth.MaxResult = math.max(min, math.min(Layout.sidebarMaxWidth.value, (viewport * 0.6).toInt))

  def clamp(w: com.histmersion.web.ResultsWidth.ClampW, viewport: com.histmersion.web.ResultsWidth.ClampViewport): com.histmersion.web.ResultsWidth.ClampResult = math.max(min, math.min(max(viewport), w))

  /** What one key press does: ← widens (the column is on the right), → narrows, by 16 px (64 with Shift); Home the
    * narrowest, End the widest. None for any other key.
    */
  def step(w: com.histmersion.web.ResultsWidth.StepW, key: com.histmersion.web.ResultsWidth.StepKey, shift: com.histmersion.web.ResultsWidth.StepShift, viewport: com.histmersion.web.ResultsWidth.StepViewport): Option[com.histmersion.web.ResultsWidth.StepResult] =
    val by = if shift then 64 else 16
    key match
      case "ArrowLeft"  => Some(clamp(w + by, viewport))
      case "ArrowRight" => Some(clamp(w - by, viewport))
      case "Home"       => Some(min)
      case "End"        => Some(max(viewport))
      case _            => None

  def decode(raw: Option[com.histmersion.web.ResultsWidth.DecodeRaw]): Option[com.histmersion.web.ResultsWidth.DecodeResult] = raw.flatMap(_.trim.toIntOption).filter(_ > 0)

  def stored: Option[com.histmersion.web.ResultsWidth.StoredResult] = decode(Store.Browser.get(StorageKey))
  def keep(w: com.histmersion.web.ResultsWidth.KeepW): Unit = Store.Browser.put(StorageKey, w.toString)
