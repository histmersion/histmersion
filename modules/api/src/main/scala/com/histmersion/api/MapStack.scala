package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

@movesTo(FeaturePlace.ApiUi)
object MapStack:

  enum Pane(val name: Pane.Name, val point: Pane.Point, val canvas: Pane.Canvas):
    case Areas extends Pane(Pane.Name("areas"), point = Pane.Point.Area, canvas = Pane.Canvas.Canvas)

    case AreaPoints extends Pane(Pane.Name("area-points"), point = Pane.Point.Area, canvas = Pane.Canvas.Dom)

    case Labels extends Pane(Pane.Name("labels"), point = Pane.Point.Area, canvas = Pane.Canvas.Dom)

    case Sites extends Pane(Pane.Name("sites"), point = Pane.Point.Point, canvas = Pane.Canvas.Dom)

    case Markers extends Pane(Pane.Name("markers"), point = Pane.Point.Point, canvas = Pane.Canvas.Dom)

    case Events extends Pane(Pane.Name("events"), point = Pane.Point.Point, canvas = Pane.Canvas.Dom)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def cssClass: String = (s"hd-pane--$name")

  object Pane:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    enum Point derives CanEqual:
      case Point, Area
    object Point extends TwoStates[Point]:
      extension (point: Point) def point: Boolean = point == Point
    enum Canvas derives CanEqual:
      case Canvas, Dom
    object Canvas extends TwoStates[Canvas]:
      extension (canvas: Canvas) def canvas: Boolean = canvas == Canvas

  val order: List[Pane] = Pane.values.toList

  val domPanes: List[Pane] = order.filterNot((refactorCallback => ((refactorCallback.canvas) == Pane.Canvas.Canvas)))

  def pointsAboveAreas(order: List[Pane]): Boolean =
    val firstPoint = order.indexWhere((refactorCallback => ((refactorCallback.point) == Pane.Point.Point)))
    (firstPoint < 0 || order
      .drop(firstPoint)
      .forall((refactorCallback => ((refactorCallback.point) == Pane.Point.Point))))
