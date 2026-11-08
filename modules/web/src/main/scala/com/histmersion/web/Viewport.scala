package com.histmersion
package web

import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure

/** Which layout the page is in. At or below `ui.narrow-max-width-px` it is NARROW — the phone layout: the map first,
  * the sidebar a bottom sheet, windows full-screen. Wider, but at or below `ui.tablet-max-width-px`, it is TABLET — the
  * same map-first layout at tablet size: the controls behind the app bar's buttons, the timeline open, the list a sheet
  * at the side, windows floating as on a desktop. The stylesheet does the work, keyed off `<html data-layout="narrow">`
  * (both) and `<html data-tablet>` (the tablet's own sizes); this object only decides the mode and says so. Desktop
  * never gets `narrow`, so no rule written for a phone or a tablet can reach it.
  */
object Viewport:
  type MediaQuery = MediaQuery.T
  object MediaQuery extends Newtype[String, Pure]

  type ModeWidth = ModeWidth.T
  object ModeWidth extends Newtype[Double, Pure]
  type ModeHeight = ModeHeight.T
  object ModeHeight extends Newtype[Double, Pure]

  enum Mode(val wire: Mode.Wire):
    case Narrow extends Mode(Mode.Wire("narrow"))
    case Tablet extends Mode(Mode.Wire("narrow"))
    case Wide extends Mode(Mode.Wire("wide"))

  object Mode:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

  /** Pure: the mode a viewport of `width` × `height` (CSS px) is in. Narrow when it is thin, or when it is short and
    * driven by touch — a phone held sideways, which is wide but has no room; else a tablet up to `tabletMaxWidth`.
    */
  def mode(
      width: ModeWidth,
      height: ModeHeight,
      coarsePointer: Boolean,
      maxWidth: UiConfig.NarrowMaxWidthPx,
      maxHeight: UiConfig.NarrowMaxHeightPx,
      tabletMaxWidth: UiConfig.TabletMaxWidthPx
  ): Mode =
    if width.value <= maxWidth.value || (coarsePointer && height.value <= maxHeight.value) then Mode.Narrow
    else if width.value <= tabletMaxWidth.value then Mode.Tablet
    else Mode.Wide

  /** The phone's rule as a media query, so the browser tells us when it flips. */
  def query(maxWidth: UiConfig.NarrowMaxWidthPx, maxHeight: UiConfig.NarrowMaxHeightPx): MediaQuery =
    MediaQuery(s"(max-width: ${maxWidth.value}px), (max-height: ${maxHeight.value}px) and (pointer: coarse)")

  /** The tablet's: every width up to its own limit (the phone's query is asked first). */
  def tabletQuery(tabletMaxWidth: UiConfig.TabletMaxWidthPx): MediaQuery = MediaQuery(s"(max-width: ${tabletMaxWidth.value}px)")

  private val current = Var(Mode.Wide)

  val modeSignal: Signal[Mode] = current.signal.distinct
  val narrow: Signal[Boolean] = current.signal.map(_ != Mode.Wide)
  def narrowNow: Boolean = current.now() != Mode.Wide
  def tabletNow: Boolean = current.now() == Mode.Tablet

  /** Follow the viewport from now on. Called once by the composition root, with the config. */
  def init(ui: UiConfig): Unit =
    def apply(m: Mode): Unit =
      current.set(m)
      val root = dom.document.documentElement
      root.setAttribute("data-layout", m.wire.value)
      if m == Mode.Tablet then root.setAttribute("data-tablet", "") else root.removeAttribute("data-tablet")
    val queries = Try(
      (
        dom.window.matchMedia(query(ui.narrowMaxWidthPx, ui.narrowMaxHeightPx).value),
        dom.window.matchMedia(tabletQuery(ui.tabletMaxWidthPx).value)
      )
    ).toOption
    queries match
      case Some((phone, tablet)) =>
        def now(): Mode = if phone.matches then Mode.Narrow else if tablet.matches then Mode.Tablet else Mode.Wide
        apply(now())
        // a rotated phone or a resized window switches at once
        phone.addEventListener("change", (_: dom.Event) => apply(now()))
        tablet.addEventListener("change", (_: dom.Event) => apply(now()))
      case None =>
        val coarse = Try(dom.window.matchMedia("(pointer: coarse)").matches).getOrElse(false)
        apply(
          mode(
            ModeWidth(dom.window.innerWidth),
            ModeHeight(dom.window.innerHeight),
            coarse,
            ui.narrowMaxWidthPx,
            ui.narrowMaxHeightPx,
            ui.tabletMaxWidthPx
          )
        )
