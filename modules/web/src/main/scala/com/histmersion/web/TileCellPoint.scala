package com.histmersion
package web

import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Geo

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
    undated: TileCellPoint.Undated = TileCellPoint.Undated(0)
)

object TileCellPoint:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  export Geo.Latitude as Lat
  export Geo.Longitude as Lon
  export Member.N
  type MidLo = MidLo.T
  object MidLo extends Newtype[Double, Pure]
  type MidHi = MidHi.T
  object MidHi extends Newtype[Double, Pure]
  type Undated = Undated.T
  object Undated extends Newtype[Int, Pure]
