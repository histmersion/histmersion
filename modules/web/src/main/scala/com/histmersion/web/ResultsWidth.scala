package com.histmersion
package web

import com.histmersion.web.ui.Layout
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The width of Explore's results column: wide enough by default for its five tabs and their counts, dragged or moved
  * with the arrow keys, kept by this browser, and never narrower than [[min]] nor wider than [[max]] of the window.
  * Pure, but for [[stored]] and [[keep]].
  */
object ResultsWidth:
  type KeyName = KeyName.T
  object KeyName extends Newtype[String, Pure]
  enum ShiftHeld derives CanEqual:
    case Held, Released
  object ShiftHeld:
    extension (shiftHeld: ShiftHeld) def held: Boolean = shiftHeld == Held
  type ViewportWidth = ViewportWidth.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ViewportWidth extends Newtype[Double, Pure]
  type Width = Width.T
  object Width extends Newtype[Int, Pure]

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ClampViewport = ViewportWidth
  val ClampViewport: ViewportWidth.type = ViewportWidth
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type StepViewport = ViewportWidth
  val StepViewport: ViewportWidth.type = ViewportWidth

  val StorageKey = "histmersion.resultsWidth"

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def min: Width = Width(Layout.sidebarMinWidth.value)

  /** The widest: `sidebarMaxWidth`, and never more than 60 % of the window, so the map keeps the rest. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def max(viewport: ViewportWidth): Width = Width(
    math.max(min.value, math.min(Layout.sidebarMaxWidth.value, (viewport.value * 0.6).toInt))
  )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def clamp(w: Width, viewport: ClampViewport): Width = Width(
    math.max(min.value, math.min(max(viewport).value, w.value))
  )

  /** What one key press does: ← widens (the column is on the right), → narrows, by 16 px (64 with Shift); Home the
    * narrowest, End the widest. None for any other key.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def step(w: Width, key: KeyName, shift: ShiftHeld, viewport: StepViewport): Option[Width] =
    val by = if shift.held then 64 else 16
    key.value match
      case "ArrowLeft"  => Some(clamp(Width(w.value + by), viewport))
      case "ArrowRight" => Some(clamp(Width(w.value - by), viewport))
      case "Home"       => Some(min)
      case "End"        => Some(max(viewport))
      case _            => None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decode(raw: Option[String]): Option[Width] = raw.flatMap(_.trim.toIntOption).filter(_ > 0).map(Width(_))

  def stored: Option[Width] = decode(Store.Browser.get(StorageKey))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def keep(w: Width): Unit = Store.Browser.put(StorageKey, w.value.toString)
