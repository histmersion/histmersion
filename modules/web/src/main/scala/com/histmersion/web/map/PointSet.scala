package com.histmersion
package web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** A group of points drawn as ONE cluster layer: nearby points of every kind join one bubble ([[Clustering]]), the rest
  * are drawn alone; only what is in view (and a margin) has an element. The kinds' look is the caller's (and their
  * pane: `paneOf` a point's, `pane` the bubbles'): `single` draws a point (its marker kept while the point stays),
  * `bubble` a group (`refresh` draws every bubble again). Points are added and removed in batches; the layout follows
  * at the next frame, the zoom's at its end. A zoom that regroups the points animates it, as markercluster did
  * ([[ClusterMotion]]): a bubble that splits sends its points out from where it stood, and points (or bubbles) that
  * merge fly into the new bubble, which appears as they arrive — while at most `animateMax` markers are drawn before
  * and after it (the markers in view, not the points held: a wide window holds thousands of events and draws a few
  * hundred); above that, or when the map does not animate ([[MotionPref]]), they are drawn at once. `repaint` recolours
  * a drawn bubble in place (its members' colours moved with the span; nothing else changed).
  */
final class PointSet[D](
    paneOf: D => MarkerPane,
    pane: () => MarkerPane,
    surface: () => MapSurface,
    key: D => PointSet.Key,
    at: D => LatLon,
    radius: () => Clustering.GroupRadius,
    single: (D, PointSet.FadeIn) => DomMarker,
    bubble: Seq[D] => DomMarker,
    animateMax: PointSet.AnimateMax,
    exitMs: PointSet.ExitMs,
    fadeOut: PointSet.FadeOut,
    repaint: (DomMarker, Seq[D]) => Unit = (_: DomMarker, _: Seq[D]) => ()
):

  private val items = mutable.LinkedHashMap.empty[PointSet.Key, D]
  // the drawn singles, by point key; the drawn bubbles, by their first point's key and size
  private val singles = mutable.HashMap.empty[PointSet.Key, (DomMarker, MarkerPane)]
  private val bubbles = mutable.HashMap.empty[Clustering.GroupLabel, (DomMarker, Seq[PointSet.Key])]
  // the groups at `level`: each one's points, and its place
  private var groups: Vector[(Vector[PointSet.Key], LatLon)] = Vector.empty
  private var groupOf: collection.Map[PointSet.Key, Int] = Map.empty
  private var level: Clustering.ZoomLevel = Clustering.ZoomLevel(Int.MinValue)
  private var queued = false
  // the newest drawing: an older one's slices still queued stop
  private var drawGeneration = 0

  /** How long one slice of marker making may take before the page gets a frame. */
  private val SliceMs = 6.0

  /** How far outside the view (px) a marker still counts as on screen: a box's half and a little more. */
  private val OnScreenMargin = Clustering.InViewMargin(40.0)
  private var installed = false
  // the view of a zoom whose markers on screen are being made, its moves not started yet
  private var awaitingMoves: Option[(LatLon, MapSurface.Zoom)] = None
  private var movesFrame = false

  /** How many zooms have animated (the browser regressions read it through the diagnostics). */
  var animations: PointSet.Animations = PointSet.Animations(0)

  def size: PointSet.PointCount = PointSet.PointCount(items.size)
  def keys: Iterable[PointSet.Key] = items.keys
  def get(k: PointSet.Key): Option[D] = items.get(k)

  private def install(): Unit =
    if !installed && surface() != null then
      installed = true
      surface().on(MapEventKind.ZoomEnd)(_ => layout(animate = true))
      surface().on(MapEventKind.MoveEnd)(_ => layout(animate = true))
      surface().on(MapEventKind.Resize)(_ => schedule())
      // a fanned-out bubble folds back when the map zooms, on Escape, and on a press anywhere else on the map
      // (not on its own markers, nor in a popup one of them opened)
      surface().on(MapEventKind.ZoomStart)(_ => unspiderfy())
      dom.document.addEventListener(
        "keydown",
        (e: dom.KeyboardEvent) =>
          if e.key == "Escape" && spider.isDefined && !e.defaultPrevented then
            e.preventDefault()
            unspiderfy()
      )
      dom.document.addEventListener(
        "pointerdown",
        (e: dom.PointerEvent) =>
          if spider.isDefined then
            val t = e.target.asInstanceOf[dom.Element]
            val onMap = t != null && surface().container.contains(t)
            val drawn = TheMap
              .drawnMarkerElAt(e.clientX, e.clientY)
              .exists(el => el.classList.contains(Spiderfy.Member) || el.classList.contains(Spiderfy.Fanned))
            if onMap && !drawn && t.closest(s".${Spiderfy.Member}, .${Spiderfy.Fanned}, .hd-popup") == null then
              unspiderfy(),
        true
      )

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
  def remove(ks: Iterable[PointSet.Key]): Unit =
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

  /** Every drawn bubble recoloured in place (only its members' colours moved with the span): no element is made again —
    * a playback frame recolours every bubble, and re-making them was ~100 elements a frame.
    */
  def recolour(): Unit =
    bubbles.values.foreach((m, ks) => if ks.forall(items.contains) then repaint(m, ks.map(items)))

  /** The point `k`'s own marker, while it is drawn alone (or fanned out of its bubble). */
  def drawnAlone(k: PointSet.Key): Option[DomMarker] =
    singles
      .get(k)
      .filter((m, p) => p.has(m))
      .map(_._1)
      .orElse(spider.flatMap(_.members.find(_._1 == k)).map(_._2).filter((m, p) => p.has(m)).map(_._1))

  // ── a bubble fanned out (spiderfied): its points drawn around it, each on a thin leg to its spot ───

  /** A fanned-out bubble: its points, their markers and panes, the legs, the bubble itself. */
  final private class Spider(
      val keys: Seq[PointSet.Key],
      val members: Seq[(PointSet.Key, (DomMarker, MarkerPane))],
      val legs: DomMarker,
      val bubble: Option[DomMarker]
  )

  private var spider: Option[Spider] = None

  /** The points fanned out now (none: no bubble is). */
  def spidered: Seq[PointSet.Key] = spider.fold(Seq.empty)(_.keys)

  /** Fan the points `ks` out around where their bubble stands ([[Spiderfy]]), each drawn as it is alone, a thin leg
    * back to the spot — the only way to reach points no zoom can part (one spot, or the deepest zoom). Anything else
    * fanned out folds back first. `separation`: the markers' spacing, px.
    */
  def spiderfy(ks: Seq[PointSet.Key], separation: Spiderfy.Separation): Unit =
    unspiderfy()
    install()
    val ds = ks.filter(items.contains)
    if ds.size >= 2 && surface() != null then
      val pts = ds.map(k => at(items(k)))
      val c = LatLon(LatLon.Lat(pts.map(_.lat.value).sum / pts.size), LatLon.Lon(pts.map(_.lon.value).sum / pts.size))
      val offsets = Spiderfy.offsets(Spiderfy.Count(ds.size), separation)
      val reach = offsets.map((x, y) => math.max(math.abs(x.value), math.abs(y.value))).maxOption.getOrElse(0.0)
      val side = (2 * math.ceil(reach) + 4).toInt
      val legsEl = DomMarker.element(Spiderfy.Legs, Spiderfy.legsSvg(offsets, Spiderfy.Side(side)))
      val legs = new DomMarker(c.lat, c.lon, legsEl, Some((DomMarker.BoxSize(side), DomMarker.BoxSize(side))))
      pane().add(legs)
      val bubble = bubbles.get(Clustering.GroupLabel(s"${ks.head}|${ks.size}")).filter(_._2 == ks).map(_._1)
      bubble.foreach(_.el.classList.add(Spiderfy.Fanned))
      val members = ds.zip(offsets).map { (k, off) =>
        val m = single(items(k), PointSet.FadeIn(false))
        m.setLatLng(c.lat, c.lon)
        m.el.classList.add(Spiderfy.Member)
        m.el.style.setProperty("--sx", s"${off._1.value}px")
        m.el.style.setProperty("--sy", s"${off._2.value}px")
        m.setZ(DomMarker.ZIndex(Spiderfy.Z))
        val p = paneOf(items(k))
        p.add(m)
        k -> (m, p)
      }
      spider = Some(Spider(ds, members, legs, bubble))

  /** Fold a fanned-out bubble back. */
  def unspiderfy(): Unit =
    spider.foreach { sp =>
      sp.members.foreach((_, mp) => mp._2.remove(mp._1))
      pane().remove(sp.legs)
      sp.bubble.foreach(_.el.classList.remove(Spiderfy.Fanned))
    }
    spider = None

  /** The group `k` is in at this zoom (itself alone: one key). */
  def groupKeys(k: PointSet.Key): Seq[PointSet.Key] = groupOf.get(k).fold(Seq(k))(g => groups(g)._1)

  /** Zoom (and pan) until `k` stands alone, then `andThen` — or, where no zoom parts it from its bubble (points at its
    * very spot, or the deepest zoom reached), `andThen` where it is: it was left undone there, and a picture at a spot
    * it shares never opened.
    */
  def zoomToShow(k: PointSet.Key, andThen: () => Unit): Unit =
    val s = surface()
    items.get(k).foreach { d =>
      def done(): Boolean = drawnAlone(k).isDefined
      def deepest: Boolean = s.zoom.value >= s.maxZoom.value - 0.01
      def inseparable: Boolean = Clustering.onePlace(groupKeys(k).flatMap(items.get).map(at))
      layout(animate = false, shared = false)
      if done() || deepest || inseparable then andThen()
      else
        val p = at(d)
        // `var`: the subscription of the zoom's end, taken off when it is reached
        var sub: Subscription = null
        sub = s.on(MapEventKind.MoveEnd) { _ =>
          layout(animate = false, shared = false)
          if done() || deepest || inseparable then
            sub.cancel()
            andThen()
        }
        s.setView(
          p,
          MapSurface.Zoom(math.min(s.maxZoom.value, math.max(s.zoom.value + 1, level.value + 2))),
          animate = MapSurface.SetViewAnimate(true)
        )
    }

  private def fadeAway(mp: (DomMarker, MarkerPane)): Unit =
    val (m, p) = mp
    if p.has(m) then
      m.el.classList.add(fadeOut.value)
      dom.window.setTimeout(() => p.remove(m), exitMs.value.toDouble)

  /** Lay out at the next frame: any number of changes before it, one layout — after a zoom's moves have started (a
    * view's points reloaded on the zoom's end answer within its few frames of making markers, and drawing them then
    * took the zoom's moves away).
    */
  def schedule(): Unit =
    install()
    if !queued then
      queued = true
      dom.window.requestAnimationFrame(_ => scheduled())

  private def scheduled(): Unit =
    if awaitingMoves.isDefined || movesFrame then { val _ = dom.window.requestAnimationFrame(_ => scheduled()) }
    else
      queued = false
      layout(animate = false)

  /** The groups at the map's zoom (again only when the zoom or the points changed), and the elements of those in view.
    */
  private def layout(animate: Boolean, shared: Boolean = true): Unit =
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
          if regrouped && animate && ClusterMotion.within(
              drawnMarkers,
              wanted._1.size + wanted._2.size,
              animateMax.value
            ) && MotionPref.animate()
          then Some(drawnPlaces())
          else None
        draw(wanted, before, if shared then t0 else dom.window.performance.now())
        // a fanned-out bubble regrouped or gone, or one of its points taken away: folded back
        spider.foreach { sp =>
          val still = sp.keys.forall(items.contains) && sp.bubble.forall(b => bubbles.values.exists(_._1 eq b))
          if !still then unspiderfy()
        }
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
  private def drawnPlaces(): Map[PointSet.Key, (ScreenPt.X, ScreenPt.Y)] =
    val m = mutable.HashMap.empty[PointSet.Key, (ScreenPt.X, ScreenPt.Y)]
    singles.foreach((k, mp) => if mp._2.has(mp._1) then m(k) = mp._1.screen)
    bubbles.values.foreach((mk, ks) => if pane().has(mk) then ks.foreach(k => m(k) = mk.screen))
    m.toMap

  private def regroup(z: Clustering.ZoomLevel): Unit =
    level = z
    groups = Clustering.groups(items.toSeq.map((k, d) => k -> at(d)), z, radius())
    val m = mutable.HashMap.empty[PointSet.Key, Int]
    var i = 0
    while i < groups.length do
      groups(i)._1.foreach(k => m(k) = i)
      i += 1
    groupOf = m

  private def draw(
      wanted: (Set[PointSet.Key], Map[Clustering.GroupLabel, Vector[PointSet.Key]]),
      before: Option[Map[PointSet.Key, (ScreenPt.X, ScreenPt.Y)]],
      began: Double
  ): Unit =
    val s = surface()
    val p = pane()
    val (wantSingles, wantBubbles) = wanted
    // what goes: at once, or (a zoom that animates) kept until it is known whether it merges into a bubble
    val gone = mutable.ListBuffer.empty[(DomMarker, MarkerPane, Seq[PointSet.Key])]
    def leave(m: DomMarker, mp: MarkerPane, ks: Seq[PointSet.Key]): Unit =
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
    val fresh = mutable.ListBuffer.empty[(DomMarker, Seq[PointSet.Key])]
    // what comes: a slice at a time, nearest the view's centre first (a zoom over the events layer's thousands
    // made a few hundred markers in one long task). A zoom that animates makes the markers on screen first,
    // hidden, and starts every move once they are all made, a few frames later (making them all in the zoom's
    // last frame held that frame long in a wide window); those in the margin around the view follow
    val fadeIn = before.isEmpty && singles.nonEmpty
    type Drawn = Either[PointSet.Key, Clustering.GroupLabel]
    type Todo = (Drawn, Vector[PointSet.Key])
    val todo: Vector[Todo] = (wantSingles.iterator.filterNot(singles.contains).map(k => (Left(k): Drawn, Vector(k))) ++
      wantBubbles.iterator.filterNot((id, _) => bubbles.contains(id)).map((id, ks) => (Right(id): Drawn, ks))).toVector
    val (w, h) = s.size
    val c = ScreenPt(ScreenPt.X(w.value / 2), ScreenPt.Y(h.value / 2))
    def dist(ks: Vector[PointSet.Key]): Double =
      val q = s.project(at(items(ks.head)))
      math.hypot(q.x.value - c.x.value, q.y.value - c.y.value)
    def onScreen(ks: Vector[PointSet.Key]): Boolean =
      val pts = ks.flatMap(items.get).map(at)
      pts.nonEmpty && Clustering.inView(
        s,
        LatLon(LatLon.Lat(pts.map(_.lat.value).sum / pts.size), LatLon.Lon(pts.map(_.lon.value).sum / pts.size)),
        OnScreenMargin
      )
    def nearestFirst(v: Vector[Todo]) =
      if v.size > 32 then v.map(t => (dist(t._2), t)).sortBy(_._1).map(_._2) else v
    val (first, later) = if before.isDefined then todo.partition(t => onScreen(t._2)) else (Vector.empty, todo)
    val queue = mutable.Queue.from(nearestFirst(first) ++ nearestFirst(later))
    // `var`s: the markers on screen still to make before the moves start, and whether they have started
    var firstLeft = first.size
    var started = false
    def make(one: Todo): Unit =
      val (id, ks) = one
      val made = id match
        case Left(key) =>
          if !singles.contains(key) && items.contains(key) then
            val m = single(items(key), PointSet.FadeIn(fadeIn))
            val sp = paneOf(items(key))
            singles(key) = (m, sp)
            sp.add(m)
            Some(m)
          else None
        case Right(label) =>
          if !bubbles.contains(label) && ks.forall(items.contains) then
            val m = bubble(ks.map(items))
            bubbles(label) = (m, ks)
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
      if !movesFrame then
        movesFrame = true
        val _ = dom.window.requestAnimationFrame(_ => movesFrame = false)
      fresh.foreach((m, _) => m.el.classList.remove(ClusterMotion.Pending))
      before match
        case Some(was) if s.center == view._1 && s.zoom == view._2 =>
          val t0 = dom.window.performance.now()
          animate(was, fresh.toList, gone.toList)
          measure("hd-points-moves", t0, dom.window.performance.now())
        case _ => gone.foreach((m, mp, _) => mp.remove(m))
    val mine = { drawGeneration += 1; drawGeneration }
    def slice(t0: Double): Unit =
      if mine == drawGeneration then
        val starting = !started && firstLeft == 0
        if starting then start()
        var count = if starting && before.isDefined then 1 else 0
        while queue.nonEmpty && (started || firstLeft > 0) && (count == 0 || dom.window.performance.now() - t0 < SliceMs)
        do
          make(queue.dequeue())
          count += 1
          if !started then firstLeft -= 1
        if queue.nonEmpty || !started then
          val _ = dom.window.requestAnimationFrame(_ => slice(dom.window.performance.now()))
      else if !started then
        // a newer drawing took over before the moves started: what was leaving goes, what was made shows
        started = true
        fresh.foreach((m, _) => m.el.classList.remove(ClusterMotion.Pending))
        gone.foreach((m, mp, _) => mp.remove(m))
    slice(began)

  /** A zoom's moves ([[ClusterMotion]]), once its markers on screen are made: a marker made of ONE former place comes
    * out of it (a split); one made of several stays where it is and appears as the markers that merge into it arrive.
    */
  private def animate(
      was: Map[PointSet.Key, (ScreenPt.X, ScreenPt.Y)],
      fresh: List[(DomMarker, Seq[PointSet.Key])],
      gone: List[(DomMarker, MarkerPane, Seq[PointSet.Key])]
  ): Unit =
    animations = PointSet.Animations(animations.value + 1)
    val drawnOf = mutable.HashMap.empty[PointSet.Key, (DomMarker, Seq[PointSet.Key])]
    singles.foreach((k, mp) => drawnOf(k) = (mp._1, Seq(k)))
    bubbles.values.foreach((m, ks) => ks.foreach(k => drawnOf(k) = (m, ks)))
    val made = js.Set[DomMarker]()
    fresh.foreach((m, _) => made.add(m))
    val arriving = mutable.LinkedHashSet.empty[DomMarker]
    val slides = mutable.ListBuffer.empty[ClusterMotion.Slide]
    fresh.foreach { (m, ks) =>
      ClusterMotion.origin(ks.flatMap(was.get)) match
        case Some(from) if !from._1.value.isNaN && (from._1 != m.screen._1 || from._2 != m.screen._2) =>
          slides += ClusterMotion.Slide(m, from, m.screen)
        case Some(_) => ()
        case None    => if ks.exists(was.contains) then arriving += m
    }
    val (w, h) = surface().size
    def seen(at: (ScreenPt.X, ScreenPt.Y)): Boolean =
      at._1.value >= -OnScreenMargin.value && at._2.value >= -OnScreenMargin.value && at._1.value <= w.value + OnScreenMargin.value && at._2.value <= h.value + OnScreenMargin.value
    // a marker merging off screen into a bubble off screen goes at once: nobody sees it fly
    gone.foreach { (m, mp, ks) =>
      ks.headOption.flatMap(drawnOf.get) match
        case Some((target, tks))
            if ClusterMotion.merges(ks, tks) && !m.screen._1.value.isNaN && (seen(m.screen) || seen(target.screen)) =>
          mp.retire(m, MarkerPane.RetireMs(ClusterMotion.Ms))
          slides += ClusterMotion.Slide(m, m.screen, target.screen)
          if !made.contains(target) then arriving -= target
        case _ => mp.remove(m)
    }
    ClusterMotion.slideAll(slides.toList)
    arriving.foreach(ClusterMotion.arrive)

object PointSet:
  export Clustering.GroupKey as Key
  type PointCount = PointCount.T
  object PointCount extends Newtype[Int, Pure]
  type FadeIn = FadeIn.T
  object FadeIn extends Newtype[Boolean, Pure]
  type AnimateMax = AnimateMax.T
  object AnimateMax extends Newtype[Int, Pure]
  type ExitMs = ExitMs.T
  object ExitMs extends Newtype[Int, Pure]
  type FadeOut = FadeOut.T
  object FadeOut extends Newtype[String, Pure]
  type Animations = Animations.T
  object Animations extends Newtype[Int, Pure]
