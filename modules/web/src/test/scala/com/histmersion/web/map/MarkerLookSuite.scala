package com.histmersion
package web.map

class MarkerLookSuite extends munit.FunSuite:

  test("two looks that differ only in their text share a shape (a bubble's count), their markup does not"):
    val two = """hd-marker|<div class="hd-bubble"><span class="hd-dot-count">2</span></div>"""
    val many = """hd-marker|<div class="hd-bubble"><span class="hd-dot-count">137</span></div>"""
    assertEquals(MarkerLook.shapeKey(two), MarkerLook.shapeKey(many))
    assertNotEquals(
      MarkerLook.shapeKey(two),
      MarkerLook.shapeKey("""hd-marker|<div class="hd-ring"><span>2</span></div>""")
    )
    assertEquals(MarkerLook.shapeKey("hd-marker|"), "hd-marker|")

  test("a list is split at its top-level commas only"):
    assertEquals(
      MarkerLook.splitTop("rgba(0, 0, 0, 0.5) 0px 1px 2px 0px, rgb(1, 2, 3) 0px 0px 0px 2px"),
      List("rgba(0, 0, 0, 0.5) 0px 1px 2px 0px", "rgb(1, 2, 3) 0px 0px 0px 2px")
    )

  test("box shadows: colour, offsets, blur, spread, inset; none is nothing"):
    assertEquals(
      MarkerLook.shadows("rgba(255, 255, 255, 0.35) 0px 0px 0px 2px, rgb(0, 0, 0) 0px 1px 2px 0px"),
      List(
        MarkerLook.Shadow("rgba(255, 255, 255, 0.35)", 0, 0, 0, 2, inset = false),
        MarkerLook.Shadow("rgb(0, 0, 0)", 0, 1, 2, 0, inset = false)
      )
    )
    assertEquals(MarkerLook.shadows("none"), Nil)
    assertEquals(MarkerLook.shadows("rgb(0, 0, 0) 0px 0px 2px 1px inset").map(_.inset), List(true))
    assertEquals(MarkerLook.shadows("rgba(0, 0, 0, 0.55) 0px 0px 2px").map(_.blur), List(2.0))

  test("a linear gradient: its angle (degrees or a side) and its stops, placed evenly when unstated"):
    assertEquals(
      MarkerLook.linear("linear-gradient(135deg, rgb(10, 20, 30) 0%, rgb(40, 50, 60) 100%)"),
      Some(
        MarkerLook.Linear(135, List(MarkerLook.Stop("rgb(10, 20, 30)", 0), MarkerLook.Stop("rgb(40, 50, 60)", 1)))
      )
    )
    assertEquals(
      MarkerLook.linear("linear-gradient(to right, red, green, blue)").map(g => (g.angleDeg, g.stops.map(_.at))),
      Some((90.0, List(0.0, 0.5, 1.0)))
    )
    assertEquals(MarkerLook.linear("none"), None)

  test("a transform matrix is read, anything else is none"):
    assertEquals(
      MarkerLook.matrix("matrix(0.7071, 0.7071, -0.7071, 0.7071, 0, 0)"),
      Some((0.7071, 0.7071, -0.7071, 0.7071, 0.0, 0.0))
    )
    assertEquals(MarkerLook.matrix("none"), None)

  test("a corner radius: pixels, or a share of the smaller side, never past half of it"):
    assertEquals(MarkerLook.radius("3px", 24, 24), 3.0)
    assertEquals(MarkerLook.radius("50%", 26, 26), 13.0)
    assertEquals(MarkerLook.radius("40px", 24, 20), 10.0)

  test("the SVG shapes become path data the canvas draws"):
    assertEquals(MarkerLook.rectPath(0, 0, 10, 4, 0, 0), "M 0 0 H 10 V 4 H 0 Z")
    assertEquals(MarkerLook.polyPath("1 2 3 4", closed = true), "M 1 2 L 3 4 Z")
    assertEquals(MarkerLook.polyPath("1,2 3,4 5,6", closed = false), "M 1 2 L 3 4 L 5 6")
    assert(MarkerLook.circlePath(12, 12, 10).startsWith("M 2 12 A 10 10"))
    assert(MarkerLook.rectPath(0, 0, 10, 10, 2, 2).contains("A 2 2"))
