package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** How a set of shapes is drawn: a fill (`fillOpacity` 0: none) and an outline in the feature's colour (or `colour`, a
  * fixed CSS colour), `dash` for the features marked dashed, points as circles of `circleRadius`. `interactive`: the
  * set answers the pointer ([[MapSurface.shapesAt]]).
  */
final case class ShapeStyle(
    fillOpacity: ShapeStyle.FillOpacity = ShapeStyle.FillOpacity(0.0),
    lineWidth: ShapeStyle.LineWidth = ShapeStyle.LineWidth(1.5),
    lineOpacity: ShapeStyle.LineOpacity = ShapeStyle.LineOpacity(0.8),
    colour: Option[ShapeStyle.Colour] = None,
    dash: List[ShapeStyle.Dash] = Nil,
    dashedLineOpacity: Option[ShapeStyle.DashedLineOpacity] = None,
    circleRadius: ShapeStyle.CircleRadius = ShapeStyle.CircleRadius(0.0),
    interactive: ShapeStyle.Interactive = ShapeStyle.Interactive(false)
)

object ShapeStyle:
  type FillOpacity = FillOpacity.T
  object FillOpacity extends RefinedType[Double, Pure]
  type LineWidth = LineWidth.T
  object LineWidth extends RefinedType[Double, Pure]
  type LineOpacity = LineOpacity.T
  object LineOpacity extends RefinedType[Double, Pure]
  type Colour = Colour.T
  object Colour extends RefinedType[String, Pure]
  type Dash = Dash.T
  object Dash extends RefinedType[Double, Pure]
  type DashedLineOpacity = DashedLineOpacity.T
  object DashedLineOpacity extends RefinedType[Double, Pure]
  type CircleRadius = CircleRadius.T
  object CircleRadius extends RefinedType[Double, Pure]
  type Interactive = Interactive.T
  object Interactive extends RefinedType[Boolean, Pure]
