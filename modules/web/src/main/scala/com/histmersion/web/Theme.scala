package com.histmersion
package web

import com.histmersion.web.ui.ThemeTokens
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** THE theme (the stylesheet's two token sets). The choice is kept per browser in `localStorage` under [[StorageKey]]
  * and written to `<html data-theme>`, which the stylesheet reads; "system" leaves the choice to its
  * `prefers-color-scheme` rule. The pages' `<head>` applies a stored choice before the stylesheet paints (no flash of
  * the other theme); this keeps it in step afterwards — a press of the button, another tab or window of the same server
  * choosing (a `storage` event), the system switching (a `matchMedia` listener) — and tells [[changes]] so that what
  * draws with a token's VALUE (the graph's canvas: [[ThemeTokens]]) draws again.
  */
@atBoundary(BoundaryKind.Dom, "keeps the theme choice in localStorage and on the html element")
object Theme:
  /** The `localStorage` key; the pages' inline `<head>` script reads the same one. */
  val StorageKey = "histmersion.theme"
  val Attribute = "data-theme"
  private val DarkQuery = "(prefers-color-scheme: dark)"

  private val chosen = Var(ThemeChoice.System)

  /** The reader's choice, as it changes. */
  val choice: Signal[ThemeChoice] = chosen.signal

  // `var`: each change of what the tokens resolve to bumps it, so subscribers redraw
  private val tick = Var(0)

  /** Fires whenever the tokens' values may have changed: the choice, or the system's setting under "system". */
  val changes: EventStream[Unit] = tick.signal.changes.mapToUnit

  private var installed = false

  /** Read the stored choice, apply it and follow later changes (once). */
  def install(): Unit =
    if !installed then
      installed = true
      apply(ThemeChoice.fromWire(Store.Browser.get(StorageKey).orNull))
      Try {
        dom.window.addEventListener(
          "storage",
          (e: dom.StorageEvent) => if e.key == StorageKey then apply(ThemeChoice.fromWire(e.newValue))
        )
      }
      if js.typeOf(dom.window.asInstanceOf[js.Dynamic].matchMedia) == "function" then
        Try(dom.window.matchMedia(DarkQuery)).toOption.filter(_ != null).foreach { mq =>
          val listener: js.Function1[js.Dynamic, Unit] = _ => if chosen.now() == ThemeChoice.System then changed()
          val d = mq.asInstanceOf[js.Dynamic]
          if js.typeOf(d.addEventListener) == "function" then d.addEventListener("change", listener)
          else d.addListener(listener)
        }

  /** Choose `c`: remembered, applied, announced. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def choose(c: ThemeChoice): Unit =
    if c == ThemeChoice.System then Store.Browser.remove(StorageKey) else Store.Browser.put(StorageKey, c.wire.value)
    apply(c)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def apply(c: ThemeChoice): Unit =
    Try(dom.document.documentElement.setAttribute(Attribute, c.wire.value))
    chosen.set(c)
    changed()

  private def changed(): Unit =
    ThemeTokens.refresh()
    tick.update(_ + 1)

  /** The button: its icon says the current choice, a press moves to the next. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def toggle: HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--ghost btn--icon theme-btn",
      dataAttr("theme-choice") <-- choice.map(_.wire.value),
      aria.label <-- choice.map(c => ThemeChoice.tooltip(c).value),
      title <-- choice.map(c => ThemeChoice.tooltip(c).value),
      child <-- choice.map(c => Icon(c.icon)),
      span(cls := "theme-label", child.text <-- choice.map(_.label.value)),
      onClick --> (_ => choose(ThemeChoice.next(chosen.now())))
    )
