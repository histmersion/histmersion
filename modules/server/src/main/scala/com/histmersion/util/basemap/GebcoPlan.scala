package com.histmersion
package util.basemap

import com.histmersion.api.BBox

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
  def readBytes: ByteCount = ByteCount.applyUnsafe(parts.map(_.bytes.value).sum)

object GebcoPlan:
  type TileKey = PmTiles.Entry.TileId
  val TileKey: PmTiles.Entry.TileId.type = PmTiles.Entry.TileId
  type TileX = PmTiles.TileColumn
  val TileX: PmTiles.TileColumn.type = PmTiles.TileColumn
  type TileY = PmTiles.TileRow
  val TileY: PmTiles.TileRow.type = PmTiles.TileRow
  type Zoom = PmTiles.Zoom
  val Zoom: PmTiles.Zoom.type = PmTiles.Zoom
