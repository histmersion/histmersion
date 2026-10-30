package com.histmersion.domain.maptiles

/** The items of one layer in one square of a tile's grid (the squares a crowded tile counts its items by: `gx`/`gy` on
  * the grid of the whole world at the tile's zoom), summed so that two such counts of the same square add up: how many
  * (`n`), how many of them also have no known date (`dup`: an undated site the span shows by its findings, which the
  * undated sites' own count holds too), the sums of their places in the tile's units (all, and of those `dup`), the
  * least and greatest middle of their dated spans, their bounding box in degrees, and the least key among them.
  */
final case class GridCell(
    gx: Long,
    gy: Long,
    n: Int,
    dup: Int,
    sumX: Double,
    sumY: Double,
    dupX: Double,
    dupY: Double,
    midLo: Option[Double],
    midHi: Option[Double],
    west: Double,
    south: Double,
    east: Double,
    north: Double,
    one: String
)
