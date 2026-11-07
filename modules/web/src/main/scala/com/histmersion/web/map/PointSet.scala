package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

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
    key: D => PointSet.KeyResult,
    at: D => LatLon,
    radius: () => PointSet.RadiusResult,
    single: (D, PointSet.SingleArg2) => DomMarker,
    bubble: Seq[D] => DomMarker,
    animateMax: PointSet.AnimateMax,
    exitMs: PointSet.ExitMs,
    fadeOut: PointSet.FadeOut,
    repaint: (DomMarker, Seq[D]) => Unit = (_: DomMarker, _: Seq[D]) => ()
):

  private val items = mutable.LinkedHashMap.empty[String, D]
  // the drawn singles, by point key; the drawn bubbles, by their first point's key and size
  private val singles = mutable.HashMap.empty[String, (DomMarker, MarkerPane)]
  private val bubbles = mutable.HashMap.empty[String, (DomMarker, Seq[String])]
  // the groups at `level`: each one's points, and its place
  private var groups: Vector[(Vector[com.histmersion.web.map.PointSet.Key], LatLon)] = Vector.empty
  private var groupOf: collection.Map[com.histmersion.web.map.PointSet.Key, com.histmersion.web.map.PointSet.GroupId] = Map.empty
  private var level: com.histmersion.web.map.PointSet.Level = Int.MinValue
  private var queued = false
  // the newest drawing: an older one's slices still queued stop
  private var drawGeneration = 0

  /** How long one slice of marker making may take before the page gets a frame. */
  private val SliceMs = 6.0

  /** How far outside the view (px) a marker still counts as on screen: a box's half and a little more. */
  private val OnScreenMargin = 40.0
  private var installed = false
  // the view of a zoom whose markers on screen are being made, its moves not started yet
  private var awaitingMoves: Option[(LatLon, com.histmersion.web.map.PointSet.Zoom)] = None

  /** How many zooms have animated (the browser regressions read it through the diagnostics). */
  var animations: com.histmersion.web.map.PointSet.Animations = com.histmersion.web.map.PointSet.Animations(0)

  def size: com.histmersion.web.map.PointSet.PointCount = items.size
  def keys: Iterable[com.histmersion.web.map.PointSet.Key] = items.keys
  def get(k: com.histmersion.web.map.PointSet.Key): Option[D] = items.get(k)

  private def install(): Unit =
    if !installed && surface() != null then
      installed = true
      surface().on(MapEventKind.ZoomEnd)(_ => layout(animate = com.histmersion.web.map.PointSet.LayoutAnimate(true)))
      surface().on(MapEventKind.MoveEnd)(_ => layout(animate = com.histmersion.web.map.PointSet.LayoutAnimate(true)))
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
            if onMap && t.closest(s".${Spiderfy.Member}, .${Spiderfy.Fanned}, .hd-popup") == null then unspiderfy(),
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
  def remove(ks: Iterable[com.histmersion.web.map.PointSet.Key]): Unit =
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
  def drawnAlone(k: com.histmersion.web.map.PointSet.Key): Option[DomMarker] =
    singles
      .get(k)
      .filter((m, p) => p.has(m))
      .map(_._1)
      .orElse(spider.flatMap(_.members.find(_._1 == k)).map(_._2).filter((m, p) => p.has(m)).map(_._1))

  // ── a bubble fanned out (spiderfied): its points drawn around it, each on a thin leg to its spot ───

  /** A fanned-out bubble: its points, their markers and panes, the legs, the bubble itself. */
  final private class Spider(
      val keys: Seq[Spider.Keys],
      val members: Seq[(Spider.MembersPart1, (DomMarker, MarkerPane))],
      val legs: DomMarker,
      val bubble: Option[DomMarker]
  )

  object Spider:
    type Keys = Keys.T
    object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MembersPart1 = MembersPart1.T
    object MembersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  private var spider: Option[Spider] = None

  /** The points fanned out now (none: no bubble is). */
  def spidered: Seq[com.histmersion.web.map.PointSet.Key] = spider.fold(Seq.empty)(_.keys)

  /** Fan the points `ks` out around where their bubble stands ([[Spiderfy]]), each drawn as it is alone, a thin leg
    * back to the spot — the only way to reach points no zoom can part (one spot, or the deepest zoom). Anything else
    * fanned out folds back first. `separation`: the markers' spacing, px.
    */
  def spiderfy(ks: Seq[com.histmersion.web.map.PointSet.Key], separation: com.histmersion.web.map.PointSet.SpiderfySeparation): Unit =
    unspiderfy()
    install()
    val ds = ks.filter(items.contains)
    if ds.size >= 2 && surface() != null then
      val pts = ds.map(k => at(items(k)))
      val c = LatLon(pts.map(_.lat).sum / pts.size, pts.map(_.lon).sum / pts.size)
      val offsets = Spiderfy.offsets(ds.size, separation)
      val reach = offsets.map((x, y) => math.max(math.abs(x), math.abs(y))).maxOption.getOrElse(0.0)
      val side = (2 * math.ceil(reach) + 4).toInt
      val legsEl = DomMarker.element(Spiderfy.Legs, Spiderfy.legsSvg(offsets, side))
      val legs = DomMarker(c.lat, c.lon, legsEl, Some((side, side)))
      pane().add(legs)
      val bubble = bubbles.get(s"${ks.head}|${ks.size}").filter(_._2 == ks).map(_._1)
      bubble.foreach(_.el.classList.add(Spiderfy.Fanned))
      val members = ds.zip(offsets).map { (k, off) =>
        val m = single(items(k), com.histmersion.web.map.PointSet.SingleArg2(false))
        m.setLatLng(c.lat, c.lon)
        m.el.classList.add(Spiderfy.Member)
        m.el.style.setProperty("--sx", s"${off._1}px")
        m.el.style.setProperty("--sy", s"${off._2}px")
        m.setZ(Spiderfy.Z)
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
  def groupKeys(k: com.histmersion.web.map.PointSet.Key): Seq[com.histmersion.web.map.PointSet.Key] = groupOf.get(k).fold(Seq(k))(g => groups(g)._1)

  /** Zoom (and pan) until `k` stands alone, then `andThen` — or, where no zoom parts it from its bubble (points at its
    * very spot, or the deepest zoom reached), `andThen` where it is: it was left undone there, and a picture at a spot
    * it shares never opened.
    */
  def zoomToShow(k: com.histmersion.web.map.PointSet.Key, andThen: () => Unit): Unit =
    val s = surface()
    items.get(k).foreach { d =>
      def done(): Boolean = drawnAlone(k).isDefined
      def deepest: Boolean = s.zoom >= s.maxZoom - 0.01
      def inseparable: Boolean = Clustering.onePlace(groupKeys(k).flatMap(items.get).map(at))
      layout(animate = false)
      if done() || deepest || inseparable then andThen()
      else
        val p = at(d)
        // `var`: the subscription of the zoom's end, taken off when it is reached
        var sub: Subscription = null
        sub = s.on(MapEventKind.MoveEnd) { _ =>
          layout(animate = false)
          if done() || deepest || inseparable then
            sub.cancel()
            andThen()
        }
        s.setView(p, math.min(s.maxZoom, math.max(s.zoom + 1, level + 2)), animate = true)
    }

  private def fadeAway(mp: (DomMarker, MarkerPane)): Unit =
    val (m, p) = mp
    if p.has(m) then
      m.el.classList.add(fadeOut)
      dom.window.setTimeout(() => p.remove(m), exitMs)

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
    if awaitingMoves.isDefined then { val _ = dom.window.requestAnimationFrame(_ => scheduled()) }
    else
      queued = false
      layout(animate = com.histmersion.web.map.PointSet.LayoutAnimate(false))

  /** The groups at the map's zoom (again only when the zoom or the points changed), and the elements of those in view.
    */
  def layout(animate: com.histmersion.web.map.PointSet.LayoutAnimate): Unit =
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
              animateMax
            ) && MotionPref.animate()
          then Some(drawnPlaces())
          else None
        draw(wanted, before)
        // a fanned-out bubble regrouped or gone, or one of its points taken away: folded back
        spider.foreach { sp =>
          val still = sp.keys.forall(items.contains) && sp.bubble.forall(b => bubbles.values.exists(_._1 eq b))
          if !still then unspiderfy()
        }
      measure(com.histmersion.web.map.PointSet.MeasureName("hd-points-group"), t0, t1)
      measure(com.histmersion.web.map.PointSet.MeasureName("hd-points-draw"), t1, dom.window.performance.now())

  /** What a layout cost, for the browser's performance panel (the latest of each). */
  private def measure(name: com.histmersion.web.map.PointSet.MeasureName, t0: com.histmersion.web.map.PointSet.Timestamp, t1: com.histmersion.web.map.PointSet.Timestamp): Unit =
    val p = dom.window.performance.asInstanceOf[js.Dynamic]
    if js.typeOf(p.measure) == "function" then
      p.clearMeasures(name)
      p.measure(name, js.Dynamic.literal(start = t0, end = t1))

  /** How many markers (points alone and bubbles) are drawn now. */
  private def drawnMarkers: com.histmersion.web.map.PointSet.MarkerCount =
    singles.valuesIterator.count((m, p) => p.has(m)) + bubbles.valuesIterator.count((m, _) => pane().has(m))

  /** Where each point is drawn now (alone or in its bubble), screen pixels. */
  private def drawnPlaces(): Map[com.histmersion.web.map.PointSet.Key, (com.histmersion.web.map.PointSet.Coordinate, com.histmersion.web.map.PointSet.Coordinate)] =
    val m = mutable.HashMap.empty[String, (Double, Double)]
    singles.foreach((k, mp) => if mp._2.has(mp._1) then m(k) = mp._1.screen)
    bubbles.values.foreach((mk, ks) => if pane().has(mk) then ks.foreach(k => m(k) = mk.screen))
    m.toMap

  private def regroup(z: com.histmersion.web.map.PointSet.ZoomLevel): Unit =
    level = z
    groups = Clustering.groups(items.toSeq.map((k, d) => k -> at(d)), z, radius())
    val m = mutable.HashMap.empty[String, Int]
    var i = 0
    while i < groups.length do
      groups(i)._1.foreach(k => m(k) = i)
      i += 1
    groupOf = m

  private def draw(
      wanted: (Set[com.histmersion.web.map.PointSet.Key], Map[com.histmersion.web.map.PointSet.Key, Vector[com.histmersion.web.map.PointSet.Key]]),
      before: Option[Map[com.histmersion.web.map.PointSet.Key, (com.histmersion.web.map.PointSet.Coordinate, com.histmersion.web.map.PointSet.Coordinate)]]
  ): Unit =
    val s = surface()
    val p = pane()
    val (wantSingles, wantBubbles) = wanted
    // what goes: at once, or (a zoom that animates) kept until it is known whether it merges into a bubble
    val gone = mutable.ListBuffer.empty[(DomMarker, MarkerPane, Seq[String])]
    def leave(m: DomMarker, mp: MarkerPane, ks: Seq[com.histmersion.web.map.PointSet.Key]): Unit =
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
    def dist(ks: Vector[com.histmersion.web.map.PointSet.Key]): com.histmersion.web.map.PointSet.Distance =
      val q = s.project(at(items(ks.head)))
      math.hypot(q.x - c.x, q.y - c.y)
    def onScreen(ks: Vector[com.histmersion.web.map.PointSet.Key]): Boolean =
      val pts = ks.flatMap(items.get).map(at)
      pts.nonEmpty && Clustering.inView(
        s,
        LatLon(pts.map(_.lat).sum / pts.size, pts.map(_.lon).sum / pts.size),
        OnScreenMargin
      )
    def nearestFirst(v: Vector[(com.histmersion.web.map.PointSet.Key, Vector[com.histmersion.web.map.PointSet.Key], com.histmersion.web.map.PointSet.IsSingle)]) =
      if v.size > 32 then v.sortBy(t => dist(t._2)) else v
    val (first, later) = if before.isDefined then todo.partition(t => onScreen(t._2)) else (Vector.empty, todo)
    val queue = mutable.Queue.from(nearestFirst(first) ++ nearestFirst(later))
    // `var`s: the markers on screen still to make before the moves start, and whether they have started
    var firstLeft = first.size
    var started = false
    def make(one: (com.histmersion.web.map.PointSet.Key, Vector[com.histmersion.web.map.PointSet.Key], com.histmersion.web.map.PointSet.IsSingle)): Unit =
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
          measure(com.histmersion.web.map.PointSet.MeasureName("hd-points-moves"), t0, dom.window.performance.now())
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

  /** A zoom's moves ([[ClusterMotion]]), once its markers on screen are made: a marker made of ONE former place comes
    * out of it (a split); one made of several stays where it is and appears as the markers that merge into it arrive.
    */
  private def animate(
      was: Map[com.histmersion.web.map.PointSet.Key, (com.histmersion.web.map.ScreenPt.X, com.histmersion.web.map.ScreenPt.Y)],
      fresh: List[(DomMarker, Seq[com.histmersion.web.map.PointSet.Key])],
      gone: List[(DomMarker, MarkerPane, Seq[com.histmersion.web.map.PointSet.Key])]
  ): Unit =
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
    def seen(at: (com.histmersion.web.map.ScreenPt.X, com.histmersion.web.map.ScreenPt.Y)): Boolean =
      at._1 >= -OnScreenMargin && at._2 >= -OnScreenMargin && at._1 <= w + OnScreenMargin && at._2 <= h + OnScreenMargin
    // a marker merging off screen into a bubble off screen goes at once: nobody sees it fly
    gone.foreach { (m, mp, ks) =>
      ks.headOption.flatMap(drawnOf.get) match
        case Some((target, tks))
            if ClusterMotion.merges(ks, tks) && !m.screen._1.isNaN && (seen(m.screen) || seen(target.screen)) =>
          mp.retire(m, ClusterMotion.Ms)
          slides += ClusterMotion.Slide(m, m.screen, target.screen)
          if !made.contains(target) then arriving -= target
        case _ => mp.remove(m)
    }
    ClusterMotion.slideAll(slides.toList)
    arriving.foreach(ClusterMotion.arrive)

object PointSet:
  type Coordinate = Coordinate.T
  object Coordinate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Distance = Distance.T
  object Distance extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupId = GroupId.T
  object GroupId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsSingle = IsSingle.T
  object IsSingle extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkerCount = MarkerCount.T
  object MarkerCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointCount = PointCount.T
  object PointCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Radius = Radius.T
  object Radius extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Timestamp = Timestamp.T
  object Timestamp extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Zoom = Zoom.T
  object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZoomLevel = ZoomLevel.T
  object ZoomLevel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type KeyResult = com.histmersion.web.map.PointSet.Key
  val KeyResult: _root_.com.histmersion.web.map.PointSet.Key.type = _root_.com.histmersion.web.map.PointSet.Key
  type RadiusResult = com.histmersion.web.map.PointSet.Radius
  val RadiusResult: _root_.com.histmersion.web.map.PointSet.Radius.type = _root_.com.histmersion.web.map.PointSet.Radius
  type SingleArg2 = com.histmersion.web.map.PointSet.IsSingle
  val SingleArg2: _root_.com.histmersion.web.map.PointSet.IsSingle.type = _root_.com.histmersion.web.map.PointSet.IsSingle
  type AnimateMax = AnimateMax.T
  object AnimateMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExitMs = ExitMs.T
  object ExitMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FadeOut = FadeOut.T
  object FadeOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Level = Level.T
  object Level extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Animations = Animations.T
  object Animations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpiderfySeparation = SpiderfySeparation.T
  object SpiderfySeparation extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayoutAnimate = LayoutAnimate.T
  object LayoutAnimate extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MeasureName = MeasureName.T
  object MeasureName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
