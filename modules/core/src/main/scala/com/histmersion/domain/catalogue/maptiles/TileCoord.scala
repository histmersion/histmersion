package com.histmersion.domain.catalogue.maptiles

/** One tile of the XYZ grid MapLibre asks for (Web Mercator; a tile's side is [[TileCoord.Extent]] units of the vector
  * tile format): zoom `z`, column `x` from the antimeridian eastwards, row `y` from the north. Only a tile inside the
  * grid exists.
  */
final case class TileCoord private (z: TileCoord.Z, x: TileCoord.X, y: TileCoord.Y):
  /** Tiles a side at this zoom. */
  def side: com.histmersion.domain.catalogue.maptiles.TileCoord.SideResult = 1L << z

  /** The tile's bounds in degrees: west, south, east, north. */
  def bounds: (com.histmersion.domain.catalogue.maptiles.TileCoord.BoundsResultPart1, com.histmersion.domain.catalogue.maptiles.TileCoord.BoundsResultPart2, com.histmersion.domain.catalogue.maptiles.TileCoord.BoundsResultPart3, com.histmersion.domain.catalogue.maptiles.TileCoord.BoundsResultPart4) =
    val n = side.toDouble
    (x / n * 360 - 180, TileCoord.latOf((y + 1) / n), (x + 1) / n * 360 - 180, TileCoord.latOf(y / n))

  /** Where `lat`/`lon` falls in this tile, in tile units (0 at its west and north edges, [[TileCoord.Extent]] at the
    * east and south): outside the tile below 0 or at the extent and beyond. Pure.
    */
  def unitsOf(lat: com.histmersion.Latitude, lon: com.histmersion.Longitude): (com.histmersion.domain.catalogue.maptiles.TileCoord.UnitsOfResultPart1, com.histmersion.domain.catalogue.maptiles.TileCoord.UnitsOfResultPart2) =
    val n = side.toDouble
    val (mx, my) = TileCoord.mercator(lat, lon)
    ((mx * n - x) * TileCoord.Extent, (my * n - y) * TileCoord.Extent)

object TileCoord:
  /** The units a tile's side is divided into (the vector tile format's usual extent). */
  type SideResult = SideResult.T
  object SideResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResultPart1 = BoundsResultPart1.T
  object BoundsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResultPart2 = BoundsResultPart2.T
  object BoundsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResultPart3 = BoundsResultPart3.T
  object BoundsResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResultPart4 = BoundsResultPart4.T
  object BoundsResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitsOfResultPart1 = UnitsOfResultPart1.T
  object UnitsOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitsOfResultPart2 = UnitsOfResultPart2.T
  object UnitsOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Extent = Extent.T
  object Extent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxLat = MaxLat.T
  object MaxLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResultPart1 = OfResultPart1.T
  object OfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfZ = OfZ.T
  object OfZ extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfX = OfX.T
  object OfX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfY = OfY.T
  object OfY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMaxZoom = OfMaxZoom.T
  object OfMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MercatorResultPart1 = MercatorResultPart1.T
  object MercatorResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MercatorResultPart2 = MercatorResultPart2.T
  object MercatorResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LatOfResult = LatOfResult.T
  object LatOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LatOfV = LatOfV.T
  object LatOfV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Z = Z.T
  object Z extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type X = X.T
  object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Y = Y.T
  object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Extent: com.histmersion.domain.catalogue.maptiles.TileCoord.Extent = 4096

  /** How far north and south the grid goes: Web Mercator's square world. */
  val MaxLat: com.histmersion.domain.catalogue.maptiles.TileCoord.MaxLat = 85.0511287798

  /** The tile `z`/`x`/`y`, when the grid has it and `z` is no deeper than `maxZoom`. */
  def of(z: com.histmersion.domain.catalogue.maptiles.TileCoord.OfZ, x: com.histmersion.domain.catalogue.maptiles.TileCoord.OfX, y: com.histmersion.domain.catalogue.maptiles.TileCoord.OfY, maxZoom: com.histmersion.domain.catalogue.maptiles.TileCoord.OfMaxZoom): Either[com.histmersion.domain.catalogue.maptiles.TileCoord.OfResultPart1, TileCoord] =
    if z < 0 || z > maxZoom then Left(s"zoom $z is outside 0..$maxZoom")
    else if x < 0 || y < 0 || x >= (1L << z) || y >= (1L << z) then Left(s"tile $x/$y is outside the grid of zoom $z")
    else Right(new TileCoord(z, x, y))

  /** Pure: a point's place on the unit square of Web Mercator (0..1 east and south), its latitude held inside
    * [[MaxLat]] so a pole lands on the grid's edge.
    */
  def mercator(lat: com.histmersion.Latitude, lon: com.histmersion.Longitude): (com.histmersion.domain.catalogue.maptiles.TileCoord.MercatorResultPart1, com.histmersion.domain.catalogue.maptiles.TileCoord.MercatorResultPart2) =
    val l = math.toRadians(math.max(-MaxLat, math.min(MaxLat, lat)))
    ((lon + 180) / 360, (1 - math.log(math.tan(l) + 1 / math.cos(l)) / math.Pi) / 2)

  /** Pure: the latitude of the unit square's row `v` (0 north, 1 south). */
  def latOf(v: com.histmersion.domain.catalogue.maptiles.TileCoord.LatOfV): com.histmersion.domain.catalogue.maptiles.TileCoord.LatOfResult = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * v))))
