package com.histmersion.web

import com.histmersion.web.ui.Layout

/** The width of Explore's results column: wide enough by default for its five tabs and their counts, dragged or moved
  * with the arrow keys, kept by this browser, and never narrower than [[min]] nor wider than [[max]] of the window.
  * Pure, but for [[stored]] and [[keep]].
  */
object ResultsWidth:

  val StorageKey = "histmersion.resultsWidth"

  def min: Int = Layout.sidebarMinWidth.value

  /** The widest: `sidebarMaxWidth`, and never more than 60 % of the window, so the map keeps the rest. */
  def max(viewport: Double): Int = math.max(min, math.min(Layout.sidebarMaxWidth.value, (viewport * 0.6).toInt))

  def clamp(w: Int, viewport: Double): Int = math.max(min, math.min(max(viewport), w))

  /** What one key press does: ← widens (the column is on the right), → narrows, by 16 px (64 with Shift); Home the
    * narrowest, End the widest. None for any other key.
    */
  def step(w: Int, key: String, shift: Boolean, viewport: Double): Option[Int] =
    val by = if shift then 64 else 16
    key match
      case "ArrowLeft"  => Some(clamp(w + by, viewport))
      case "ArrowRight" => Some(clamp(w - by, viewport))
      case "Home"       => Some(min)
      case "End"        => Some(max(viewport))
      case _            => None

  def decode(raw: Option[String]): Option[Int] = raw.flatMap(_.trim.toIntOption).filter(_ > 0)

  def stored: Option[Int] = decode(Store.Browser.get(StorageKey))
  def keep(w: Int): Unit = Store.Browser.put(StorageKey, w.toString)
