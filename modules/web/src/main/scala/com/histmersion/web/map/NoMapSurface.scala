package com.histmersion
package web.map

import com.histmersion.api.Problem
import org.scalajs.dom

import scala.collection.mutable
import io.github.iltotore.iron.constraint.any.Pure

/** The map that could not be drawn: the browser has no WebGL 2 (or WebGL is off), or the engine failed to start.
  * Instead of a blank or a crashing page, the map's place says why and what to do ([[NoMap.message]]), and the app
  * works on: every call is answered and draws nothing, markers are kept off screen, so the list, the timeline and the
  * windows behave as with a map.
  */
final class NoMapSurface(el: dom.HTMLElement, why: Problem, caps: Capabilities, maxZoom0: MapSurface.MaxZoom)
    extends MapSurface:

  // `var`s: the view the app asked for, kept so it reads back what it set; the max zoom
  private var at = LatLon(LatLon.Lat(37.0), LatLon.Lon(30.0))
  private var z = 5.0
  private var maxZ = maxZoom0

  el.classList.add("map-unavailable-host")
  private val note = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
  note.className = "map-unavailable"
  note.setAttribute("role", "alert")
  note.innerHTML = NoMap.html(why).value
  el.appendChild(note)
  // the markers' panes live here, hidden: nothing of them is ever shown
  private val overlay = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
  overlay.className = "map-unavailable-overlay is-hidden"
  el.appendChild(overlay)
  private val corners = Corner.values
    .map(c =>
      c -> {
        val d = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
        d.className = "is-hidden"
        overlay.appendChild(d)
        d
      }
    )
    .toMap
  private val sets = mutable.LinkedHashMap.empty[String, ShapeSet]

  def zoom: MapSurface.Zoom = MapSurface.Zoom(z)
  def maxZoom: MapSurface.MaxZoom = maxZ
  def setMaxZoom(v: MapSurface.MaxZoom): Unit = maxZ = v
  def center: LatLon = at
  def bounds: GeoBounds =
    GeoBounds(GeoBounds.South(-85), GeoBounds.West(-180), GeoBounds.North(85), GeoBounds.East(180))
  def size: (MapSurface.Dimension, MapSurface.Dimension) = (MapSurface.Dimension(0.0), MapSurface.Dimension(0.0))
  def setView(
      p: LatLon,
      zoom: MapSurface.Zoom,
      animate: MapSurface.SetViewAnimate = MapSurface.SetViewAnimate(false)
  ): Unit = { at = p; z = math.min(zoom.value, maxZ.value) }
  def fitBounds(
      b: GeoBounds,
      padding: MapSurface.FitBoundsPadding,
      maxZoom: MapSurface.MaxZoom,
      animate: MapSurface.FitBoundsAnimate = MapSurface.FitBoundsAnimate(true)
  ): Unit =
    at = LatLon(LatLon.Lat((b.south.value + b.north.value) / 2), LatLon.Lon((b.west.value + b.east.value) / 2))
  def panBy(
      dx: MapSurface.PanByDx,
      dy: MapSurface.PanByDy,
      animate: MapSurface.PanByAnimate = MapSurface.PanByAnimate(false)
  ): Unit = ()
  // far outside any view: a marker placed here is hidden, never moved ([[MarkerPane]])
  def project(p: LatLon): ScreenPt = ScreenPt(ScreenPt.X(-1e6), ScreenPt.Y(-1e6))
  def unproject(p: ScreenPt): LatLon = at
  def resize(): Unit = ()
  def isMoving: Boolean = false
  def isDrawn: Boolean = false
  def on(kind: MapEventKind)(f: MapEvent => Unit): Subscription = new Subscription { def cancel(): Unit = () }
  def container: dom.HTMLElement = el
  def overlayRoot: dom.HTMLElement = overlay
  def corner(c: Corner): dom.HTMLElement = corners(c)
  def setBaseMap(b: BaseMapSpec): Unit = ()
  def shapes(id: MapSurface.ShapesId, style: ShapeStyle): ShapeSet =
    sets.getOrElseUpdate(id.value, NoMap.Shapes(NoMap.Shapes.Id(id.value)))
  def shapeSet(id: MapSurface.ShapeSetId): Option[ShapeSet] = sets.get(id.value)
  def shapesAt(p: ScreenPt): Seq[ShapeHit] = Nil
  def pointTiles(id: MapSurface.LayerId, layer: MapSurface.LayerId, maxZoom: MapSurface.PointTilesMaxZoom): PointTiles =
    NoMap.Tiles
  def popup(at: LatLon, html: MapSurface.PopupHtml, cls: MapSurface.PopupCls = MapSurface.PopupCls("")): Popup =
    new Popup:
      def close(): Unit = ()
      def isOpen: Boolean = false
      def element: Option[dom.HTMLElement] = None
  def closePopups(): Unit = ()
  def drawnNames: Seq[MapSurface.LabelText] = Nil
  def baseMapAreasAt(p: ScreenPt): Seq[MapSurface.LayerId] = Nil
  def baseMapLabels: Seq[(MapSurface.LabelText, MapSurface.LabelText, ScreenPt)] = Nil
  def baseMapLayer(id: MapSurface.LayerId): Option[MapSurface.LayerId] = None
  def baseMapDrawn(id: MapSurface.LayerId): MapSurface.DrawnCount = MapSurface.DrawnCount(0)
  def capabilities: Capabilities = caps
  def destroy(): Unit = { note.remove(); overlay.remove() }

  /** Why there is no map (what the message says first). */
  def unavailable: Problem = why

