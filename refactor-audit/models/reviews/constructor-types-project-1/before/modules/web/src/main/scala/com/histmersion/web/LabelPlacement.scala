package com.histmersion.web

import scala.collection.mutable

/** Where a region's or polity's NAME goes on the map, and which names are shown — all pure, so the placement is a unit
  * test and the map only applies the answer ([[MapLabels]]).
  *
  * Coordinates are Web-Mercator pixels at zoom 0 (a 256 px world, `y` down), the plane the map draws on: a distance
  * there times `2^zoom` is screen pixels, so whatever does not depend on the viewport (the anchor, the size, the
  * thinned outline, the hull) is computed ONCE per shape ([[shape]]) and every zoom and pan after that is arithmetic
  * plus, near the screen edge, one small clip.
  *
  *   - [[shape]]: the name's home, the pole of inaccessibility (Mapbox's polylabel) — the point inside the shape
  *     farthest from its edge. A shape whose largest part is most of its land is named in that part (a crescent or a
  *     coastline-hugging polity inside itself, never at a centroid in the sea); an ARCHIPELAGO (no part is half of the
  *     land) is named at the middle of the hull of all its parts, among its islands, and is as big as that hull — the
  *     sea between them counts;
  *   - [[place]]: where the name goes in THIS viewport — at its home when the whole name fits on screen there (so it
  *     never moves while the home stays visible), else at the pole of the part of the shape still on screen, inset by
  *     half the name so all of it stays visible, else nowhere;
  *   - [[tier]]: how big the name is drawn at a zoom, from the whole shape's on-screen size; only a shape a few pixels
  *     across has no name at all;
  *   - [[cull]]: which names survive collisions — the ONLY other reason a name is hidden. Events first, then larger
  *     shapes.
  */
object LabelPlacement:

  /** A point in the zoom-0 Mercator plane. */
  type ProjectLat = ProjectLat.T
  object ProjectLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProjectLon = ProjectLon.T
  object ProjectLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnprojectResultPart1 = UnprojectResultPart1.T
  object UnprojectResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnprojectResultPart2 = UnprojectResultPart2.T
  object UnprojectResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RingAreaResult = RingAreaResult.T
  object RingAreaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PolyAreaResult = PolyAreaResult.T
  object PolyAreaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapePolysPart1 = ShapePolysPart1.T
  object ShapePolysPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapePolysPart2 = ShapePolysPart2.T
  object ShapePolysPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThinMax = ThinMax.T
  object ThinMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnchorPrecision = AnchorPrecision.T
  object AnchorPrecision extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnchorMaxProbes = AnchorMaxProbes.T
  object AnchorMaxProbes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CentroidCellResultPart1 = CentroidCellResultPart1.T
  object CentroidCellResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CentroidCellResultPart2 = CentroidCellResultPart2.T
  object CentroidCellResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignedDistanceResult = SignedDistanceResult.T
  object SignedDistanceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignedDistanceX = SignedDistanceX.T
  object SignedDistanceX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignedDistanceY = SignedDistanceY.T
  object SignedDistanceY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignedDistanceFlatResult = SignedDistanceFlatResult.T
  object SignedDistanceFlatResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignedDistanceFlatX = SignedDistanceFlatX.T
  object SignedDistanceFlatX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignedDistanceFlatY = SignedDistanceFlatY.T
  object SignedDistanceFlatY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegDistSqResult = SegDistSqResult.T
  object SegDistSqResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegDistSqPx = SegDistSqPx.T
  object SegDistSqPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegDistSqPy = SegDistSqPy.T
  object SegDistSqPy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegDistSqAx = SegDistSqAx.T
  object SegDistSqAx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegDistSqAy = SegDistSqAy.T
  object SegDistSqAy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegDistSqBx = SegDistSqBx.T
  object SegDistSqBx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SegDistSqBy = SegDistSqBy.T
  object SegDistSqBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierSize0 = TierSize0.T
  object TierSize0 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierExtent0 = TierExtent0.T
  object TierExtent0 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierZoom = TierZoom.T
  object TierZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceScale = PlaceScale.T
  object PlaceScale extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceHalfW = PlaceHalfW.T
  object PlaceHalfW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceHalfH = PlaceHalfH.T
  object PlaceHalfH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgePlaceScale = EdgePlaceScale.T
  object EdgePlaceScale extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgePlaceHalfW = EdgePlaceHalfW.T
  object EdgePlaceHalfW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgePlaceHalfH = EdgePlaceHalfH.T
  object EdgePlaceHalfH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GlideScale = GlideScale.T
  object GlideScale extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GlideHalfW = GlideHalfW.T
  object GlideHalfW extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GlideHalfH = GlideHalfH.T
  object GlideHalfH extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CullResult = CullResult.T
  object CullResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CullCell = CullCell.T
  object CullCell extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CullShownArg1 = CullShownArg1.T
  object CullShownArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CullShownResult = CullShownResult.T
  object CullShownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Pt(x: Pt.X, y: Pt.Y)

  object Pt:
    type X = X.T
    object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Y = Y.T
    object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A polygon: its outer ring then its holes, each a closed or open list of points. */
  type Poly = Vector[Vector[Pt]]

  /** An axis-aligned rectangle (a bounding box, a viewport), `min ≤ max` on both axes when non-empty. */
  final case class Rect(minX: Rect.MinX, minY: Rect.MinY, maxX: Rect.MaxX, maxY: Rect.MaxY):
    def w: com.histmersion.web.LabelPlacement.Rect.WResult = maxX - minX
    def h: com.histmersion.web.LabelPlacement.Rect.HResult = maxY - minY
    def isEmpty: com.histmersion.web.LabelPlacement.Rect.IsEmptyResult = minX > maxX || minY > maxY
    def contains(p: Pt): com.histmersion.web.LabelPlacement.Rect.ContainsResult = p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY
    def intersects(o: Rect): com.histmersion.web.LabelPlacement.Rect.IntersectsResult = minX <= o.maxX && o.minX <= maxX && minY <= o.maxY && o.minY <= maxY
    def within(o: Rect): com.histmersion.web.LabelPlacement.Rect.WithinResult = minX >= o.minX && maxX <= o.maxX && minY >= o.minY && maxY <= o.maxY

    /** Shrunk by `dx` left and right and `dy` top and bottom (empty when that leaves nothing). */
    def inset(dx: com.histmersion.web.LabelPlacement.Rect.InsetDx, dy: com.histmersion.web.LabelPlacement.Rect.InsetDy): Rect = Rect(minX + dx, minY + dy, maxX - dx, maxY - dy)

    /** The nearest point of the rectangle to `p`. */
    def clamp(p: Pt): Pt = Pt(math.max(minX, math.min(maxX, p.x)), math.max(minY, math.min(maxY, p.y)))

  object Rect:
    /** Pure: the bounding box of some points (empty for none). */
    type WResult = WResult.T
    object WResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HResult = HResult.T
    object HResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsEmptyResult = IsEmptyResult.T
    object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContainsResult = ContainsResult.T
    object ContainsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IntersectsResult = IntersectsResult.T
    object IntersectsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WithinResult = WithinResult.T
    object WithinResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InsetDx = InsetDx.T
    object InsetDx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InsetDy = InsetDy.T
    object InsetDy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type MinX = MinX.T
    object MinX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinY = MinY.T
    object MinY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxX = MaxX.T
    object MaxX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxY = MaxY.T
    object MaxY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(pts: Iterable[Pt]): Rect =
      var x0 = Double.PositiveInfinity
      var y0 = Double.PositiveInfinity
      var x1 = Double.NegativeInfinity
      var y1 = Double.NegativeInfinity
      pts.foreach { p =>
        if p.x < x0 then x0 = p.x
        if p.x > x1 then x1 = p.x
        if p.y < y0 then y0 = p.y
        if p.y > y1 then y1 = p.y
      }
      Rect(x0, y0, x1, y1)

  private val World = 256.0
  private val MaxLat = 85.0511287798

  /** Pure: (lat, lon) → the zoom-0 Mercator plane. */
  def project(lat: com.histmersion.web.LabelPlacement.ProjectLat, lon: com.histmersion.web.LabelPlacement.ProjectLon): Pt =
    val phi = math.toRadians(math.max(-MaxLat, math.min(MaxLat, lat)))
    Pt((lon + 180.0) / 360.0 * World, (1.0 - math.log(math.tan(phi) + 1.0 / math.cos(phi)) / math.Pi) / 2.0 * World)

  /** Pure: the inverse of [[project]] → (lat, lon). */
  def unproject(p: Pt): (com.histmersion.web.LabelPlacement.UnprojectResultPart1, com.histmersion.web.LabelPlacement.UnprojectResultPart2) =
    val lon = p.x / World * 360.0 - 180.0
    val n = math.Pi - 2.0 * math.Pi * p.y / World
    (math.toDegrees(math.atan(math.sinh(n))), lon)

  /** Pure: a ring's unsigned area (shoelace). */
  def ringArea(r: Vector[Pt]): com.histmersion.web.LabelPlacement.RingAreaResult =
    if r.length < 3 then 0.0
    else
      var s = 0.0
      var j = r.length - 1
      var i = 0
      while i < r.length do
        s += (r(j).x - r(i).x) * (r(j).y + r(i).y)
        j = i
        i += 1
      math.abs(s) / 2.0

  /** Pure: a polygon's area — its outer ring less its holes. */
  def polyArea(p: Poly): com.histmersion.web.LabelPlacement.PolyAreaResult =
    p.headOption.fold(0.0)(ringArea) - p.drop(1).map(ringArea).sum

  /** Pure: the convex hull of some points (Andrew's monotone chain), counter-clockwise in the plane, without a repeated
    * first point; fewer than three distinct points come back as they are.
    */
  def hull(pts: Seq[Pt]): Vector[Pt] =
    val ps = pts.distinct.sortBy(p => (p.x, p.y)).toVector
    if ps.length < 3 then ps
    else
      def cross(o: Pt, a: Pt, b: Pt): Double = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
      val h = mutable.ArrayBuffer.empty[Pt]
      for p <- ps do
        while h.length >= 2 && cross(h(h.length - 2), h(h.length - 1), p) <= 0 do h.remove(h.length - 1)
        h += p
      val lower = h.length + 1
      for p <- ps.reverseIterator.drop(1) do
        while h.length >= lower && cross(h(h.length - 2), h(h.length - 1), p) <= 0 do h.remove(h.length - 1)
        h += p
      h.remove(h.length - 1)
      h.toVector

  /** One part of a shape as the viewport placement uses it: its rings, thinned to [[ViewRingPoints]], and its bounding
    * box — so a part off screen is skipped without a clip.
    */
  final case class Part(rings: Poly, bbox: Rect)

  /** What the map needs to know about one shape, computed once (nothing here depends on the viewport):
    *   - `lat`/`lon` and `home` (the same point, zoom-0): where its name goes when that is on screen;
    *   - `size0`: its area at zoom 0 — all its land, or for an archipelago the hull around its islands; what sets the
    *     name's size and its rank in a collision;
    *   - `bbox0`: the box around every part;
    *   - `parts`: the outline the name may move within when its home is off screen (an archipelago's: its hull);
    *   - `archipelago`: no part holds [[DominantShare]] of the land, so the hull stands for the shape.
    */
  final case class Shape(
      lat: Shape.Lat,
      lon: Shape.Lon,
      home: Pt,
      size0: Shape.Size0,
      bbox0: Rect,
      parts: Vector[Part],
      archipelago: Shape.Archipelago
  ):
    /** The shape's larger side at zoom 0. */
    def extent0: com.histmersion.web.LabelPlacement.Shape.Extent0Result = math.max(bbox0.w, bbox0.h)

  object Shape:
    type Extent0Result = Extent0Result.T
    object Extent0Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Lat = Lat.T
    object Lat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lon = Lon.T
    object Lon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Size0 = Size0.T
    object Size0 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Archipelago = Archipelago.T
    object Archipelago extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A part that holds at least this share of a shape's land names it; below that the shape is an archipelago and is
    * named, and sized, by the hull of all its parts.
    */
  val DominantShare = 0.5

  /** Pure: the label home and size of a (multi)polygon given in (lat, lon) rings — `None` for an empty one.
    */
  def shape(polys: Seq[Seq[Seq[(com.histmersion.web.LabelPlacement.ShapePolysPart1, com.histmersion.web.LabelPlacement.ShapePolysPart2)]]]): Option[Shape] =
    val projected = polys
      .map(_.map(_.map((la, lo) => project(la, lo)).toVector).toVector)
      .filter(_.nonEmpty)
      .filter(_.head.length >= 3)
      .toVector
    if projected.isEmpty then None
    else
      val areas = projected.map(polyArea)
      val total = areas.sum
      val largest = areas.indices.maxBy(areas)
      val box = Rect.of(projected.iterator.flatMap(_.head).toSeq)
      val archipelago = projected.length > 1 && areas(largest) < DominantShare * total
      val h = if archipelago then hull(projected.flatMap(p => thin(p.head))) else Vector.empty
      val (home, size0, parts) =
        if h.length >= 3 then (anchor(Vector(h)), math.max(ringArea(h), total), Vector(part(Vector(h))))
        else (anchor(projected(largest)), total, projected.map(part))
      val (lat, lon) = unproject(home)
      Some(Shape(lat, lon, home, size0, box, parts, archipelago = h.length >= 3))

  private def part(p: Poly): Part =
    val rings = p.map(thin(_, ViewRingPoints))
    Part(rings, Rect.of(rings.head))

  /** At most this many points of a ring are used to place a name: a label needs the shape's room, not its coastline,
    * and a 20 000-point Cliopatria outline would otherwise cost every probe 20 000 segment distances.
    */
  val MaxRingPoints = 400

  /** Pure: every `k`-th point, so the ring keeps at most `max` (its first point always). */
  def thin(r: Vector[Pt], max: com.histmersion.web.LabelPlacement.ThinMax = MaxRingPoints): Vector[Pt] =
    if r.length <= max then r
    else
      val step = math.ceil(r.length.toDouble / max).toInt
      r.indices.by(step).map(r).toVector

  /** Cells probed at most per shape, so one pathological outline cannot stall a frame. */
  val MaxProbes = 1500

  /** The first grid of [[anchor]] has at most this many cells along a shape's longer side. */
  val MaxGridCells = 32.0

  /** Pure: the pole of inaccessibility of `poly` (polylabel, Agafonkin 2016): a best-first quadtree search for the
    * interior point farthest from every edge, to within `precision` (default 1% of the shape's size), probing at most
    * `maxProbes` cells. The first grid has at most [[MaxGridCells]] cells along the longer side, so a sliver (a shape
    * clipped to a screen edge) costs no more than a square.
    */
  def anchor(poly: Poly, precision: com.histmersion.web.LabelPlacement.AnchorPrecision = 0.0, maxProbes: com.histmersion.web.LabelPlacement.AnchorMaxProbes = MaxProbes): Pt =
    val rings = poly.map(thin(_))
    val outer = rings.head
    val minX = outer.map(_.x).min
    val maxX = outer.map(_.x).max
    val minY = outer.map(_.y).min
    val maxY = outer.map(_.y).max
    val w = maxX - minX
    val h = maxY - minY
    val cellSize = math.max(math.min(w, h), math.max(w, h) / MaxGridCells)
    if cellSize <= 0 then Pt(minX + w / 2, minY + h / 2)
    else
      val tolerance = if precision > 0 then precision else math.max(w, h) / 100.0
      // the rings as flat coordinate arrays, once: every probe reads every vertex, and reading a
      // `Vector[Pt]` cost an index walk and a cast per vertex — a whole range's 1 600 shapes took
      // ~600 ms of the load's one task (#1949)
      val flat = flatten(rings)
      final case class Cell(x: com.histmersion.web.LabelPlacement.Pt.X, y: com.histmersion.web.LabelPlacement.Pt.Y, half: Cell.Half):
        val d: Double = signedDistanceFlat(x, y, flat)
        val max: Double = d + half * math.sqrt(2)

      object Cell:
        type Half = Half.T
        object Half extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
      val queue = mutable.PriorityQueue.empty[Cell](using Ordering.by[Cell, Double](_.max))
      var cx = minX
      while cx < maxX do
        var cy = minY
        while cy < maxY do
          queue.enqueue(Cell(cx + cellSize / 2, cy + cellSize / 2, cellSize / 2))
          cy += cellSize
        cx += cellSize
      var best = centroidCell(outer).map((x, y) => Cell(x, y, 0)).getOrElse(Cell(minX + w / 2, minY + h / 2, 0))
      val box = Cell(minX + w / 2, minY + h / 2, 0)
      if box.d > best.d then best = box
      var probes = 0
      while queue.nonEmpty && probes < maxProbes do
        val c = queue.dequeue()
        probes += 1
        if c.d > best.d then best = c
        if c.max - best.d > tolerance then
          val q = c.half / 2
          queue.enqueue(Cell(c.x - q, c.y - q, q))
          queue.enqueue(Cell(c.x + q, c.y - q, q))
          queue.enqueue(Cell(c.x - q, c.y + q, q))
          queue.enqueue(Cell(c.x + q, c.y + q, q))
      Pt(best.x, best.y)

  private def centroidCell(r: Vector[Pt]): Option[(com.histmersion.web.LabelPlacement.CentroidCellResultPart1, com.histmersion.web.LabelPlacement.CentroidCellResultPart2)] =
    var area = 0.0
    var x = 0.0
    var y = 0.0
    var j = r.length - 1
    var i = 0
    while i < r.length do
      val a = r(i)
      val b = r(j)
      val f = a.x * b.y - b.x * a.y
      x += (a.x + b.x) * f
      y += (a.y + b.y) * f
      area += f * 3
      j = i
      i += 1
    if area == 0 then None else Some((x / area, y / area))

  /** Pure: the distance from (x, y) to the polygon's nearest edge, positive inside, negative outside. */
  def signedDistance(x: com.histmersion.web.LabelPlacement.SignedDistanceX, y: com.histmersion.web.LabelPlacement.SignedDistanceY, rings: Poly): com.histmersion.web.LabelPlacement.SignedDistanceResult = signedDistanceFlat(x, y, flatten(rings))

  /** Each ring as `[x0, y0, x1, y1, …]`. */
  private def flatten(rings: Poly): Array[Array[Double]] =
    rings.iterator.map { r =>
      val a = new Array[Double](r.length * 2)
      var i = 0
      r.foreach { p => a(i) = p.x; a(i + 1) = p.y; i += 2 }
      a
    }.toArray

  /** [[signedDistance]] over [[flatten]]ed rings: plain array reads in the probe loop. */
  private def signedDistanceFlat(x: com.histmersion.web.LabelPlacement.SignedDistanceFlatX, y: com.histmersion.web.LabelPlacement.SignedDistanceFlatY, rings: Array[Array[Double]]): com.histmersion.web.LabelPlacement.SignedDistanceFlatResult =
    // `var`s: one pass over every edge of every ring
    var inside = false
    var minSq = Double.PositiveInfinity
    var k = 0
    while k < rings.length do
      val r = rings(k)
      val n = r.length / 2
      var j = n - 1
      var i = 0
      while i < n do
        val ax = r(2 * i)
        val ay = r(2 * i + 1)
        val bx = r(2 * j)
        val by = r(2 * j + 1)
        if (ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax then inside = !inside
        val d = segDistSq(x, y, ax, ay, bx, by)
        if d < minSq then minSq = d
        j = i
        i += 1
      k += 1
    (if inside then 1 else -1) * math.sqrt(minSq)

  private def segDistSq(px: com.histmersion.web.LabelPlacement.SegDistSqPx, py: com.histmersion.web.LabelPlacement.SegDistSqPy, ax: com.histmersion.web.LabelPlacement.SegDistSqAx, ay: com.histmersion.web.LabelPlacement.SegDistSqAy, bx: com.histmersion.web.LabelPlacement.SegDistSqBx, by: com.histmersion.web.LabelPlacement.SegDistSqBy): com.histmersion.web.LabelPlacement.SegDistSqResult =
    var x = ax
    var y = ay
    val dx = bx - x
    val dy = by - y
    if dx != 0 || dy != 0 then
      val t = ((px - x) * dx + (py - y) * dy) / (dx * dx + dy * dy)
      if t > 1 then { x = bx; y = by }
      else if t > 0 then { x += dx * t; y += dy * t }
    val ex = px - x
    val ey = py - y
    ex * ex + ey * ey

  // ── how big, and whether at all ──────────────────────────────────────────────────────────────────

  /** A name's size class (`.hd-map-label--s/m/l`): small shapes get small names. */
  enum Tier(val css: String):
    case S extends Tier("hd-map-label--s")
    case M extends Tier("hd-map-label--m")
    case L extends Tier("hd-map-label--l")

  /** A shape whose larger side is under this many screen pixels is not named at that zoom: a name on a dot names
    * nothing anyone can see. Everything larger is named unless it collides.
    */
  val MinExtentPx = 6.0
  val MediumSidePx = 150.0
  val LargeSidePx = 340.0

  /** Pure: the size of a shape's name at `zoom`, from the side of a square of its on-screen size ([[Shape.size0]]: all
    * its land, an archipelago's hull) — `None` only when the whole shape is a few pixels across ([[MinExtentPx]]).
    */
  def tier(size0: com.histmersion.web.LabelPlacement.TierSize0, extent0: com.histmersion.web.LabelPlacement.TierExtent0, zoom: com.histmersion.web.LabelPlacement.TierZoom): Option[Tier] =
    val scale = math.pow(2, zoom)
    val side = math.sqrt(size0) * scale
    if extent0 * scale < MinExtentPx then None
    else if side >= LargeSidePx then Some(Tier.L)
    else if side >= MediumSidePx then Some(Tier.M)
    else Some(Tier.S)

  def tier(s: Shape, zoom: com.histmersion.web.LabelPlacement.TierZoom): Option[Tier] = tier(s.size0, s.extent0, zoom)

  // ── where, in this viewport ──────────────────────────────────────────────────────────────────────

  /** Points of a ring kept for the viewport placement: it runs at the end of every pan, so it works on a coarser
    * outline than the home does (a few pixels of coastline never move a name).
    */
  val ViewRingPoints = 96

  /** Cells probed at most when a name moves into the visible part of its shape. */
  val ViewProbes = 200

  /** The visible-part pole is found to within this many screen pixels. */
  val ViewPrecisionPx = 2.0

  /** Room kept between a name and the screen edge, px. */
  val EdgePadPx = 4.0

  /** When no part of a shape is left inside the inset viewport (a sliver, or a small island in the band along the
    * screen edge), its name is pulled on screen and may then sit at most this many pixels beside what is left of the
    * shape — overlapping it is fine, floating off is not.
    */
  val NearPx = 24.0

  /** A name moved off its home stays where it was after the next pan while it keeps at least this share of the room the
    * new best place would give it — no jitter for a small pan.
    */
  val KeepShare = 0.5

  /** Pure: where the name of `s` goes in a viewport `view` (zoom-0 plane) drawn at `scale` (`2^zoom`), for a name
    * `halfW` × `halfH` screen pixels from its centre to its sides — `None` when no place keeps the whole name on screen
    * and on (or right beside) its shape. `prev` is where the name was after the last pass.
    *
    *   1. its home, when the whole name fits on screen there — it never moves while that holds;
    *   2. else the pole of the part of the shape inside the viewport inset by half the name (the largest such piece),
    *      or `prev` while that is still on that piece with [[KeepShare]] of the room;
    *   3. else (only a sliver along the edge) the middle of the shape's on-screen box pulled inside the inset viewport,
    *      while the name then still touches that box or sits within [[NearPx]] of it;
    *   4. else nowhere.
    */
  def place(s: Shape, view: Rect, scale: com.histmersion.web.LabelPlacement.PlaceScale, halfW: com.histmersion.web.LabelPlacement.PlaceHalfW, halfH: com.histmersion.web.LabelPlacement.PlaceHalfH, prev: Option[Pt] = None): Option[Pt] =
    val inner = view.inset((halfW + EdgePadPx) / scale, (halfH + EdgePadPx) / scale)
    if inner.isEmpty || !s.bbox0.intersects(view) then None
    else if inner.contains(s.home) then Some(s.home)
    else
      visiblePiece(s, inner) match
        case Some(piece) =>
          val p = anchor(piece, ViewPrecisionPx / scale, ViewProbes)
          prev.filter(q => inner.contains(q) && q != s.home) match
            case Some(q) if signedDistance(q.x, q.y, piece) >= KeepShare * signedDistance(p.x, p.y, piece) => Some(q)
            case _                                                                                         => Some(p)
        case None => edgePlace(visibleBox(s, view), inner, scale, halfW, halfH)

  /** Pure: the box around the parts of `s` inside `r` (each part's box cut to `r`) — the cheap stand-in for the visible
    * part, used every frame of a pan. Empty when nothing is inside.
    */
  def visibleBox(s: Shape, r: Rect): Rect =
    var x0 = Double.PositiveInfinity
    var y0 = Double.PositiveInfinity
    var x1 = Double.NegativeInfinity
    var y1 = Double.NegativeInfinity
    s.parts.foreach { p =>
      val b = p.bbox
      if b.intersects(r) then
        x0 = math.min(x0, math.max(b.minX, r.minX))
        y0 = math.min(y0, math.max(b.minY, r.minY))
        x1 = math.max(x1, math.min(b.maxX, r.maxX))
        y1 = math.max(y1, math.min(b.maxY, r.maxY))
    }
    Rect(x0, y0, x1, y1)

  /** Pure: step 3 of [[place]] — the middle of `box` (what is on screen of a shape) pulled into `inner`, kept while the
    * name there touches `box` or is at most [[NearPx]] beside it.
    */
  def edgePlace(box: Rect, inner: Rect, scale: com.histmersion.web.LabelPlacement.EdgePlaceScale, halfW: com.histmersion.web.LabelPlacement.EdgePlaceHalfW, halfH: com.histmersion.web.LabelPlacement.EdgePlaceHalfH): Option[Pt] =
    if box.isEmpty || inner.isEmpty then None
    else
      val q = inner.clamp(Pt((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2))
      val gx = math.max(0.0, math.max(box.minX - q.x, q.x - box.maxX) * scale - halfW)
      val gy = math.max(0.0, math.max(box.minY - q.y, q.y - box.maxY) * scale - halfH)
      Option.when(math.hypot(gx, gy) <= NearPx)(q)

  /** Pure: where a name heads on a frame of a pan or zoom — cheap, from what is cached per shape: its home while the
    * whole name fits on screen there, else `last` (the [[place]] of the last settled view, or the home) pulled into the
    * inset viewport and the on-screen box of the shape, else as in step 3 of [[place]]. `None`: no place on screen, the
    * name fades out.
    */
  def glide(s: Shape, view: Rect, scale: com.histmersion.web.LabelPlacement.GlideScale, halfW: com.histmersion.web.LabelPlacement.GlideHalfW, halfH: com.histmersion.web.LabelPlacement.GlideHalfH, last: Option[Pt]): Option[Pt] =
    val inner = view.inset((halfW + EdgePadPx) / scale, (halfH + EdgePadPx) / scale)
    if inner.isEmpty || !s.bbox0.intersects(view) then None
    else if inner.contains(s.home) then Some(s.home)
    else
      val box = visibleBox(s, view)
      val room = Rect(
        math.max(inner.minX, box.minX),
        math.max(inner.minY, box.minY),
        math.min(inner.maxX, box.maxX),
        math.min(inner.maxY, box.maxY)
      )
      if room.isEmpty || box.isEmpty then edgePlace(box, inner, scale, halfW, halfH)
      else Some(room.clamp(last.getOrElse(s.home)))

  /** Pure: the largest piece of the shape's parts inside `r` (each part clipped to it), if any has area. */
  def visiblePiece(s: Shape, r: Rect): Option[Poly] =
    var best: Poly = Vector.empty
    var bestArea = 0.0
    s.parts.foreach { part =>
      if part.bbox.intersects(r) then
        val piece = if part.bbox.within(r) then part.rings else clip(part.rings, r)
        if piece.nonEmpty then
          val a = polyArea(piece)
          if a > bestArea then
            best = piece
            bestArea = a
    }
    Option.when(bestArea > 0)(best)

  /** Pure: a polygon clipped to a rectangle (Sutherland–Hodgman, ring by ring): the outer ring and the holes that keep
    * three points or more; empty when the outer ring is gone. A concave shape may come back joined by zero-width seams
    * along the rectangle's edge — its area and its pole are unaffected.
    */
  def clip(poly: Poly, r: Rect): Poly =
    val outer = clipRing(poly.head, r)
    if outer.length < 3 then Vector.empty
    else outer +: poly.tail.map(clipRing(_, r)).filter(_.length >= 3)

  /** Pure: one ring clipped to a rectangle (Sutherland–Hodgman). */
  def clipRing(ring: Vector[Pt], r: Rect): Vector[Pt] =
    def edge(in: Vector[Pt], inside: Pt => Boolean, cut: (Pt, Pt) => Pt): Vector[Pt] =
      if in.isEmpty then in
      else
        val out = Vector.newBuilder[Pt]
        var prev = in.last
        var prevIn = inside(prev)
        in.foreach { cur =>
          val curIn = inside(cur)
          if curIn then
            if !prevIn then out += cut(prev, cur)
            out += cur
          else if prevIn then out += cut(prev, cur)
          prev = cur
          prevIn = curIn
        }
        out.result()
    def atX(x: Double)(a: Pt, b: Pt): Pt = Pt(x, a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x))
    def atY(y: Double)(a: Pt, b: Pt): Pt = Pt(a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y), y)
    val l = edge(ring, _.x >= r.minX, atX(r.minX))
    val rt = edge(l, _.x <= r.maxX, atX(r.maxX))
    val t = edge(rt, _.y >= r.minY, atY(r.minY))
    edge(t, _.y <= r.maxY, atY(r.maxY))

  // ── collisions ───────────────────────────────────────────────────────────────────────────────────

  /** A label as the culling sees it: its key, its centre in screen (or layer) pixels, its size, and its rank — higher
    * wins a collision.
    */
  final case class Box(key: Box.Key, cx: Box.Cx, cy: Box.Cy, w: Box.W, h: Box.H, rank: Box.Rank)

  object Box:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cx = Cx.T
    object Cx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cy = Cy.T
    object Cy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type W = W.T
    object W extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type H = H.T
    object H extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rank = Rank.T
    object Rank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Room kept between two names, px. */
  val PadPx = 3.0

  /** A name that is NOT shown yet must clear every shown one by this much more than [[PadPx]] to appear: two names
    * drifting together hide one when they touch, and it comes back only once they are clearly apart — never a flicker
    * at the boundary.
    */
  val AppearPadPx = 10.0

  /** Pure: which labels are drawn — greedily, highest rank first (ties by key, so the answer is stable), a label is
    * kept unless its box (padded by [[PadPx]], or by [[PadPx]] + [[AppearPadPx]] when it is not in `shown`) overlaps
    * one already kept. A grid of `cell`-pixel buckets keeps it near-linear: 400 labels are a few thousand box tests.
    */
  def cull(boxes: Seq[Box], cell: com.histmersion.web.LabelPlacement.CullCell = 128.0, shown: com.histmersion.web.LabelPlacement.CullShownArg1 => com.histmersion.web.LabelPlacement.CullShownResult = _ => true): Set[com.histmersion.web.LabelPlacement.CullResult] =
    val grid = mutable.HashMap.empty[(Int, Int), mutable.ArrayBuffer[Box]]
    val kept = mutable.Set.empty[String]
    def cells(b: Box, pad: Double): Seq[(Int, Int)] =
      val x0 = math.floor((b.cx - b.w / 2 - pad) / cell).toInt
      val x1 = math.floor((b.cx + b.w / 2 + pad) / cell).toInt
      val y0 = math.floor((b.cy - b.h / 2 - pad) / cell).toInt
      val y1 = math.floor((b.cy + b.h / 2 + pad) / cell).toInt
      for gx <- x0 to x1; gy <- y0 to y1 yield (gx, gy)
    def overlaps(a: Box, b: Box, pad: Double): Boolean =
      math.abs(a.cx - b.cx) * 2 < a.w + b.w + 2 * pad && math.abs(a.cy - b.cy) * 2 < a.h + b.h + 2 * pad
    for b <- boxes.sortBy(b => (-b.rank, b.key)) do
      val pad = if shown(b.key) then PadPx else PadPx + AppearPadPx
      if !cells(b, pad).exists(c => grid.get(c).exists(_.exists(overlaps(b, _, pad)))) then
        kept += b.key
        cells(b, PadPx).foreach(c => grid.getOrElseUpdate(c, mutable.ArrayBuffer.empty) += b)
    kept.toSet
