package com.histmersion.web

import com.histmersion.core.UiConfig
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.util.Try

/** Which layout the page is in. At or below `ui.narrow-max-width-px` it is NARROW — the phone layout:
  * the map first, the sidebar a bottom sheet, windows full-screen. The stylesheet does the work, keyed
  * off `<html data-layout="narrow">`; this object only decides the mode and says so. Desktop never
  * gets `narrow`, so no rule written for a phone can reach it. */
object Viewport:

  enum Mode(val wire: String):
    case Narrow extends Mode("narrow")
    case Wide   extends Mode("wide")

  /** Pure: the mode a viewport `width` (CSS px) is in. */
  def mode(width: Double, narrowMaxWidthPx: Int): Mode =
    if width <= narrowMaxWidthPx then Mode.Narrow else Mode.Wide

  private val current = Var(Mode.Wide)

  val narrow: Signal[Boolean] = current.signal.map(_ == Mode.Narrow)
  def narrowNow: Boolean      = current.now() == Mode.Narrow

  /** Follow the viewport from now on. Called once by the composition root, with the config. */
  def init(ui: UiConfig): Unit =
    def apply(m: Mode): Unit =
      current.set(m)
      dom.document.documentElement.setAttribute("data-layout", m.wire)
    apply(mode(dom.window.innerWidth, ui.narrowMaxWidthPx))
    // the same boundary as a media query, so a rotated phone or a resized window switches at once
    Try(dom.window.matchMedia(s"(max-width: ${ui.narrowMaxWidthPx}px)")).foreach { mq =>
      mq.addEventListener("change", { (_: dom.Event) =>
        apply(if mq.matches then Mode.Narrow else Mode.Wide)
      })
    }
