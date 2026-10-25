package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The clustering of points on screen, pure: which points are drawn as one bubble at a zoom. */
object Clustering:

  /** World pixels of a point at zoom `z` on the 256-pixel grid (Web Mercator). Pure. */
  def world(lat: Double, lon: Double, z: Double): (Double, Double) =
    val scale = 256.0 * math.pow(2, z)
    val l = math.max(-85.05112878, math.min(85.05112878, lat))
    val s = math.sin(math.toRadians(l))
    ((lon + 180) / 360 * scale, (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.Pi)) * scale)

  /** Greedy grouping, in the points' order (the rule markercluster had): a point joins the nearest
    * group whose FIRST point is within `radius` px, else starts one. Answers each point's group number
    * (0, 1, … in the order the groups were started). Pure; one pass with a grid of `radius` cells. */
  def group(xs: Array[Double], ys: Array[Double], radius: Double): Array[Int] =
    val n = xs.length
    val out = new Array[Int](n)
    if radius <= 0 then
      var i = 0
      while i < n do { out(i) = i; i += 1 }
      out
    else
      // cell → the groups seeded there; a JS map over a number key (cells are whole numbers well inside
      // 2^53: the grid of a world at zoom 20 is 2^28 / radius cells a side)
      val cells = js.Map.empty[Double, js.Array[Int]]
      val seedX = js.Array[Double]()
      val seedY = js.Array[Double]()
      def cell(cx: Double, cy: Double): Double = cx * 67108864.0 + cy
      val r2 = radius * radius
      var i = 0
      while i < n do
        val x = xs(i)
        val y = ys(i)
        val cx = math.floor(x / radius)
        val cy = math.floor(y / radius)
        var best = -1
        var bestD = Double.MaxValue
        var dx = -1.0
        while dx <= 1 do
          var dy = -1.0
          while dy <= 1 do
            val gs = cells.getOrElse(cell(cx + dx, cy + dy), null)
            if gs != null then
              var k = 0
              while k < gs.length do
                val g = gs(k)
                val ex = seedX(g) - x
                val ey = seedY(g) - y
                val d = ex * ex + ey * ey
                if d <= r2 && (d < bestD || (d == bestD && g < best)) then { best = g; bestD = d }
                k += 1
            dy += 1
          dx += 1
        if best < 0 then
          best = seedX.length
          seedX.push(x)
          seedY.push(y)
          val c = cell(cx, cy)
          val gs = cells.getOrElse(c, null)
          if gs == null then cells(c) = js.Array(best) else gs.push(best)
        out(i) = best
        i += 1
      out

  /** The groups of `pts` at zoom `z`: each group's keys (in the points' order) and where its bubble
    * stands — the mean of its points. Pure. */
  def groups(pts: Seq[(String, LatLon)], z: Int, radius: Double): Vector[(Vector[String], LatLon)] =
    val n = pts.length
    val arr = pts.toArray
    val xs = new Array[Double](n)
    val ys = new Array[Double](n)
    var i = 0
    while i < n do
      val (x, y) = world(arr(i)._2.lat, arr(i)._2.lon, z)
      xs(i) = x; ys(i) = y
      i += 1
    val g = group(xs, ys, radius)
    val count = if n == 0 then 0 else g.max + 1
    val keys = Array.fill(count)(js.Array[String]())
    val lat = new Array[Double](count)
    val lon = new Array[Double](count)
    i = 0
    while i < n do
      val k = g(i)
      keys(k).push(arr(i)._1)
      lat(k) += arr(i)._2.lat
      lon(k) += arr(i)._2.lon
      i += 1
    Vector.tabulate(count)(k => (keys(k).toVector, LatLon(lat(k) / keys(k).length, lon(k) / keys(k).length)))

  /** Is `at` on `s`'s screen, or within `margin` px of it (the copy of the world nearest the view)? */
  def inView(s: MapSurface, at: LatLon, margin: Double): Boolean =
    val (w, h) = s.size
    val c = s.center.lon
    val q = s.project(LatLon(at.lat, at.lon + 360 * math.round((c - at.lon) / 360).toDouble))
    q.x >= -margin && q.y >= -margin && q.x <= w + margin && q.y <= h + margin

  /** What a cluster layer draws of `groups` on `s`: the points drawn alone and the bubbles (their
    * points), those in view and a margin of a quarter of the view. Pure but for the projection. */
  def drawn(groups: Vector[(Vector[String], LatLon)], s: MapSurface): (Set[String], Map[String, Vector[String]]) =
    val (w, h) = s.size
    val margin = 0.25 * math.max(w, h)
    val shown = groups.filter((_, at) => inView(s, at, margin))
    (shown.collect { case (ks, _) if ks.size == 1 => ks.head }.toSet,
      shown.collect { case (ks, _) if ks.size > 1 => s"${ks.head}|${ks.size}" -> ks }.toMap)

  /** The zoom a map at `zoom` clusters at: the nearest whole zoom (the bubbles regroup as the map
    * passes the middle between two). Pure. */
  def level(zoom: Double): Int = math.round(zoom).toInt

/** A group of points drawn as ONE cluster layer: nearby points of every kind join one bubble
  * ([[Clustering]]), the rest are drawn alone; only what is in view (and a margin) has an element. The
  * kinds' look is the caller's (and their pane: `paneOf` a point's, `pane` the bubbles'): `single` draws a point (its marker kept while the point stays), `bubble`
  * a group (`refresh` draws every bubble again). Points are added and removed in batches; the layout
  * follows at the next frame, the zoom's at its end. A zoom that regroups the points animates it, as
  * markercluster did ([[ClusterMotion]]): a bubble that splits sends its points out from where it stood, and
  * points (or bubbles) that merge fly into the new bubble, which appears as they arrive — while at most
  * `animateMax` markers are drawn before and after it (the markers in view, not the points held: a wide
  * window holds thousands of events and draws a few hundred); above that, or when the map does not animate
  * ([[MotionPref]]), they are drawn at once. `repaint`
  * recolours a drawn bubble in place (its members' colours moved with the span; nothing else changed). */
final class PointSet[D](paneOf: D => MarkerPane, pane: () => MarkerPane, surface: () => MapSurface,
    key: D => String, at: D => LatLon, radius: () => Double,
    single: (D, Boolean) => DomMarker, bubble: Seq[D] => DomMarker,
    animateMax: Int, exitMs: Int, fadeOut: String,
    repaint: (DomMarker, Seq[D]) => Unit = (_: DomMarker, _: Seq[D]) => ()):

  private val items = mutable.LinkedHashMap.empty[String, D]
  // the drawn singles, by point key; the drawn bubbles, by their first point's key and size
  private val singles = mutable.HashMap.empty[String, (DomMarker, MarkerPane)]
  private val bubbles = mutable.HashMap.empty[String, (DomMarker, Seq[String])]
  // the groups at `level`: each one's points, and its place
  private var groups: Vector[(Vector[String], LatLon)] = Vector.empty
  private var groupOf: collection.Map[String, Int] = Map.empty
  private var level: Int = Int.MinValue
  private var queued = false
  // the newest drawing: an older one's slices still queued stop
  private var drawGeneration = 0
  /** How long one slice of marker making may take before the page gets a frame. */
  private val SliceMs = 6.0
  /** How far outside the view (px) a marker still counts as on screen: a box's half and a little more. */
  private val OnScreenMargin = 40.0
  private var installed = false
  // the view of a zoom whose markers on screen are being made, its moves not started yet
  private var awaitingMoves: Option[(LatLon, Double)] = None
  /** How many zooms have animated (the browser regressions read it through the diagnostics). */
  var animations: Int = 0

  def size: Int = items.size
  def keys: Iterable[String] = items.keys
  def get(k: String): Option[D] = items.get(k)

  private def install(): Unit =
    if !installed && surface() != null then
      installed = true
      surface().on(MapEventKind.ZoomEnd)(_ => layout(animate = true))
      surface().on(MapEventKind.MoveEnd)(_ => layout(animate = true))
      surface().on(MapEventKind.Resize)(_ => schedule())

  def add(ds: Iterable[D]): Unit =
    ds.foreach(d => items(key(d)) = d)
    if ds.nonEmpty then schedule()

  /** A point whose markup changed: it is drawn again (a colour alone is the caller's to set in place). */
  def replace(d: D): Unit =
    val k = key(d)
    items(k) = d
    singles.remove(k).foreach((m, p) => p.remove(m))
    schedule()

  /** Take these points out: one drawn alone fades out where it stood. */
  def remove(ks: Iterable[String]): Unit =
    var any = false
    ks.foreach { k =>
      if items.remove(k).isDefined then any = true
      singles.remove(k).foreach(fadeAway)
    }
    if any then schedule()

  /** Every bubble drawn again (what is in them changed). */
  def refresh(): Unit =
    bubbles.values.foreach((m, _) => pane().remove(m))
    bubbles.clear()
    schedule()

  /** Every drawn bubble recoloured in place (only its members' colours moved with the span): no element
    * is made again — a playback frame recolours every bubble, and re-making them was ~100 elements a frame. */
  def recolour(): Unit =
    bubbles.values.foreach((m, ks) => if ks.forall(items.contains) then repaint(m, ks.map(items)))

  /** The point `k`'s own marker, while it is drawn alone. */
  def drawnAlone(k: String): Option[DomMarker] = singles.get(k).filter((m, p) => p.has(m)).map(_._1)

  /** The group `k` is in at this zoom (itself alone: one key). */
  def groupKeys(k: String): Seq[String] = groupOf.get(k).fold(Seq(k))(g => groups(g)._1)

  /** Zoom (and pan) until `k` stands alone, then `andThen`. */
  def zoomToShow(k: String, andThen: () => Unit): Unit =
    val s = surface()
    items.get(k).foreach { d =>
      def done(): Boolean = drawnAlone(k).isDefined
      layout(animate = false)
      if done() then andThen()
      else
        val p = at(d)
        // `var`: the subscription of the zoom's end, taken off when it is reached
        var sub: Subscription = null
        sub = s.on(MapEventKind.MoveEnd) { _ =>
          layout(animate = false)
          if done() || s.zoom >= s.maxZoom - 0.01 then
            sub.cancel()
            if done() then andThen()
        }
        s.setView(p, math.min(s.maxZoom, math.max(s.zoom + 1, level + 2)), animate = true)
    }

  private def fadeAway(mp: (DomMarker, MarkerPane)): Unit =
    val (m, p) = mp
    if p.has(m) then
      m.el.classList.add(fadeOut)
      dom.window.setTimeout(() => p.remove(m), exitMs)

  /** Lay out at the next frame: any number of changes before it, one layout — after a zoom's moves have
    * started (a view's points reloaded on the zoom's end answer within its few frames of making markers,
    * and drawing them then took the zoom's moves away). */
  def schedule(): Unit =
    install()
    if !queued then
      queued = true
      dom.window.requestAnimationFrame(_ => scheduled())

  private def scheduled(): Unit =
    if awaitingMoves.isDefined then { val _ = dom.window.requestAnimationFrame(_ => scheduled()) }
    else
      queued = false
      layout(animate = false)

  /** The groups at the map's zoom (again only when the zoom or the points changed), and the elements
    * of those in view. */
  def layout(animate: Boolean): Unit =
    val s = surface()
    if s != null then
      val z = Clustering.level(s.zoom)
      val changed = z != level || groupOf.size != items.size || items.keysIterator.exists(k => !groupOf.contains(k))
      val regrouped = changed && z != level
      val t0 = dom.window.performance.now()
      if changed then regroup(z)
      val t1 = dom.window.performance.now()
      // a zoom's moves wait for its markers on screen to be made: the move's end that follows the zoom's,
      // at the same view, leaves them to it
      if changed || !awaitingMoves.contains((s.center, s.zoom)) then
        val wanted = Clustering.drawn(groups, s)
        val before =
          if regrouped && animate && ClusterMotion.within(drawnMarkers, wanted._1.size + wanted._2.size, animateMax) && MotionPref.animate() then Some(drawnPlaces())
          else None
        draw(wanted, before)
      measure("hd-points-group", t0, t1)
      measure("hd-points-draw", t1, dom.window.performance.now())

  /** What a layout cost, for the browser's performance panel (the latest of each). */
  private def measure(name: String, t0: Double, t1: Double): Unit =
    val p = dom.window.performance.asInstanceOf[js.Dynamic]
    if js.typeOf(p.measure) == "function" then
      p.clearMeasures(name)
      p.measure(name, js.Dynamic.literal(start = t0, end = t1))

  /** How many markers (points alone and bubbles) are drawn now. */
  private def drawnMarkers: Int =
    singles.valuesIterator.count((m, p) => p.has(m)) + bubbles.valuesIterator.count((m, _) => pane().has(m))

  /** Where each point is drawn now (alone or in its bubble), screen pixels. */
  private def drawnPlaces(): Map[String, (Double, Double)] =
    val m = mutable.HashMap.empty[String, (Double, Double)]
    singles.foreach((k, mp) => if mp._2.has(mp._1) then m(k) = mp._1.screen)
    bubbles.values.foreach((mk, ks) => if pane().has(mk) then ks.foreach(k => m(k) = mk.screen))
    m.toMap

  private def regroup(z: Int): Unit =
    level = z
    groups = Clustering.groups(items.toSeq.map((k, d) => k -> at(d)), z, radius())
    val m = mutable.HashMap.empty[String, Int]
    var i = 0
    while i < groups.length do
      groups(i)._1.foreach(k => m(k) = i)
      i += 1
    groupOf = m

  private def draw(wanted: (Set[String], Map[String, Vector[String]]), before: Option[Map[String, (Double, Double)]]): Unit =
    val s = surface()
    val p = pane()
    val (wantSingles, wantBubbles) = wanted
    // what goes: at once, or (a zoom that animates) kept until it is known whether it merges into a bubble
    val gone = mutable.ListBuffer.empty[(DomMarker, MarkerPane, Seq[String])]
    def leave(m: DomMarker, mp: MarkerPane, ks: Seq[String]): Unit =
      if before.isDefined && mp.has(m) then gone += ((m, mp, ks)) else mp.remove(m)
    singles.filterInPlace { (k, mp) =>
      val keep = wantSingles(k) && items.contains(k)
      if !keep then leave(mp._1, mp._2, Seq(k))
      keep
    }
    bubbles.filterInPlace { (id, mk) =>
      val keep = wantBubbles.get(id).contains(mk._2)
      if !keep then leave(mk._1, p, mk._2)
      keep
    }
    val fresh = mutable.ListBuffer.empty[(DomMarker, Seq[String])]
    // what comes: a slice at a time, nearest the view's centre first (a zoom over the events layer's thousands
    // made a few hundred markers in one long task). A zoom that animates makes the markers on screen first,
    // hidden, and starts every move once they are all made, a few frames later (making them all in the zoom's
    // last frame held that frame long in a wide window); those in the margin around the view follow
    val fadeIn = before.isEmpty && singles.nonEmpty
    val todo = (wantSingles.iterator.filterNot(singles.contains).map(k => (k, Vector(k), true)) ++
      wantBubbles.iterator.filterNot((id, _) => bubbles.contains(id)).map((id, ks) => (id, ks, false))).toVector
    val (w, h) = s.size
    val c = ScreenPt(w / 2, h / 2)
    def dist(ks: Vector[String]): Double =
      val q = s.project(at(items(ks.head)))
      math.hypot(q.x - c.x, q.y - c.y)
    def onScreen(ks: Vector[String]): Boolean =
      val pts = ks.flatMap(items.get).map(at)
      pts.nonEmpty && Clustering.inView(s, LatLon(pts.map(_.lat).sum / pts.size, pts.map(_.lon).sum / pts.size), OnScreenMargin)
    def nearestFirst(v: Vector[(String, Vector[String], Boolean)]) = if v.size > 32 then v.sortBy(t => dist(t._2)) else v
    val (first, later) = if before.isDefined then todo.partition(t => onScreen(t._2)) else (Vector.empty, todo)
    val queue = mutable.Queue.from(nearestFirst(first) ++ nearestFirst(later))
    // `var`s: the markers on screen still to make before the moves start, and whether they have started
    var firstLeft = first.size
    var started = false
    def make(one: (String, Vector[String], Boolean)): Unit =
      val (id, ks, alone) = one
      val made =
        if alone then
          if !singles.contains(id) && items.contains(id) then
            val m = single(items(id), fadeIn)
            val sp = paneOf(items(id))
            singles(id) = (m, sp)
            sp.add(m)
            Some(m)
          else None
        else if !bubbles.contains(id) && ks.forall(items.contains) then
          val m = bubble(ks.map(items))
          bubbles(id) = (m, ks)
          p.add(m)
          Some(m)
        else None
      made.foreach { m =>
        if !started then m.el.classList.add(ClusterMotion.Pending)
        fresh += ((m, ks))
      }
    val view = (s.center, s.zoom)
    awaitingMoves = Option.when(before.isDefined)(view)
    def start(): Unit =
      started = true
      awaitingMoves = None
      fresh.foreach((m, _) => m.el.classList.remove(ClusterMotion.Pending))
      before match
        case Some(was) if s.center == view._1 && s.zoom == view._2 =>
          val t0 = dom.window.performance.now()
          animate(was, fresh.toList, gone.toList)
          measure("hd-points-moves", t0, dom.window.performance.now())
        case _ => gone.foreach((m, mp, _) => mp.remove(m))
    val mine = { drawGeneration += 1; drawGeneration }
    def slice(): Unit =
      if mine == drawGeneration then
        val t0 = dom.window.performance.now()
        if !started && firstLeft == 0 then start()
        while queue.nonEmpty && dom.window.performance.now() - t0 < SliceMs do
          make(queue.dequeue())
          if !started then
            firstLeft -= 1
            if firstLeft == 0 then start()
        if queue.nonEmpty then { val _ = dom.window.requestAnimationFrame(_ => slice()) }
      else if !started then
        // a newer drawing took over before the moves started: what was leaving goes, what was made shows
        started = true
        fresh.foreach((m, _) => m.el.classList.remove(ClusterMotion.Pending))
        gone.foreach((m, mp, _) => mp.remove(m))
    slice()

  /** A zoom's moves ([[ClusterMotion]]), once its markers on screen are made: a marker made of ONE former
    * place comes out of it (a split); one made of several stays where it is and appears as the markers that
    * merge into it arrive. */
  private def animate(was: Map[String, (Double, Double)], fresh: List[(DomMarker, Seq[String])],
      gone: List[(DomMarker, MarkerPane, Seq[String])]): Unit =
    animations += 1
    val drawnOf = mutable.HashMap.empty[String, (DomMarker, Seq[String])]
    singles.foreach((k, mp) => drawnOf(k) = (mp._1, Seq(k)))
    bubbles.values.foreach((m, ks) => ks.foreach(k => drawnOf(k) = (m, ks)))
    val made = js.Set[DomMarker]()
    fresh.foreach((m, _) => made.add(m))
    val arriving = mutable.LinkedHashSet.empty[DomMarker]
    val slides = mutable.ListBuffer.empty[ClusterMotion.Slide]
    fresh.foreach { (m, ks) =>
      ClusterMotion.origin(ks.flatMap(was.get)) match
        case Some(from) if !from._1.isNaN && (from._1 != m.screen._1 || from._2 != m.screen._2) =>
          slides += ClusterMotion.Slide(m, from, m.screen)
        case Some(_) => ()
        case None    => if ks.exists(was.contains) then arriving += m
    }
    val (w, h) = surface().size
    def seen(at: (Double, Double)): Boolean =
      at._1 >= -OnScreenMargin && at._2 >= -OnScreenMargin && at._1 <= w + OnScreenMargin && at._2 <= h + OnScreenMargin
    // a marker merging off screen into a bubble off screen goes at once: nobody sees it fly
    gone.foreach { (m, mp, ks) =>
      ks.headOption.flatMap(drawnOf.get) match
        case Some((target, tks)) if ClusterMotion.merges(ks, tks) && !m.screen._1.isNaN && (seen(m.screen) || seen(target.screen)) =>
          mp.retire(m, ClusterMotion.Ms)
          slides += ClusterMotion.Slide(m, m.screen, target.screen)
          if !made.contains(target) then arriving -= target
        case _ => mp.remove(m)
    }
    ClusterMotion.slideAll(slides.toList)
    arriving.foreach(ClusterMotion.arrive)

/** How the cluster layer moves on a zoom that regroups it (#3764: the raster map's cluster plugin animated it, the
  * MapLibre release drew the new groups in one step). A split slides each new marker out from where its
  * bubble stood; a merge slides the markers that join a bubble into it while the bubble fades in — about a
  * quarter of a second, the stylesheet's `.hd-cluster-move`/`.hd-cluster-arrive`. When the map does not
  * animate ([[MotionPref]]: the reader's "Map motion", by default the system's reduced-motion setting) nothing
  * moves: the new groups are drawn at once. */
object ClusterMotion:
  /** How long a move takes (the stylesheet's animation), ms. */
  val Ms = 250
  /** A marker made for a zoom's moves before they start: drawn hidden until then. */
  val Pending = "hd-cluster-pending"

  /** Pure: where a marker made of points last drawn at `places` comes from — the one place they all
    * stood at (a split, or the same group), or none when they came from several (a merge) or nowhere. */
  def origin(places: Seq[(Double, Double)]): Option[(Double, Double)] =
    places.distinct match
      case Seq(one) => Some(one)
      case _        => None

  /** Pure: does a marker of the points `ks` merge into the marker of `target` (a group that holds more
    * than it did)? A split's old bubble does not: its points come out of it instead. */
  def merges(ks: Seq[String], target: Seq[String]): Boolean =
    target.size > ks.size && ks.forall(target.contains)

  /** A marker's move from one screen point to another. */
  final case class Slide(marker: DomMarker, from: (Double, Double), to: (Double, Double))

  /** Pure: does a zoom from `drawnBefore` markers to `drawnAfter` animate — at most `max` on either side?
    * What an animation costs is the markers drawn and moved on screen, not the points the layer holds: a wide
    * window's view holds thousands of events, of which a few hundred are drawn. */
  def within(drawnBefore: Int, drawnAfter: Int, max: Int): Boolean =
    drawnBefore <= max && drawnAfter <= max

  /** Move every marker of `slides` from its `from` to its `to` over [[Ms]]: the stylesheet animates the offset
    * between them (`--move-x`/`--move-y`) on top of the marker's place — a marker made at its new place comes out of
    * the old one, a ghost (still at its old place) flies to the new one. Nothing is read back from the page: a
    * transition needs each marker's start laid out before its move, and reading that layout between one
    * marker's writes and the next's lays the whole page out once per marker. */
  def slideAll(slides: List[Slide]): Unit =
    slides.foreach { sl =>
      val ghost = sl.marker.el.classList.contains("hd-cluster-ghost")
      val (dx, dy) = if ghost then (sl.to._1 - sl.from._1, sl.to._2 - sl.from._2) else (sl.from._1 - sl.to._1, sl.from._2 - sl.to._2)
      sl.marker.el.style.setProperty("--move-x", s"${dx}px")
      sl.marker.el.style.setProperty("--move-y", s"${dy}px")
      sl.marker.el.classList.add("hd-cluster-move")
    }
    if slides.nonEmpty then
      dom.window.setTimeout(() => slides.foreach(_.marker.el.classList.remove("hd-cluster-move")), Ms + 70)

  /** `m` appears as what merges into it arrives. */
  def arrive(m: DomMarker): Unit =
    m.el.classList.add("hd-cluster-arrive")
    dom.window.setTimeout(() => m.el.classList.remove("hd-cluster-arrive"), Ms + 70)
