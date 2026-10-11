package com.histmersion.web

import com.histmersion.core.UiConfig
import com.histmersion.web.LabelMotion.Motion
import com.histmersion.web.LabelPlacement.{Pt, Rect}
import com.histmersion.web.leaflet.*
import com.raquo.laminar.api.L.Owner
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import scala.util.Try

/** The NAMES on the map: every region and polity on the boundaries layer is named, and the names that
  * would collide are thinned out, events first, then larger shapes ([[LabelPlacement]] decides where
  * and which, [[LabelMotion]] how they move; this object applies both to Leaflet).
  *
  *   - an area's name sits at its home (the pole of inaccessibility of its main part, or of an
  *     archipelago's hull), computed once per shape (cached by its id and outline). When that is off
  *     screen or too near the edge for the whole name, the name moves into the part of the shape still
  *     on screen; it is hidden only when no place keeps it on screen, or when it collides;
  *   - THE PASS (at the end of a pan or zoom, `moveend`, and when the set of shapes changes) does the
  *     costly part once: size classes (writes), one layout read of every name's size, and the exact
  *     place in the settled viewport ([[LabelPlacement.place]], a clip and a polylabel near the edge);
  *   - THE FRAME (every animation frame while the map moves or a name is still moving) does the cheap
  *     part: each name's target from what the pass cached ([[LabelPlacement.glide]], a clamp), the
  *     collision cull with hysteresis, and the easing — a name glides toward its target, never jumps,
  *     fades and grows in, shrinks and fades out, and scales a little with a zoom before it settles at
  *     its size class. The name's marker stays at the home; the frame writes only the CSS custom
  *     properties of the names that changed (`--dx --dy` offset, `--s` size, `--o` opacity), which the
  *     stylesheet turns into a transform — no layout, no DOM rebuild. With reduced motion, positions and
  *     sizes snap and the names only fade;
  *   - a point-only region or polity (no boundary yet) and a people keep their own interactive label
  *     ([[PlaceGlyph.labelMarker]]) and join the cull, ranked after every area;
  *   - an event's name in playback ([[EventLayer]]) joins it too, ranked ABOVE every area and point
  *     name ([[EventPlayback.rank]]): it is there for a moment, the static names come back when it
  *     goes. Only its name is culled, never its dot. Point and event names fade and grow through a CSS
  *     transition on `.is-culled`;
  *   - the names' colours follow the base map's tone ([[MapTone]]): `.map-tone-light` /
  *     `.map-tone-dark` on the map container, halo text either way (`style.css`). */
object MapLabels:

  /** The Leaflet pane the area names are drawn in: above the shapes, below the markers, and never a
    * pointer target (the shape under a name answers the pointer). */
  val Pane = MapPanes.Labels

  // `var`s: the one map, and the frames queued for it
  private var map: LeafletMap = null
  private var queued = false
  private var looping = false

  private val areaLayer = FadingLayer()

  /** A name the pass places: its anchor, its rank (an area's zoom-0 size; a point label ranks below
    * every area, an event above), the shape it names (areas only), its element once Leaflet has drawn
    * it, and (events) its marker's element, whose corner IS the anchor — an event's name is beside its
    * dot, stacked under another event's, so the pass measures where it actually is. */
  private final class Entry(val latlng: LatLng, val rank: Double, val shape: Option[LabelPlacement.Shape],
      val el: () => Option[dom.HTMLElement], val anchorEl: Option[() => Option[dom.HTMLElement]] = None,
      val marker: Option[Marker] = None)

  private val entries = mutable.LinkedHashMap.empty[String, Entry]

  /** What the frames need of one name between passes: its size and centre offset from its anchor
    * (px, measured by the pass), its size class, the exact place of the settled view (areas), where it
    * heads, how it moves, how visible and how big it is, and what was last written to it. */
  private final class Live:
    var w = 0.0
    var h = 0.0
    var ox = 0.0
    var oy = 0.0
    var measured = false
    var tier: Option[LabelPlacement.Tier] = None
    var precise: Option[Pt] = None
    var target: Option[Pt] = None
    var motion: Motion = null
    var a = 0.0
    var s = 1.0
    var vs = 0.0
    var shown = false
    var wrote = Array(Double.NaN, Double.NaN, Double.NaN, Double.NaN)   // --dx --dy --s --o

  private val state = mutable.HashMap.empty[String, Live]

  /** Shapes already placed, by id and outline — a year's slider drag re-reads the same shapes. */
  private val shapes = mutable.LinkedHashMap.empty[String, Option[LabelPlacement.Shape]]
  private val ShapeCacheSize = 2000
  private def live(k: String): Live = state.getOrElseUpdate(k, Live())

  // the view: the zoom the size classes were chosen at, whether the view is the one the pass placed
  // the names for, whether the map is moving, a zoom animation in flight, and the last frame's time
  private var settledZoom = 0.0
  private var settled = false
  private var moving = false
  private var flight: Option[(LabelMotion.ZoomFlight, Double)] = None
  private var lastFrame = 0.0

  def install(m: LeafletMap, ui: UiConfig)(using Owner): Unit =
    map = m
    MapPanes.ensure(m)
    // a pan or a zoom: the frames ease the names along; its END places them exactly (the pass)
    m.on("movestart", (_: LeafletEvent) => { moving = true; settled = false; loop() })
    m.on("zoomstart", (_: LeafletEvent) => { moving = true; settled = false; loop() })
    m.on("zoomanim", (e: LeafletEvent) => startFlight(e))
    m.on("zoomend", (_: LeafletEvent) => flight = None)
    m.on("moveend", (_: LeafletEvent) => { moving = false; flight = None; schedule() })
    val configured = MapTone.parse(ui.tilesTone)
    UiState.baseMap.signal.foreach { b =>
      val c = m.getContainer().classList
      val dark = BaseMap.tone(b, configured) == MapTone.Dark
      c.toggle("map-tone-dark", dark)
      c.toggle("map-tone-light", !dark)
      c.toggle("map-tiles-dark", BaseMap.darkened(b, configured))
    }

  /** The area names follow the boundaries layer: shown with it, gone with it. */
  def attach(): Unit = if map != null then areaLayer.attach(map)

  def detach(): Unit =
    if map != null then
      areaLayer.detach(map)
      entries.filterInPlace((k, _) => !k.startsWith(AreaKey))
      state.filterInPlace((k, _) => !k.startsWith(AreaKey))

  private val AreaKey = "a|"
  private val PointKey = "p|"
  private val EventKey = "e|"

  /** Name each area feature (a GeoJSON Feature with a Polygon or MultiPolygon) of the layer just
    * drawn. `key` is the feature's key on the layer; `wrapClass` its label wrapper's classes. */
  def syncAreas(features: Seq[js.Dynamic], key: js.Dynamic => String, label: js.Dynamic => String,
      wrapClass: js.Dynamic => String): Unit =
    if map != null && areaLayer.isAttached then
      val t0 = now()
      val named = features.flatMap(f => shapeOf(f).map(s => (f, s)))
      timed("hd-label-shapes", t0)
      val keep = named.map((f, _) => AreaKey + key(f)).toSet
      entries.filterInPlace((k, _) => !k.startsWith(AreaKey) || keep(k))
      state.filterInPlace((k, _) => !k.startsWith(AreaKey) || keep(k))
      areaLayer.sync[(js.Dynamic, LabelPlacement.Shape)](
        named,
        key = (f, _) => AreaKey + key(f),
        create = (f, s) =>
          val k = AreaKey + key(f)
          val m = areaMarker(s, label(f), wrapClass(f))
          state.remove(k)   // a new element: it fades in from nothing
          register(k, m, s)
          m,
        update = (lyr, fs) =>
          val (f, s) = fs
          val k = AreaKey + key(f)
          val m = lyr.asInstanceOf[Marker]
          val at = m.getLatLng()
          // a new outline moves the home; the name stays where it is drawn and glides from there
          if at.lat != s.lat || at.lng != s.lon then
            m.setLatLng(js.Array(s.lat, s.lon))
            state.get(k).foreach(st => inner(m).foreach(el => render(st, el, s, math.pow(2, zoomNow()))))
          register(k, m, s)
      )
      schedule()

  /** A point-only label ([[PlaceGlyph.labelMarker]]) takes part in the pass while it is on the map. */
  def trackPoint(marker: Marker, key: String): Unit =
    val k = PointKey + key
    marker.on("add", (_: LeafletEvent) =>
      entries(k) = Entry(marker.getLatLng(), -1.0, None, () => inner(marker))
      state.remove(k)
      schedule())
    marker.on("remove", (_: LeafletEvent) =>
      // a FadingLayer removes the old marker after the new one of the same key was added
      entries.get(k).foreach(e => if e.latlng eq marker.getLatLng() then { entries.remove(k); state.remove(k) })
      ())

  /** An event's name ([[EventLayer]]) takes part in the pass while it is shown: `label` is its name
    * element (what a cull hides), `marker` its marker. Added hidden (`is-culled`, so it never flashes
    * over another name); without a map pass it is simply shown. */
  def trackEvent(key: String, marker: Marker, rank: Double, label: () => Option[dom.HTMLElement]): Unit =
    if map == null then label().foreach(_.classList.remove(Culled))
    else
      entries(EventKey + key) = Entry(marker.getLatLng(), rank, None, label, Some(() => element(marker)))
      state.remove(EventKey + key)
      schedule()

  /** The event's name is gone from the map: the names it hid come back at the next pass. */
  def untrackEvent(key: String): Unit =
    state.remove(EventKey + key)
    if entries.remove(EventKey + key).isDefined then schedule()

  /** Every event's name is gone (playback stopped). */
  def untrackEvents(): Unit =
    val before = entries.size
    entries.filterInPlace((k, _) => !k.startsWith(EventKey))
    state.filterInPlace((k, _) => !k.startsWith(EventKey))
    if entries.size != before then schedule()

  private def register(k: String, m: Marker, s: LabelPlacement.Shape): Unit =
    entries(k) = Entry(m.getLatLng(), s.size0, Some(s), () => inner(m), marker = Some(m))

  private def inner(m: Marker): Option[dom.HTMLElement] =
    element(m).flatMap(el => Option(el.firstElementChild)).map(_.asInstanceOf[dom.HTMLElement])

  private def element(m: Marker): Option[dom.HTMLElement] =
    val el = m.asInstanceOf[js.Dynamic].getElement()
    if js.isUndefined(el) || el == null then None else Some(el.asInstanceOf[dom.HTMLElement])

  private def areaMarker(s: LabelPlacement.Shape, label: String, wrapClass: String): Marker =
    L.marker(js.Array(s.lat, s.lon), js.Dynamic.literal(
      pane = Pane,
      interactive = false,
      keyboard = false,
      icon = L.divIcon(js.Dynamic.literal(
        className = wrapClass,
        iconSize = null,
        // hidden until the first pass has sized and placed it; it then fades and grows in
        html = s"""<span class="hd-map-label is-culled" style="--o:0">${Fmt.esc(label)}</span>"""
      ).asInstanceOf[js.Object])
    ).asInstanceOf[js.Object])

  // ── shapes ─────────────────────────────────────────────────────────────────────────────────────

  /** The label place of an area feature, from the cache when its outline was seen before. */
  private def shapeOf(f: js.Dynamic): Option[LabelPlacement.Shape] =
    val g = f.geometry
    if js.isUndefined(g) || g == null then None
    else
      val t = g.selectDynamic("type").toString
      if t != "Polygon" && t != "MultiPolygon" then None
      else
        val parts =
          if t == "Polygon" then js.Array(g.coordinates)
          else g.coordinates.asInstanceOf[js.Array[js.Dynamic]]
        val sig = signature(f, parts.asInstanceOf[js.Array[js.Dynamic]])
        shapes.get(sig) match
          case Some(s) => s
          case None =>
            val s = LabelPlacement.shape(parts.asInstanceOf[js.Array[js.Dynamic]].toSeq.map(polyRings))
            shapes(sig) = s
            while shapes.size > ShapeCacheSize do shapes.remove(shapes.head._1)
            s

  /** An outline's identity, cheaply: the feature's id, its parts, and each outer ring's length and
    * first vertex — two years of one polity with the same outline share it. */
  private def signature(f: js.Dynamic, parts: js.Array[js.Dynamic]): String =
    val id = Option(f.properties).filterNot(js.isUndefined).map(_.id.toString).getOrElse("")
    val sb = StringBuilder(id)
    parts.foreach { p =>
      val outer = p.asInstanceOf[js.Array[js.Array[js.Array[Double]]]](0)
      sb.append('|').append(outer.length)
      if outer.length > 0 then sb.append(':').append(outer(0)(0)).append(',').append(outer(0)(1))
    }
    sb.toString

  /** One polygon's rings as (lat, lon), thinned on the way in to at most
    * [[LabelPlacement.MaxRingPoints]] each — the label needs the shape's room, not its coastline. */
  private def polyRings(p: js.Dynamic): Seq[Seq[(Double, Double)]] =
    p.asInstanceOf[js.Array[js.Array[js.Array[Double]]]].toSeq.map { ring =>
      val step = math.max(1, math.ceil(ring.length.toDouble / LabelPlacement.MaxRingPoints).toInt)
      (0 until ring.length by step).map(i => (ring(i)(1), ring(i)(0)))
    }

  // ── the pass ───────────────────────────────────────────────────────────────────────────────────

  /** Queue one pass for the next frame; any number of calls before it run it once. */
  def schedule(): Unit =
    if !queued && map != null then
      queued = true
      dom.window.requestAnimationFrame(_ => { queued = false; run() })

  private val Culled = "is-culled"
  private val TierClasses = LabelPlacement.Tier.values.map(_.css)

  /** The map's zoom now — fractional in a pinch. */
  private def zoomNow(): Double = map.zoomLevel()

  private def reducedMotion(): Boolean =
    Try(dom.window.matchMedia("(prefers-reduced-motion: reduce)").matches).getOrElse(false)

  /** The settled viewport in the zoom-0 plane. */
  private def viewNow(zoom: Double): Rect =
    val scale = math.pow(2, zoom)
    val pb = map.getPixelBounds()
    Rect(pb.min.x / scale, pb.min.y / scale, pb.max.x / scale, pb.max.y / scale)

  /** Size every name for the zoom (writes), measure them (one layout), place the area names exactly in
    * the settled viewport (pure), then draw the first frame of the result. */
  private def run(): Unit =
    val t0 = now()
    val z = zoomNow()
    val scale = math.pow(2, z)
    val view = viewNow(z)
    val reduced = reducedMotion()
    val items = entries.toList.flatMap((k, e) => e.el().map(el => (k, e, el, live(k))))
    // 1. size classes (writes only); a class change is remembered with the width it had
    val before = mutable.HashMap.empty[String, Double]
    items.foreach { (k, e, el, st) =>
      e.shape.foreach { s =>
        val t = LabelPlacement.tier(s, z)
        if t != st.tier then
          if st.measured && st.a > 0 then before(k) = st.w
          TierClasses.foreach(c => if !t.exists(_.css == c) then el.classList.remove(c))
          t.foreach(tier => el.classList.add(tier.css))
          st.tier = t
      }
    }
    // 2. measure (reads only: one layout for the whole batch)
    items.foreach { (_, e, el, st) =>
      e.anchorEl match
        case Some(anchor) =>
          // where the name is drawn, relative to its anchor (the marker element's corner); the box's
          // centre is unmoved by the fade's scale, its size is the element's own
          anchor().foreach { a =>
            val r = el.getBoundingClientRect()
            val o = a.getBoundingClientRect()
            st.w = el.offsetWidth.toDouble
            st.h = el.offsetHeight.toDouble
            st.ox = r.left + r.width / 2 - o.left
            st.oy = r.top + r.height / 2 - o.top
            st.measured = st.w > 0
          }
        case None =>
          st.w = el.offsetWidth.toDouble
          st.h = el.offsetHeight.toDouble
          st.measured = st.w > 0
    }
    // 3. the exact place of each area name in this view (pure)
    val tPlace = now()
    items.foreach { (k, e, _, st) =>
      e.shape.foreach { s =>
        st.precise =
          if st.tier.isEmpty || !st.measured then None
          else LabelPlacement.place(s, view, scale, st.w / 2, st.h / 2, st.precise)
        before.get(k).foreach(w0 => st.s = LabelMotion.rebase(st.s, w0, st.w, reduced))
      }
    }
    timed("hd-label-place", tPlace)
    settledZoom = z
    settled = !moving
    // 4. the first frame of it, now, so a class change and its size compensation land together
    frame(now(), reduced)
    timed("hd-labels", t0)
    loop()

  // ── the frames ─────────────────────────────────────────────────────────────────────────────────

  /** Keep one frame queued while the map moves or a name is still moving. */
  private def loop(): Unit =
    if !looping && map != null then
      looping = true
      lastFrame = now()
      dom.window.requestAnimationFrame(tick)

  private val tick: js.Function1[Double, Unit] = (_: Double) =>
    val t = now()
    val busy = frame(t, reducedMotion())
    timed("hd-label-frame", t)
    if busy || moving || flight.isDefined then dom.window.requestAnimationFrame(tick)
    else looping = false

  /** A zoom animation starts: the frames follow Leaflet's own easing to the target view. */
  private def startFlight(e: LeafletEvent): Unit =
    val d = e.asInstanceOf[js.Dynamic]
    val c = d.center.asInstanceOf[LatLng]
    val z1 = d.zoom.asInstanceOf[Double]
    val z0 = zoomNow()
    val size = map.asInstanceOf[js.Dynamic].getSize()
    flight = Some((LabelMotion.ZoomFlight(viewNow(z0), z0, LabelPlacement.project(c.lat, c.lng), z1,
      size.x.asInstanceOf[Double], size.y.asInstanceOf[Double]), now()))
    moving = true
    settled = false
    loop()

  /** One frame: every name's target (cheap), the cull, the easing, and the writes of what changed.
    * `true` while some name is still on its way. */
  private def frame(t: Double, reduced: Boolean): Boolean =
    if map == null then false
    else
      val dt = math.max(0.0, math.min(0.1, (t - lastFrame) / 1000.0))
      lastFrame = t
      val (view, z) = flight match
        case Some((f, t0)) => f.at(LabelMotion.zoomEase((t - t0) / 1000.0 / LabelMotion.ZoomSec))
        case None          => val z = zoomNow(); (viewNow(z), z)
      val scale = math.pow(2, z)
      val far = PlaceGlyph.far(math.round(z).toInt)
      val zs = LabelMotion.zoomScale(z, settledZoom, reduced)
      val items = entries.toList.flatMap((k, e) => e.el().map(el => (k, e, el, live(k))))
      // 1. targets and boxes (world pixels at this zoom)
      val boxes = items.flatMap { (k, e, _, st) =>
        if !st.measured then None
        else
          e.shape match
            case Some(s) =>
              st.target =
                if st.tier.isEmpty then None
                else if settled then st.precise
                else LabelPlacement.glide(s, view, scale, st.w * zs / 2, st.h * zs / 2, st.precise)
              st.target.map(p => LabelPlacement.Box(k, p.x * scale, p.y * scale, st.w * zs, st.h * zs, e.rank))
            case None =>
              if far && e.anchorEl.isEmpty then None
              else
                val p = LabelPlacement.project(e.latlng.lat, e.latlng.lng)
                Some(LabelPlacement.Box(k, p.x * scale + st.ox, p.y * scale + st.oy, st.w, st.h, e.rank))
      }
      // 2. which are shown (a shown name keeps its slot until the overlap is clear)
      val kept = LabelPlacement.cull(boxes, shown = k => state.get(k).exists(_.shown))
      // 3. ease and write
      var busy = false
      items.foreach { (k, e, el, st) =>
        val show = kept(k)
        e.shape match
          case Some(s) =>
            st.target.foreach { p =>
              val inner = view.inset((st.w * zs / 2 + LabelPlacement.EdgePadPx) / scale,
                (st.h * zs / 2 + LabelPlacement.EdgePadPx) / scale)
              st.motion =
                if st.motion == null || st.a == 0 || reduced then Motion.at(p)
                else st.motion.toward(p, dt, reduced).within(inner)
              if !st.motion.restsAt(p, scale) then busy = true
            }
            if st.motion == null then st.motion = Motion.at(s.home)
            st.a = LabelMotion.fade(st.a, show, dt)
            if reduced then { st.s = zs; st.vs = 0 }
            else
              val (s1, v1) = LabelMotion.damp(st.s, st.vs, zs, dt)
              st.s = s1
              st.vs = v1
            if math.abs(st.s - zs) > 1e-3 || math.abs(st.vs) > 1e-3 then busy = true
            if (show && st.a < 1) || (!show && st.a > 0) then busy = true
            if st.a > 0 then
              if el.classList.contains(Culled) then el.classList.remove(Culled)
              render(st, el, s, scale, reduced)
            else
              writeO(st, el, 0.0)
              if !el.classList.contains(Culled) then el.classList.add(Culled)
          case None =>
            if st.shown != show || el.classList.contains(Culled) == show then el.classList.toggle(Culled, !show)
        st.shown = show
      }
      busy

  /** Write a name's offset from its home, its size and its opacity — only what changed. */
  private def render(st: Live, el: dom.HTMLElement, s: LabelPlacement.Shape, scale: Double,
      reduced: Boolean = false): Unit =
    if st.motion != null then
      write(st, el, 0, "--dx", (st.motion.p.x - s.home.x) * scale, 0.05, "px")
      write(st, el, 1, "--dy", (st.motion.p.y - s.home.y) * scale, 0.05, "px")
    write(st, el, 2, "--s", st.s * LabelMotion.appearScale(st.a, reduced), 0.002, "")
    writeO(st, el, st.a)

  private def writeO(st: Live, el: dom.HTMLElement, a: Double): Unit = write(st, el, 3, "--o", a, 0.005, "")

  private def write(st: Live, el: dom.HTMLElement, i: Int, name: String, v: Double, eps: Double, unit: String): Unit =
    val last = st.wrote(i)
    // past the threshold, or arriving exactly at an end (fully hidden, fully shown, at home)
    if last.isNaN || math.abs(last - v) > eps || (v != last && (v == 0 || v == 1)) then
      st.wrote(i) = v
      val text = v.asInstanceOf[js.Dynamic].toFixed(if unit.isEmpty then 4 else 2).asInstanceOf[String] + unit
      name match
        case "--dx" => el.style.setProperty("--dx", text)
        case "--dy" => el.style.setProperty("--dy", text)
        case "--s"  => el.style.setProperty("--s", text)
        case _      => el.style.setProperty("--o", text)

  // ── what a pass costs, for the browser's performance panel (the latest of each, never a backlog) ──

  private def now(): Double = dom.window.performance.now()

  private def timed(name: String, t0: Double): Unit =
    val p = dom.window.performance.asInstanceOf[js.Dynamic]
    if js.typeOf(p.measure) == "function" then
      p.clearMeasures(name)
      p.measure(name, js.Dynamic.literal(start = t0, end = now()))
