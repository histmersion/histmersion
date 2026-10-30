package com.histmersion.web.map

import com.histmersion.api.BasemapStatus
import org.scalajs.dom

import scala.scalajs.js

/** THE MAP, as the app sees it (docs/user-guide/contributing/architecture.md, "The map"): a thin layer
  * shaped by what the app needs, so that changing the map library changes one package
  * (the adapter `map.maplibre`, the only one that may name a map library's types — `MapEngineBoundarySuite`
  * fails the build otherwise). Everything else talks to these types:
  *
  *   - [[MapSurface]]: the view (centre, zoom, bounds, fitting, panning), its events, projection,
  *     the base map ([[BaseMapSpec]]), the corners for controls, popups, the GL-drawn shapes
  *     ([[ShapeSet]]) and what is under the pointer;
  *   - [[MapEngine]]: makes a surface, and says before that what the browser can draw ([[Capabilities]]);
  *   - the points, bubbles and names are DOM markers in panes over the map ([[MarkerPane]]), placed with
  *     the surface's projection — library-neutral, so they keep the cards, the keyboard and the
  *     stylesheet's look whatever draws the base map.
  *
  * ZOOM is the app's unit: the zoom of the 256-pixel raster grid (OSM Carto's, every
  * `Layout` value and link the app had). An adapter converts to its library's own. */
final case class LatLon(lat: Double, lon: Double)

/** A screen point, CSS pixels from the map container's top-left corner. */
final case class ScreenPt(x: Double, y: Double)

/** A region: south, west, north, east (east may pass 180 across the antimeridian). */
final case class GeoBounds(south: Double, west: Double, north: Double, east: Double):
  def contains(p: LatLon): Boolean = p.lat >= south && p.lat <= north && p.lon >= west && p.lon <= east
  def extend(o: GeoBounds): GeoBounds =
    GeoBounds(math.min(south, o.south), math.min(west, o.west), math.max(north, o.north), math.max(east, o.east))
  def valid: Boolean = south <= north && west <= east && !south.isNaN && !west.isNaN

object GeoBounds:
  /** The bounds of a GeoJSON geometry's coordinates (`None` for none). Pure over the parsed object. */
  def ofGeometry(g: js.Dynamic): Option[GeoBounds] =
    // `var`s: one pass over every coordinate of the geometry
    var s = Double.MaxValue
    var w = Double.MaxValue
    var n = -Double.MaxValue
    var e = -Double.MaxValue
    def walk(c: js.Dynamic): Unit =
      if js.Array.isArray(c) then
        val a = c.asInstanceOf[js.Array[js.Dynamic]]
        if a.length >= 2 && js.typeOf(a(0)) == "number" then
          val lon = a(0).asInstanceOf[Double]
          val lat = a(1).asInstanceOf[Double]
          if lat < s then s = lat
          if lat > n then n = lat
          if lon < w then w = lon
          if lon > e then e = lon
        else a.foreach(walk)
    if !js.isUndefined(g) && g != null then
      if !js.isUndefined(g.geometries) then g.geometries.asInstanceOf[js.Array[js.Dynamic]].foreach(x => walk(x.coordinates))
      else walk(g.coordinates)
    Option.when(s <= n)(GeoBounds(s, w, n, e))

/** Which shape is under a point: pure geometry over GeoJSON, so finding what the pointer is over
  * never depends on what the library has rendered yet. */
object ShapeHits:
  /** Is (`lat`, `lon`) inside the polygon or multipolygon `g` (even–odd over every ring: holes are
    * outside)? Points and lines contain nothing. Pure. */
  def contains(g: js.Dynamic, lat: Double, lon: Double): Boolean =
    if js.isUndefined(g) || g == null then false
    else
      def ring(r: js.Array[js.Array[Double]]): Boolean =
        // `var`s: the crossing count's walk over the ring's edges
        var inside = false
        var j = r.length - 1
        var i = 0
        while i < r.length do
          val (xi, yi, xj, yj) = (r(i)(0), r(i)(1), r(j)(0), r(j)(1))
          if (yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi then inside = !inside
          j = i
          i += 1
        inside
      def polygon(p: js.Array[js.Array[js.Array[Double]]]): Boolean = p.count(ring) % 2 == 1
      g.selectDynamic("type").toString match
        case "Polygon"      => polygon(g.coordinates.asInstanceOf[js.Array[js.Array[js.Array[Double]]]])
        case "MultiPolygon" => g.coordinates.asInstanceOf[js.Array[js.Array[js.Array[js.Array[Double]]]]].exists(polygon)
        case "GeometryCollection" => g.geometries.asInstanceOf[js.Array[js.Dynamic]].exists(contains(_, lat, lon))
        case _              => false

  /** The point of a Point geometry (`None` for any other). Pure. */
  def point(g: js.Dynamic): Option[LatLon] =
    if js.isUndefined(g) || g == null || g.selectDynamic("type").toString != "Point" then None
    else Some(LatLon(g.coordinates.selectDynamic("1").asInstanceOf[Double], g.coordinates.selectDynamic("0").asInstanceOf[Double]))

/** What happened on the map. */
enum MapEventKind:
  case MoveStart, Move, MoveEnd, ZoomStart, ZoomEnd, Resize
  /** A click on the map (the app's markers answer their own clicks: see [[MapEvent.overMarker]]). */
  case Click
  /** The pointer moved over the map, or left it. */
  case PointerMove, PointerOut
  /** A new base map is drawn (its first frame with every source asked for). */
  case BaseMapReady

/** A map event: where the pointer was (pointer events), and the browser's own event. */
final case class MapEvent(point: Option[ScreenPt] = None, at: Option[LatLon] = None, native: Option[dom.Event] = None):
  /** Was it over one of the app's markers (a point, a bubble, a name, a popup, a control)? Then the
    * marker answers it, not the map. */
  def overMarker: Boolean = native.flatMap(e => Option(e.target)).exists { t =>
    t.isInstanceOf[dom.Element] && t.asInstanceOf[dom.Element].closest(".hd-marker, .hd-popup, .hd-map-control") != null
  }
  def clientXY: Option[(Double, Double)] = native.collect { case e: dom.MouseEvent => (e.clientX, e.clientY) }

/** A handler on the map, to take off. */
trait Subscription:
  def cancel(): Unit

/** A corner of the map, where controls (the legend, the notice and the credits, the zoom buttons) go. Every
  * corner stacks above the marker panes and below the popups (#3844): the map's z-index scale is the
  * stylesheet's `--z-map-*` tokens — the canvas, then the panes (core MapStack), then the corners
  * (`.hd-map-corner`, `--z-map-controls`), then the popups (`--z-map-popups`); windows and modals open over
  * the whole map. */
enum Corner:
  case TopLeft, TopRight, BottomLeft, BottomRight

/** The two vector styles drawn from the server's own extracts (docs/research-map-styles.md). */
enum VectorStyle(val id: String, val label: String):
  case Readable extends VectorStyle("readable", "Readable")
  case Physical extends VectorStyle("physical", "Physical")

object VectorStyle:
  def parse(s: String): Option[VectorStyle] = values.find(_.id == s.trim.toLowerCase)

/** A base map, as the app describes it; the adapter makes its library's style of it. */
enum BaseMapSpec:
  /** Nothing yet: the page's own background. */
  case Blank
  /** One of the vector styles over the server's extracts (`status`, what it has), names in `lang`
    * (`local`: each in its own script; `names = false`: none), contours where the relief is deeper than
    * the extract — a focus region or the on-demand relief (Physical) — and the sea coloured by its depth
    * (`seaDepth`, where the sea floor is downloaded). `reduced`: the cheapest drawing of it (no relief)
    * for a browser without a GPU. */
  case Vector(style: VectorStyle, status: BasemapStatus, lang: String, names: Boolean, contours: Boolean, seaDepth: Boolean,
      reduced: Boolean = false)
  /** An online raster map: its tile URL template (`{s}` for its subdomains), its deepest zoom, its
    * credits (HTML), drawn inverted (`dark`: the light tiles as a dark map) at `opacity`. */
  case Raster(template: String, maxZoom: Int, attribution: String, dark: Boolean = false, opacity: Double = 0.85)

/** What the browser can draw: `software` when its WebGL is rasterised on the CPU (SwiftShader,
  * llvmpipe…) — the GL map then breaks the interaction budgets (docs/research-map-styles.md §3.2) and
  * the app draws its cheapest map. `renderer` is the string it was told. */
final case class Capabilities(webgl: Boolean, software: Boolean, renderer: String, webgl2: Boolean = true)

object Capabilities:
  /** Pure: why an engine that needs WebGL 2 cannot draw in a browser with `c`, or None when it can. */
  def withoutWebgl2(c: Capabilities): Option[String] =
    if !c.webgl then Some("this browser draws no WebGL (it is turned off, or its graphics driver is blocked)")
    else if !c.webgl2 then Some("this browser draws WebGL 1 only, not WebGL 2")
    else None

  /** Pure: a WebGL renderer string that names a software rasteriser. */
  /** A renderer name that hides the GPU (Chromium's and Safari's answer to `RENDERER`): ask the debug
    * extension instead. Pure. */
  def genericRenderer(renderer: String): Boolean =
    val r = renderer.trim.toLowerCase
    r.isEmpty || r == "webkit webgl" || r == "webgl" || r.startsWith("mozilla")

  def isSoftware(renderer: String): Boolean =
    val r = renderer.toLowerCase
    List("swiftshader", "llvmpipe", "softpipe", "software", "basic render", "mesa offscreen", "lavapipe").exists(r.contains)

/** How a set of shapes is drawn: a fill (`fillOpacity` 0: none) and an outline in the feature's colour
  * (or `colour`, a fixed CSS colour), `dash` for the features marked dashed, points as circles of
  * `circleRadius`. `interactive`: the set answers the pointer ([[MapSurface.shapesAt]]). */
final case class ShapeStyle(fillOpacity: Double = 0.0, lineWidth: Double = 1.5, lineOpacity: Double = 0.8,
    colour: Option[String] = None, dash: List[Double] = Nil, dashedLineOpacity: Option[Double] = None,
    circleRadius: Double = 0.0, interactive: Boolean = false)

/** One shape: its id (unique in its set), GeoJSON geometry, colour, whether it is drawn dashed, and
  * its visibility (0…1). */
final case class ShapeFeature(id: String, geometry: js.Any, colour: String = "", dashed: Boolean = false, alpha: Double = 1.0)

/** Shapes drawn by the map itself (areas, outlines). Updates are DIFFS: a shape whose geometry is the
  * same keeps its tile data, its colour and visibility change in place — so a timeline drag recolours
  * 1 600 states without the map cutting a single tile again. */
trait ShapeSet:
  def id: String
  /** Draw exactly these (by id): new ones added, gone ones removed, kept ones restyled. */
  def set(shapes: Seq[ShapeFeature]): Unit
  /** One shape's colour and visibility, in place. */
  def restyle(id: String, colour: String, alpha: Double): Unit
  def clear(): Unit = set(Nil)
  /** The ids drawn now. */
  def ids: Seq[String]
  /** How many times the shapes' outlines were sent to the map (a restyle sends none; diagnostics). */
  def uploads: Int = 0
  /** A drawn shape's colour and visibility (diagnostics). */
  def styleOf(id: String): Option[(String, Double)] = None

/** A point feature of a [[PointTiles]] layer: where it is and its properties (`api.TileProps`). */
final case class TilePointFeature(at: LatLon, props: js.Dictionary[js.Any])

/** One of the server's point layers read as vector tiles (`GET /api/tiles/{layer}/{z}/{x}/{y}`): the map
  * fetches, caches and decodes the tiles of the view; the app reads their features and draws them as
  * its own markers. Nothing is drawn by the map itself. */
trait PointTiles:
  /** The URL template of the tiles (`{z}`, `{x}`, `{y}`), or none: no tile is asked for. A template the
    * same as the one set is no change; another one is fetched while the features of the old one stay
    * readable until its tiles are in. */
  def setTiles(template: Option[String]): Unit
  /** The features of the tiles of the view at its zoom, each once (a world copy, a tile's neighbour
    * holding the same item: one). */
  def features: Seq[TilePointFeature]
  /** `f` whenever the features may have changed: the tiles of a view, or of a new template, are in. */
  def onChange(f: () => Unit): Subscription
  /** Every tile of the view at the current template is in. */
  def loaded: Boolean

/** A shape under the pointer: its set and its id. */
final case class ShapeHit(set: String, id: String)

/** A popup at a map point, with the app's own markup. */
trait Popup:
  def close(): Unit
  def isOpen: Boolean
  def element: Option[dom.HTMLElement]

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
  /** The container changed size (a window resize, or the chrome around it: a folded filter row, a dragged
    * sidebar): the map takes it and is drawn again at once — nothing is fetched or made again. */
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
    * ([[MapEventKind.BaseMapReady]]); the app shows them ([[com.histmersion.web.BaseMaps]]). */
  def credits: Seq[String] = Nil
  def shapes(id: String, style: ShapeStyle): ShapeSet
  /** A set made already, if it was. */
  def shapeSet(id: String): Option[ShapeSet]
  /** The interactive shapes under `p`, topmost first. */
  def shapesAt(p: ScreenPt): Seq[ShapeHit]
  /** The point layer `id` of the server's tiles, its features in the tiles' layer `layer`, cut down to
    * the tiles' `maxZoom` (MapLibre's 512-pixel zoom; a deeper view reads those). Made once per id. */
  def pointTiles(id: String, layer: String, maxZoom: Int): PointTiles
  def popup(at: LatLon, html: String, cls: String = ""): Popup
  def closePopups(): Unit
  /** The base map's names placed in the view now, their text as drawn (the regressions read it). */
  def drawnNames: Seq[String]
  /** The base map's areas drawn under a point, as `layer:kind` (the regressions check the land cover). */
  def baseMapAreasAt(p: ScreenPt): Seq[String]
  /** The base map's names placed now: their layer, text and anchor on the screen (a line's middle) — the
    * regressions measure their contrast against what lies under them. */
  def baseMapLabels: Seq[(String, String, ScreenPt)]
  /** A layer of the base map's style: its visibility (`visible`, `none`), `None` when the style has no
    * such layer (the regressions check what an option turns on and off). */
  def baseMapLayer(id: String): Option[String]
  /** How many of a base-map layer's features are drawn in the view now (0 for none, or no such layer). */
  def baseMapDrawn(id: String): Int
  /** The browser's own capabilities, as the surface found them. */
  def capabilities: Capabilities
  /** Take the map away (its element stays). */
  def destroy(): Unit

/** Makes surfaces. */
trait MapEngine:
  /** What the browser can draw, before any map is made. */
  def probe(): Capabilities
  /** Why this engine cannot draw in a browser with `c` (None: it can). */
  def unsupported(c: Capabilities): Option[String] = None
  /** A surface in `el`, its zoom limits and pixel ratio (`None`: the screen's). */
  def create(el: dom.HTMLElement, maxZoom: Double, pixelRatio: Option[Double]): MapSurface
