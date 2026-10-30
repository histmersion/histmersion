package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

trait MapSurface:
  // ── the view ───────────────────────────────────────────────────────────────────────────────
  def zoom: Double
  def maxZoom: Double
  def setMaxZoom(z: Double): Unit
  def center: LatLon
  def bounds: GeoBounds

  /** The container's size, CSS pixels. */
  def size: (Double, Double)
  def setView(at: LatLon, zoom: Double, animate: Boolean = false): Unit
  def setZoom(zoom: Double, animate: Boolean = true): Unit = setView(center, zoom, animate)
  def fitBounds(b: GeoBounds, padding: Int, maxZoom: Double, animate: Boolean = true): Unit
  def panBy(dx: Double, dy: Double, animate: Boolean = false): Unit
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
  def setLocked(locked: Boolean): Unit = ()

  /** How many times a change of the container's size was taken (diagnostics). */
  def resizeCount: Int = 0
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
  def credits: Seq[String] = Nil
  def shapes(id: String, style: ShapeStyle): ShapeSet

  /** A set made already, if it was. */
  def shapeSet(id: String): Option[ShapeSet]

  /** The interactive shapes under `p`, topmost first. */
  def shapesAt(p: ScreenPt): Seq[ShapeHit]

  /** The point layer `id` of the server's tiles, its features in the tiles' layer `layer`, cut down to the tiles'
    * `maxZoom` (MapLibre's 512-pixel zoom; a deeper view reads those). Made once per id.
    */
  def pointTiles(id: String, layer: String, maxZoom: Int): PointTiles
  def popup(at: LatLon, html: String, cls: String = ""): Popup
  def closePopups(): Unit

  /** The base map's names placed in the view now, their text as drawn (the regressions read it). */
  def drawnNames: Seq[String]

  /** The base map's areas drawn under a point, as `layer:kind` (the regressions check the land cover). */
  def baseMapAreasAt(p: ScreenPt): Seq[String]

  /** The base map's names placed now: their layer, text and anchor on the screen (a line's middle) — the regressions
    * measure their contrast against what lies under them.
    */
  def baseMapLabels: Seq[(String, String, ScreenPt)]

  /** A layer of the base map's style: its visibility (`visible`, `none`), `None` when the style has no such layer (the
    * regressions check what an option turns on and off).
    */
  def baseMapLayer(id: String): Option[String]

  /** How many of a base-map layer's features are drawn in the view now (0 for none, or no such layer). */
  def baseMapDrawn(id: String): Int

  /** The browser's own capabilities, as the surface found them. */
  def capabilities: Capabilities

  /** Take the map away (its element stays). */
  def destroy(): Unit
