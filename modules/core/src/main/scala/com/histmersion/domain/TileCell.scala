package com.histmersion.domain

/** What a tile's query answers before the tile is written: ONE item (`key` the item's, `n` 1) or a CELL of `n` items
  * (`key` the cell's), at `x`/`y` in tile units (a cell's: its items' mean), the least and greatest middle of their
  * dated spans (`None`: none of them is dated at both ends), their bounding box in degrees, and how many of them have
  * no known date (`undated`: drawn only when a viewer asks for them).
  */
final case class TileCell(
    key: String,
    x: Int,
    y: Int,
    n: Int,
    midLo: Option[Double],
    midHi: Option[Double],
    west: Double,
    south: Double,
    east: Double,
    north: Double,
    undated: Int = 0
):
  def isItem: Boolean = n == 1
