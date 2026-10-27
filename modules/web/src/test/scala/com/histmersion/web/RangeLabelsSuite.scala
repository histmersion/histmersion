package com.histmersion.web

import com.histmersion.web.RangeLabels.*

/** The timeline overview's "from" and "to" years never overlap and never leave the bar. */
class RangeLabelsSuite extends munit.FunSuite:

  private val bar = 600.0

  private def labels(p: Placement): List[Label] = p match
    case Placement.Inside(f, t)  => List(f, t)
    case Placement.Outside(f, t) => List(f, t)
    case Placement.Joined(l)     => List(l)

  private def assertReadable(p: Placement): Unit =
    labels(p).foreach(l => assert(l.x >= 0 && l.end <= bar, s"$l leaves the bar"))
    labels(p) match
      case List(f, t) => assert(f.end + Gap <= t.x, s"$f and $t overlap")
      case _          => ()

  test("a wide selection: both years inside it, next to their handles"):
    val p = place("1500 BCE", "1200 BCE", 100, 400, bar)
    p match
      case Placement.Inside(f, t) =>
        assertEqualsDouble(f.x, 100 + Pad, 1e-9)
        assertEqualsDouble(t.end, 400 - Pad, 1e-9)
      case other => fail(other.toString)
    assertReadable(p)

  test("close handles: the years move outside them, each on its own side"):
    val p = place("1500 BCE", "1450 BCE", 300, 320, bar)
    p match
      case Placement.Outside(f, t) =>
        assertEqualsDouble(f.end, 300 - Pad, 1e-9)
        assertEqualsDouble(t.x, 320 + Pad, 1e-9)
      case other => fail(other.toString)
    assertReadable(p)

  test("close handles at either end of the bar: pushed back into it, still apart"):
    for (x0, x1) <- List((2.0, 10.0), (588.0, 598.0)) do
      val p = place("3000 BCE", "2990 BCE", x0, x1, bar)
      assert(p.isInstanceOf[Placement.Outside], p.toString)
      assertReadable(p)

  test("no room even outside (a bar narrower than both years): one label for both, from the bar's start"):
    val p = place("3000 BCE", "2990 BCE", 40, 45, 80)
    p match
      case Placement.Joined(l) =>
        assertEquals(l.text, "3000 BCE – 2990 BCE")
        assertEqualsDouble(l.x, 0.0, 1e-9)
      case other => fail(other.toString)

  test("every handle position keeps the years readable"):
    for x0 <- 0 to 600 by 7; d <- List(0, 1, 5, 20, 60, 120) do
      val x1 = math.min(bar, x0 + d.toDouble)
      val p  = place("12500 BCE", "12480 BCE", x0.toDouble, x1, bar)
      assertReadable(p)
