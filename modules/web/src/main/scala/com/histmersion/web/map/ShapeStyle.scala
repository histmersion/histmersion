package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** How a set of shapes is drawn: a fill (`fillOpacity` 0: none) and an outline in the feature's colour (or `colour`, a
  * fixed CSS colour), `dash` for the features marked dashed, points as circles of `circleRadius`. `interactive`: the
  * set answers the pointer ([[MapSurface.shapesAt]]).
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ShapeStyle(
    fillOpacity: ShapeStyle.FillOpacity = ShapeStyle.FillOpacity(0.0),
    lineWidth: ShapeStyle.LineWidth = ShapeStyle.LineWidth(1.5),
    lineOpacity: ShapeStyle.LineOpacity = ShapeStyle.LineOpacity(0.8),
    colour: Option[ShapeStyle.Colour] = None,
    dash: List[ShapeStyle.Dash] = Nil,
    dashedLineOpacity: Option[ShapeStyle.DashedLineOpacity] = None,
    circleRadius: ShapeStyle.CircleRadius = ShapeStyle.CircleRadius(0.0),
    interactive: ShapeStyle.Interactive = ShapeStyle.Interactive.Inert
)

object ShapeStyle:
  type FillOpacity = FillOpacity.T
  object FillOpacity extends Newtype[Double, Pure]
  type LineWidth = LineWidth.T
  object LineWidth extends Newtype[Double, Pure]
  type LineOpacity = LineOpacity.T
  object LineOpacity extends Newtype[Double, Pure]
  export ShapeFeature.Colour
  type Dash = Dash.T
  object Dash extends Newtype[Double, Pure]
  type DashedLineOpacity = DashedLineOpacity.T
  object DashedLineOpacity extends Newtype[Double, Pure]
  type CircleRadius = CircleRadius.T
  object CircleRadius extends Newtype[Double, Pure]
  enum Interactive derives CanEqual:
    case Interactive, Inert
  object Interactive:
    extension (interactive: Interactive) def interactive: Boolean = interactive == Interactive
