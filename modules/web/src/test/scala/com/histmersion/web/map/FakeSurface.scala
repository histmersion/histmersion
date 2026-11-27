package com.histmersion
package web.map

import hearth.kindlings.optics.syntax.*

import org.scalajs.dom

import scala.collection.mutable
import com.histmersion.api.Html
import com.histmersion.lint.{tolerated, LintRule}

/** A [[MapSurface]] in memory: Web Mercator on the 256-pixel grid, a fixed size, its events fired by hand, its shapes
  * kept as data, its base maps recorded. The test double of the map engine — the view-model's logic (clustering, what
  * is drawn, base-map decisions) runs on it without a browser.
  */
final class FakeSurface(
    w: MapSurface.Dimension = MapSurface.Dimension(1000),
    h: MapSurface.Dimension = MapSurface.Dimension(600),
    caps: Capabilities =
      Capabilities(Capabilities.Webgl(true), Capabilities.Software(false), Capabilities.Renderer("fake"))
) extends MapSurface:
  // `var`s: the view, as a map holds it
  private var c = LatLon(LatLon.Lat(37), LatLon.Lon(30))
  private var z = MapSurface.Zoom(5.0)
  private var zmax = MapSurface.MaxZoom(18.0)
  val baseMaps: mutable.ListBuffer[BaseMapSpec] = mutable.ListBuffer.empty
  val sets: mutable.LinkedHashMap[String, FakeShapes] = mutable.LinkedHashMap.empty
  private val handlers = mutable.HashMap.empty[MapEventKind, mutable.ListBuffer[MapEvent => Unit]]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def world(p: LatLon): (Double, Double) = Clustering.world(p.lat, p.lon, z.value)

  def zoom: MapSurface.Zoom = z
  def maxZoom: MapSurface.MaxZoom = zmax
  def setMaxZoom(m: MapSurface.MaxZoom): Unit = zmax = m
  def center: LatLon = c
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def bounds: GeoBounds =
    val nw = unproject(ScreenPt(ScreenPt.X(0), ScreenPt.Y(0)))
    val se = unproject(ScreenPt(ScreenPt.X(w.value), ScreenPt.Y(h.value)))
    GeoBounds(
      GeoBounds.South(se.lat.value),
      GeoBounds.West(nw.lon.value),
      GeoBounds.North(nw.lat.value),
      GeoBounds.East(se.lon.value)
    )
  def size: (MapSurface.Dimension, MapSurface.Dimension) = (w, h)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def setView(
      at: LatLon,
      zoom: MapSurface.Zoom,
      animate: MapSurface.SetViewAnimate = MapSurface.SetViewAnimate(false)
  ): Unit =
    c = at
    z = MapSurface.Zoom(math.min(zoom.value, zmax.value))
    fire(MapEventKind.Move); fire(MapEventKind.MoveEnd); fire(MapEventKind.ZoomEnd)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fitBounds(
      b: GeoBounds,
      padding: MapSurface.FitBoundsPadding,
      maxZoom: MapSurface.MaxZoom,
      animate: MapSurface.FitBoundsAnimate = MapSurface.FitBoundsAnimate(true)
  ): Unit =
    setView(
      LatLon(LatLon.Lat((b.south.value + b.north.value) / 2), LatLon.Lon((b.west.value + b.east.value) / 2)),
      MapSurface.Zoom(math.min(maxZoom.value, z.value + 1))
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def panBy(
      dx: MapSurface.PanByDx,
      dy: MapSurface.PanByDy,
      animate: MapSurface.PanByAnimate = MapSurface.PanByAnimate(false)
  ): Unit =
    setView(unproject(ScreenPt(ScreenPt.X(w.value / 2 + dx.value), ScreenPt.Y(h.value / 2 + dy.value))), z)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def project(p: LatLon): ScreenPt =
    val (x, y) = world(p)
    val (cx, cy) = world(c)
    ScreenPt(ScreenPt.X(x - cx + w.value / 2), ScreenPt.Y(y - cy + h.value / 2))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def unproject(p: ScreenPt): LatLon =
    val scale = 256.0 * math.pow(2, z.value)
    val (cx, cy) = world(c)
    val x = cx + p.x.value - w.value / 2
    val y = cy + p.y.value - h.value / 2
    val lon = x / scale * 360 - 180
    val lat = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * y / scale))))
    LatLon(LatLon.Lat(lat), LatLon.Lon(lon))
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def shapes(id: MapSurface.ShapesId, style: ShapeStyle): ShapeSet =
    sets.getOrElseUpdate(id.value, FakeShapes(NoMap.Shapes.Id(id.value)))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def shapeSet(id: MapSurface.ShapeSetId): Option[ShapeSet] = sets.get(id.value)
  def shapesAt(p: ScreenPt): Seq[ShapeHit] = Nil
  def pointTiles(id: MapSurface.LayerId, layer: MapSurface.LayerId, maxZoom: MapSurface.PointTilesMaxZoom): PointTiles =
    NoMap.Tiles
  def popup(at: LatLon, html: Html, cls: MapSurface.PopupCls = MapSurface.PopupCls("")): Popup =
    new Popup:
      def close(): Unit = ()
      def isOpen: Boolean = true
      def element: Option[dom.HTMLElement] = None
  def closePopups(): Unit = ()
  def capabilities: Capabilities = caps
  def drawnNames: Seq[MapSurface.LabelText] = Nil
  def baseMapAreasAt(p: ScreenPt): Seq[MapSurface.LayerId] = Nil
  def baseMapLabels: Seq[(MapSurface.LabelText, MapSurface.LabelText, ScreenPt)] = Nil
  def baseMapLayer(id: MapSurface.LayerId): Option[MapSurface.LayerId] = None
  def baseMapDrawn(id: MapSurface.LayerId): MapSurface.DrawnCount = MapSurface.DrawnCount(0)
  def destroy(): Unit = ()

/** The shapes of a [[FakeSurface]]: what is drawn, and how often the geometry was sent again. */
final class FakeShapes(val id: NoMap.Shapes.Id) extends ShapeSet:
  val drawn: mutable.LinkedHashMap[String, ShapeFeature] = mutable.LinkedHashMap.empty
  // `var`: how many times the whole set was sent again (a membership change), not restyled in place
  var rebuilt = 0
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def set(shapes: Seq[ShapeFeature]): Unit =
    if shapes.map(_.id.value).toSet != drawn.keySet then
      rebuilt += 1
      drawn.clear()
    shapes.foreach(s => drawn(s.id.value) = s)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def restyle(id: ShapeFeature.Id, colour: ShapeFeature.Colour, alpha: ShapeFeature.Alpha): Unit =
    drawn.get(id.value).foreach(s => drawn(id.value) = s.modify(_.colour).setTo(colour).modify(_.alpha).setTo(alpha))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ids: Seq[ShapeFeature.Id] = drawn.keys.toSeq.map(ShapeFeature.Id(_))
