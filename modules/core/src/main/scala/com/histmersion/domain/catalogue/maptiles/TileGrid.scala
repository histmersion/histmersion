package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** How a layer's items become a tile's features ([[com.histmersion.api.catalogue.TileLayer]]): below `maxZoom` a tile
  * holding more than `maxPoints` items counts them by square cells of `cellUnits` tile units, one feature a cell; at
  * `maxZoom` (the deepest tile cut — deeper views draw it larger) every item is one.
  */
final case class TileGrid private (
    maxZoom: TileGrid.MaxZoom,
    maxPoints: TileGrid.MaxPoints,
    cellUnits: TileGrid.CellUnits
):
  /** The most items `c` keeps as features of their own before it groups them by cell. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pointsAt(c: TileCoord): TileGrid.MaxPoints =
    if c.z.value >= maxZoom.value then TileGrid.MaxPoints(Int.MaxValue) else maxPoints

object TileGrid:
  /** A tile is 512 CSS pixels a side where MapLibre draws it at its own zoom. */
  type TilePx = TilePxValue
  type TilePxValue = TilePxValue.T
  object TilePxValue extends Newtype[Int, Pure]

  type MaxZoom = MaxZoom.T
  object MaxZoom extends Newtype[Int, Pure]
  type MaxPoints = MaxPoints.T
  object MaxPoints extends Newtype[Int, Pure]
  type CellPx = CellPx.T
  object CellPx extends Newtype[Int, Pure]
  type CellUnits = CellUnits.T
  object CellUnits extends Newtype[Int, Pure]

  val TilePx: TilePx = TilePxValue(512)

  /** The grid of `ui.map-tiles`: tiles to `maxZoom`, groups above `maxPoints` items, cells of `cellPx` pixels (each
    * held to at least 1 and at most a tile).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(maxZoom: MaxZoom, maxPoints: MaxPoints, cellPx: CellPx): TileGrid =
    val px = cellPx.value.max(1).min(TilePx.value)
    new TileGrid(
      MaxZoom(maxZoom.value.max(0).min(22)),
      MaxPoints(maxPoints.value.max(1)),
      CellUnits(TileCoord.Extent.value * px / TilePx.value)
    )
