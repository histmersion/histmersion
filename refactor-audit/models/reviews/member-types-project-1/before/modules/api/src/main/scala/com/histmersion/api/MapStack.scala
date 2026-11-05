package com.histmersion.api

object MapStack:

  enum Pane(val name: String, val point: Boolean, val canvas: Boolean):
    case Areas extends Pane("areas", point = false, canvas = true)

    case AreaPoints extends Pane("area-points", point = false, canvas = false)

    case Labels extends Pane("labels", point = false, canvas = false)

    case Sites extends Pane("sites", point = true, canvas = false)

    case Markers extends Pane("markers", point = true, canvas = false)

    case Events extends Pane("events", point = true, canvas = false)

    def cssClass: String = s"hd-pane--$name"

  val order: List[Pane] = Pane.values.toList

  val domPanes: List[Pane] = order.filterNot(_.canvas)

  def pointsAboveAreas(order: List[Pane]): Boolean =
    val firstPoint = order.indexWhere(_.point)
    firstPoint < 0 || order.drop(firstPoint).forall(_.point)
