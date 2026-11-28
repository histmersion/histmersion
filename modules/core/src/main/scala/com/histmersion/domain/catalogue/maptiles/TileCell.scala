package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What a tile's query answers before the tile is written: ONE item (`key` the item's, `n` 1) or a CELL of `n` items
  * (`key` the cell's), at `x`/`y` in tile units (a cell's: its items' mean), the least and greatest middle of their
  * dated spans (`None`: none of them is dated at both ends), their bounding box in degrees, and how many of them have
  * no known date (`undated`: drawn only when a viewer asks for them).
  */
final case class TileCell(
    key: TileCell.Key,
    x: TileCell.X,
    y: TileCell.Y,
    n: TileCell.N,
    midLo: Option[TileCell.MidLo],
    midHi: Option[TileCell.MidHi],
    west: TileCell.West,
    south: TileCell.South,
    east: TileCell.East,
    north: TileCell.North,
    undated: TileCell.Undated = TileCell.Undated(0)
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isItem: Boolean = n.value == 1

object TileCell:

  type Key = Key.T
  object Key extends Newtype[String, Pure]
  export TilePoint.{X, Y}
  type N = N.T
  object N extends Newtype[Int, Pure]
  export GridCell.MidLo
  export GridCell.MidHi
  type West = West.T
  object West extends Newtype[Double, Pure]
  type South = South.T
  object South extends Newtype[Double, Pure]
  type East = East.T
  object East extends Newtype[Double, Pure]
  type North = North.T
  object North extends Newtype[Double, Pure]
  type Undated = Undated.T
  object Undated extends Newtype[Int, Pure]
