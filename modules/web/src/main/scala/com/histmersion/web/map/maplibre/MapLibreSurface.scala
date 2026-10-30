package com.histmersion.web.map.maplibre

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** A [[MapSurface]] over one MapLibre map. */
final class MapLibreSurface(el: dom.HTMLElement, maxZoom0: Double, pixelRatio: Option[Double], caps: Capabilities)
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

  def zoom: Double = gl.getZoom() + ZoomShift
  def maxZoom: Double = gl.getMaxZoom() + ZoomShift
  def setMaxZoom(z: Double): Unit = { val _ = gl.setMaxZoom(z - ZoomShift) }
  def center: LatLon = { val c = gl.getCenter(); LatLon(c.lat, c.lng) }
  def bounds: GeoBounds =
    val b = gl.getBounds()
    GeoBounds(b.getSouth(), b.getWest(), b.getNorth(), b.getEast())
  // the container's size, read when it changes (a layout read per marker per frame forced a layout each)
  private var sizeV: (Double, Double) = (el.clientWidth.toDouble, el.clientHeight.toDouble)
  gl.on("resize", (_: GlEvent) => sizeV = (el.clientWidth.toDouble, el.clientHeight.toDouble))
  def size: (Double, Double) = sizeV

  // Every camera move the app asks for: animated only when the map animates now ([[MotionPref]]), and then
  // `essential` — MapLibre otherwise drops the ease itself when the system asks for reduced motion, and "always
  // animate" would not. A move that does not animate is a jump.
  def setView(at: LatLon, z: Double, animate: Boolean): Unit =
    val o = js.Dynamic.literal(center = js.Array(at.lon, at.lat), zoom = z - ZoomShift)
    if MapLibreSurface.eases(animate) then { o.essential = true; val _ = gl.easeTo(o.asInstanceOf[js.Object]) }
    else { val _ = gl.jumpTo(o.asInstanceOf[js.Object]) }

  def fitBounds(b: GeoBounds, padding: Int, maxZoom: Double, animate: Boolean): Unit =
    val _ = gl.fitBounds(
      js.Array(js.Array(b.west, b.south), js.Array(b.east, b.north)),
      MapLibreSurface.motion(animate, js.Dynamic.literal(padding = padding, maxZoom = maxZoom - ZoomShift))
    )

  def panBy(dx: Double, dy: Double, animate: Boolean): Unit =
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

  def drawnNames: Seq[String] =
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
  def baseMapLabels: Seq[(String, String, ScreenPt)] =
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
  def baseMapAreasAt(p: ScreenPt): Seq[String] =
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
  def baseMapLayer(id: String): Option[String] =
    gl.getLayer(id)
      .toOption
      .map(_ => Option(gl.getLayoutProperty(id, "visibility")).filterNot(js.isUndefined).fold("visible")(_.toString))
  def baseMapDrawn(id: String): Int =
    if gl.getLayer(id).isEmpty then 0
    else
      gl.queryRenderedFeatures(
        js.undefined.asInstanceOf[js.Array[Double]],
        js.Dynamic.literal(layers = js.Array(id)).asInstanceOf[js.Object]
      ).length
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
  private var creditsV: Seq[String] = Nil
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
    def make(retry: Boolean): Unit =
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
    make(retry = true)

  // ── shapes ───────────────────────────────────────────────────────────────────────────────────

  private val shapeSets = mutable.LinkedHashMap.empty[String, GlShapes]

  def shapes(id: String, style: ShapeStyle): ShapeSet =
    shapeSets.getOrElseUpdate(id, GlShapes(gl, id, style))

  def shapeSet(id: String): Option[ShapeSet] = shapeSets.get(id)

  def shapesAt(p: ScreenPt): Seq[ShapeHit] =
    val at = unproject(p)
    shapeSets.values.toSeq.reverse
      .filter(_.look.interactive)
      .flatMap(set => set.under(at, p, this).map(ShapeHit(set.id, _)))

  // ── point layers from the server's tiles ─────────────────────────────────────────────────────

  private val pointSets = mutable.LinkedHashMap.empty[String, GlPointTiles]

  def pointTiles(id: String, layer: String, maxZoom: Int): PointTiles =
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

  def popup(at: LatLon, html: String, cls: String): Popup =
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
  /** Does a camera move the caller wants `animated` ease — only while the map animates ([[MotionPref]]). */
  def eases(animated: Boolean): Boolean = animated && MotionPref.animate()

  /** `o` with the motion of a camera move: `animate`, and `essential` when it eases (see `setView`). */
  def motion(animated: Boolean, o: js.Dynamic): js.Object =
    val e = eases(animated)
    o.animate = e
    if e then o.essential = true
    o.asInstanceOf[js.Object]

/** One [[ShapeSet]] as a GeoJSON source and its layers. Its features carry numeric ids (feature state needs them) and
  * their own id as `hid`; colour and visibility live in feature state, so a restyle never sends the geometry again.
  */
final private[maplibre] class GlShapes(gl: GlMap, val id: String, val look: ShapeStyle) extends ShapeSet:
  // `var`s: the next numeric id, and what is drawn: our id → (numeric id, geometry, colour, alpha, dashed)
  private var next = 1
  final private class Drawn(
      val n: Int,
      val geometry: js.Any,
      var colour: String,
      var alpha: Double,
      val dashed: Boolean
  ):
    lazy val bounds: Option[GeoBounds] = GeoBounds.ofGeometry(geometry.asInstanceOf[js.Dynamic])
  private val drawn = mutable.LinkedHashMap.empty[String, Drawn]
  private val numeric = mutable.HashMap.empty[String, Int]
  // the shapes whose colour or visibility was set in place (feature state): only these need it again
  // after the geometry is sent anew — the others carry theirs in their properties
  private val stated = mutable.HashSet.empty[String]

  private def colourExpr: js.Any = look.colour match
    case Some(c) => c
    case None    => js.Array("coalesce", js.Array("feature-state", "c"), js.Array("get", "c"))
  private val alphaExpr: js.Array[js.Any] = js.Array("coalesce", js.Array("feature-state", "a"), js.Array("get", "a"))
  private def times(k: Double): js.Any = js.Array("*", k, alphaExpr)
  private val notPoint = js.Array("!=", js.Array("geometry-type"), "Point")

  def layerIds: List[String] =
    List(
      Option.when(look.fillOpacity > 0)(s"$id-fill"),
      Some(s"$id-line"),
      Option.when(look.dash.nonEmpty)(s"$id-dashed"),
      Option.when(look.circleRadius > 0)(s"$id-points")
    ).flatten

  private def layers: List[js.Object] =
    val fill = Option.when(look.fillOpacity > 0)(
      js.Dynamic.literal(
        id = s"$id-fill",
        `type` = "fill",
        source = id,
        filter = notPoint,
        paint = js.Dynamic.literal(`fill-color` = colourExpr, `fill-opacity` = times(look.fillOpacity))
      )
    )
    val solid = js.Dynamic.literal(
      id = s"$id-line",
      `type` = "line",
      source = id,
      filter = if look.dash.isEmpty then notPoint else js.Array("all", notPoint, js.Array("!", js.Array("get", "d"))),
      layout = js.Dynamic.literal(`line-join` = "round"),
      paint = js.Dynamic
        .literal(`line-color` = colourExpr, `line-width` = look.lineWidth, `line-opacity` = times(look.lineOpacity))
    )
    val dashed = Option.when(look.dash.nonEmpty)(
      js.Dynamic.literal(
        id = s"$id-dashed",
        `type` = "line",
        source = id,
        filter = js.Array("all", notPoint, js.Array("get", "d")),
        paint = js.Dynamic.literal(
          `line-color` = colourExpr,
          `line-width` = look.lineWidth,
          `line-opacity` = times(look.dashedLineOpacity.getOrElse(look.lineOpacity)),
          `line-dasharray` = look.dash.toJSArray
        )
      )
    )
    val points = Option.when(look.circleRadius > 0)(
      js.Dynamic.literal(
        id = s"$id-points",
        `type` = "circle",
        source = id,
        filter = js.Array("==", js.Array("geometry-type"), "Point"),
        paint = js.Dynamic.literal(
          `circle-radius` = look.circleRadius,
          `circle-color` = colourExpr,
          `circle-opacity` = times(0.9),
          `circle-stroke-width` = 2,
          `circle-stroke-color` = colourExpr
        )
      )
    )
    (fill.toList :+ solid) ++ dashed.toList ++ points.toList map (_.asInstanceOf[js.Object])

  private def collection: js.Object =
    js.Dynamic
      .literal(
        `type` = "FeatureCollection",
        features = drawn.toSeq.map { (k, d) =>
          js.Dynamic.literal(
            `type` = "Feature",
            id = d.n,
            geometry = d.geometry,
            properties = js.Dynamic.literal(hid = k, c = d.colour, a = d.alpha, d = d.dashed)
          )
        }.toJSArray
      )
      .asInstanceOf[js.Object]

  /** The source and layers, when the style lacks them (made, or a new base map loaded). */
  def restore(): Unit =
    if gl.isStyleLoaded() || gl.getStyle() != null then
      scala.util.Try {
        if gl.getSource(id).isEmpty then
          gl.addSource(id, js.Dynamic.literal(`type` = "geojson", data = collection).asInstanceOf[js.Object])
        layers.foreach(l =>
          if gl.getLayer(l.asInstanceOf[js.Dynamic].id.asInstanceOf[String]).isEmpty then gl.addLayer(l)
        )
        stated.foreach(k => drawn.get(k).foreach(state))
      }

  private def state(d: Drawn): Unit =
    scala.util.Try(
      gl.setFeatureState(
        js.Dynamic.literal(source = id, id = d.n).asInstanceOf[js.Object],
        js.Dynamic.literal(c = d.colour, a = d.alpha).asInstanceOf[js.Object]
      )
    )

  restore()

  def ids: Seq[String] = drawn.keys.toSeq

  // `var`: how many times the outlines were sent (diagnostics)
  private var sent = 0
  override def uploads: Int = sent
  override def styleOf(k: String): Option[(String, Double)] = drawn.get(k).map(d => (d.colour, d.alpha))

  /** The shapes under map point `at` (screen point `p`), topmost (last drawn) first: an area that holds it, a point
    * within its circle.
    */
  def under(at: LatLon, p: ScreenPt, s: MapSurface): Seq[String] =
    // a shape faded out (playback's states before and after their years) answers nothing
    drawn.toSeq.reverseIterator
      .filter((_, d) => d.alpha > 0.05)
      .filter { (_, d) =>
        ShapeHits.point(d.geometry.asInstanceOf[js.Dynamic]) match
          case Some(q) =>
            val sp = s.project(q)
            math.hypot(sp.x - p.x, sp.y - p.y) <= look.circleRadius + 3
          case None =>
            d.bounds.exists(b => at.lat >= b.south && at.lat <= b.north) && {
              // the copy of the world the shape's bounds are in
              val lon = d.bounds.fold(at.lon)(b =>
                if at.lon < b.west then at.lon + 360 else if at.lon > b.east then at.lon - 360 else at.lon
              )
              d.bounds.exists(b => lon >= b.west && lon <= b.east) && ShapeHits.contains(
                d.geometry.asInstanceOf[js.Dynamic],
                at.lat,
                lon
              )
            }
      }
      .map(_._1)
      .toSeq

  def set(shapes: Seq[ShapeFeature]): Unit =
    val want = shapes.map(s => s.id -> s).toMap
    val sameSet = want.size == drawn.size && shapes.forall(s =>
      drawn.get(s.id).exists(d => sameGeometry(d.geometry, s.geometry) && d.dashed == s.dashed)
    )
    if sameSet then shapes.foreach(s => restyle(s.id, s.colour, s.alpha))
    else
      drawn.clear()
      shapes.foreach { s =>
        val n = numeric.getOrElseUpdate(s.id, { next += 1; next })
        drawn(s.id) = Drawn(n, s.geometry, s.colour, s.alpha, s.dashed)
      }
      gl.getSource(id).toOption match
        case Some(src) =>
          sent += 1
          src.setData(collection)
          stated.filterInPlace(drawn.contains)
          stated.foreach(k => state(drawn(k)))
        case None => restore()

  def restyle(k: String, colour: String, alpha: Double): Unit =
    drawn.get(k).foreach { d =>
      if d.colour != colour || d.alpha != alpha then
        d.colour = colour
        d.alpha = alpha
        stated += k
        state(d)
    }

  /** The same outline: the same object, or (a new answer for the same shape) the same rings' lengths and first vertex —
    * cheap, and two years of one state with the same outline share it.
    */
  private def sameGeometry(a: js.Any, b: js.Any): Boolean =
    (a eq b) || {
      def sig(g: js.Any): String =
        val d = g.asInstanceOf[js.Dynamic]
        if js.isUndefined(d) || d == null then ""
        else
          val sb = StringBuilder(d.selectDynamic("type").toString)
          def walk(c: js.Dynamic, depth: Int): Unit =
            if js.Array.isArray(c) && depth < 4 then
              val arr = c.asInstanceOf[js.Array[js.Dynamic]]
              sb.append('[').append(arr.length)
              if arr.nonEmpty then
                if js.typeOf(arr(0)) == "number" then sb.append(':').append(arr.mkString(","))
                else { walk(arr(0), depth + 1); if arr.length > 1 then walk(arr(arr.length - 1), depth + 1) }
              sb.append(']')
          walk(d.coordinates, 0)
          sb.toString
      sig(a) == sig(b)
    }

/** One [[PointTiles]] layer as a vector source of the server's tiles. MapLibre fetches, caches and decodes the tiles
  * (in its workers) only for a source a style layer reads: an invisible circle layer does. The features are read from
  * the tiles of the view's zoom (`api.TileProps.Zoom`, which every feature carries — MapLibre keeps a coarser tile
  * drawn while the finer ones come in), once each.
  */
final private[maplibre] class GlPointTiles(gl: GlMap, id: String, layer: String, maxZoom: Int) extends PointTiles:
  // `var`s: the template asked for, and the listeners
  private var template: Option[String] = None
  private val listeners = mutable.ListBuffer.empty[() => Unit]
  private var queued = false
  private val hitLayer = s"$id-points"

  private def notifyLater(): Unit =
    if !queued then
      queued = true
      val _ = dom.window.requestAnimationFrame { _ => queued = false; listeners.toList.foreach(_()) }

  gl.on(
    "sourcedata",
    (e: GlEvent) =>
      if e.asInstanceOf[js.Dynamic].sourceId.asInstanceOf[js.UndefOr[String]].contains(id) && loaded then notifyLater()
  )
  gl.on("moveend", (_: GlEvent) => if loaded then notifyLater())

  /** The source and its layer, when the style lacks them (made, or a new base map loaded). */
  def restore(): Unit =
    template.foreach { t =>
      if gl.isStyleLoaded() || gl.getStyle() != null then
        scala.util.Try {
          if gl.getSource(id).isEmpty then
            gl.addSource(
              id,
              js.Dynamic
                .literal(`type` = "vector", tiles = js.Array(t), minzoom = 0, maxzoom = maxZoom, tileSize = 512)
                .asInstanceOf[js.Object]
            )
          if gl.getLayer(hitLayer).isEmpty then
            gl.addLayer(
              js.Dynamic
                .literal(
                  id = hitLayer,
                  `type` = "circle",
                  source = id,
                  `source-layer` = layer,
                  paint = js.Dynamic.literal(`circle-radius` = 0, `circle-opacity` = 0, `circle-stroke-width` = 0)
                )
                .asInstanceOf[js.Object]
            )
        }
    }

  def setTiles(t: Option[String]): Unit =
    if t != template then
      template = t
      t match
        case None =>
          scala.util.Try {
            if gl.getLayer(hitLayer).isDefined then gl.removeLayer(hitLayer)
            if gl.getSource(id).isDefined then gl.removeSource(id)
          }
        case Some(url) =>
          gl.getSource(id).toOption match
            case Some(src) => scala.util.Try(src.setTiles(js.Array(url)))
            case None      => restore()

  def loaded: Boolean =
    template.isEmpty || scala.util.Try(gl.getSource(id).isDefined && gl.isSourceLoaded(id)).getOrElse(false)

  def onChange(f: () => Unit): Subscription =
    listeners += f
    () => { val _ = listeners -= f }

  def features: Seq[TilePointFeature] =
    if template.isEmpty || gl.getSource(id).isEmpty then Nil
    else
      val z = math.max(0, math.min(maxZoom, math.floor(gl.getZoom()).toInt))
      val seen = mutable.HashSet.empty[String]
      scala.util
        .Try(gl.querySourceFeatures(id, js.Dynamic.literal(sourceLayer = layer).asInstanceOf[js.Object]))
        .getOrElse(js.Array())
        .toSeq
        .flatMap { f =>
          val props = f.properties.asInstanceOf[js.Dictionary[js.Any]]
          val atZoom = props.get(com.histmersion.api.TileProps.Zoom).forall(v => v.asInstanceOf[Double].toInt == z)
          val key = props.get(com.histmersion.api.TileProps.Key).map(_.toString).getOrElse("")
          if !atZoom || !seen.add(key) then None
          else ShapeHits.point(f.geometry).map(TilePointFeature(_, props))
        }
