package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** How a set of shapes is drawn: a fill (`fillOpacity` 0: none) and an outline in the feature's colour (or `colour`, a
  * fixed CSS colour), `dash` for the features marked dashed, points as circles of `circleRadius`. `interactive`: the
  * set answers the pointer ([[MapSurface.shapesAt]]).
  */
final case class ShapeStyle(
    fillOpacity: Double = 0.0,
    lineWidth: Double = 1.5,
    lineOpacity: Double = 0.8,
    colour: Option[String] = None,
    dash: List[Double] = Nil,
    dashedLineOpacity: Option[Double] = None,
    circleRadius: Double = 0.0,
    interactive: Boolean = false
)
