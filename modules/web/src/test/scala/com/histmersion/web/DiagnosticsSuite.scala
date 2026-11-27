package com.histmersion
package web

import scala.scalajs.js
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class DiagnosticsSuite extends munit.FunSuite:

  test("toJs: one object per cache, an unbounded weight as -1") {
    val o = Diagnostics
      .toJs(
        List(
          Diagnostics.CacheName("a") -> BoundedCache.Stats(
            BoundedCache.Stats.Entries(2),
            BoundedCache.Stats.MaxEntries(10),
            BoundedCache.Stats.Weight(30L),
            BoundedCache.Stats.MaxWeight(100L)
          ),
          Diagnostics.CacheName("b") -> BoundedCache.Stats(
            BoundedCache.Stats.Entries(1),
            BoundedCache.Stats.MaxEntries(5),
            BoundedCache.Stats.Weight(0L),
            BoundedCache.Stats.MaxWeight(Long.MaxValue)
          )
        )
      )
      .asInstanceOf[js.Dynamic]
    assertEquals(o.caches.a.entries.asInstanceOf[Int], 2)
    assertEquals(o.caches.a.maxEntries.asInstanceOf[Int], 10)
    assertEquals(o.caches.a.weight.asInstanceOf[Double], 30.0)
    assertEquals(o.caches.a.maxWeight.asInstanceOf[Double], 100.0)
    assertEquals(o.caches.b.maxWeight.asInstanceOf[Double], -1.0)
  }
