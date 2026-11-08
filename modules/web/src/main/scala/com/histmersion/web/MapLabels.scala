package com.histmersion
package web

import com.histmersion.api.instance.UiConfig
import com.histmersion.web.LabelMotion.Motion
import com.histmersion.web.LabelPlacement.{Pt, Rect}
import com.histmersion.api.MapStack
import com.histmersion.web.map.*
import com.raquo.laminar.api.L.Owner
import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.UiLabels
import com.histmersion.api.Years
import com.histmersion.web.ui.Layout
import com.histmersion.api.catalogue.Event
import com.histmersion.api.Geo

/** The NAMES on the map: every region and polity on the boundaries layer is named, and the names that would collide are
  * thinned out, events first, then larger shapes ([[LabelPlacement]] decides where and which, [[LabelMotion]] how they
  * move; this object applies both to the map).
  *
  *   - an area's name sits at its home (the pole of inaccessibility of its main part, or of an archipelago's hull),
  *     computed once per shape (cached by its id and outline). When that is off screen or too near the edge for the
  *     whole name, the name moves into the part of the shape still on screen; it is hidden only when no place keeps it
  *     on screen, or when it collides;
  *   - THE PASS (at the end of a pan or zoom, `moveend`, and when the set of shapes changes) does the costly part once:
  *     size classes (writes), one layout read of every name's size, and the exact place in the settled viewport
  *     ([[LabelPlacement.place]], a clip and a polylabel near the edge);
  *   - THE FRAME (every animation frame while the map moves or a name is still moving) does the cheap part: each name's
  *     target from what the pass cached ([[LabelPlacement.glide]], a clamp), the collision cull with hysteresis, and
  *     the easing — a name glides toward its target, never jumps, fades and grows in, shrinks and fades out, and scales
  *     a little with a zoom before it settles at its size class. The name's marker stays at the home; the frame writes
  *     only the CSS custom properties of the names that changed (`--dx --dy` offset, `--s` size, `--o` opacity), which
  *     the stylesheet turns into a transform — no layout, no DOM rebuild. With reduced motion, positions and sizes snap
  *     and the names only fade;
  *   - a point-only region or polity (no boundary yet) and a people keep their own interactive label
  *     ([[PlaceGlyph.labelMarker]]) and join the cull, ranked after every area;
  *   - an event's name in playback ([[EventLayer]]) joins it too, ranked ABOVE every area and point name
  *     ([[EventPlayback.rank]]): it is there for a moment, the static names come back when it goes. Only its name is
  *     culled, never its dot. Point and event names fade and grow through a CSS transition on `.is-culled`;
  *   - the names' colours follow the base map's tone ([[MapTone]]): `.map-tone-light` / `.map-tone-dark` on the map
  *     container, halo text either way (`style.css`);
  *   - an area's name is the keyboard's way to the area (its shape is on the map's canvas): a button that shows the
  *     place's card and chooses it ([[PlaceHover.bindName]]).
  */
object MapLabels:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type WrapClass = WrapClass.T
  object WrapClass extends Newtype[String, Pure]

  /** The pane the area names are drawn in: above the shapes, below the markers, and never a pointer target (the shape
    * under a name answers the pointer).
    */
  private def pane = TheMap.pane(MapStack.Pane.Labels)

  // `var`s: the one map, and the frames queued for it
  private var map: MapSurface = null
  private var queued = false
  private var looping = false

  // the area names on the map, by key, and whether the boundaries layer (theirs) is on
  private val areaMarkers = mutable.LinkedHashMap.empty[String, DomMarker]
  private var areasOn = false

  /** A name the pass places: its anchor, its rank (an area's zoom-0 size; a point label ranks below every area, an
    * event above), the shape it names (areas only), its element once it is drawn it, and (events) its marker's element,
    * whose corner IS the anchor — an event's name is beside its dot, stacked under another event's, so the pass
    * measures where it actually is.
    */
  final private class Entry(
      val latlng: LatLon,
      val rank: Entry.Rank,
      val shape: Option[LabelPlacement.Shape],
      val el: () => Option[dom.HTMLElement],
      val anchorEl: Option[() => Option[dom.HTMLElement]] = None,
      val marker: Option[DomMarker] = None
  )

  object Entry:
    type Rank = Rank.T
    object Rank extends Newtype[Double, Pure]

  private val entries = mutable.LinkedHashMap.empty[String, Entry]

  /** What the frames need of one name between passes: its size and centre offset from its anchor (px, measured by the
    * pass), its size class, the exact place of the settled view (areas), where it heads, how it moves, how visible and
    * how big it is, and what was last written to it.
    */
  final private class Live:
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
    var wrote = Array(Double.NaN, Double.NaN, Double.NaN, Double.NaN) // --dx --dy --s --o

  private val state = mutable.HashMap.empty[String, Live]

  /** Shapes already placed, by id and outline — a year's slider drag re-reads the same shapes. */
  private val ShapeCacheSize = 2000
  private val shapes = BoundedCache[String, Option[LabelPlacement.Shape]](BoundedCache.MaxEntries(ShapeCacheSize))

  /** What the names hold, for [[Diagnostics]]: the names placed, their per-name state, the shapes. */
  def stats: List[(Key, BoundedCache.Stats)] =
    List(
      Key("labels.entries") -> BoundedCache.Stats(
        BoundedCache.Stats.Entries(entries.size),
        BoundedCache.Stats.MaxEntries(0),
        BoundedCache.Stats.Weight(0L),
        BoundedCache.Stats.MaxWeight(0L)
      ),
      Key("labels.state") -> BoundedCache.Stats(
        BoundedCache.Stats.Entries(state.size),
        BoundedCache.Stats.MaxEntries(0),
        BoundedCache.Stats.Weight(0L),
        BoundedCache.Stats.MaxWeight(0L)
      ),
      Key("labels.shapes") -> BoundedCache.stats(shapes)
    )
  private def live(k: String): Live = state.getOrElseUpdate(k, Live())

  // the view: the zoom the size classes were chosen at, whether the view is the one the pass placed
  // the names for, whether the map is moving, a zoom animation in flight, and the last frame's time
  private var settledZoom = 0.0
  private var settled = false
  private var moving = false
  private var lastFrame = 0.0

  def install(m: MapSurface, ui: UiConfig)(using Owner): Unit =
    map = m
    // a pan or a zoom: the frames ease the names along (every frame reads the map's own view); its END
    // places them exactly (the pass)
    m.on(MapEventKind.MoveStart) { _ => moving = true; settled = false; loop() }
    m.on(MapEventKind.ZoomStart) { _ => moving = true; settled = false; loop() }
    m.on(MapEventKind.MoveEnd) { _ => moving = false; schedule() }
    val configured = MapTone.parse(MapTone.Wire(ui.tilesTone.value))
    BaseMaps.shown.signal.foreach { b =>
      val c = m.container.classList
      val dark = BaseMap.tone(b, configured) == MapTone.Dark
      c.toggle("map-tone-dark", dark)
      c.toggle("map-tone-light", !dark)
    }

  /** The area names follow the boundaries layer: shown with it, gone with it. */
  def attach(): Unit = areasOn = true

  def detach(): Unit =
    generation += 1 // the slices still placing names stop
    areasOn = false
    if map != null then
      areaMarkers.values.foreach(pane.remove)
      areaMarkers.clear()
      entries.filterInPlace((k, _) => !k.startsWith(AreaKey))
      state.filterInPlace((k, _) => !k.startsWith(AreaKey))

  private val AreaKey = "a|"
  private val PointKey = "p|"
  private val EventKey = "e|"

  /** Name each area feature (a GeoJSON Feature with a Polygon or MultiPolygon) of the layer just drawn. `key` is the
    * feature's key on the layer; `wrapClass` its label wrapper's classes; `release` is told when a feature's outline is
    * no longer needed here (the caller then drops it).
    *
    * The areas whose label place is known (cached from an earlier answer) are named at once; the others are placed a
    * slice at a time, [[SliceMs]] each, between the page's other work, and named together once all are placed (#1949):
    * a whole range's 1 600 new shapes took one ~300 ms task, during which no input was answered. A newer call (or the
    * layer switched off) stops the slices of an older one.
    */
  def syncAreas(
      features: Seq[js.Dynamic],
      key: js.Dynamic => Key,
      label: js.Dynamic => Label,
      wrapClass: js.Dynamic => WrapClass,
      place: js.Dynamic => Option[(PlaceHover.PlaceId, UiLabels.Text, PlaceHover.PlaceKind)],
      asOf: () => Option[Years.Astronomical],
      release: js.Dynamic => Unit = _ => ()
  ): Unit =
    generation += 1
    if map == null || !areasOn then features.foreach(release)
    else
      val mine = generation
      val known = mutable.LinkedHashMap.empty[js.Dynamic, LabelPlacement.Shape]
      val todo = mutable.Queue.empty[js.Dynamic]
      features.foreach { f =>
        cachedShape(f) match
          case Some(s) => s.foreach(known(f) = _); release(f)
          case None    => todo += f
      }
      def name(): Unit =
        applyAreas(features.flatMap(f => known.get(f).map(f -> _)), key, label, wrapClass, place, asOf)
      name()
      def slice(): Unit =
        if mine != generation then { todo.foreach(release); todo.clear() }
        else
          val t0 = now()
          while todo.nonEmpty && now() - t0 < SliceMs do
            val f = todo.dequeue()
            shapeOf(f).foreach(known(f) = _)
            release(f)
          timed("hd-label-shapes", t0)
          // named once all are placed: each naming is a diff of every area and a layout pass
          if todo.nonEmpty then { val _ = dom.window.setTimeout(() => slice(), 0) }
          else name()
      if todo.nonEmpty then slice()

  /** How long one slice of label placing may take before the page gets its turn (#1949). */
  private val SliceMs = 12.0

  // `var`: the newest [[syncAreas]] call; an older one's slices stop
  private var generation = 0

  /** The names of `named` (the areas whose place is known), the others' names gone (fading out). */
  private def applyAreas(
      named: Seq[(js.Dynamic, LabelPlacement.Shape)],
      key: js.Dynamic => Key,
      label: js.Dynamic => Label,
      wrapClass: js.Dynamic => WrapClass,
      place: js.Dynamic => Option[(PlaceHover.PlaceId, UiLabels.Text, PlaceHover.PlaceKind)],
      asOf: () => Option[Years.Astronomical]
  ): Unit =
    if map != null && areasOn then
      val keep = named.map((f, _) => AreaKey + key(f).value).toSet
      entries.filterInPlace((k, _) => !k.startsWith(AreaKey) || keep(k))
      state.filterInPlace((k, _) => !k.startsWith(AreaKey) || keep(k))
      areaMarkers.filterInPlace { (k, m) =>
        val stays = keep(k)
        if !stays then
          m.el.classList.add(FadingLayer.fadeOut.value.toString)
          dom.window.setTimeout(() => pane.remove(m), Layout.exitAnimMs.value)
        stays
      }
      // the names new to the map are made a slice at a time (a whole range's 1 600 at once was one long
      // task); the ones on the map already are moved at once
      val fresh = scala.collection.mutable.Queue.empty[(js.Dynamic, LabelPlacement.Shape)]
      val mine = generation
      named.foreach { (f, s) =>
        val k = AreaKey + key(f).value
        areaMarkers.get(k) match
          case Some(m) =>
            // a new outline moves the home; the name stays where it is drawn and glides from there
            if m.lat != s.lat || m.lon != s.lon then
              m.setLatLng(s.lat, s.lon)
              state.get(k).foreach(st => inner(m).foreach(el => render(st, el, s, math.pow(2, zoomNow()))))
            register(k, m, s)
          case None => fresh += ((f, s))
      }
      def make(): Unit =
        if mine == generation && areasOn then
          val t0 = now()
          while fresh.nonEmpty && now() - t0 < SliceMs do
            val (f, s) = fresh.dequeue()
            val k = AreaKey + key(f).value
            if !areaMarkers.contains(k) then
              val m = areaMarker(s, label(f), wrapClass(f))
              place(f).foreach((id, l, kind) => PlaceHover.bindName(m, id, l, kind, asOf))
              state.remove(k) // a new element: it fades in from nothing
              areaMarkers(k) = m
              pane.add(m)
              register(k, m, s)
          schedule()
          if fresh.nonEmpty then { val _ = dom.window.setTimeout(() => make(), 0) }
      make()
      schedule()

  /** A point-only label ([[PlaceGlyph.labelMarker]]) takes part in the pass while it is on the map. */
  def trackPoint(marker: DomMarker, key: Key): Unit =
    val k = PointKey + key.value
    marker.onAdded(() =>
      entries(k) = Entry(marker.at, Entry.Rank(-1.0), None, () => inner(marker))
      state.remove(k)
      schedule()
    )
    marker.onRemoved(() =>
      // a newer marker of the same key may have taken its place already
      entries.get(k).foreach(e => if e.el().forall(el => !el.isConnected) then { entries.remove(k); state.remove(k) })
    )

  /** An event's name ([[EventLayer]]) takes part in the pass while it is shown: `label` is its name element (what a
    * cull hides), `marker` its marker. Added hidden (`is-culled`, so it never flashes over another name); without a map
    * pass it is simply shown.
    */
  def trackEvent(key: Event.Id, marker: DomMarker, rank: Entry.Rank, label: () => Option[dom.HTMLElement]): Unit =
    if map == null then label().foreach(_.classList.remove(Culled))
    else
      entries(EventKey + key.value) = Entry(marker.at, rank, None, label, Some(() => Some(marker.el)))
      state.remove(EventKey + key.value)
      schedule()

  /** The event's name is gone from the map: the names it hid come back at the next pass. */
  def untrackEvent(key: Event.Id): Unit =
    state.remove(EventKey + key.value)
    if entries.remove(EventKey + key.value).isDefined then schedule()

  /** Every event's name is gone (playback stopped). */
  def untrackEvents(): Unit =
    val before = entries.size
    entries.filterInPlace((k, _) => !k.startsWith(EventKey))
    state.filterInPlace((k, _) => !k.startsWith(EventKey))
    if entries.size != before then schedule()

  private def register(k: String, m: DomMarker, s: LabelPlacement.Shape): Unit =
    entries(k) = Entry(m.at, Entry.Rank(s.size0), Some(s), () => inner(m), marker = Some(m))

  private def inner(m: DomMarker): Option[dom.HTMLElement] =
    Option(m.el.firstElementChild).map(_.asInstanceOf[dom.HTMLElement])

  private def areaMarker(s: LabelPlacement.Shape, label: Label, wrapClass: WrapClass): DomMarker =
    // hidden until the first pass has sized and placed it; it then fades and grows in
    new DomMarker(
      s.lat,
      s.lon,
      DomMarker.element(
        wrapClass.value,
        s"""<span class="hd-map-label is-culled" style="--o:0">${Fmt.esc(label.value)}</span>"""
      )
    )

  // ── shapes ─────────────────────────────────────────────────────────────────────────────────────

  /** The parts of an area feature's outline, and their cache key; `None` for anything but a polygon. */
  private def outline(f: js.Dynamic): Option[(String, js.Array[js.Dynamic])] =
    val g = f.geometry
    if js.isUndefined(g) || g == null then None
    else
      val t = g.selectDynamic("type").toString
      if t != "Polygon" && t != "MultiPolygon" then None
      else
        val parts =
          if t == "Polygon" then js.Array(g.coordinates)
          else g.coordinates.asInstanceOf[js.Array[js.Dynamic]]
        Some((signature(f, parts), parts))

  /** The label place of a feature when it is known without work: `Some(None)` for a feature that is not an area
    * (nothing to place), `Some(Some(_))` from the cache, `None` when it must be computed.
    */
  private def cachedShape(f: js.Dynamic): Option[Option[LabelPlacement.Shape]] =
    outline(f) match
      case None           => Some(None)
      case Some((sig, _)) => shapes.get(sig)

  /** The label place of an area feature, from the cache when its outline was seen before. */
  private def shapeOf(f: js.Dynamic): Option[LabelPlacement.Shape] =
    outline(f).flatMap { (sig, parts) =>
      shapes.get(sig) match
        case Some(s) => s
        case None    =>
          val s = LabelPlacement.shape(parts.toSeq.map(polyRings))
          shapes.put(sig, s)
          s
    }

  /** An outline's identity, cheaply: the feature's id, its parts, and each outer ring's length and first vertex — two
    * years of one polity with the same outline share it.
    */
  private def signature(f: js.Dynamic, parts: js.Array[js.Dynamic]): String =
    val id = Option(f.properties).filterNot(js.isUndefined).map(_.id.toString).getOrElse("")
    val sb = StringBuilder(id)
    parts.foreach { p =>
      val outer = p.asInstanceOf[js.Array[js.Array[js.Array[Double]]]](0)
      sb.append('|').append(outer.length)
      if outer.length > 0 then sb.append(':').append(outer(0)(0)).append(',').append(outer(0)(1))
    }
    sb.toString

  /** One polygon's rings as (lat, lon), thinned on the way in to at most [[LabelPlacement.MaxRingPoints]] each — the
    * label needs the shape's room, not its coastline.
    */
  private def polyRings(p: js.Dynamic): Seq[Seq[(Geo.Latitude, Geo.Longitude)]] =
    p.asInstanceOf[js.Array[js.Array[js.Array[Double]]]].toSeq.map { ring =>
      val step = math.max(1, math.ceil(ring.length.toDouble / LabelPlacement.MaxRingPoints).toInt)
      (0 until ring.length by step).map(i => (Geo.Latitude(ring(i)(1)), Geo.Longitude(ring(i)(0))))
    }

  // ── the pass ───────────────────────────────────────────────────────────────────────────────────

  /** Queue one pass for the next frame; any number of calls before it run it once. */
  def schedule(): Unit =
    if !queued && map != null then
      queued = true
      dom.window.requestAnimationFrame { _ => queued = false; run() }

  private val Culled = "is-culled"
  private val TierClasses = LabelPlacement.Tier.values.map(_.css)

  /** The map's zoom now (the raster grid's) — fractional in a zoom. */
  private def zoomNow(): Double = map.zoom.value

  /** The names snap instead of easing when the map does not animate (the reader's "Map motion", #4718). */
  private def reducedMotion(): Boolean = !MotionPref.animate()

  /** The settled viewport in the zoom-0 plane. */
  private def viewNow(zoom: Double): Rect =
    val scale = math.pow(2, zoom)
    val c = map.center
    val p = LabelPlacement.project(c.lat, c.lon)
    val (w, h) = (map.size._1.value, map.size._2.value)
    Rect(p.x - w / 2 / scale, p.y - h / 2 / scale, p.x + w / 2 / scale, p.y + h / 2 / scale)

  /** Size every name for the zoom (writes), measure them (one layout), place the area names exactly in the settled
    * viewport (pure), then draw the first frame of the result.
    */
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
    if busy || moving then dom.window.requestAnimationFrame(tick)
    else looping = false

  /** One frame: every name's target (cheap), the cull, the easing, and the writes of what changed. `true` while some
    * name is still on its way.
    */
  private def frame(t: Double, reduced: Boolean): Boolean =
    if map == null then false
    else
      val dt = math.max(0.0, math.min(0.1, (t - lastFrame) / 1000.0))
      lastFrame = t
      val z = zoomNow()
      val view = viewNow(z)
      val scale = math.pow(2, z)
      val far = PlaceGlyph.far(PlaceGlyph.ZoomLevel(math.round(z).toInt))
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
              st.target.map(p => LabelPlacement.Box(k, p.x * scale, p.y * scale, st.w * zs, st.h * zs, e.rank.value))
            case None =>
              if far && e.anchorEl.isEmpty then None
              else
                val p = LabelPlacement.project(e.latlng.lat, e.latlng.lon)
                Some(LabelPlacement.Box(k, p.x * scale + st.ox, p.y * scale + st.oy, st.w, st.h, e.rank.value))
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
              val inner = view.inset(
                (st.w * zs / 2 + LabelPlacement.EdgePadPx) / scale,
                (st.h * zs / 2 + LabelPlacement.EdgePadPx) / scale
              )
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
  private def render(
      st: Live,
      el: dom.HTMLElement,
      s: LabelPlacement.Shape,
      scale: Double,
      reduced: Boolean = false
  ): Unit =
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
