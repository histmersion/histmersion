package com.histmersion
package domain.catalogue.maptiles
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Count, Invalid}

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One tile of the XYZ grid MapLibre asks for (Web Mercator; a tile's side is [[TileCoord.Extent]] units of the vector
  * tile format): zoom `z`, column `x` from the antimeridian eastwards, row `y` from the north. Only a tile inside the
  * grid exists.
  */
final case class TileCoord private (z: TileCoord.Z, x: TileCoord.X, y: TileCoord.Y):
  /** Tiles a side at this zoom. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def side: TileCoord.TilesPerSide = TileCoord.TilesPerSide(1L << z.value)

  /** The tile's bounds in degrees: west, south, east, north. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def bounds: (Double, Double, Double, Double) =
    val n = side.value.toDouble
    (
      x.value / n * 360 - 180,
      TileCoord.latOf((y.value + 1) / n).value,
      (x.value + 1) / n * 360 - 180,
      TileCoord.latOf(y.value / n).value
    )

  /** Where `lat`/`lon` falls in this tile, in tile units (0 at its west and north edges, [[TileCoord.Extent]] at the
    * east and south): outside the tile below 0 or at the extent and beyond. Pure.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def unitsOf(lat: Latitude, lon: Longitude): (Double, Double) =
    val n = side.value.toDouble
    val (mx, my) = TileCoord.mercator(lat, lon)
    ((mx * n - x.value) * TileCoord.Extent.value, (my * n - y.value) * TileCoord.Extent.value)

object TileCoord:
  type TilesPerSide = TilesPerSide.T
  object TilesPerSide extends Newtype[Long, Pure]

  /** The units a tile's side is divided into (the vector tile format's usual extent). */
  type Extent = ExtentValue
  type ExtentValue = ExtentValue.T
  object ExtentValue extends Newtype[Int, Pure]
  type MaxLat = MaxLatValue
  type MaxLatValue = MaxLatValue.T
  object MaxLatValue extends Newtype[Double, Pure]

  type Z = Z.T
  object Z extends Newtype[Int, Pure]
  type X = X.T
  object X extends Newtype[Long, Pure]
  type Y = Y.T
  object Y extends Newtype[Long, Pure]

  val Extent: Extent = ExtentValue(4096)

  /** How far north and south the grid goes: Web Mercator's square world. */
  val MaxLat: MaxLat = MaxLatValue(85.0511287798)

  /** The tile `z`/`x`/`y`, when the grid has it and `z` is no deeper than `maxZoom`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(z: Z, x: X, y: Y, maxZoom: Z): Either[Problem, TileCoord] =
    if z.value < 0 || z > maxZoom then
      Left(Invalid.Broken.here(Invalid.Rule.ZoomWithin(Count.of(z), Count.of(maxZoom))))
    else if x.value < 0 || y.value < 0 || x.value >= (1L << z.value) || y.value >= (1L << z.value) then
      Left(Invalid.Broken.here(Invalid.Rule.TileWithin(Count.of(x), Count.of(y), Count.of(z))))
    else Right(new TileCoord(z, x, y))

  /** Pure: a point's place on the unit square of Web Mercator (0..1 east and south), its latitude held inside
    * [[MaxLat]] so a pole lands on the grid's edge.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mercator(lat: Latitude, lon: Longitude): (Double, Double) =
    val l = math.toRadians(math.max(-MaxLat.value, math.min(MaxLat.value, lat.value)))
    ((lon.value + 180) / 360, (1 - math.log(math.tan(l) + 1 / math.cos(l)) / math.Pi) / 2)

  /** Pure: the latitude of the unit square's row `v` (0 north, 1 south). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def latOf(v: Double): Latitude = Latitude.assume(math.toDegrees(math.atan(math.sinh(math.Pi * (1 - 2 * v)))))
