package com.histmersion
package web

import com.histmersion.api.catalogue.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Key extends RefinedType[String, Pure]
  type Lat = Lat.T
  object Lat extends RefinedType[Double, Pure]
  type Lon = Lon.T
  object Lon extends RefinedType[Double, Pure]
  type N = N.T
  object N extends RefinedType[Int, Pure]
  type MidLo = MidLo.T
  object MidLo extends RefinedType[Double, Pure]
  type MidHi = MidHi.T
  object MidHi extends RefinedType[Double, Pure]
  type Undated = Undated.T
  object Undated extends RefinedType[Int, Pure]
