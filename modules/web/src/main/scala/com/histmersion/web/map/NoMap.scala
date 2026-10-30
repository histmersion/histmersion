package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable

/** The map that could not be drawn: the browser has no WebGL 2 (or WebGL is off), or the engine failed to start.
  * Instead of a blank or a crashing page, the map's place says why and what to do ([[NoMap.message]]), and the app
  * works on: every call is answered and draws nothing, markers are kept off screen, so the list, the timeline and the
  * windows behave as with a map.
  */
final class NoMapSurface(el: dom.HTMLElement, why: String, caps: Capabilities, maxZoom0: Double) extends MapSurface:

  // `var`s: the view the app asked for, kept so it reads back what it set; the max zoom
  private var at = LatLon(37.0, 30.0)
  private var z = 5.0
  private var maxZ = maxZoom0

  el.classList.add("map-unavailable-host")
  private val note = dom.document.createElement("div").asInstanceOf[dom.HTMLElement]
  note.className = "map-unavailable"
  note.setAttribute("role", "alert")
  note.innerHTML = NoMap.html(why)
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

  def zoom: Double = z
  def maxZoom: Double = maxZ
  def setMaxZoom(v: Double): Unit = maxZ = v
  def center: LatLon = at
  def bounds: GeoBounds = GeoBounds(-85, -180, 85, 180)
  def size: (Double, Double) = (0.0, 0.0)
  def setView(p: LatLon, zoom: Double, animate: Boolean = false): Unit = { at = p; z = zoom.min(maxZ) }
  def fitBounds(b: GeoBounds, padding: Int, maxZoom: Double, animate: Boolean = true): Unit =
    at = LatLon((b.south + b.north) / 2, (b.west + b.east) / 2)
  def panBy(dx: Double, dy: Double, animate: Boolean = false): Unit = ()
  // far outside any view: a marker placed here is hidden, never moved ([[MarkerPane]])
  def project(p: LatLon): ScreenPt = ScreenPt(-1e6, -1e6)
  def unproject(p: ScreenPt): LatLon = at
  def resize(): Unit = ()
  def isMoving: Boolean = false
  def isDrawn: Boolean = false
  def on(kind: MapEventKind)(f: MapEvent => Unit): Subscription = new Subscription { def cancel(): Unit = () }
  def container: dom.HTMLElement = el
  def overlayRoot: dom.HTMLElement = overlay
  def corner(c: Corner): dom.HTMLElement = corners(c)
  def setBaseMap(b: BaseMapSpec): Unit = ()
  def shapes(id: String, style: ShapeStyle): ShapeSet = sets.getOrElseUpdate(id, NoMap.Shapes(id))
  def shapeSet(id: String): Option[ShapeSet] = sets.get(id)
  def shapesAt(p: ScreenPt): Seq[ShapeHit] = Nil
  def pointTiles(id: String, layer: String, maxZoom: Int): PointTiles = NoMap.Tiles
  def popup(at: LatLon, html: String, cls: String = ""): Popup = new Popup:
    def close(): Unit = ()
    def isOpen: Boolean = false
    def element: Option[dom.HTMLElement] = None
  def closePopups(): Unit = ()
  def drawnNames: Seq[String] = Nil
  def baseMapAreasAt(p: ScreenPt): Seq[String] = Nil
  def baseMapLabels: Seq[(String, String, ScreenPt)] = Nil
  def baseMapLayer(id: String): Option[String] = None
  def baseMapDrawn(id: String): Int = 0
  def capabilities: Capabilities = caps
  def destroy(): Unit = { note.remove(); overlay.remove() }

  /** Why there is no map (what the message says first). */
  def unavailable: String = why

object NoMap:
  /** A shape set that draws nothing and reads back the ids it was given. */
  final class Shapes(val id: String) extends ShapeSet:
    // `var`: the ids the app set, read back as it set them
    private var current: Seq[String] = Nil
    def set(shapes: Seq[ShapeFeature]): Unit = current = shapes.map(_.id)
    def restyle(id: String, colour: String, alpha: Double): Unit = ()
    def ids: Seq[String] = current

  /** Tiles that are never asked for: no map, no features. */
  object Tiles extends PointTiles:
    def setTiles(template: Option[String]): Unit = ()
    def features: Seq[TilePointFeature] = Nil
    def onChange(f: () => Unit): Subscription = new Subscription { def cancel(): Unit = () }
    def loaded: Boolean = true

  private def esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  /** Pure: the message in the map's place — why, then what to do; the rest of the app works without it. */
  def message(why: String): (String, String) =
    (
      s"The map cannot be drawn here: $why.",
      "Every base map — the online ones and the local ones — is drawn with WebGL 2. Turn on hardware " +
        "(graphics) acceleration in the browser's settings, or open the page in a current Chrome, Edge, " +
        "Firefox or Safari. The list, the timeline and the articles work without the map."
    )

  def html(why: String): String =
    val (head, body) = message(why)
    s"""<p class="map-unavailable-title">${esc(head)}</p><p>${esc(body)}</p>"""
