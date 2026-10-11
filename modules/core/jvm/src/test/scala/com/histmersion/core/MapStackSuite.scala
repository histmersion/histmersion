package com.histmersion.core

import MapStack.Pane

class MapStackSuite extends munit.FunSuite:

  test("every point pane is above every area pane"):
    assert(MapStack.pointsAboveAreas(MapStack.order))
    val firstPoint = MapStack.order.indexWhere(_.point)
    assert(MapStack.order.take(firstPoint).forall(!_.point))
    assert(MapStack.order.contains(Pane.Sites) && MapStack.order.indexOf(Pane.Sites) > MapStack.order.indexOf(Pane.Areas))

  test("the soundness check catches an area above a point"):
    assert(!MapStack.pointsAboveAreas(List(Pane.Sites, Pane.Areas)))
    assert(!MapStack.pointsAboveAreas(List(Pane.Areas, Pane.Sites, Pane.Labels)))
    assert(MapStack.pointsAboveAreas(Nil))
    assert(MapStack.pointsAboveAreas(List(Pane.Areas, Pane.Labels)))

  test("the class Leaflet gives each pane"):
    assertEquals(Pane.Areas.cssClass, "leaflet-hd-areas-pane")
    assertEquals(Pane.AreaPoints.cssClass, "leaflet-hd-area-points-pane")
    assertEquals(Pane.Markers.cssClass, "leaflet-marker-pane")
    assertEquals(MapStack.order.filter(_.builtIn), List(Pane.Markers))

  test("only the panes Leaflet draws paths in are vector panes"):
    assertEquals(MapStack.order.filter(_.vector), List(Pane.Areas, Pane.Sites))
