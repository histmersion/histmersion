package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

trait MapSurface:
  // ── the view ───────────────────────────────────────────────────────────────────────────────
  def zoom: com.histmersion.web.map.MapSurface.ZoomResult
  def maxZoom: com.histmersion.web.map.MapSurface.MaxZoomResult
  def setMaxZoom(z: com.histmersion.web.map.MapSurface.SetMaxZoomZ): Unit
  def center: LatLon
  def bounds: GeoBounds

  /** The container's size, CSS pixels. */
  def size: (com.histmersion.web.map.MapSurface.SizeResultPart1, com.histmersion.web.map.MapSurface.SizeResultPart2)
  def setView(at: LatLon, zoom: com.histmersion.web.map.MapSurface.SetViewZoom, animate: com.histmersion.web.map.MapSurface.SetViewAnimate = false): Unit
  def setZoom(zoom: com.histmersion.web.map.MapSurface.SetZoomZoom, animate: com.histmersion.web.map.MapSurface.SetZoomAnimate = true): Unit = setView(center, zoom, animate)
  def fitBounds(b: GeoBounds, padding: com.histmersion.web.map.MapSurface.FitBoundsPadding, maxZoom: com.histmersion.web.map.MapSurface.FitBoundsMaxZoom, animate: com.histmersion.web.map.MapSurface.FitBoundsAnimate = true): Unit
  def panBy(dx: com.histmersion.web.map.MapSurface.PanByDx, dy: com.histmersion.web.map.MapSurface.PanByDy, animate: com.histmersion.web.map.MapSurface.PanByAnimate = false): Unit
  def project(p: LatLon): ScreenPt
  def unproject(p: ScreenPt): LatLon

  /** The container changed size (a window resize, or the chrome around it: a folded filter row, a dragged sidebar): the
    * map takes it and is drawn again at once — nothing is fetched or made again.
    */
  def resize(): Unit

  /** Whether `el` is the map's own chrome — a control, a popup — where the page's map menu does not open. */
  def isChrome(el: dom.Element): com.histmersion.web.map.MapSurface.IsChromeResult = false

  /** Put the page's own control `el` in corner `c`, styled as the map's controls are. */
  def addControl(el: dom.HTMLElement, c: Corner): Unit = Option(corner(c)).foreach(_.appendChild(el))

  /** Where a popup's own content goes (the popup itself where it has no separate box). */
  def popupBody(p: Popup): Option[dom.HTMLElement] = p.element

  /** Locked, a person's drag, wheel, keys and pinch neither pan nor zoom it (the page's own moves still do). */
  def setLocked(locked: com.histmersion.web.map.MapSurface.SetLockedLocked): Unit = ()

  /** How many times a change of the container's size was taken (diagnostics). */
  def resizeCount: com.histmersion.web.map.MapSurface.ResizeCountResult = 0
  def isMoving: com.histmersion.web.map.MapSurface.IsMovingResult

  /** Still, and every tile of the view drawn. */
  def isDrawn: com.histmersion.web.map.MapSurface.IsDrawnResult
  // ── events ─────────────────────────────────────────────────────────────────────────────────
  def on(kind: MapEventKind)(f: MapEvent => Unit): Subscription
  // ── the DOM ────────────────────────────────────────────────────────────────────────────────
  def container: dom.HTMLElement

  /** Where the marker panes go: over the map, inside its gesture area (a drag over a marker pans). */
  def overlayRoot: dom.HTMLElement

  /** A corner's element, for a control of the app's. */
  def corner(c: Corner): dom.HTMLElement
  // ── what is drawn ──────────────────────────────────────────────────────────────────────────
  def setBaseMap(b: BaseMapSpec): Unit

  /** The credits (HTML, one per source) of the base map drawn now — known once it is drawn
    * ([[MapEventKind.BaseMapReady]]); the app shows them ([[com.histmersion.web.BaseMaps]]).
    */
  def credits: Seq[com.histmersion.web.map.MapSurface.CreditsResult] = Nil
  def shapes(id: com.histmersion.web.map.MapSurface.ShapesId, style: ShapeStyle): ShapeSet

  /** A set made already, if it was. */
  def shapeSet(id: com.histmersion.web.map.MapSurface.ShapeSetId): Option[ShapeSet]

  /** The interactive shapes under `p`, topmost first. */
  def shapesAt(p: ScreenPt): Seq[ShapeHit]

  /** The point layer `id` of the server's tiles, its features in the tiles' layer `layer`, cut down to the tiles'
    * `maxZoom` (MapLibre's 512-pixel zoom; a deeper view reads those). Made once per id.
    */
  def pointTiles(id: com.histmersion.web.map.MapSurface.PointTilesId, layer: com.histmersion.web.map.MapSurface.PointTilesLayer, maxZoom: com.histmersion.web.map.MapSurface.PointTilesMaxZoom): PointTiles
  def popup(at: LatLon, html: com.histmersion.web.map.MapSurface.PopupHtml, cls: com.histmersion.web.map.MapSurface.PopupCls = ""): Popup
  def closePopups(): Unit

  /** The base map's names placed in the view now, their text as drawn (the regressions read it). */
  def drawnNames: Seq[com.histmersion.web.map.MapSurface.DrawnNamesResult]

  /** The base map's areas drawn under a point, as `layer:kind` (the regressions check the land cover). */
  def baseMapAreasAt(p: ScreenPt): Seq[com.histmersion.web.map.MapSurface.BaseMapAreasAtResult]

  /** The base map's names placed now: their layer, text and anchor on the screen (a line's middle) — the regressions
    * measure their contrast against what lies under them.
    */
  def baseMapLabels: Seq[(com.histmersion.web.map.MapSurface.BaseMapLabelsResultPart1, com.histmersion.web.map.MapSurface.BaseMapLabelsResultPart2, ScreenPt)]

  /** A layer of the base map's style: its visibility (`visible`, `none`), `None` when the style has no such layer (the
    * regressions check what an option turns on and off).
    */
  def baseMapLayer(id: com.histmersion.web.map.MapSurface.BaseMapLayerId): Option[com.histmersion.web.map.MapSurface.BaseMapLayerResult]

  /** How many of a base-map layer's features are drawn in the view now (0 for none, or no such layer). */
  def baseMapDrawn(id: com.histmersion.web.map.MapSurface.BaseMapDrawnId): com.histmersion.web.map.MapSurface.BaseMapDrawnResult

  /** The browser's own capabilities, as the surface found them. */
  def capabilities: Capabilities

  /** Take the map away (its element stays). */
  def destroy(): Unit

object MapSurface:
  type ZoomResult = ZoomResult.T
  object ZoomResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoomResult = MaxZoomResult.T
  object MaxZoomResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetMaxZoomZ = SetMaxZoomZ.T
  object SetMaxZoomZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SizeResultPart1 = SizeResultPart1.T
  object SizeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SizeResultPart2 = SizeResultPart2.T
  object SizeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetViewZoom = SetViewZoom.T
  object SetViewZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetViewAnimate = SetViewAnimate.T
  object SetViewAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetZoomZoom = SetZoomZoom.T
  object SetZoomZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetZoomAnimate = SetZoomAnimate.T
  object SetZoomAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FitBoundsPadding = FitBoundsPadding.T
  object FitBoundsPadding extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FitBoundsMaxZoom = FitBoundsMaxZoom.T
  object FitBoundsMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FitBoundsAnimate = FitBoundsAnimate.T
  object FitBoundsAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanByDx = PanByDx.T
  object PanByDx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanByDy = PanByDy.T
  object PanByDy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanByAnimate = PanByAnimate.T
  object PanByAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsChromeResult = IsChromeResult.T
  object IsChromeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetLockedLocked = SetLockedLocked.T
  object SetLockedLocked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResizeCountResult = ResizeCountResult.T
  object ResizeCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsMovingResult = IsMovingResult.T
  object IsMovingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsDrawnResult = IsDrawnResult.T
  object IsDrawnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreditsResult = CreditsResult.T
  object CreditsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapesId = ShapesId.T
  object ShapesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapeSetId = ShapeSetId.T
  object ShapeSetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointTilesId = PointTilesId.T
  object PointTilesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointTilesLayer = PointTilesLayer.T
  object PointTilesLayer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointTilesMaxZoom = PointTilesMaxZoom.T
  object PointTilesMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PopupHtml = PopupHtml.T
  object PopupHtml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PopupCls = PopupCls.T
  object PopupCls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrawnNamesResult = DrawnNamesResult.T
  object DrawnNamesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BaseMapAreasAtResult = BaseMapAreasAtResult.T
  object BaseMapAreasAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BaseMapLabelsResultPart1 = BaseMapLabelsResultPart1.T
  object BaseMapLabelsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BaseMapLabelsResultPart2 = BaseMapLabelsResultPart2.T
  object BaseMapLabelsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BaseMapLayerResult = BaseMapLayerResult.T
  object BaseMapLayerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BaseMapLayerId = BaseMapLayerId.T
  object BaseMapLayerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BaseMapDrawnResult = BaseMapDrawnResult.T
  object BaseMapDrawnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BaseMapDrawnId = BaseMapDrawnId.T
  object BaseMapDrawnId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
