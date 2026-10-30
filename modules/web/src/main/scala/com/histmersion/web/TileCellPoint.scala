package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.api.TileProps as P
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** A CELL of a server tile: `n` items of one kind counted where there were more than a tile keeps alone
  * (`ui.map-tiles`, docs/map-layers.md) — at the items' mean point, with the least and the greatest middle of their
  * dates and their box.
  */
final case class TileCellPoint(
    key: String,
    kind: DotKind,
    lat: Double,
    lon: Double,
    n: Int,
    midLo: Option[Double],
    midHi: Option[Double],
    box: GeoBounds,
    undated: Int = 0
)
