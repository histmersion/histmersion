package com.histmersion.api

object MapStack:

  type PointsAboveAreasResult = PointsAboveAreasResult.T
  object PointsAboveAreasResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Pane(val name: Pane.Name, val point: Pane.Point, val canvas: Pane.Canvas):
    case Areas extends Pane(_root_.com.histmersion.api.MapStack.Pane.Name("areas"), point = _root_.com.histmersion.api.MapStack.Pane.Point(false), canvas = _root_.com.histmersion.api.MapStack.Pane.Canvas(true))

    case AreaPoints extends Pane(_root_.com.histmersion.api.MapStack.Pane.Name("area-points"), point = _root_.com.histmersion.api.MapStack.Pane.Point(false), canvas = _root_.com.histmersion.api.MapStack.Pane.Canvas(false))

    case Labels extends Pane(_root_.com.histmersion.api.MapStack.Pane.Name("labels"), point = _root_.com.histmersion.api.MapStack.Pane.Point(false), canvas = _root_.com.histmersion.api.MapStack.Pane.Canvas(false))

    case Sites extends Pane(_root_.com.histmersion.api.MapStack.Pane.Name("sites"), point = _root_.com.histmersion.api.MapStack.Pane.Point(true), canvas = _root_.com.histmersion.api.MapStack.Pane.Canvas(false))

    case Markers extends Pane(_root_.com.histmersion.api.MapStack.Pane.Name("markers"), point = _root_.com.histmersion.api.MapStack.Pane.Point(true), canvas = _root_.com.histmersion.api.MapStack.Pane.Canvas(false))

    case Events extends Pane(_root_.com.histmersion.api.MapStack.Pane.Name("events"), point = _root_.com.histmersion.api.MapStack.Pane.Point(true), canvas = _root_.com.histmersion.api.MapStack.Pane.Canvas(false))

    def cssClass: com.histmersion.api.MapStack.Pane.CssClassResult = _root_.com.histmersion.api.MapStack.Pane.CssClassResult(s"hd-pane--$name")

  object Pane:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Point = Point.T
    object Point extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Canvas = Canvas.T
    object Canvas extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type CssClassResult = CssClassResult.T
    object CssClassResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val order: List[Pane] = Pane.values.toList

  val domPanes: List[Pane] = order.filterNot(_.canvas)

  def pointsAboveAreas(order: List[Pane]): com.histmersion.api.MapStack.PointsAboveAreasResult =
    val firstPoint = order.indexWhere(_.point)
    firstPoint < 0 || order.drop(firstPoint).forall(_.point)
