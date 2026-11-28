package com.histmersion
package api

import MapStack.Pane
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MapStackSuite extends munit.FunSuite:

  test("every point pane is above every area pane"):
    assert(MapStack.pointsAboveAreas(MapStack.order))
    val firstPoint = MapStack.order.indexWhere(_.point.point)
    assert(MapStack.order.take(firstPoint).forall(!_.point.point))
    assert(
      MapStack.order.contains(Pane.Sites) && MapStack.order.indexOf(Pane.Sites) > MapStack.order.indexOf(Pane.Areas)
    )

  test("the soundness check catches an area above a point"):
    assert(!MapStack.pointsAboveAreas(List(Pane.Sites, Pane.Areas)))
    assert(!MapStack.pointsAboveAreas(List(Pane.Areas, Pane.Sites, Pane.Labels)))
    assert(MapStack.pointsAboveAreas(Nil))
    assert(MapStack.pointsAboveAreas(List(Pane.Areas, Pane.Labels)))

  test("each DOM pane's class; the areas are the canvas, under every pane"):
    assertEquals(Pane.AreaPoints.cssClass, "hd-pane--area-points")
    assertEquals(Pane.Markers.cssClass, "hd-pane--markers")
    assertEquals(MapStack.order.filter(_.canvas.canvas), List(Pane.Areas))
    assertEquals(MapStack.domPanes.headOption, Some(Pane.AreaPoints))
