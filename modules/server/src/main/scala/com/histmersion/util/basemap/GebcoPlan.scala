package com.histmersion.util.basemap

import com.histmersion.api.BBox

/** What a GEBCO fetch of a region reads and writes: the GeoTIFF parts, and the tiles (z, x, y) of zooms 0…`maxZoom`
  * over the region — `edge` are those reaching past it, whose outside is taken from ETOPO1's tile of the same place.
  */
final case class GebcoPlan(
    bbox: BBox,
    maxZoom: GebcoPlan.MaxZoom,
    parts: List[GebcoPart],
    tiles: Vector[(GebcoPlan.TilesPart1, (GebcoPlan.TilesPart2Part1, GebcoPlan.TilesPart2Part2, GebcoPlan.TilesPart2Part3))],
    edge: Set[(GebcoPlan.EdgePart1, GebcoPlan.EdgePart2, GebcoPlan.EdgePart3)]
):
  def readBytes: com.histmersion.ByteCount = parts.map(_.bytes).sum

object GebcoPlan:
  type TileKey = TileKey.T
  object TileKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileX = TileX.T
  object TileX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileY = TileY.T
  object TileY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Zoom = Zoom.T
  object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesPart1 = com.histmersion.util.basemap.GebcoPlan.TileKey
  val TilesPart1: _root_.com.histmersion.util.basemap.GebcoPlan.TileKey.type = _root_.com.histmersion.util.basemap.GebcoPlan.TileKey
  type TilesPart2Part1 = com.histmersion.util.basemap.GebcoPlan.Zoom
  val TilesPart2Part1: _root_.com.histmersion.util.basemap.GebcoPlan.Zoom.type = _root_.com.histmersion.util.basemap.GebcoPlan.Zoom
  type TilesPart2Part2 = com.histmersion.util.basemap.GebcoPlan.TileX
  val TilesPart2Part2: _root_.com.histmersion.util.basemap.GebcoPlan.TileX.type = _root_.com.histmersion.util.basemap.GebcoPlan.TileX
  type TilesPart2Part3 = com.histmersion.util.basemap.GebcoPlan.TileY
  val TilesPart2Part3: _root_.com.histmersion.util.basemap.GebcoPlan.TileY.type = _root_.com.histmersion.util.basemap.GebcoPlan.TileY
  type EdgePart1 = com.histmersion.util.basemap.GebcoPlan.Zoom
  val EdgePart1: _root_.com.histmersion.util.basemap.GebcoPlan.Zoom.type = _root_.com.histmersion.util.basemap.GebcoPlan.Zoom
  type EdgePart2 = com.histmersion.util.basemap.GebcoPlan.TileX
  val EdgePart2: _root_.com.histmersion.util.basemap.GebcoPlan.TileX.type = _root_.com.histmersion.util.basemap.GebcoPlan.TileX
  type EdgePart3 = com.histmersion.util.basemap.GebcoPlan.TileY
  val EdgePart3: _root_.com.histmersion.util.basemap.GebcoPlan.TileY.type = _root_.com.histmersion.util.basemap.GebcoPlan.TileY
