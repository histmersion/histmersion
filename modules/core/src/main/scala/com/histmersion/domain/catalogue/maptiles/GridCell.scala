package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The items of one layer in one square of a tile's grid (the squares a crowded tile counts its items by: `gx`/`gy` on
  * the grid of the whole world at the tile's zoom), summed so that two such counts of the same square add up: how many
  * (`n`), how many of them also have no known date (`dup`: an undated site the span shows by its findings, which the
  * undated sites' own count holds too), the sums of their places in the tile's units (all, and of those `dup`), the
  * least and greatest middle of their dated spans, their bounding box in degrees, and the least key among them.
  */
final case class GridCell(
    gx: GridCell.Gx,
    gy: GridCell.Gy,
    n: GridCell.N,
    dup: GridCell.Dup,
    sumX: GridCell.SumX,
    sumY: GridCell.SumY,
    dupX: GridCell.DupX,
    dupY: GridCell.DupY,
    midLo: Option[GridCell.MidLo],
    midHi: Option[GridCell.MidHi],
    west: GridCell.West,
    south: GridCell.South,
    east: GridCell.East,
    north: GridCell.North,
    one: GridCell.One
)

object GridCell:
  type Gx = Gx.T
  object Gx extends Newtype[Long, Pure]
  type Gy = Gy.T
  object Gy extends Newtype[Long, Pure]
  type N = N.T
  object N extends Newtype[Int, Pure]
  type Dup = Dup.T
  object Dup extends Newtype[Int, Pure]
  type SumX = SumX.T
  object SumX extends Newtype[Double, Pure]
  type SumY = SumY.T
  object SumY extends Newtype[Double, Pure]
  type DupX = DupX.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object DupX extends Newtype[Double, Pure]
  type DupY = DupY.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object DupY extends Newtype[Double, Pure]
  type MidLo = MidLo.T
  object MidLo extends Newtype[Double, Pure]
  type MidHi = MidHi.T
  object MidHi extends Newtype[Double, Pure]
  type West = West.T
  object West extends Newtype[Double, Pure]
  type South = South.T
  object South extends Newtype[Double, Pure]
  type East = East.T
  object East extends Newtype[Double, Pure]
  type North = North.T
  object North extends Newtype[Double, Pure]
  type One = One.T
  object One extends Newtype[String, Pure]
