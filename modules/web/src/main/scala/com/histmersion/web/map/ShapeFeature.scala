package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** One shape: its id (unique in its set), GeoJSON geometry, colour, whether it is drawn dashed, and its visibility
  * (0…1).
  */
final case class ShapeFeature(
    id: String,
    geometry: js.Any,
    colour: String = "",
    dashed: Boolean = false,
    alpha: Double = 1.0
)
