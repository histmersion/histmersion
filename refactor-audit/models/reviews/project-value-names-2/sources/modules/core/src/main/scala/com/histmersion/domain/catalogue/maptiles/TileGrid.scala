package com.histmersion.domain.catalogue.maptiles

/** How a layer's items become a tile's features ([[com.histmersion.api.catalogue.TileLayer]]): below `maxZoom` a tile
  * holding more than `maxPoints` items counts them by square cells of `cellUnits` tile units, one feature a cell; at
  * `maxZoom` (the deepest tile cut — deeper views draw it larger) every item is one.
  */
final case class TileGrid private (maxZoom: TileGrid.MaxZoom, maxPoints: TileGrid.MaxPoints, cellUnits: TileGrid.CellUnits):
  /** The most items `c` keeps as features of their own before it groups them by cell. */
  def pointsAt(c: TileCoord): com.histmersion.domain.catalogue.maptiles.TileGrid.PointsAtResult = if c.z >= maxZoom then Int.MaxValue else maxPoints

object TileGrid:
  /** A tile is 512 CSS pixels a side where MapLibre draws it at its own zoom. */
  type PointsAtResult = PointsAtResult.T
  object PointsAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilePx = TilePxValue
  type TilePxValue = TilePxValue.T
  object TilePxValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMaxZoom = OfMaxZoom.T
  object OfMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMaxPoints = OfMaxPoints.T
  object OfMaxPoints extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfCellPx = OfCellPx.T
  object OfCellPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxPoints = MaxPoints.T
  object MaxPoints extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CellUnits = CellUnits.T
  object CellUnits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val TilePx: com.histmersion.domain.catalogue.maptiles.TileGrid.TilePx = 512

  /** The grid of `ui.map-tiles`: tiles to `maxZoom`, groups above `maxPoints` items, cells of `cellPx` pixels (each
    * held to at least 1 and at most a tile).
    */
  def of(maxZoom: com.histmersion.domain.catalogue.maptiles.TileGrid.OfMaxZoom, maxPoints: com.histmersion.domain.catalogue.maptiles.TileGrid.OfMaxPoints, cellPx: com.histmersion.domain.catalogue.maptiles.TileGrid.OfCellPx): TileGrid =
    val px = cellPx.max(1).min(TilePx)
    new TileGrid(maxZoom.max(0).min(22), maxPoints.max(1), TileCoord.Extent * px / TilePx)
