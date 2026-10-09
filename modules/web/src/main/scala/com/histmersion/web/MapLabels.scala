package com.histmersion.web

import com.histmersion.core.UiConfig
import com.histmersion.web.leaflet.*
import com.raquo.laminar.api.L.Owner
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The NAMES on the map: every region and polity on the boundaries layer is named inside its shape,
  * and the names that would collide are thinned out, larger shapes first ([[LabelPlacement]] decides;
  * this object applies it to Leaflet).
  *
  *   - an area's name sits at its pole of inaccessibility, computed once per shape (cached by its id
  *     and outline) — never per frame;
  *   - its size class and the collision pass run once per ZOOM and once per new set of shapes, never
  *     on a pan: a pan moves every name by the same offset, so nothing new can collide. One pass is a
  *     batch of class writes, one layout read of the label sizes, and the pure cull;
  *   - a point-only region or polity (no boundary yet) and a people keep their own interactive label
  *     ([[PlaceGlyph.labelMarker]]) and join the same pass, ranked after every area;
  *   - an event's name in playback ([[EventLayer]]) joins it too, ranked ABOVE every area and point
  *     name ([[EventPlayback.rank]]): it is there for a moment, the static names come back when it
  *     goes. Only its name is culled, never its dot. The pass runs when an event comes or goes, not
  *     on every frame of its fade;
  *   - the names' colours follow the base map's tone ([[MapTone]]): `.map-tone-light` /
  *     `.map-tone-dark` on the map container, halo text either way (`style.css`). */
object MapLabels:

  /** The Leaflet pane the area names are drawn in: above the shapes, below the markers, and never a
    * pointer target (the shape under a name answers the pointer). */
  val Pane = "hd-labels"

  // `var`s: the one map, and the frame a pass is queued for
  private var map: LeafletMap = null
  private var queued = false

  private val areaLayer = FadingLayer()

  /** A name the pass places: its anchor, its rank (an area's zoom-0 size; a point label ranks below
    * every area, an event above), the shape it names (areas only) and its element once Leaflet has
    * drawn it. An event's name is not centred on its anchor (beside its dot, stacked under another
    * event's), so `anchorEl` — its marker's element, whose corner IS the anchor — lets the pass
    * measure where the name actually is. */
  private final class Entry(val latlng: LatLng, val rank: Double, val shape: Option[LabelPlacement.Shape],
      val el: () => Option[dom.HTMLElement], val anchorEl: Option[() => Option[dom.HTMLElement]] = None)

  private val entries = mutable.LinkedHashMap.empty[String, Entry]

  /** Shapes already placed, by id and outline — a year's slider drag re-reads the same shapes. */
  private val shapes = mutable.LinkedHashMap.empty[String, Option[LabelPlacement.Shape]]
  private val ShapeCacheSize = 2000

  def install(m: LeafletMap, ui: UiConfig)(using Owner): Unit =
    map = m
    if m.getPane(Pane).isEmpty then
      val _ = m.createPane(Pane)
    m.on("zoomend", (_: LeafletEvent) => schedule())
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
      areaLayer.sync[(js.Dynamic, LabelPlacement.Shape)](
        named,
        key = (f, _) => AreaKey + key(f),
        create = (f, s) =>
          val m = areaMarker(s, label(f), wrapClass(f))
          register(AreaKey + key(f), m, s)
          m,
        update = (lyr, fs) =>
          val (f, s) = fs
          val m = lyr.asInstanceOf[Marker]
          val at = m.getLatLng()
          if at.lat != s.lat || at.lng != s.lon then m.setLatLng(js.Array(s.lat, s.lon))
          register(AreaKey + key(f), m, s)
      )
      schedule()

  /** A point-only label ([[PlaceGlyph.labelMarker]]) takes part in the pass while it is on the map. */
  def trackPoint(marker: Marker, key: String): Unit =
    val k = PointKey + key
    marker.on("add", (_: LeafletEvent) =>
      entries(k) = Entry(marker.getLatLng(), -1.0, None, () => inner(marker))
      schedule())
    marker.on("remove", (_: LeafletEvent) =>
      // a FadingLayer removes the old marker after the new one of the same key was added
      entries.get(k).foreach(e => if e.latlng eq marker.getLatLng() then entries.remove(k))
      ())

  /** An event's name ([[EventLayer]]) takes part in the pass while it is shown: `label` is its name
    * element (what a cull hides), `marker` its marker. Added hidden (`is-culled`, so it never flashes
    * over another name); without a map pass it is simply shown. */
  def trackEvent(key: String, marker: Marker, rank: Double, label: () => Option[dom.HTMLElement]): Unit =
    if map == null then label().foreach(_.classList.remove(Culled))
    else
      entries(EventKey + key) = Entry(marker.getLatLng(), rank, None, label, Some(() => element(marker)))
      schedule()

  /** The event's name is gone from the map: the names it hid come back at the next pass. */
  def untrackEvent(key: String): Unit =
    if entries.remove(EventKey + key).isDefined then schedule()

  /** Every event's name is gone (playback stopped). */
  def untrackEvents(): Unit =
    val before = entries.size
    entries.filterInPlace((k, _) => !k.startsWith(EventKey))
    if entries.size != before then schedule()

  private def register(k: String, m: Marker, s: LabelPlacement.Shape): Unit =
    entries(k) = Entry(m.getLatLng(), s.area0, Some(s), () => inner(m))

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
        // hidden until the first pass has sized and placed it — a name never flashes at the wrong size
        html = s"""<span class="hd-map-label is-culled">${Fmt.esc(label)}</span>"""
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

  /** Size every name for the zoom (writes), measure them (one layout), keep the ones that fit and do
    * not collide (pure), hide the rest (writes). */
  private def run(): Unit =
    val t0 = now()
    val z = map.getZoom().toDouble
    val far = PlaceGlyph.far(map.getZoom())
    val live = entries.toList.flatMap((k, e) => e.el().map(el => (k, e, el)))
    // 1. sizes (writes only)
    val sized = live.flatMap { (k, e, el) =>
      e.shape match
        case Some(s) =>
          val t = LabelPlacement.tier(s.area0, z)
          TierClasses.foreach(c => if !t.exists(_.css == c) then el.classList.remove(c))
          t match
            case Some(tier) => el.classList.add(tier.css); Some((k, e, el))
            case None       => el.classList.add(Culled); None
        // an event's name shows at any zoom (the far map hides only the point-only area names)
        case None => if far && e.anchorEl.isEmpty then None else Some((k, e, el))
    }
    // 2. measure (reads only: one layout for the whole batch)
    val boxes = sized.flatMap { (k, e, el) =>
      e.anchorEl match
        case Some(anchor) =>
          // where the name is drawn, relative to its anchor (the marker element's corner)
          anchor().flatMap { a =>
            val r = el.getBoundingClientRect()
            val o = a.getBoundingClientRect()
            Option.when(r.width > 0) {
              val p = map.latLngToLayerPoint(e.latlng)
              LabelPlacement.Box(k, p.x + (r.left + r.width / 2 - o.left), p.y + (r.top + r.height / 2 - o.top),
                r.width, r.height, e.rank)
            }
          }
        case None =>
          val w = el.offsetWidth.toDouble
          val h = el.offsetHeight.toDouble
          val fits = e.shape.forall(s => LabelPlacement.fits(w, s.width0, z))
          Option.when(w > 0 && fits) {
            val p = map.latLngToLayerPoint(e.latlng)
            LabelPlacement.Box(k, p.x, p.y, w, h, e.rank)
          }
    }
    // 3. decide, then show/hide (writes only)
    val kept = LabelPlacement.cull(boxes)
    live.foreach((k, _, el) => el.classList.toggle(Culled, !kept(k)))
    timed("hd-labels", t0)

  // ── what a pass costs, for the browser's performance panel (the latest of each, never a backlog) ──

  private def now(): Double = dom.window.performance.now()

  private def timed(name: String, t0: Double): Unit =
    val p = dom.window.performance.asInstanceOf[js.Dynamic]
    if js.typeOf(p.measure) == "function" then
      p.clearMeasures(name)
      p.measure(name, js.Dynamic.literal(start = t0, end = now()))
