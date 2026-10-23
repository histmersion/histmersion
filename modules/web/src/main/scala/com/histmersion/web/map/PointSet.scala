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
  * follows at the next frame, the zoom's at its end. A zoom that regroups up to `animateMax` points
  * moves the points out of (or into) the bubbles they were in; above that, they are drawn at once. */
final class PointSet[D](paneOf: D => MarkerPane, pane: () => MarkerPane, surface: () => MapSurface,
    key: D => String, at: D => LatLon, radius: () => Double,
    single: (D, Boolean) => DomMarker, bubble: Seq[D] => DomMarker,
    animateMax: Int, exitMs: Int, fadeOut: String):

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
  private var installed = false
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

  /** Every bubble drawn again (what is in them changed colour). */
  def refresh(): Unit =
    bubbles.values.foreach((m, _) => pane().remove(m))
    bubbles.clear()
    schedule()

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

  /** Lay out at the next frame: any number of changes before it, one layout. */
  def schedule(): Unit =
    install()
    if !queued then
      queued = true
      dom.window.requestAnimationFrame(_ => { queued = false; layout(animate = false) })

  /** The groups at the map's zoom (again only when the zoom or the points changed), and the elements
    * of those in view. */
  def layout(animate: Boolean): Unit =
    val s = surface()
    if s != null then
      val z = Clustering.level(s.zoom)
      val changed = z != level || groupOf.size != items.size || items.keysIterator.exists(k => !groupOf.contains(k))
      val before = if changed && animate && z != level && items.size <= animateMax then Some(drawnPlaces()) else None
      val t0 = dom.window.performance.now()
      if changed then regroup(z)
      val t1 = dom.window.performance.now()
      draw(before)
      measure("hd-points-group", t0, t1)
      measure("hd-points-draw", t1, dom.window.performance.now())

  /** What a layout cost, for the browser's performance panel (the latest of each). */
  private def measure(name: String, t0: Double, t1: Double): Unit =
    val p = dom.window.performance.asInstanceOf[js.Dynamic]
    if js.typeOf(p.measure) == "function" then
      p.clearMeasures(name)
      p.measure(name, js.Dynamic.literal(start = t0, end = t1))

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

  private def draw(before: Option[Map[String, (Double, Double)]]): Unit =
    val s = surface()
    val p = pane()
    val (wantSingles, wantBubbles) = Clustering.drawn(groups, s)
    // what goes
    singles.filterInPlace { (k, mp) =>
      val keep = wantSingles(k) && items.contains(k)
      if !keep then mp._2.remove(mp._1)
      keep
    }
    bubbles.filterInPlace { (id, mk) =>
      val keep = wantBubbles.get(id).contains(mk._2)
      if !keep then p.remove(mk._1)
      keep
    }
    val fresh = mutable.ListBuffer.empty[(DomMarker, Seq[String])]
    // what comes: nearest the view's centre first; among more than `animateMax` points a slice at a time
    // (a zoom over the events layer's thousands made a few hundred markers in one long task)
    val fadeIn = before.isEmpty && singles.nonEmpty
    val todo = (wantSingles.iterator.filterNot(singles.contains).map(k => (k, Vector(k), true)) ++
      wantBubbles.iterator.filterNot((id, _) => bubbles.contains(id)).map((id, ks) => (id, ks, false))).toVector
    val (w, h) = s.size
    val c = ScreenPt(w / 2, h / 2)
    def dist(ks: Vector[String]): Double =
      val q = s.project(at(items(ks.head)))
      math.hypot(q.x - c.x, q.y - c.y)
    val queue = mutable.Queue.from(if todo.size > 32 then todo.sortBy(t => dist(t._2)) else todo)
    def make(one: (String, Vector[String], Boolean)): Unit =
      val (id, ks, alone) = one
      if alone then
        if !singles.contains(id) && items.contains(id) then
          val m = single(items(id), fadeIn)
          val sp = paneOf(items(id))
          singles(id) = (m, sp)
          sp.add(m)
          fresh += ((m, ks))
      else if !bubbles.contains(id) && ks.forall(items.contains) then
        val m = bubble(ks.map(items))
        bubbles(id) = (m, ks)
        p.add(m)
        fresh += ((m, ks))
    val sliced = items.size > animateMax
    val mine = { drawGeneration += 1; drawGeneration }
    def slice(): Unit =
      if mine == drawGeneration then
        val t0 = dom.window.performance.now()
        while queue.nonEmpty && (!sliced || dom.window.performance.now() - t0 < SliceMs) do make(queue.dequeue())
        if queue.nonEmpty then { val _ = dom.window.requestAnimationFrame(_ => slice()) }
    slice()
    // a zoom that regrouped a few points: each comes from where it was drawn before
    before.foreach { was =>
      val moves = fresh.flatMap((m, ks) => ks.iterator.flatMap(was.get).nextOption().map(m -> _))
        .filter((m, from) => !from._1.isNaN && (from._1 != m.screen._1 || from._2 != m.screen._2))
      // a zoom that regrouped a few points animates (whether or not a point moved on screen)
      animations += 1
      if moves.nonEmpty then
        moves.foreach { (m, from) =>
          val to = m.screen
          m.moveTo(from._1, from._2)
          val _ = m.el.offsetWidth   // the start is laid out before the move
          m.el.classList.add("hd-cluster-move")
          m.moveTo(to._1, to._2)
          dom.window.setTimeout(() => m.el.classList.remove("hd-cluster-move"), 320)
        }
    }
