package com.histmersion.domain.catalogue.maptiles

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
    undated: TileCell.Undated = 0
):
  def isItem: com.histmersion.domain.catalogue.maptiles.TileCell.IsItemResult = n == 1

object TileCell:
  type IsItemResult = IsItemResult.T
  object IsItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type X = X.T
  object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Y = Y.T
  object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type N = N.T
  object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type Undated = Undated.T
  object Undated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
