package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Html
import com.histmersion.lint.{tolerated, LintRule}

trait MapSurface:
  // ── the view ───────────────────────────────────────────────────────────────────────────────
  def zoom: MapSurface.Zoom
  def maxZoom: MapSurface.MaxZoom
  def setMaxZoom(z: MapSurface.MaxZoom): Unit
  def center: LatLon
  def bounds: GeoBounds

  /** The container's size, CSS pixels. */
  def size: (MapSurface.Dimension, MapSurface.Dimension)
  def setView(
      at: LatLon,
      zoom: MapSurface.Zoom,
      animate: MapSurface.SetViewAnimate = MapSurface.SetViewAnimate.Instant
  ): Unit
  def setZoom(zoom: MapSurface.Zoom, animate: MapSurface.SetViewAnimate = MapSurface.SetViewAnimate.Animated): Unit =
    setView(center, zoom, animate)
  def fitBounds(
      b: GeoBounds,
      padding: MapSurface.FitBoundsPadding,
      maxZoom: MapSurface.MaxZoom,
      animate: MapSurface.FitBoundsAnimate = MapSurface.FitBoundsAnimate.Animated
  ): Unit
  def panBy(
      dx: MapSurface.PanByDx,
      dy: MapSurface.PanByDy,
      animate: MapSurface.PanByAnimate = MapSurface.PanByAnimate.Instant
  ): Unit
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
  def setLocked(locked: MapSurface.Locked): Unit = ()

  /** How many times a change of the container's size was taken (diagnostics). */
  def resizeCount: MapSurface.ResizeCount = MapSurface.ResizeCount(0)
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
  def credits: Seq[BaseMapSpec.Raster.Attribution] = Nil
  def shapes(id: MapSurface.ShapesId, style: ShapeStyle): ShapeSet

  /** A set made already, if it was. */
  def shapeSet(id: MapSurface.ShapeSetId): Option[ShapeSet]

  /** The interactive shapes under `p`, topmost first. */
  def shapesAt(p: ScreenPt): Seq[ShapeHit]

  /** The point layer `id` of the server's tiles, its features in the tiles' layer `layer`, cut down to the tiles'
    * `maxZoom` (MapLibre's 512-pixel zoom; a deeper view reads those). Made once per id.
    */
  def pointTiles(id: MapSurface.LayerId, layer: MapSurface.LayerId, maxZoom: MapSurface.PointTilesMaxZoom): PointTiles
  def popup(at: LatLon, html: Html, cls: MapSurface.PopupCls = MapSurface.PopupCls("")): Popup
  def closePopups(): Unit

  /** The base map's names placed in the view now, their text as drawn (the regressions read it). */
  def drawnNames: Seq[MapSurface.LabelText]

  /** The base map's areas drawn under a point, as `layer:kind` (the regressions check the land cover). */
  def baseMapAreasAt(p: ScreenPt): Seq[MapSurface.LayerId]

  /** The base map's names placed now: their layer, text and anchor on the screen (a line's middle) — the regressions
    * measure their contrast against what lies under them.
    */
  def baseMapLabels: Seq[(MapSurface.LabelText, MapSurface.LabelText, ScreenPt)]

  /** A layer of the base map's style: its visibility (`visible`, `none`), `None` when the style has no such layer (the
    * regressions check what an option turns on and off).
    */
  def baseMapLayer(id: MapSurface.LayerId): Option[MapSurface.LayerId]

  /** How many of a base-map layer's features are drawn in the view now (0 for none, or no such layer). */
  def baseMapDrawn(id: MapSurface.LayerId): MapSurface.DrawnCount

  /** The browser's own capabilities, as the surface found them. */
  def capabilities: Capabilities

  /** Take the map away (its element stays). */
  def destroy(): Unit

object MapSurface:
  type Dimension = Dimension.T
  object Dimension extends Newtype[Double, Pure]
  type DrawnCount = DrawnCount.T
  object DrawnCount extends Newtype[Int, Pure]
  type LabelText = LabelText.T
  object LabelText extends Newtype[String, Pure]
  type LayerId = LayerId.T
  object LayerId extends Newtype[String, Pure]
  export MapEngine.MaxZoom
  type ResizeCount = ResizeCount.T
  object ResizeCount extends Newtype[Int, Pure]
  type Zoom = Zoom.T
  object Zoom extends Newtype[Double, Pure]
  enum SetViewAnimate derives CanEqual:
    case Animated, Instant
  object SetViewAnimate:
    extension (setViewAnimate: SetViewAnimate) def animated: Boolean = setViewAnimate == Animated
  type FitBoundsPadding = FitBoundsPadding.T
  object FitBoundsPadding extends Newtype[Int, Pure]
  enum FitBoundsAnimate derives CanEqual:
    case Animated, Instant
  object FitBoundsAnimate:
    extension (fitBoundsAnimate: FitBoundsAnimate) def animated: Boolean = fitBoundsAnimate == Animated
  type PanByDx = PanByDx.T
  object PanByDx extends Newtype[Double, Pure]
  type PanByDy = PanByDy.T
  object PanByDy extends Newtype[Double, Pure]
  enum PanByAnimate derives CanEqual:
    case Animated, Instant
  object PanByAnimate:
    extension (panByAnimate: PanByAnimate) def animated: Boolean = panByAnimate == Animated
  enum Locked derives CanEqual:
    case Locked, Free
  object Locked:
    extension (locked: Locked) def locked: Boolean = locked == Locked
  type ShapesId = ShapesId.T
  object ShapesId extends Newtype[String, Pure]
  type ShapeSetId = ShapeSetId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ShapeSetId extends Newtype[String, Pure]
  type PointTilesMaxZoom = PointTilesMaxZoom.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PointTilesMaxZoom extends Newtype[Int, Pure]
  type PopupCls = PopupCls.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PopupCls extends Newtype[String, Pure]
