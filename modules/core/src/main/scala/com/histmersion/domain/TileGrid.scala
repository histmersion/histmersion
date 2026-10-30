package com.histmersion.domain

/** How a layer's items become a tile's features ([[com.histmersion.api.TileLayer]]): below `maxZoom` a tile holding
  * more than `maxPoints` items counts them by square cells of `cellUnits` tile units, one feature a cell; at `maxZoom`
  * (the deepest tile cut — deeper views draw it larger) every item is one.
  */
final case class TileGrid private (maxZoom: Int, maxPoints: Int, cellUnits: Int):
  /** The most items `c` keeps as features of their own before it groups them by cell. */
  def pointsAt(c: TileCoord): Int = if c.z >= maxZoom then Int.MaxValue else maxPoints

object TileGrid:
  /** A tile is 512 CSS pixels a side where MapLibre draws it at its own zoom. */
  val TilePx: Int = 512

  /** The grid of `ui.map-tiles`: tiles to `maxZoom`, groups above `maxPoints` items, cells of `cellPx` pixels (each
    * held to at least 1 and at most a tile).
    */
  def of(maxZoom: Int, maxPoints: Int, cellPx: Int): TileGrid =
    val px = cellPx.max(1).min(TilePx)
    new TileGrid(maxZoom.max(0).min(22), maxPoints.max(1), TileCoord.Extent * px / TilePx)
