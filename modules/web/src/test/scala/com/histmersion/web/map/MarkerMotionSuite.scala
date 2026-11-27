package com.histmersion
package web.map

import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
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

  test("a motion starts at the first frame that draws it: its first painted state is its start whatever the gap"):
    val classAdded = 1000.0
    List(0.0, 16.0, 55.0, 125.0, 400.0).foreach { gap =>
      val firstFrame = classAdded + gap
      val clock = MarkerMotion.Clock.Pending.startedBy(firstFrame)
      val slide = MarkerMotion.motion(Set("hd-cluster-move"), 30, -12, clock.elapsed(firstFrame), still = false)
      assertEquals((slide.dx, slide.dy, slide.running), (30.0, -12.0, true), s"a slide first drawn $gap ms late")
      val fade = MarkerMotion.motion(Set("hd-fade-in"), 0, 0, clock.elapsed(firstFrame), still = false)
      assertEquals(fade.alpha, 0.0, s"a fade first drawn $gap ms late")
      val entering = MarkerMotion.motion(Set("hd-entering"), 0, 0, clock.elapsed(firstFrame), still = false)
      assertEquals(entering.scale, 0.0, s"a dot first drawn $gap ms late")
      assertEquals(clock.elapsed(firstFrame + 125), 125.0)
    }

  test("a pending motion stands at its start until a frame starts it, and a started one keeps its start"):
    assertEquals(MarkerMotion.Clock.Pending.elapsed(5000), 0.0)
    val started = MarkerMotion.Clock.Pending.startedBy(1000)
    assertEquals(started, MarkerMotion.Clock.Started(1000))
    assertEquals(started.startedBy(2000), started)
    assertEquals(started.elapsed(1250), 250.0)

  test("an arriving bubble stays hidden for the first 55 % of the move"):
    assertEquals(MarkerMotion.motion(Set("hd-cluster-arrive"), 0, 0, 100, still = false).alpha, 0.0)
    assertEquals(MarkerMotion.motion(Set("hd-cluster-arrive"), 0, 0, 250, still = false).alpha, 1.0)

  test("a dot grows in past its size and settles; a fading layer fades in"):
    val mid = MarkerMotion.motion(Set("hd-entering"), 0, 0, 240, still = false)
    assertEqualsDouble(mid.scale, 1.15, 1e-9)
    assertEqualsDouble(MarkerMotion.motion(Set("hd-entering"), 0, 0, 400, still = false).scale, 1.0, 1e-9)
    assertEquals(MarkerMotion.motion(Set("hd-fade-in"), 0, 0, 0, still = false).alpha, 0.0)

  test("still motion draws the end at once: nothing moves, what leaves is gone"):
    assertEquals(
      MarkerMotion.motion(Set("hd-cluster-move"), 30, 30, 0, still = true),
      MarkerMotion.Motion(0, 0, 1, 1, false)
    )
    assertEquals(MarkerMotion.motion(Set("hd-fade-out"), 0, 0, 0, still = true).alpha, 0.0)
