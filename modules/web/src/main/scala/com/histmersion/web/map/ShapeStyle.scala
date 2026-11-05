package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** How a set of shapes is drawn: a fill (`fillOpacity` 0: none) and an outline in the feature's colour (or `colour`, a
  * fixed CSS colour), `dash` for the features marked dashed, points as circles of `circleRadius`. `interactive`: the
  * set answers the pointer ([[MapSurface.shapesAt]]).
  */
final case class ShapeStyle(
    fillOpacity: ShapeStyle.FillOpacity = 0.0,
    lineWidth: ShapeStyle.LineWidth = 1.5,
    lineOpacity: ShapeStyle.LineOpacity = 0.8,
    colour: Option[ShapeStyle.Colour] = None,
    dash: List[ShapeStyle.Dash] = Nil,
    dashedLineOpacity: Option[ShapeStyle.DashedLineOpacity] = None,
    circleRadius: ShapeStyle.CircleRadius = 0.0,
    interactive: ShapeStyle.Interactive = false
)

object ShapeStyle:
  type FillOpacity = FillOpacity.T
  object FillOpacity extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineWidth = LineWidth.T
  object LineWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineOpacity = LineOpacity.T
  object LineOpacity extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Colour = Colour.T
  object Colour extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dash = Dash.T
  object Dash extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DashedLineOpacity = DashedLineOpacity.T
  object DashedLineOpacity extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CircleRadius = CircleRadius.T
  object CircleRadius extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Interactive = Interactive.T
  object Interactive extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
