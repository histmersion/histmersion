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
  def readBytes: Long = parts.map(_.bytes).sum

object GebcoPlan:
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesPart1 = TilesPart1.T
  object TilesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesPart2Part1 = TilesPart2Part1.T
  object TilesPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesPart2Part2 = TilesPart2Part2.T
  object TilesPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TilesPart2Part3 = TilesPart2Part3.T
  object TilesPart2Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgePart1 = EdgePart1.T
  object EdgePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgePart2 = EdgePart2.T
  object EdgePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgePart3 = EdgePart3.T
  object EdgePart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
