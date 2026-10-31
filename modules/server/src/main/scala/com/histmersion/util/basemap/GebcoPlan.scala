package com.histmersion.util.basemap

import com.histmersion.api.BBox

/** What a GEBCO fetch of a region reads and writes: the GeoTIFF parts, and the tiles (z, x, y) of zooms 0…`maxZoom`
  * over the region — `edge` are those reaching past it, whose outside is taken from ETOPO1's tile of the same place.
  */
final case class GebcoPlan(
    bbox: BBox,
    maxZoom: Int,
    parts: List[GebcoPart],
    tiles: Vector[(Long, (Int, Long, Long))],
    edge: Set[(Int, Long, Long)]
):
  def readBytes: Long = parts.map(_.bytes).sum
