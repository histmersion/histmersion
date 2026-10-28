package com.histmersion.web

import com.histmersion.web.ui.ThemeTokens
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.scalajs.js
import scala.util.Try

/** The colour theme a reader picked: follow the system's setting (the default), always light, or always
  * dark. One button cycles them in that order, as the documentation site's does. */
enum ThemeChoice(val wire: String, val label: String, val icon: Icon):
  case System extends ThemeChoice("system", "Theme: follows the system", Icon.ThemeSystem)
  case Light  extends ThemeChoice("light", "Theme: light", Icon.ThemeLight)
  case Dark   extends ThemeChoice("dark", "Theme: dark", Icon.ThemeDark)

object ThemeChoice:
  /** A stored or attribute value; anything unknown (nothing stored, an old value) is the system's. */
  def fromWire(s: String): ThemeChoice = values.find(_.wire == Option(s).fold("")(_.trim.toLowerCase)).getOrElse(System)

  /** The button's next choice: system → light → dark → system. */
  def next(c: ThemeChoice): ThemeChoice = c match
    case System => Light
    case Light  => Dark
    case Dark   => System

  /** What a press does, read out and shown on hover: the current choice and the one a press picks. */
  def tooltip(c: ThemeChoice): String =
    s"${c.label}. Click for ${next(c) match
        case System => "the system's setting"
        case Light  => "light"
        case Dark   => "dark"}."

/** THE theme (the stylesheet's two token sets). The choice is kept per browser in `localStorage` under
  * [[StorageKey]] and written to `<html data-theme>`, which the stylesheet reads; "system" leaves the
  * choice to its `prefers-color-scheme` rule. The pages' `<head>` applies a stored choice before the
  * stylesheet paints (no flash of the other theme); this keeps it in step afterwards — a press of the
  * button, another tab or window of the same server choosing (a `storage` event), the system switching
  * (a `matchMedia` listener) — and tells [[changes]] so that what draws with a token's VALUE (the graph's
  * canvas: [[ThemeTokens]]) draws again. */
object Theme:
  /** The `localStorage` key; the pages' inline `<head>` script reads the same one. */
  val StorageKey = "histmersion.theme"
  val Attribute  = "data-theme"
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
        dom.window.addEventListener("storage", { (e: dom.StorageEvent) =>
          if e.key == StorageKey then apply(ThemeChoice.fromWire(e.newValue))
        })
      }
      if js.typeOf(dom.window.asInstanceOf[js.Dynamic].matchMedia) == "function" then
        Try(dom.window.matchMedia(DarkQuery)).toOption.filter(_ != null).foreach { mq =>
          val listener: js.Function1[js.Dynamic, Unit] = _ => if chosen.now() == ThemeChoice.System then changed()
          val d = mq.asInstanceOf[js.Dynamic]
          if js.typeOf(d.addEventListener) == "function" then d.addEventListener("change", listener)
          else d.addListener(listener)
        }

  /** Choose `c`: remembered, applied, announced. */
  def choose(c: ThemeChoice): Unit =
    if c == ThemeChoice.System then Store.Browser.remove(StorageKey) else Store.Browser.put(StorageKey, c.wire)
    apply(c)

  private def apply(c: ThemeChoice): Unit =
    Try(dom.document.documentElement.setAttribute(Attribute, c.wire))
    chosen.set(c)
    changed()

  private def changed(): Unit =
    ThemeTokens.refresh()
    tick.update(_ + 1)

  /** The button: its icon says the current choice, a press moves to the next. */
  def toggle: HtmlElement =
    button(
      typ := "button",
      cls := "btn btn--ghost btn--icon theme-btn",
      dataAttr("theme-choice") <-- choice.map(_.wire),
      aria.label <-- choice.map(ThemeChoice.tooltip),
      title <-- choice.map(ThemeChoice.tooltip),
      child <-- choice.map(c => Icon(c.icon)),
      span(cls := "theme-label", child.text <-- choice.map(_.label)),
      onClick --> (_ => choose(ThemeChoice.next(chosen.now())))
    )
