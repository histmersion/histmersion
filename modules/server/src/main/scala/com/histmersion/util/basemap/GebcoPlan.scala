package com.histmersion
package util.basemap

import com.histmersion.api.BBox
import com.histmersion.lint.{tolerated, LintRule}

/** What a GEBCO fetch of a region reads and writes: the GeoTIFF parts, and the tiles (z, x, y) of zooms 0…`maxZoom`
  * over the region — `edge` are those reaching past it, whose outside is taken from ETOPO1's tile of the same place.
  */
final case class GebcoPlan(
    bbox: BBox,
    maxZoom: PmTiles.Zoom,
    parts: List[GebcoPart],
    tiles: Vector[(GebcoPlan.TileKey, (GebcoPlan.Zoom, GebcoPlan.TileX, GebcoPlan.TileY))],
    edge: Set[(GebcoPlan.Zoom, GebcoPlan.TileX, GebcoPlan.TileY)]
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def readBytes: ByteCount = ByteCount.applyUnsafe(parts.map(_.bytes.value).sum)

object GebcoPlan:
  export PmTiles.Entry.TileId as TileKey
  export PmTiles.{TileColumn as TileX, TileRow as TileY, Zoom}
