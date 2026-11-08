package com.histmersion
package api

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

object MapStack:


  enum Pane(val name: Pane.Name, val point: Pane.Point, val canvas: Pane.Canvas):
    case Areas extends Pane(Pane.Name("areas"), point = Pane.Point(false), canvas = Pane.Canvas(true))

    case AreaPoints extends Pane(Pane.Name("area-points"), point = Pane.Point(false), canvas = Pane.Canvas(false))

    case Labels extends Pane(Pane.Name("labels"), point = Pane.Point(false), canvas = Pane.Canvas(false))

    case Sites extends Pane(Pane.Name("sites"), point = Pane.Point(true), canvas = Pane.Canvas(false))

    case Markers extends Pane(Pane.Name("markers"), point = Pane.Point(true), canvas = Pane.Canvas(false))

    case Events extends Pane(Pane.Name("events"), point = Pane.Point(true), canvas = Pane.Canvas(false))

    def cssClass: String = (s"hd-pane--$name")

  object Pane:
    type Name = Name.T
    object Name extends RefinedType[String, Pure]
    type Point = Point.T
    object Point extends RefinedType[Boolean, Pure]
    type Canvas = Canvas.T
    object Canvas extends RefinedType[Boolean, Pure]


  val order: List[Pane] = Pane.values.toList

  val domPanes: List[Pane] = order.filterNot((refactorCallback => ((refactorCallback.canvas) == Pane.Canvas(true))))

  def pointsAboveAreas(order: List[Pane]): Boolean =
    val firstPoint = order.indexWhere((refactorCallback => ((refactorCallback.point) == Pane.Point(true))))
    (firstPoint < 0 || order.drop(firstPoint).forall((refactorCallback => ((refactorCallback.point) == Pane.Point(true)))))
