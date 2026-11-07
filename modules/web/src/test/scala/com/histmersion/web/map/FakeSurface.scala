package com.histmersion.web.map

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import org.scalajs.dom

import scala.collection.mutable

/** A [[MapSurface]] in memory: Web Mercator on the 256-pixel grid, a fixed size, its events fired by hand, its shapes
  * kept as data, its base maps recorded. The test double of the map engine — the view-model's logic (clustering, what
  * is drawn, base-map decisions) runs on it without a browser.
  */
final class FakeSurface(w: Double = 1000, h: Double = 600, caps: Capabilities = Capabilities(com.histmersion.web.map.Capabilities.Webgl(true), com.histmersion.web.map.Capabilities.Software(false), com.histmersion.web.map.Capabilities.Renderer("fake")))
    extends MapSurface:
  // `var`s: the view, as a map holds it
  private var c = LatLon(com.histmersion.web.map.LatLon.Lat(37), com.histmersion.web.map.LatLon.Lon(30))
  private var z = 5.0
  private var zmax = 18.0
  val baseMaps: mutable.ListBuffer[BaseMapSpec] = mutable.ListBuffer.empty
  val sets: mutable.LinkedHashMap[String, FakeShapes] = mutable.LinkedHashMap.empty
  private val handlers = mutable.HashMap.empty[MapEventKind, mutable.ListBuffer[MapEvent => Unit]]

  private def world(p: LatLon): (Double, Double) = Clustering.world(p.lat, p.lon, z)

  def zoom: Double = z
  def maxZoom: Double = zmax
  def setMaxZoom(m: Double): Unit = zmax = m
  def center: LatLon = c
  def bounds: GeoBounds =
    val nw = unproject(ScreenPt(com.histmersion.web.map.ScreenPt.X(0), com.histmersion.web.map.ScreenPt.Y(0)))
    val se = unproject(ScreenPt(w, h))
    GeoBounds(se.lat, nw.lon, nw.lat, se.lon)
  def size: (Double, Double) = (w, h)
  def setView(at: LatLon, zoom: Double, animate: Boolean): Unit =
    c = at
    z = math.min(zoom, zmax)
    fire(MapEventKind.Move); fire(MapEventKind.MoveEnd); fire(MapEventKind.ZoomEnd)
  def fitBounds(b: GeoBounds, padding: Int, maxZoom: Double, animate: Boolean): Unit =
    setView(LatLon((b.south + b.north) / 2, (b.west + b.east) / 2), math.min(maxZoom, z + 1), animate)
  def panBy(dx: Double, dy: Double, animate: Boolean): Unit =
    setView(unproject(ScreenPt(w / 2 + dx, h / 2 + dy)), z, animate)
  def project(p: LatLon): ScreenPt =
    val (x, y) = world(p)
    val (cx, cy) = world(c)
    ScreenPt(x - cx + w / 2, y - cy + h / 2)
  def unproject(p: ScreenPt): LatLon =
    val scale = 256.0 * math.pow(2, z)
    val (cx, cy) = world(c)
    val x = cx + p.x - w / 2
    val y = cy + p.y - h / 2
    val lon = x / scale * 360 - 180
    val lat = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * y / scale))))
    LatLon(lat, lon)
  def resize(): Unit = fire(MapEventKind.Resize)
  def isMoving: Boolean = false
  def isDrawn: Boolean = true
  def on(kind: MapEventKind)(f: MapEvent => Unit): Subscription =
    val hs = handlers.getOrElseUpdate(kind, mutable.ListBuffer.empty)
    hs += f
    () => { val _ = hs -= f }
  def fire(kind: MapEventKind, e: MapEvent = MapEvent()): Unit = handlers.get(kind).foreach(_.toList.foreach(_(e)))
  def container: dom.HTMLElement = null
  def overlayRoot: dom.HTMLElement = null
  def corner(c: Corner): dom.HTMLElement = null
  def setBaseMap(b: BaseMapSpec): Unit = baseMaps += b
  def shapes(id: String, style: ShapeStyle): ShapeSet = sets.getOrElseUpdate(id, FakeShapes(id))
  def shapeSet(id: String): Option[ShapeSet] = sets.get(id)
  def shapesAt(p: ScreenPt): Seq[ShapeHit] = Nil
  def pointTiles(id: String, layer: String, maxZoom: Int): PointTiles = NoMap.Tiles
  def popup(at: LatLon, html: String, cls: String): Popup = new Popup:
    def close(): Unit = ()
    def isOpen: Boolean = true
    def element: Option[dom.HTMLElement] = None
  def closePopups(): Unit = ()
  def capabilities: Capabilities = caps
  def drawnNames: Seq[String] = Nil
  def baseMapAreasAt(p: ScreenPt): Seq[String] = Nil
  def baseMapLabels: Seq[(String, String, ScreenPt)] = Nil
  def baseMapLayer(id: String): Option[String] = None
  def baseMapDrawn(id: String): Int = 0
  def destroy(): Unit = ()

/** The shapes of a [[FakeSurface]]: what is drawn, and how often the geometry was sent again. */
final class FakeShapes(val id: String) extends ShapeSet:
  val drawn: mutable.LinkedHashMap[String, ShapeFeature] = mutable.LinkedHashMap.empty
  // `var`: how many times the whole set was sent again (a membership change), not restyled in place
  var rebuilt = 0
  def set(shapes: Seq[ShapeFeature]): Unit =
    if shapes.map(_.id).toSet != drawn.keySet then
      rebuilt += 1
      drawn.clear()
    shapes.foreach(s => drawn(s.id) = s)
  def restyle(id: String, colour: String, alpha: Double): Unit =
    drawn.get(id).foreach(s => drawn(id) = s.modify(_.colour).setTo(colour).modify(_.alpha).setTo((alpha: _root_.scala.Double)))
  def ids: Seq[String] = drawn.keys.toSeq
