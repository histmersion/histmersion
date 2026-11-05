package com.histmersion.api

object MapStack:

  type PointsAboveAreasResult = PointsAboveAreasResult.T
  object PointsAboveAreasResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Pane(val name: Pane.Name, val point: Pane.Point, val canvas: Pane.Canvas):
    case Areas extends Pane("areas", point = false, canvas = true)

    case AreaPoints extends Pane("area-points", point = false, canvas = false)

    case Labels extends Pane("labels", point = false, canvas = false)

    case Sites extends Pane("sites", point = true, canvas = false)

    case Markers extends Pane("markers", point = true, canvas = false)

    case Events extends Pane("events", point = true, canvas = false)

    def cssClass: com.histmersion.api.MapStack.Pane.CssClassResult = s"hd-pane--$name"

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
