package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js

/** A point feature of a [[PointTiles]] layer: where it is and its properties (`api.TileProps`). */
final case class TilePointFeature(at: LatLon, props: js.Dictionary[js.Any])
