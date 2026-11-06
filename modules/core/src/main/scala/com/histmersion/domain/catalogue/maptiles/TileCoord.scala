package com.histmersion.domain.catalogue.maptiles

/** One tile of the XYZ grid MapLibre asks for (Web Mercator; a tile's side is [[TileCoord.Extent]] units of the vector
  * tile format): zoom `z`, column `x` from the antimeridian eastwards, row `y` from the north. Only a tile inside the
  * grid exists.
  */
final case class TileCoord private (z: TileCoord.Z, x: TileCoord.X, y: TileCoord.Y):
  /** Tiles a side at this zoom. */
  def side: com.histmersion.domain.catalogue.maptiles.TileCoord.TilesPerSide = 1L << z

  /** The tile's bounds in degrees: west, south, east, north. */
  def bounds: (Double, Double, Double, Double) =
    val n = side.toDouble
    (x / n * 360 - 180, TileCoord.latOf((y + 1) / n), (x + 1) / n * 360 - 180, TileCoord.latOf(y / n))

  /** Where `lat`/`lon` falls in this tile, in tile units (0 at its west and north edges, [[TileCoord.Extent]] at the
    * east and south): outside the tile below 0 or at the extent and beyond. Pure.
    */
  def unitsOf(lat: com.histmersion.Latitude, lon: com.histmersion.Longitude): (Double, Double) =
    val n = side.toDouble
    val (mx, my) = TileCoord.mercator(lat, lon)
    ((mx * n - x) * TileCoord.Extent, (my * n - y) * TileCoord.Extent)

object TileCoord:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesPerSide = TilesPerSide.T
  object TilesPerSide extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The units a tile's side is divided into (the vector tile format's usual extent). */
  type Extent = ExtentValue
  type ExtentValue = ExtentValue.T
  object ExtentValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxLat = MaxLatValue
  type MaxLatValue = MaxLatValue.T
  object MaxLatValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfZ = _root_.com.histmersion.domain.catalogue.maptiles.TileCoord.Z
  val OfZ: _root_.com.histmersion.domain.catalogue.maptiles.TileCoord.Z.type = _root_.com.histmersion.domain.catalogue.maptiles.TileCoord.Z
  type OfX = _root_.com.histmersion.domain.catalogue.maptiles.TileCoord.X
  val OfX: _root_.com.histmersion.domain.catalogue.maptiles.TileCoord.X.type = _root_.com.histmersion.domain.catalogue.maptiles.TileCoord.X
  type OfY = _root_.com.histmersion.domain.catalogue.maptiles.TileCoord.Y
  val OfY: _root_.com.histmersion.domain.catalogue.maptiles.TileCoord.Y.type = _root_.com.histmersion.domain.catalogue.maptiles.TileCoord.Y

  type Z = Z.T
  object Z extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type X = X.T
  object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Y = Y.T
  object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Extent: com.histmersion.domain.catalogue.maptiles.TileCoord.Extent = com.histmersion.domain.catalogue.maptiles.TileCoord.Extent(4096)

  /** How far north and south the grid goes: Web Mercator's square world. */
  val MaxLat: com.histmersion.domain.catalogue.maptiles.TileCoord.MaxLat = 85.0511287798

  /** The tile `z`/`x`/`y`, when the grid has it and `z` is no deeper than `maxZoom`. */
  def of(z: com.histmersion.domain.catalogue.maptiles.TileCoord.OfZ, x: com.histmersion.domain.catalogue.maptiles.TileCoord.OfX, y: com.histmersion.domain.catalogue.maptiles.TileCoord.OfY, maxZoom: com.histmersion.domain.catalogue.maptiles.TileCoord.Z): Either[com.histmersion.domain.catalogue.maptiles.TileCoord.Problem, TileCoord] =
    if z < 0 || z > maxZoom then Left(s"zoom $z is outside 0..$maxZoom")
    else if x < 0 || y < 0 || x >= (1L << z) || y >= (1L << z) then Left(s"tile $x/$y is outside the grid of zoom $z")
    else Right(new TileCoord(z, x, y))

  /** Pure: a point's place on the unit square of Web Mercator (0..1 east and south), its latitude held inside
    * [[MaxLat]] so a pole lands on the grid's edge.
    */
  def mercator(lat: com.histmersion.Latitude, lon: com.histmersion.Longitude): (Double, Double) =
    val l = math.toRadians(math.max(-MaxLat, math.min(MaxLat, lat)))
    ((lon + 180) / 360, (1 - math.log(math.tan(l) + 1 / math.cos(l)) / math.Pi) / 2)

  /** Pure: the latitude of the unit square's row `v` (0 north, 1 south). */
  def latOf(v: Double): com.histmersion.Latitude = math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * v))))
