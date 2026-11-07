package com.histmersion.web

import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** A CELL of a server tile: `n` items of one kind counted where there were more than a tile keeps alone
  * (`ui.map-tiles`, docs/map-layers.md) — at the items' mean point, with the least and the greatest middle of their
  * dates and their box.
  */
final case class TileCellPoint(
    key: TileCellPoint.Key,
    kind: DotKind,
    lat: TileCellPoint.Lat,
    lon: TileCellPoint.Lon,
    n: TileCellPoint.N,
    midLo: Option[TileCellPoint.MidLo],
    midHi: Option[TileCellPoint.MidHi],
    box: GeoBounds,
    undated: TileCellPoint.Undated = com.histmersion.web.TileCellPoint.Undated(0)
)

object TileCellPoint:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lat = Lat.T
  object Lat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lon = Lon.T
  object Lon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type N = N.T
  object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MidLo = MidLo.T
  object MidLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MidHi = MidHi.T
  object MidHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Undated = Undated.T
  object Undated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
