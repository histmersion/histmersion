package com.histmersion
package web.map

class MarkerMotionSuite extends munit.FunSuite:

  test("a cubic Bézier eases from 0 to 1, and linear is the identity"):
    assertEquals(MarkerMotion.bezier(0, 0, 0.25, 1, 0), 0.0)
    assertEquals(MarkerMotion.bezier(0, 0, 0.25, 1, 1), 1.0)
    assertEqualsDouble(MarkerMotion.bezier(0, 0, 1, 1, 0.3), 0.3, 1e-6)
    assert(MarkerMotion.bezier(0, 0, 0.25, 1, 0.5) > 0.5, "an ease-out is ahead at the middle")

  test("a split slides a marker from its bubble's place to its own over a quarter second"):
    val start = MarkerMotion.motion(Set("hd-cluster-move"), 30, -12, 0, still = false)
    assertEquals((start.dx, start.dy, start.running), (30.0, -12.0, true))
    val end = MarkerMotion.motion(Set("hd-cluster-move"), 30, -12, 300, still = false)
    assertEquals((end.dx, end.dy, end.running), (0.0, 0.0, false))

  test("a merging ghost flies to the bubble and fades to 35 %, and stays there"):
    val end = MarkerMotion.motion(Set("hd-cluster-move", "hd-cluster-ghost"), 20, 10, 1000, still = false)
    assertEquals((end.dx, end.dy, end.running), (20.0, 10.0, false))
    assertEqualsDouble(end.alpha, 0.35, 1e-9)

  test("an arriving bubble stays hidden for the first 55 % of the move"):
    assertEquals(MarkerMotion.motion(Set("hd-cluster-arrive"), 0, 0, 100, still = false).alpha, 0.0)
    assertEquals(MarkerMotion.motion(Set("hd-cluster-arrive"), 0, 0, 250, still = false).alpha, 1.0)

  test("a dot grows in past its size and settles; a fading layer fades in"):
    val mid = MarkerMotion.motion(Set("hd-entering"), 0, 0, 240, still = false)
    assertEqualsDouble(mid.scale, 1.15, 1e-9)
    assertEqualsDouble(MarkerMotion.motion(Set("hd-entering"), 0, 0, 400, still = false).scale, 1.0, 1e-9)
    assertEquals(MarkerMotion.motion(Set("hd-fade-in"), 0, 0, 0, still = false).alpha, 0.0)

  test("still motion draws the end at once: nothing moves, what leaves is gone"):
    assertEquals(MarkerMotion.motion(Set("hd-cluster-move"), 30, 30, 0, still = true), MarkerMotion.Motion(0, 0, 1, 1, false))
    assertEquals(MarkerMotion.motion(Set("hd-fade-out"), 0, 0, 0, still = true).alpha, 0.0)
