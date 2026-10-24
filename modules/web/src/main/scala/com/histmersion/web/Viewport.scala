package com.histmersion.web

import com.histmersion.api.UiConfig
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

  /** Pure: the mode a viewport of `width` × `height` (CSS px) is in. Narrow when it is thin, or when
    * it is short and driven by touch — a phone held sideways, which is wide but has no room. */
  def mode(width: Double, height: Double, coarsePointer: Boolean, maxWidth: Int, maxHeight: Int): Mode =
    if width <= maxWidth || (coarsePointer && height <= maxHeight) then Mode.Narrow else Mode.Wide

  /** The same rule as a media query, so the browser tells us when it flips. */
  def query(maxWidth: Int, maxHeight: Int): String =
    s"(max-width: ${maxWidth}px), (max-height: ${maxHeight}px) and (pointer: coarse)"

  private val current = Var(Mode.Wide)

  val narrow: Signal[Boolean] = current.signal.map(_ == Mode.Narrow)
  def narrowNow: Boolean      = current.now() == Mode.Narrow

  /** Follow the viewport from now on. Called once by the composition root, with the config. */
  def init(ui: UiConfig): Unit =
    def apply(m: Mode): Unit =
      current.set(m)
      dom.document.documentElement.setAttribute("data-layout", m.wire)
    Try(dom.window.matchMedia(query(ui.narrowMaxWidthPx, ui.narrowMaxHeightPx))).toOption match
      case Some(mq) =>
        apply(if mq.matches then Mode.Narrow else Mode.Wide)
        // a rotated phone or a resized window switches at once
        mq.addEventListener("change", (_: dom.Event) => apply(if mq.matches then Mode.Narrow else Mode.Wide))
      case None =>
        val coarse = Try(dom.window.matchMedia("(pointer: coarse)").matches).getOrElse(false)
        apply(mode(dom.window.innerWidth, dom.window.innerHeight, coarse, ui.narrowMaxWidthPx, ui.narrowMaxHeightPx))
