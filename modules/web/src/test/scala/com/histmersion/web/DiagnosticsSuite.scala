package com.histmersion.web

import scala.scalajs.js

class DiagnosticsSuite extends munit.FunSuite:

  test("toJs: one object per cache, an unbounded weight as -1") {
    val o = Diagnostics.toJs(List("a" -> BoundedCache.Stats(2, 10, 30L, 100L),
      "b" -> BoundedCache.Stats(1, 5, 0L, Long.MaxValue))).asInstanceOf[js.Dynamic]
    assertEquals(o.caches.a.entries.asInstanceOf[Int], 2)
    assertEquals(o.caches.a.maxEntries.asInstanceOf[Int], 10)
    assertEquals(o.caches.a.weight.asInstanceOf[Double], 30.0)
    assertEquals(o.caches.a.maxWeight.asInstanceOf[Double], 100.0)
    assertEquals(o.caches.b.maxWeight.asInstanceOf[Double], -1.0)
  }
