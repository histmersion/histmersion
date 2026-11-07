package com.histmersion.web

import com.histmersion.api.instance.UiConfig
import com.raquo.laminar.api.L.*
import org.scalajs.dom

import scala.util.Try

/** Which layout the page is in. At or below `ui.narrow-max-width-px` it is NARROW — the phone layout: the map first,
  * the sidebar a bottom sheet, windows full-screen. Wider, but at or below `ui.tablet-max-width-px`, it is TABLET — the
  * same map-first layout at tablet size: the controls behind the app bar's buttons, the timeline open, the list a sheet
  * at the side, windows floating as on a desktop. The stylesheet does the work, keyed off `<html data-layout="narrow">`
  * (both) and `<html data-tablet>` (the tablet's own sizes); this object only decides the mode and says so. Desktop
  * never gets `narrow`, so no rule written for a phone or a tablet can reach it.
  */
object Viewport:
  type MediaQuery = MediaQuery.T
  object MediaQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ModeWidth = ModeWidth.T
  object ModeWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModeHeight = ModeHeight.T
  object ModeHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModeCoarsePointer = ModeCoarsePointer.T
  object ModeCoarsePointer extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModeMaxWidth = ModeMaxWidth.T
  object ModeMaxWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModeMaxHeight = ModeMaxHeight.T
  object ModeMaxHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModeTabletMaxWidth = ModeTabletMaxWidth.T
  object ModeTabletMaxWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryMaxWidth = QueryMaxWidth.T
  object QueryMaxWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryMaxHeight = QueryMaxHeight.T
  object QueryMaxHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TabletQueryTabletMaxWidth = TabletQueryTabletMaxWidth.T
  object TabletQueryTabletMaxWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Narrow = Narrow.T
  object Narrow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Mode(val wire: Mode.Wire):
    case Narrow extends Mode("narrow")
    case Tablet extends Mode("narrow")
    case Wide extends Mode("wide")

  object Mode:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: the mode a viewport of `width` × `height` (CSS px) is in. Narrow when it is thin, or when it is short and
    * driven by touch — a phone held sideways, which is wide but has no room; else a tablet up to `tabletMaxWidth`.
    */
  def mode(
      width: com.histmersion.web.Viewport.ModeWidth,
      height: com.histmersion.web.Viewport.ModeHeight,
      coarsePointer: com.histmersion.web.Viewport.ModeCoarsePointer,
      maxWidth: com.histmersion.web.Viewport.ModeMaxWidth,
      maxHeight: com.histmersion.web.Viewport.ModeMaxHeight,
      tabletMaxWidth: com.histmersion.web.Viewport.ModeTabletMaxWidth
  ): Mode =
    if width <= maxWidth || (coarsePointer && height <= maxHeight) then Mode.Narrow
    else if width <= tabletMaxWidth then Mode.Tablet
    else Mode.Wide

  /** The phone's rule as a media query, so the browser tells us when it flips. */
  def query(maxWidth: com.histmersion.web.Viewport.QueryMaxWidth, maxHeight: com.histmersion.web.Viewport.QueryMaxHeight): com.histmersion.web.Viewport.MediaQuery =
    s"(max-width: ${maxWidth}px), (max-height: ${maxHeight}px) and (pointer: coarse)"

  /** The tablet's: every width up to its own limit (the phone's query is asked first). */
  def tabletQuery(tabletMaxWidth: com.histmersion.web.Viewport.TabletQueryTabletMaxWidth): com.histmersion.web.Viewport.MediaQuery = s"(max-width: ${tabletMaxWidth}px)"

  private val current = Var(Mode.Wide)

  val modeSignal: Signal[Mode] = current.signal.distinct
  val narrow: Signal[com.histmersion.web.Viewport.Narrow] = current.signal.map(_ != Mode.Wide)
  def narrowNow: Boolean = current.now() != Mode.Wide
  def tabletNow: Boolean = current.now() == Mode.Tablet

  /** Follow the viewport from now on. Called once by the composition root, with the config. */
  def init(ui: UiConfig): Unit =
    def apply(m: Mode): Unit =
      current.set(m)
      val root = dom.document.documentElement
      root.setAttribute("data-layout", m.wire)
      if m == Mode.Tablet then root.setAttribute("data-tablet", "") else root.removeAttribute("data-tablet")
    val queries = Try(
      (
        dom.window.matchMedia(query(ui.narrowMaxWidthPx, ui.narrowMaxHeightPx)),
        dom.window.matchMedia(tabletQuery(ui.tabletMaxWidthPx))
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
            dom.window.innerWidth,
            dom.window.innerHeight,
            coarse,
            ui.narrowMaxWidthPx,
            ui.narrowMaxHeightPx,
            ui.tabletMaxWidthPx
          )
        )
