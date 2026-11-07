package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

trait MapSurface:
  // ── the view ───────────────────────────────────────────────────────────────────────────────
  def zoom: com.histmersion.web.map.MapSurface.Zoom
  def maxZoom: com.histmersion.web.map.MapSurface.MaxZoom
  def setMaxZoom(z: com.histmersion.web.map.MapSurface.MaxZoom): Unit
  def center: LatLon
  def bounds: GeoBounds

  /** The container's size, CSS pixels. */
  def size: (com.histmersion.web.map.MapSurface.Dimension, com.histmersion.web.map.MapSurface.Dimension)
  def setView(at: LatLon, zoom: com.histmersion.web.map.MapSurface.Zoom, animate: com.histmersion.web.map.MapSurface.SetViewAnimate = com.histmersion.web.map.MapSurface.SetViewAnimate(false)): Unit
  def setZoom(zoom: com.histmersion.web.map.MapSurface.Zoom, animate: com.histmersion.web.map.MapSurface.SetZoomAnimate = com.histmersion.web.map.MapSurface.SetZoomAnimate(true)): Unit = setView(center, zoom, animate)
  def fitBounds(b: GeoBounds, padding: com.histmersion.web.map.MapSurface.FitBoundsPadding, maxZoom: com.histmersion.web.map.MapSurface.MaxZoom, animate: com.histmersion.web.map.MapSurface.FitBoundsAnimate = com.histmersion.web.map.MapSurface.FitBoundsAnimate(true)): Unit
  def panBy(dx: com.histmersion.web.map.MapSurface.PanByDx, dy: com.histmersion.web.map.MapSurface.PanByDy, animate: com.histmersion.web.map.MapSurface.PanByAnimate = com.histmersion.web.map.MapSurface.PanByAnimate(false)): Unit
  def project(p: LatLon): ScreenPt
  def unproject(p: ScreenPt): LatLon

  /** The container changed size (a window resize, or the chrome around it: a folded filter row, a dragged sidebar): the
    * map takes it and is drawn again at once — nothing is fetched or made again.
    */
  def resize(): Unit

  /** Whether `el` is the map's own chrome — a control, a popup — where the page's map menu does not open. */
  def isChrome(el: dom.Element): Boolean = false

  /** Put the page's own control `el` in corner `c`, styled as the map's controls are. */
  def addControl(el: dom.HTMLElement, c: Corner): Unit = Option(corner(c)).foreach(_.appendChild(el))

  /** Where a popup's own content goes (the popup itself where it has no separate box). */
  def popupBody(p: Popup): Option[dom.HTMLElement] = p.element

  /** Locked, a person's drag, wheel, keys and pinch neither pan nor zoom it (the page's own moves still do). */
  def setLocked(locked: com.histmersion.web.map.MapSurface.SetLockedLocked): Unit = ()

  /** How many times a change of the container's size was taken (diagnostics). */
  def resizeCount: com.histmersion.web.map.MapSurface.ResizeCount = com.histmersion.web.map.MapSurface.ResizeCount(0)
  def isMoving: Boolean

  /** Still, and every tile of the view drawn. */
  def isDrawn: Boolean
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
  def credits: Seq[com.histmersion.web.map.BaseMapSpec.Raster.Attribution] = Nil
  def shapes(id: com.histmersion.web.map.MapSurface.ShapesId, style: ShapeStyle): ShapeSet

  /** A set made already, if it was. */
  def shapeSet(id: com.histmersion.web.map.MapSurface.ShapeSetId): Option[ShapeSet]

  /** The interactive shapes under `p`, topmost first. */
  def shapesAt(p: ScreenPt): Seq[ShapeHit]

  /** The point layer `id` of the server's tiles, its features in the tiles' layer `layer`, cut down to the tiles'
    * `maxZoom` (MapLibre's 512-pixel zoom; a deeper view reads those). Made once per id.
    */
  def pointTiles(id: com.histmersion.web.map.MapSurface.LayerId, layer: com.histmersion.web.map.MapSurface.LayerId, maxZoom: com.histmersion.web.map.MapSurface.PointTilesMaxZoom): PointTiles
  def popup(at: LatLon, html: com.histmersion.web.map.MapSurface.PopupHtml, cls: com.histmersion.web.map.MapSurface.PopupCls = com.histmersion.web.map.MapSurface.PopupCls("")): Popup
  def closePopups(): Unit

  /** The base map's names placed in the view now, their text as drawn (the regressions read it). */
  def drawnNames: Seq[com.histmersion.web.map.MapSurface.LabelText]

  /** The base map's areas drawn under a point, as `layer:kind` (the regressions check the land cover). */
  def baseMapAreasAt(p: ScreenPt): Seq[com.histmersion.web.map.MapSurface.LayerId]

  /** The base map's names placed now: their layer, text and anchor on the screen (a line's middle) — the regressions
    * measure their contrast against what lies under them.
    */
  def baseMapLabels: Seq[(com.histmersion.web.map.MapSurface.LabelText, com.histmersion.web.map.MapSurface.LabelText, ScreenPt)]

  /** A layer of the base map's style: its visibility (`visible`, `none`), `None` when the style has no such layer (the
    * regressions check what an option turns on and off).
    */
  def baseMapLayer(id: com.histmersion.web.map.MapSurface.LayerId): Option[com.histmersion.web.map.MapSurface.LayerId]

  /** How many of a base-map layer's features are drawn in the view now (0 for none, or no such layer). */
  def baseMapDrawn(id: com.histmersion.web.map.MapSurface.LayerId): com.histmersion.web.map.MapSurface.DrawnCount

  /** The browser's own capabilities, as the surface found them. */
  def capabilities: Capabilities

  /** Take the map away (its element stays). */
  def destroy(): Unit

object MapSurface:
  type Dimension = Dimension.T
  object Dimension extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrawnCount = DrawnCount.T
  object DrawnCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelText = LabelText.T
  object LabelText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerId = LayerId.T
  object LayerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResizeCount = ResizeCount.T
  object ResizeCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Zoom = Zoom.T
  object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetViewAnimate = SetViewAnimate.T
  object SetViewAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetZoomAnimate = SetZoomAnimate.T
  object SetZoomAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FitBoundsPadding = FitBoundsPadding.T
  object FitBoundsPadding extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FitBoundsAnimate = FitBoundsAnimate.T
  object FitBoundsAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanByDx = PanByDx.T
  object PanByDx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanByDy = PanByDy.T
  object PanByDy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanByAnimate = PanByAnimate.T
  object PanByAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetLockedLocked = SetLockedLocked.T
  object SetLockedLocked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapesId = ShapesId.T
  object ShapesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapeSetId = ShapeSetId.T
  object ShapeSetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointTilesMaxZoom = PointTilesMaxZoom.T
  object PointTilesMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PopupHtml = PopupHtml.T
  object PopupHtml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PopupCls = PopupCls.T
  object PopupCls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
