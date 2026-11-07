package com.histmersion.web.map.maplibre

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** A [[MapSurface]] over one MapLibre map. */
final class MapLibreSurface(el: dom.HTMLElement, maxZoom0: MapLibreSurface.MaxZoom0, pixelRatio: Option[MapLibreSurface.PixelRatio], caps: Capabilities)
    extends MapSurface:
  import MapLibreEngine.ZoomShift

  private val gl: GlMap =
    val o = js.Dynamic.literal(
      container = el,
      style = MapLibreStyles.blank(),
      center = js.Array(30.0, 37.0),
      zoom = 5 - ZoomShift,
      maxZoom = maxZoom0 - ZoomShift,
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
      trackResize = false
    )
    pixelRatio.foreach(r => o.pixelRatio = r)
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

  def zoom: com.histmersion.web.map.MapSurface.Zoom = gl.getZoom() + ZoomShift
  def maxZoom: com.histmersion.web.map.MapSurface.MaxZoom = gl.getMaxZoom() + ZoomShift
  def setMaxZoom(z: com.histmersion.web.map.MapSurface.MaxZoom): Unit = { val _ = gl.setMaxZoom(z - ZoomShift) }
  def center: LatLon = { val c = gl.getCenter(); LatLon(c.lat, c.lng) }
  def bounds: GeoBounds =
    val b = gl.getBounds()
    GeoBounds(b.getSouth(), b.getWest(), b.getNorth(), b.getEast())
  // the container's size, read when it changes (a layout read per marker per frame forced a layout each)
  private var sizeV: (com.histmersion.web.map.MapSurface.Dimension, com.histmersion.web.map.MapSurface.Dimension) = (el.clientWidth.toDouble, el.clientHeight.toDouble)
  gl.on("resize", (_: GlEvent) => sizeV = (el.clientWidth.toDouble, el.clientHeight.toDouble))
  def size: (com.histmersion.web.map.MapSurface.Dimension, com.histmersion.web.map.MapSurface.Dimension) = sizeV

  // Every camera move the app asks for: animated only when the map animates now ([[MotionPref]]), and then
  // `essential` — MapLibre otherwise drops the ease itself when the system asks for reduced motion, and "always
  // animate" would not. A move that does not animate is a jump.
  def setView(at: LatLon, z: com.histmersion.web.map.MapSurface.Zoom, animate: com.histmersion.web.map.maplibre.MapLibreSurface.SetViewAnimate): Unit =
    val o = js.Dynamic.literal(center = js.Array(at.lon, at.lat), zoom = z - ZoomShift)
    if MapLibreSurface.eases(animate) then { o.essential = true; val _ = gl.easeTo(o.asInstanceOf[js.Object]) }
    else { val _ = gl.jumpTo(o.asInstanceOf[js.Object]) }

  def fitBounds(b: GeoBounds, padding: com.histmersion.web.map.maplibre.MapLibreSurface.FitBoundsPadding, maxZoom: com.histmersion.web.map.MapSurface.MaxZoom, animate: com.histmersion.web.map.maplibre.MapLibreSurface.FitBoundsAnimate): Unit =
    val _ = gl.fitBounds(
      js.Array(js.Array(b.west, b.south), js.Array(b.east, b.north)),
      MapLibreSurface.motion(animate, js.Dynamic.literal(padding = padding, maxZoom = maxZoom - ZoomShift))
    )

  def panBy(dx: com.histmersion.web.map.maplibre.MapLibreSurface.PanByDx, dy: com.histmersion.web.map.maplibre.MapLibreSurface.PanByDy, animate: com.histmersion.web.map.maplibre.MapLibreSurface.PanByAnimate): Unit =
    val _ = gl.panBy(js.Array(dx, dy), MapLibreSurface.motion(animate, js.Dynamic.literal()))

  def project(p: LatLon): ScreenPt = { val q = gl.project(js.Array(p.lon, p.lat)); ScreenPt(q.x, q.y) }
  def unproject(p: ScreenPt): LatLon = { val ll = gl.unproject(js.Array(p.x, p.y)); LatLon(ll.lat, ll.lng) }

  /** The container's new size taken — only when it changed (a resize sends every move event: the markers, the names and
    * the tiled layers follow those) — and the map drawn again AT ONCE, in the same frame: a resized canvas is blank
    * until it is drawn, and drawn only on the next animation frame it was painted blank for one frame, a flash of the
    * whole map on every step of a sidebar drag or a filter row folded.
    */
  def resize(): Unit =
    val now = (el.clientWidth.toDouble, el.clientHeight.toDouble)
    if now != sizeV then
      sizeV = now
      resizes += 1
      val _ = gl.resize()
      val _ = gl.redraw()
  // `var`: how many times the container's size was taken (diagnostics: a chrome change resizes, nothing more)
  private var resizes = 0
  override def resizeCount: Int = resizes
  def isMoving: Boolean = gl.isMoving()
  def isDrawn: Boolean = !gl.isMoving() && gl.loaded() && gl.areTilesLoaded()
  def capabilities: Capabilities = caps

  // the names' language of the base map drawn now
  private var lang = "en"

  def drawnNames: Seq[com.histmersion.web.map.MapSurface.LabelText] =
    val style = gl.getStyle()
    if style == null || js.isUndefined(style) then Nil
    else
      val symbols = style.layers
        .asInstanceOf[js.Array[js.Dynamic]]
        .filter(l =>
          l.`type`.toString == "symbol" &&
            !shapeSets.values.exists(_.layerIds.contains(l.id.toString))
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
            f.properties.get(key).orElse(f.properties.get("name:en")).orElse(f.properties.get("name")).map(_.toString)
          )
          .distinct
  def baseMapLabels: Seq[(com.histmersion.web.map.MapSurface.LabelText, com.histmersion.web.map.MapSurface.LabelText, ScreenPt)] =
    val style = gl.getStyle()
    if style == null || js.isUndefined(style) then Nil
    else
      val symbols = style.layers
        .asInstanceOf[js.Array[js.Dynamic]]
        .filter(l =>
          l.`type`.toString == "symbol" &&
            !shapeSets.values.exists(_.layerIds.contains(l.id.toString))
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
                cs.lift(cs.length / 2).map(c => LatLon(c(1), c(0)))
              case "MultiLineString" =>
                val cs = g.coordinates.asInstanceOf[js.Array[js.Array[js.Array[Double]]]].flatMap(identity)
                cs.lift(cs.length / 2).map(c => LatLon(c(1), c(0)))
              case _ => None
            for t <- text; a <- at yield (f.layer.id.toString, t, project(a))
          }
          .distinctBy((l, t, _) => (l, t))
  def baseMapAreasAt(p: ScreenPt): Seq[com.histmersion.web.map.MapSurface.LayerId] =
    val style = gl.getStyle()
    if style == null || js.isUndefined(style) then Nil
    else
      val fills = style.layers
        .asInstanceOf[js.Array[js.Dynamic]]
        .filter(l =>
          l.`type`.toString == "fill" &&
            !shapeSets.values.exists(_.layerIds.contains(l.id.toString))
        )
        .map(_.id.toString)
      if fills.isEmpty then Nil
      else
        gl.queryRenderedFeatures(js.Array(p.x, p.y), js.Dynamic.literal(layers = fills).asInstanceOf[js.Object])
          .toSeq
          .map(f => s"${f.layer.id}:${f.properties.get("kind").fold("")(_.toString)}")
          .distinct
  def baseMapLayer(id: com.histmersion.web.map.MapSurface.LayerId): Option[com.histmersion.web.map.MapSurface.LayerId] =
    gl.getLayer(id)
      .toOption
      .map(_ => Option(gl.getLayoutProperty(id, "visibility")).filterNot(js.isUndefined).fold("visible")(_.toString))
  def baseMapDrawn(id: com.histmersion.web.map.MapSurface.LayerId): com.histmersion.web.map.MapSurface.DrawnCount =
    if gl.getLayer(id).isEmpty then com.histmersion.web.map.MapSurface.DrawnCount(0)
    else
      gl.queryRenderedFeatures(
        js.undefined.asInstanceOf[js.Array[Double]],
        js.Dynamic.literal(layers = js.Array(id)).asInstanceOf[js.Object]
      ).length
  def destroy(): Unit = gl.remove()

  // ── events ───────────────────────────────────────────────────────────────────────────────────

  private def name(k: MapEventKind): com.histmersion.web.map.maplibre.MapLibreSurface.EventName = k match
    case MapEventKind.MoveStart    => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("movestart")
    case MapEventKind.Move         => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("move")
    case MapEventKind.MoveEnd      => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("moveend")
    case MapEventKind.ZoomStart    => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("zoomstart")
    case MapEventKind.ZoomEnd      => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("zoomend")
    case MapEventKind.Resize       => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("resize")
    case MapEventKind.Click        => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("click")
    case MapEventKind.PointerMove  => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("mousemove")
    case MapEventKind.PointerOut   => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("mouseout")
    case MapEventKind.BaseMapReady => com.histmersion.web.map.maplibre.MapLibreSurface.EventName("hd-basemap")

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
          .map(p => ScreenPt(p.x.asInstanceOf[Double], p.y.asInstanceOf[Double]))
        val ll = Option(e.lngLat)
          .filterNot(js.isUndefined)
          .map(l => LatLon(l.lat.asInstanceOf[Double], l.lng.asInstanceOf[Double]))
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
  private var creditsV: Seq[com.histmersion.web.map.BaseMapSpec.Raster.Attribution] = Nil
  override def credits: Seq[String] = creditsV

  def setBaseMap(b: BaseMapSpec): Unit =
    generation += 1
    b match
      case v: BaseMapSpec.Vector => lang = v.lang
      case _                     => ()
    val mine = generation
    // the same style with other options (names, their language, contours): MapLibre's diff changes only the
    // layers that differ, where a whole new style would load every tile again
    val sameStyle = (lastSpec, b) match
      case (Some(p: BaseMapSpec.Vector), v: BaseMapSpec.Vector) =>
        p.style == v.style && p.status == v.status && p.reduced == v.reduced
      case _ => false
    lastSpec = Some(b)
    def make(retry: com.histmersion.web.map.maplibre.MapLibreSurface.SetBaseMapMakeRetry): Unit =
      MapLibreStyles.style(b, dom.window.location.origin).onComplete {
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
                      val _ = gl.setStyle(style, js.Dynamic.literal(diff = sameStyle).asInstanceOf[js.Object])
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
    make(retry = com.histmersion.web.map.maplibre.MapLibreSurface.SetBaseMapMakeRetry(true))

  // ── shapes ───────────────────────────────────────────────────────────────────────────────────

  private val shapeSets = mutable.LinkedHashMap.empty[String, GlShapes]

  def shapes(id: com.histmersion.web.map.maplibre.MapLibreSurface.ShapesId, style: ShapeStyle): ShapeSet =
    shapeSets.getOrElseUpdate(id, GlShapes(gl, id, style))

  def shapeSet(id: com.histmersion.web.map.maplibre.MapLibreSurface.ShapeSetId): Option[ShapeSet] = shapeSets.get(id)

  def shapesAt(p: ScreenPt): Seq[ShapeHit] =
    val at = unproject(p)
    shapeSets.values.toSeq.reverse
      .filter(_.look.interactive)
      .flatMap(set => set.under(at, p, this).map(ShapeHit(set.id, _)))

  // ── point layers from the server's tiles ─────────────────────────────────────────────────────

  private val pointSets = mutable.LinkedHashMap.empty[String, GlPointTiles]

  def pointTiles(id: com.histmersion.web.map.MapSurface.LayerId, layer: com.histmersion.web.map.MapSurface.LayerId, maxZoom: com.histmersion.web.map.maplibre.MapLibreSurface.PointTilesMaxZoom): PointTiles =
    pointSets.getOrElseUpdate(id, GlPointTiles(gl, id, layer, maxZoom))

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

  override def setLocked(locked: Boolean): Unit =
    List(gl.dragPan, gl.scrollZoom, gl.boxZoom, gl.doubleClickZoom, gl.keyboard, gl.touchZoomRotate).foreach { h =>
      if locked then h.disable() else h.enable()
    }
    if !locked then
      gl.touchZoomRotate.disableRotation()
      gl.keyboard.disableRotation()
    el.classList.toggle("is-map-locked", locked)

  def popup(at: LatLon, html: com.histmersion.web.map.maplibre.MapLibreSurface.PopupHtml, cls: com.histmersion.web.map.maplibre.MapLibreSurface.PopupCls): Popup =
    closePopups()
    val p = GlPopup(
      js.Dynamic
        .literal(
          closeButton = true,
          closeOnClick = true,
          maxWidth = "340px",
          offset = 14,
          className = s"hd-popup $cls".trim
        )
        .asInstanceOf[js.Object]
    )
    p.setLngLat(js.Array(at.lon, at.lat)).setHTML(html).addTo(gl)
    popups += p
    p.on("close", _ => { val _ = popups -= p })
    new Popup:
      def close(): Unit = { val _ = p.remove() }
      def isOpen: Boolean = p.isOpen()
      def element: Option[dom.HTMLElement] = Option(p.getElement()).filterNot(js.isUndefined)

  def closePopups(): Unit = popups.toList.foreach(_.remove())

object MapLibreSurface:
  type EventName = EventName.T
  object EventName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Does a camera move the caller wants `animated` ease — only while the map animates ([[MotionPref]]). */
  type SetBaseMapMakeRetry = SetBaseMapMakeRetry.T
  object SetBaseMapMakeRetry extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MaxZoom0 = com.histmersion.web.map.MapSurface.MaxZoom
  val MaxZoom0: _root_.com.histmersion.web.map.MapSurface.MaxZoom.type = _root_.com.histmersion.web.map.MapSurface.MaxZoom
  type PixelRatio = PixelRatio.T
  object PixelRatio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SetViewAnimate = SetViewAnimate.T
  object SetViewAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type EasesAnimated = EasesAnimated.T
  object EasesAnimated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MotionAnimated = _root_.com.histmersion.web.map.maplibre.MapLibreSurface.EasesAnimated
  val MotionAnimated: _root_.com.histmersion.web.map.maplibre.MapLibreSurface.EasesAnimated.type = _root_.com.histmersion.web.map.maplibre.MapLibreSurface.EasesAnimated

  def eases(animated: com.histmersion.web.map.maplibre.MapLibreSurface.EasesAnimated): Boolean = animated && MotionPref.animate()

  /** `o` with the motion of a camera move: `animate`, and `essential` when it eases (see `setView`). */
  def motion(animated: com.histmersion.web.map.maplibre.MapLibreSurface.MotionAnimated, o: js.Dynamic): js.Object =
    val e = eases(animated)
    o.animate = e
    if e then o.essential = true
    o.asInstanceOf[js.Object]
