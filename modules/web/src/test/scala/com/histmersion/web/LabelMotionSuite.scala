package com.histmersion.web

import com.histmersion.web.LabelMotion.*
import com.histmersion.web.LabelPlacement.{Pt, Rect}

/** #1447 — a map name never jumps: it eases toward its target, a new target only bends its path, it fades and grows in
  * and out, and scales a little with a zoom before settling.
  */
class LabelMotionSuite extends munit.FunSuite:

  test("damp: reaches its target without overshoot, from either side, at any frame rate"):
    for (x0, target) <- Seq((0.0, 10.0), (10.0, 0.0)); dt <- Seq(1.0 / 120, 1.0 / 60, 1.0 / 20, 0.5) do
      var x = x0
      var v = 0.0
      for _ <- 0 until (3.0 / dt).toInt do
        val (nx, nv) = damp(x, v, target, dt)
        assert(if target > x0 then nx <= target + 1e-9 else nx >= target - 1e-9, s"overshoot $nx (dt $dt)")
        x = nx
        v = nv
      assertEqualsDouble(x, target, 1e-3, s"dt $dt")

  test("damp: a new target mid-way keeps position and velocity — the path bends, it never jumps"):
    var m = Motion.at(Pt(0, 0))
    val dt = 1.0 / 60
    for _ <- 0 until 6 do m = m.toward(Pt(10, 0), dt, reduced = false)
    val before = m
    // the end of the pan computes a better place: one frame later the name is a small step on
    val after = m.toward(Pt(4, 6), dt, reduced = false)
    val step = math.hypot(after.p.x - before.p.x, after.p.y - before.p.y)
    val speed = math.hypot(before.vx, before.vy) * dt
    assert(step <= speed * 1.5 + 0.2, s"step $step vs the previous frame's $speed")
    // and with reduced motion it snaps
    assertEquals(m.toward(Pt(4, 6), dt, reduced = true).p, Pt(4, 6))

  test("within: a name easing behind a pan is pushed along by the screen edge, its velocity kept"):
    val m = Motion(Pt(-5, 3), -2, 0)
    val inner = Rect(0, 0, 10, 10)
    assertEquals(m.within(inner), Motion(Pt(0, 3), -2, 0))
    assertEquals(Motion(Pt(4, 4), 1, 1).within(inner), Motion(Pt(4, 4), 1, 1))
    // a pan of 1 unit a frame moves the edge, and the pushed name, by exactly that much: no jump
    val path = (0 until 5).map(i => m.within(Rect(i.toDouble, 0, 10 + i, 10)).p.x)
    assertEquals(path.sliding(2).map(w => w(1) - w(0)).toSet, Set(1.0))

  test("fade: linear, reversible mid-way, bounded; the size grows in and shrinks out unless motion is reduced"):
    assertEqualsDouble(fade(0, shown = true, FadeSec / 2), 0.5, 1e-9)
    assertEqualsDouble(fade(0.5, shown = false, FadeSec / 4), 0.25, 1e-9)
    assertEquals(fade(0.9, shown = true, 1.0), 1.0)
    assertEquals(fade(0.1, shown = false, 1.0), 0.0)
    assertEqualsDouble(appearScale(0, reduced = false), GrowFrom, 1e-9)
    assertEqualsDouble(appearScale(1, reduced = false), 1.0, 1e-9)
    val grow = (0 to 10).map(i => appearScale(i / 10.0, reduced = false))
    assertEquals(grow, grow.sorted)
    assertEquals(appearScale(0, reduced = true), 1.0)

  test(
    "zoom: names follow part of the map's scaling and are 1 again when settled; a class change keeps the drawn width"
  ):
    assertEqualsDouble(zoomScale(6, 6, reduced = false), 1.0, 1e-12)
    assert(zoomScale(7, 6, reduced = false) > 1 && zoomScale(7, 6, reduced = false) < 2)
    assert(zoomScale(5, 6, reduced = false) < 1)
    assertEquals(zoomScale(7, 6, reduced = true), 1.0)
    // 80 px wide in the small class, 100 px in the next: drawn at 0.8 so the width is unchanged
    assertEqualsDouble(rebase(1.0, 80, 100, reduced = false) * 100, 80.0, 1e-9)
    assertEquals(rebase(1.0, 80, 100, reduced = true), 1.0)
