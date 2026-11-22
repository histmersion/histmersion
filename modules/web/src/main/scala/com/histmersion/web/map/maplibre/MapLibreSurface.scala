package com.histmersion
package web.map.maplibre

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import com.histmersion.web.map.MapSurface
import com.histmersion.web.map.BaseMapSpec
import com.histmersion.web.Auth
import com.histmersion.web.HtmlSink
import com.histmersion.api.Html

/** A [[MapSurface]] over one MapLibre map. */
final class MapLibreSurface(
    el: dom.HTMLElement,
    maxZoom0: MapEngine.MaxZoom,
    pixelRatio: Option[MapEngine.PixelRatio],
    caps: Capabilities
) extends MapSurface:
  import MapLibreEngine.ZoomShift

  private val gl: GlMap =
    val o = js.Dynamic.literal(
      container = el,
      style = MapLibreStyles.blank(),
      center = js.Array(30.0, 37.0),
      zoom = 5 - ZoomShift,
      maxZoom = maxZoom0.value - ZoomShift,
      renderWorldCopies = true,
      attributionControl = false,
      maxPitch = 0,
      pitchWithRotate = false,
      dragRotate = false,
      touchPitch = false,
      fadeDuration = 150,
      refreshExpiredTiles = false,
      // the page follows the container's size itself ([[resize]], from MapView's observer): MapLibre's own
      // observer resized it a second time, 50 ms later, sending every move event again
      trackResize = false,
      // tiles and styles from this server carry the key, which a server reached from an untrusted network needs
      transformRequest = ((url: String, _: String) => Auth.mapRequest(url)): js.Function2[String, String, js.Object]
    )
    pixelRatio.foreach(r => o.pixelRatio = r.value)
    // MapLibre's own gestures (the zoom buttons, a double click, the keys, a drag's glide) ease unless it is told
    // the reader wants reduced motion: its one switch for them is this option, read when the map is made — the
    // reader's "Map motion" ([[MotionPref]]; under "follow the system" MapLibre reads the system itself, live)
    MapMotion.mapLibreReduce(MotionPref.choice).foreach(r => o.reduceMotion = r)
    GlMap(o.asInstanceOf[js.Object])
  gl.touchZoomRotate.disableRotation()
  gl.keyboard.disableRotation()
  gl.addControl(NavigationControl(js.Dynamic.literal(showCompass = false).asInstanceOf[js.Object]), "top-left")
  // the credits are the app's to show (MapSurface.credits: BaseMaps writes them under the notice, #3759),
  // not MapLibre's corner control and its ⓘ button; every corner stacks over the marker panes (#3844)
  List("top-left", "top-right", "bottom-left", "bottom-right").foreach(c =>
    Option(el.querySelector(s".maplibregl-ctrl-$c")).foreach(_.classList.add("hd-map-corner"))
  )
  gl.on("style.load", (_: GlEvent) => { shapeSets.values.foreach(_.restore()); pointSets.values.foreach(_.restore()) })
  // a tile that could not be read (offline, refused, outside the extract) is not an error of the page:
  // the map draws without it (MapLibre would otherwise print every one to the console)
  gl.on("error", (_: GlEvent) => ())

  // ── the view ─────────────────────────────────────────────────────────────────────────────────

  def zoom: MapSurface.Zoom = MapSurface.Zoom(gl.getZoom() + ZoomShift)
  def maxZoom: MapSurface.MaxZoom = MapSurface.MaxZoom(gl.getMaxZoom() + ZoomShift)
  def setMaxZoom(z: MapSurface.MaxZoom): Unit = { val _ = gl.setMaxZoom(z.value - ZoomShift) }
  def center: LatLon = { val c = gl.getCenter(); LatLon(c.lat, c.lng) }
  def bounds: GeoBounds =
    val b = gl.getBounds()
    GeoBounds(
      GeoBounds.South(b.getSouth()),
      GeoBounds.West(b.getWest()),
      GeoBounds.North(b.getNorth()),
      GeoBounds.East(b.getEast())
    )
  private def sizeNow(): (MapSurface.Dimension, MapSurface.Dimension) =
    (MapSurface.Dimension(el.clientWidth.toDouble), MapSurface.Dimension(el.clientHeight.toDouble))
  // the container's size, read when it changes (a layout read per marker per frame forced a layout each)
  private var sizeV: (MapSurface.Dimension, MapSurface.Dimension) = sizeNow()
  gl.on("resize", (_: GlEvent) => sizeV = sizeNow())
  def size: (MapSurface.Dimension, MapSurface.Dimension) = sizeV

  // Every camera move the app asks for: animated only when the map animates now ([[MotionPref]]), and then
  // `essential` — MapLibre otherwise drops the ease itself when the system asks for reduced motion, and "always
  // animate" would not. A move that does not animate is a jump.
  def setView(at: LatLon, z: MapSurface.Zoom, animate: MapSurface.SetViewAnimate): Unit =
    val o = js.Dynamic.literal(center = js.Array(at.lon.value, at.lat.value), zoom = z.value - ZoomShift)
    if MapLibreSurface.eases(animate.value) then { o.essential = true; val _ = gl.easeTo(o.asInstanceOf[js.Object]) }
    else { val _ = gl.jumpTo(o.asInstanceOf[js.Object]) }

  def fitBounds(
      b: GeoBounds,
      padding: MapSurface.FitBoundsPadding,
      maxZoom: MapSurface.MaxZoom,
      animate: MapSurface.FitBoundsAnimate
  ): Unit =
    val _ = gl.fitBounds(
      js.Array(js.Array(b.west.value, b.south.value), js.Array(b.east.value, b.north.value)),
      MapLibreSurface.motion(
        animate.value,
        js.Dynamic.literal(padding = padding.value, maxZoom = maxZoom.value - ZoomShift)
      )
    )

  def panBy(dx: MapSurface.PanByDx, dy: MapSurface.PanByDy, animate: MapSurface.PanByAnimate): Unit =
    val _ = gl.panBy(js.Array(dx.value, dy.value), MapLibreSurface.motion(animate.value, js.Dynamic.literal()))

  def project(p: LatLon): ScreenPt = { val q = gl.project(js.Array(p.lon.value, p.lat.value)); ScreenPt(q.x, q.y) }
  def unproject(p: ScreenPt): LatLon = { val ll = gl.unproject(js.Array(p.x.value, p.y.value)); LatLon(ll.lat, ll.lng) }

  /** The container's new size taken — only when it changed (a resize sends every move event: the markers, the names and
    * the tiled layers follow those) — and the map drawn again AT ONCE, in the same frame: a resized canvas is blank
    * until it is drawn, and drawn only on the next animation frame it was painted blank for one frame, a flash of the
    * whole map on every step of a sidebar drag or a filter row folded.
    */
  def resize(): Unit =
    val now = sizeNow()
    if now != sizeV then
      sizeV = now
      resizes += 1
      val _ = gl.resize()
      val _ = gl.redraw()
  // `var`: how many times the container's size was taken (diagnostics: a chrome change resizes, nothing more)
  private var resizes = 0
  override def resizeCount: MapSurface.ResizeCount = MapSurface.ResizeCount(resizes)
  def isMoving: Boolean = gl.isMoving()
  def isDrawn: Boolean = !gl.isMoving() && gl.loaded() && gl.areTilesLoaded()
  def capabilities: Capabilities = caps

  // the names' language of the base map drawn now
  private var lang = "en"

  def drawnNames: Seq[MapSurface.LabelText] =
    val style = gl.getStyle()
    if style == null || js.isUndefined(style) then Nil
    else
      val symbols = style.layers
        .asInstanceOf[js.Array[js.Dynamic]]
        .filter(l =>
          l.`type`.toString == "symbol" &&
            !shapeSets.values.exists(_.layerIds.contains(MapSurface.LayerId(l.id.toString)))
        )
        .map(_.id.toString)
      if symbols.isEmpty then Nil
      else
        val key = if lang == "local" then "name" else s"name:$lang"
        gl.queryRenderedFeatures(
          js.undefined.asInstanceOf[js.Array[Double]],
          js.Dynamic.literal(layers = symbols).asInstanceOf[js.Object]
        ).toSeq
          .flatMap(f =>
            f.properties
              .get(key)
              .orElse(f.properties.get("name:en"))
              .orElse(f.properties.get("name"))
              .map(t => MapSurface.LabelText(t.toString))
          )
          .distinct
  def baseMapLabels: Seq[(MapSurface.LabelText, MapSurface.LabelText, ScreenPt)] =
    val style = gl.getStyle()
    if style == null || js.isUndefined(style) then Nil
    else
      val symbols = style.layers
        .asInstanceOf[js.Array[js.Dynamic]]
        .filter(l =>
          l.`type`.toString == "symbol" &&
            !shapeSets.values.exists(_.layerIds.contains(MapSurface.LayerId(l.id.toString)))
        )
        .map(_.id.toString)
      if symbols.isEmpty then Nil
      else
        val key = if lang == "local" then "name" else s"name:$lang"
        gl.queryRenderedFeatures(
          js.undefined.asInstanceOf[js.Array[Double]],
          js.Dynamic.literal(layers = symbols).asInstanceOf[js.Object]
        ).toSeq
          .flatMap { f =>
            val text =
              f.properties.get(key).orElse(f.properties.get("name:en")).orElse(f.properties.get("name")).map(_.toString)
            val g = f.asInstanceOf[js.Dynamic].geometry
            val at: Option[LatLon] = g.`type`.toString match
              case "Point"      => ShapeHits.point(g)
              case "LineString" =>
                val cs = g.coordinates.asInstanceOf[js.Array[js.Array[Double]]]
                cs.lift(cs.length / 2).map(c => LatLon(LatLon.Lat(c(1)), LatLon.Lon(c(0))))
              case "MultiLineString" =>
                val cs = g.coordinates.asInstanceOf[js.Array[js.Array[js.Array[Double]]]].flatMap(identity)
                cs.lift(cs.length / 2).map(c => LatLon(LatLon.Lat(c(1)), LatLon.Lon(c(0))))
              case _ => None
            for t <- text; a <- at
            yield (MapSurface.LabelText(f.layer.id.toString), MapSurface.LabelText(t), project(a))
          }
          .distinctBy((l, t, _) => (l, t))
  def baseMapAreasAt(p: ScreenPt): Seq[MapSurface.LayerId] =
    val style = gl.getStyle()
    if style == null || js.isUndefined(style) then Nil
    else
      val fills = style.layers
        .asInstanceOf[js.Array[js.Dynamic]]
        .filter(l =>
          l.`type`.toString == "fill" &&
            !shapeSets.values.exists(_.layerIds.contains(MapSurface.LayerId(l.id.toString)))
        )
        .map(_.id.toString)
      if fills.isEmpty then Nil
      else
        gl.queryRenderedFeatures(
          js.Array(p.x.value, p.y.value),
          js.Dynamic.literal(layers = fills).asInstanceOf[js.Object]
        ).toSeq
          .map(f => MapSurface.LayerId(s"${f.layer.id}:${f.properties.get("kind").fold("")(_.toString)}"))
          .distinct
  def baseMapLayer(id: MapSurface.LayerId): Option[MapSurface.LayerId] =
    gl.getLayer(id.value)
      .toOption
      .map(_ =>
        MapSurface.LayerId(
          Option(gl.getLayoutProperty(id.value, "visibility")).filterNot(js.isUndefined).fold("visible")(_.toString)
        )
      )
  def baseMapDrawn(id: MapSurface.LayerId): MapSurface.DrawnCount =
    if gl.getLayer(id.value).isEmpty then MapSurface.DrawnCount(0)
    else
      MapSurface.DrawnCount(
        gl.queryRenderedFeatures(
          js.undefined.asInstanceOf[js.Array[Double]],
          js.Dynamic.literal(layers = js.Array(id.value)).asInstanceOf[js.Object]
        ).length
      )
  def destroy(): Unit = gl.remove()

  // ── events ───────────────────────────────────────────────────────────────────────────────────

  private def name(k: MapEventKind): String = k match
    case MapEventKind.MoveStart    => "movestart"
    case MapEventKind.Move         => "move"
    case MapEventKind.MoveEnd      => "moveend"
    case MapEventKind.ZoomStart    => "zoomstart"
    case MapEventKind.ZoomEnd      => "zoomend"
    case MapEventKind.Resize       => "resize"
    case MapEventKind.Click        => "click"
    case MapEventKind.PointerMove  => "mousemove"
    case MapEventKind.PointerOut   => "mouseout"
    case MapEventKind.BaseMapReady => "hd-basemap"

  // the base map's "drawn" listeners (our own event: the first `idle` after a new style)
  private val ready = mutable.ListBuffer.empty[MapEvent => Unit]

  def on(kind: MapEventKind)(f: MapEvent => Unit): Subscription =
    if kind == MapEventKind.BaseMapReady then
      ready += f
      () => { val _ = ready -= f }
    else
      val h: js.Function1[js.Dynamic, Unit] = e =>
        val pt = Option(e.point)
          .filterNot(js.isUndefined)
          .map(p => ScreenPt(ScreenPt.X(p.x.asInstanceOf[Double]), ScreenPt.Y(p.y.asInstanceOf[Double])))
        val ll = Option(e.lngLat)
          .filterNot(js.isUndefined)
          .map(l => LatLon(LatLon.Lat(l.lat.asInstanceOf[Double]), LatLon.Lon(l.lng.asInstanceOf[Double])))
        val ne = Option(e.originalEvent).filterNot(js.isUndefined).map(_.asInstanceOf[dom.Event])
        f(MapEvent(pt, ll, ne))
      val n = name(kind)
      gl.asInstanceOf[js.Dynamic].on(n, h)
      () => { val _ = gl.asInstanceOf[js.Dynamic].off(n, h) }

  // ── the DOM ──────────────────────────────────────────────────────────────────────────────────

  def container: dom.HTMLElement = el
  def overlayRoot: dom.HTMLElement = gl.getCanvasContainer()
  def corner(c: Corner): dom.HTMLElement =
    val cls = c match
      case Corner.TopLeft     => "top-left"
      case Corner.TopRight    => "top-right"
      case Corner.BottomLeft  => "bottom-left"
      case Corner.BottomRight => "bottom-right"
    el.querySelector(s".maplibregl-ctrl-$cls").asInstanceOf[dom.HTMLElement]

  // ── the base map ─────────────────────────────────────────────────────────────────────────────

  // `var`: the newest base map asked for (a slow style answer must not replace a newer one)
  private var generation = 0

  // `var`: the base map last asked for (an option change of the same style is applied as a diff)
  private var lastSpec: Option[BaseMapSpec] = None
  // `var`: the page is being left (its fetches are being cut off)
  private var leaving = false
  dom.window.addEventListener("pagehide", (_: dom.Event) => leaving = true)
  dom.window.addEventListener("pageshow", (_: dom.Event) => leaving = false)

  // `var`: the credits of the style set last
  private var creditsV: Seq[BaseMapSpec.Raster.Attribution] = Nil
  override def credits: Seq[BaseMapSpec.Raster.Attribution] = creditsV

  def setBaseMap(b: BaseMapSpec): Unit =
    generation += 1
    b match
      case v: BaseMapSpec.Vector => lang = v.lang.value
      case _                     => ()
    val mine = generation
    // the same style with other options (names, their language, contours): MapLibre's diff changes only the
    // layers that differ, where a whole new style would load every tile again
    val sameStyle = (lastSpec, b) match
      case (Some(p: BaseMapSpec.Vector), v: BaseMapSpec.Vector) =>
        p.style == v.style && p.status == v.status && p.reduced == v.reduced
      case _ => false
    lastSpec = Some(b)
    def make(retry: Boolean): Unit =
      MapLibreStyles.style(b, MapLibreStyles.Origin(dom.window.location.origin)).onComplete {
        case scala.util.Success(style) =>
          // applied after the next paint: the input that asked for it (a checkbox, a menu) shows first, and the
          // style's work runs off that input
          if mine == generation then
            // two frames: the second's callbacks run after the first was painted, in every engine
            val _ = dom.window.requestAnimationFrame(_ =>
              val _ = dom.window.requestAnimationFrame(_ =>
                val _ = dom.window.setTimeout(
                  () =>
                    if mine == generation then
                      creditsV = MapLibreStyles.credits(style.asInstanceOf[js.Dynamic])
                      val overlays = shapeSets.keySet.toSet ++ pointSets.keySet
                      val carry: js.Function2[js.Dynamic, js.Dynamic, js.Dynamic] =
                        (previous, next) => MapLibreStyles.carryOverlays(previous, next, overlays)
                      val options =
                        if sameStyle then js.Dynamic.literal(diff = true, transformStyle = carry)
                        else js.Dynamic.literal(diff = false)
                      val _ = gl.setStyle(style, options.asInstanceOf[js.Object])
                      gl.once("idle", (_: GlEvent) => if mine == generation then ready.toList.foreach(_(MapEvent()))),
                  0
                )
              )
            )
        case scala.util.Failure(e) =>
          // a fetch cut short by leaving the page (Firefox answers NetworkError before `pagehide`) or by a
          // newer choice is no failure: asked once more a moment later, it is reported only when that fails too
          if mine == generation && !leaving && !e.getMessage.contains("AbortError") then
            if retry then {
              val _ = dom.window.setTimeout(() => if mine == generation && !leaving then make(retry = false), 1000)
            } else dom.console.error(s"the base map could not be made: ${e.getMessage}")
      }
    make(retry = true)

  // ── shapes ───────────────────────────────────────────────────────────────────────────────────

  private val shapeSets = mutable.LinkedHashMap.empty[String, GlShapes]

  def shapes(id: MapSurface.ShapesId, style: ShapeStyle): ShapeSet =
    shapeSets.getOrElseUpdate(id.value, GlShapes(gl, NoMap.Shapes.Id(id.value), style))

  def shapeSet(id: MapSurface.ShapeSetId): Option[ShapeSet] = shapeSets.get(id.value)

  def shapesAt(p: ScreenPt): Seq[ShapeHit] =
    val at = unproject(p)
    shapeSets.values.toSeq.reverse
      .filter(_.look.interactive.value)
      .flatMap(set => set.under(at, p, this).map(fid => ShapeHit(ShapeHit.Set(set.id.value), ShapeHit.Id(fid.value))))

  // ── point layers from the server's tiles ─────────────────────────────────────────────────────

  private val pointSets = mutable.LinkedHashMap.empty[String, GlPointTiles]

  def pointTiles(id: MapSurface.LayerId, layer: MapSurface.LayerId, maxZoom: MapSurface.PointTilesMaxZoom): PointTiles =
    pointSets.getOrElseUpdate(id.value, GlPointTiles(gl, id, layer, maxZoom))

  // ── popups ───────────────────────────────────────────────────────────────────────────────────

  private val popups = mutable.ListBuffer.empty[GlPopup]

  override def isChrome(e: dom.Element): Boolean = e.closest(".maplibregl-popup, .maplibregl-ctrl") != null

  override def addControl(c: dom.HTMLElement, at: Corner): Unit =
    c.classList.add("maplibregl-ctrl")
    Option(corner(at)).foreach(_.appendChild(c))

  override def popupBody(p: Popup): Option[dom.HTMLElement] =
    p.element.map(e =>
      Option(e.querySelector(".maplibregl-popup-content")).collect { case h: dom.HTMLElement => h }.getOrElse(e)
    )

  override def setLocked(locked: MapSurface.Locked): Unit =
    List(gl.dragPan, gl.scrollZoom, gl.boxZoom, gl.doubleClickZoom, gl.keyboard, gl.touchZoomRotate).foreach { h =>
      if locked.value then h.disable() else h.enable()
    }
    if !locked.value then
      gl.touchZoomRotate.disableRotation()
      gl.keyboard.disableRotation()
    el.classList.toggle("is-map-locked", locked.value)

  def popup(at: LatLon, html: Html, cls: MapSurface.PopupCls): Popup =
    closePopups()
    val p = GlPopup(
      js.Dynamic
        .literal(
          closeButton = true,
          closeOnClick = true,
          maxWidth = "340px",
          offset = 14,
          className = s"hd-popup ${cls.value}".trim
        )
        .asInstanceOf[js.Object]
    )
    p.setLngLat(js.Array(at.lon.value, at.lat.value)).setDOMContent(HtmlSink.fragment(html)).addTo(gl)
    popups += p
    p.on("close", _ => { val _ = popups -= p })
    new Popup:
      def close(): Unit = { val _ = p.remove() }
      def isOpen: Boolean = p.isOpen()
      def element: Option[dom.HTMLElement] = Option(p.getElement()).filterNot(js.isUndefined)

  def closePopups(): Unit = popups.toList.foreach(_.remove())

object MapLibreSurface:
  /** Does a camera move the caller wants `animated` ease — only while the map animates ([[MotionPref]]). */
  private[web] def eases(animated: Boolean): Boolean = animated && MotionPref.animate()

  /** `o` with the motion of a camera move: `animate`, and `essential` when it eases (see `setView`). */
  private[web] def motion(animated: Boolean, o: js.Dynamic): js.Object =
    val e = eases(animated)
    o.animate = e
    if e then o.essential = true
    o.asInstanceOf[js.Object]
