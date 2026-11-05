package com.histmersion.domain.catalogue.maptiles

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
  object Gx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Gy = Gy.T
  object Gy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type N = N.T
  object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dup = Dup.T
  object Dup extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SumX = SumX.T
  object SumX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SumY = SumY.T
  object SumY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DupX = DupX.T
  object DupX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DupY = DupY.T
  object DupY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MidLo = MidLo.T
  object MidLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MidHi = MidHi.T
  object MidHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type West = West.T
  object West extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type South = South.T
  object South extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type East = East.T
  object East extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type North = North.T
  object North extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type One = One.T
  object One extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
