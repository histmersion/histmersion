package com.histmersion
package web

import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class BoundedCacheSuite extends munit.FunSuite:

  test("past its entry ceiling the least recently used entry goes") {
    val c = BoundedCache[String, Int](BoundedCache.MaxEntries(2))
    c.put("a", 1); c.put("b", 2); c.put("c", 3)
    assertEquals(c.keys, List("b", "c"))
    assertEquals(c.get("a"), None)
  }

  test("a read makes an entry the most recently used: it outlives an older write") {
    val c = BoundedCache[String, Int](BoundedCache.MaxEntries(2))
    c.put("a", 1); c.put("b", 2)
    assertEquals(c.get("a"), Some(1))
    c.put("c", 3)
    assertEquals(c.keys, List("a", "c"))
  }

  test("peek does not touch the recency") {
    val c = BoundedCache[String, Int](BoundedCache.MaxEntries(2))
    c.put("a", 1); c.put("b", 2)
    assertEquals(c.peek("a"), Some(1))
    c.put("c", 3)
    assertEquals(c.keys, List("b", "c"))
  }

  test("past its weight ceiling the oldest go until the rest fits") {
    val c = BoundedCache[String, String](
      BoundedCache.MaxEntries(10),
      maxWeight = BoundedCache.MaxWeight(10),
      weigh = v => BoundedCache.Weighed(v.length.toLong)
    )
    c.put("a", "xxxx"); c.put("b", "xxxx"); c.put("c", "xxxx")
    assertEquals(c.keys, List("b", "c"))
    assertEquals(c.weight.value, 8L)
  }

  test("one entry heavier than the budget is kept, alone") {
    val c = BoundedCache[String, String](
      BoundedCache.MaxEntries(10),
      maxWeight = BoundedCache.MaxWeight(5),
      weigh = v => BoundedCache.Weighed(v.length.toLong)
    )
    c.put("a", "xx"); c.put("b", "xxxxxxxxxx")
    assertEquals(c.keys, List("b"))
    assertEquals(c.weight.value, 10L)
  }

  test("reweigh: a value whose size arrives later evicts the others it now crowds out, never itself") {
    val c = BoundedCache[String, String](BoundedCache.MaxEntries(10), maxWeight = BoundedCache.MaxWeight(10))
    c.put("a", "pending"); c.put("b", "pending"); c.put("c", "pending")
    assertEquals(c.weight.value, 0L)
    c.reweigh("a", BoundedCache.ReweighW(4)); c.reweigh("c", BoundedCache.ReweighW(4))
    assertEquals(c.keys, List("a", "b", "c"))
    c.reweigh("b", BoundedCache.ReweighW(6))
    assertEquals(c.keys, List("b", "c"))
    assertEquals(c.weight.value, 10L)
  }

  test("put over an existing key replaces its weight; remove and clear give it back") {
    val c = BoundedCache[String, String](
      BoundedCache.MaxEntries(10),
      maxWeight = BoundedCache.MaxWeight(100),
      weigh = v => BoundedCache.Weighed(v.length.toLong)
    )
    c.put("a", "xxx"); c.put("a", "x")
    assertEquals((c.size.value, c.weight.value), (1, 1L))
    c.put("b", "xx")
    assertEquals(c.remove("a"), Some("x"))
    assertEquals(c.weight.value, 2L)
    c.clear()
    assertEquals((c.size.value, c.weight.value), (0, 0L))
  }

  test("getOrElseUpdate computes once and then answers from the cache") {
    val c = BoundedCache[String, Int](BoundedCache.MaxEntries(3))
    var calls = 0
    def v = { calls += 1; 7 }
    assertEquals(c.getOrElseUpdate("k", v), 7)
    assertEquals(c.getOrElseUpdate("k", v), 7)
    assertEquals(calls, 1)
  }

  test("stats reports entries and weight against the ceilings") {
    val c = BoundedCache[String, String](
      BoundedCache.MaxEntries(4),
      maxWeight = BoundedCache.MaxWeight(9),
      weigh = v => BoundedCache.Weighed(v.length.toLong)
    )
    c.put("a", "abc")
    assertEquals(
      BoundedCache.stats(c),
      BoundedCache.Stats(
        BoundedCache.Stats.Entries(1),
        BoundedCache.Stats.MaxEntries(4),
        BoundedCache.Stats.Weight(3L),
        BoundedCache.Stats.MaxWeight(9L)
      )
    )
  }
