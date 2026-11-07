package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

class BoundedCacheSuite extends munit.FunSuite:

  test("past its entry ceiling the least recently used entry goes") {
    val c = BoundedCache[String, Int](com.histmersion.web.BoundedCache.MaxEntries(2))
    c.put("a", 1); c.put("b", 2); c.put("c", 3)
    assertEquals(c.keys, List("b", "c"))
    assertEquals(c.get("a"), None)
  }

  test("a read makes an entry the most recently used: it outlives an older write") {
    val c = BoundedCache[String, Int](com.histmersion.web.BoundedCache.MaxEntries(2))
    c.put("a", 1); c.put("b", 2)
    assertEquals(c.get("a"), Some(1))
    c.put("c", 3)
    assertEquals(c.keys, List("a", "c"))
  }

  test("peek does not touch the recency") {
    val c = BoundedCache[String, Int](com.histmersion.web.BoundedCache.MaxEntries(2))
    c.put("a", 1); c.put("b", 2)
    assertEquals(c.peek("a"), Some(1))
    c.put("c", 3)
    assertEquals(c.keys, List("b", "c"))
  }

  test("past its weight ceiling the oldest go until the rest fits") {
    val c = BoundedCache[String, String](com.histmersion.web.BoundedCache.MaxEntries(10), maxWeight = com.histmersion.web.BoundedCache.MaxWeight(10), weigh = _.length.toLong)
    c.put("a", "xxxx"); c.put("b", "xxxx"); c.put("c", "xxxx")
    assertEquals(c.keys, List("b", "c"))
    assertEquals(c.weight, 8L)
  }

  test("one entry heavier than the budget is kept, alone") {
    val c = BoundedCache[String, String](com.histmersion.web.BoundedCache.MaxEntries(10), maxWeight = com.histmersion.web.BoundedCache.MaxWeight(5), weigh = _.length.toLong)
    c.put("a", "xx"); c.put("b", "xxxxxxxxxx")
    assertEquals(c.keys, List("b"))
    assertEquals(c.weight, 10L)
  }

  test("reweigh: a value whose size arrives later evicts the others it now crowds out, never itself") {
    val c = BoundedCache[String, String](com.histmersion.web.BoundedCache.MaxEntries(10), maxWeight = com.histmersion.web.BoundedCache.MaxWeight(10))
    c.put("a", "pending"); c.put("b", "pending"); c.put("c", "pending")
    assertEquals(c.weight, 0L)
    c.reweigh("a", com.histmersion.web.BoundedCache.ReweighW(4)); c.reweigh("c", com.histmersion.web.BoundedCache.ReweighW(4))
    assertEquals(c.keys, List("a", "b", "c"))
    c.reweigh("b", com.histmersion.web.BoundedCache.ReweighW(6))
    assertEquals(c.keys, List("b", "c"))
    assertEquals(c.weight, 10L)
  }

  test("put over an existing key replaces its weight; remove and clear give it back") {
    val c = BoundedCache[String, String](com.histmersion.web.BoundedCache.MaxEntries(10), maxWeight = com.histmersion.web.BoundedCache.MaxWeight(100), weigh = _.length.toLong)
    c.put("a", "xxx"); c.put("a", "x")
    assertEquals((c.size, c.weight), (1, 1L))
    c.put("b", "xx")
    assertEquals(c.remove("a"), Some("x"))
    assertEquals(c.weight, 2L)
    c.clear()
    assertEquals((c.size, c.weight), (0, 0L))
  }

  test("getOrElseUpdate computes once and then answers from the cache") {
    val c = BoundedCache[String, Int](com.histmersion.web.BoundedCache.MaxEntries(3))
    var calls = 0
    def v = { calls += 1; 7 }
    assertEquals(c.getOrElseUpdate("k", v), 7)
    assertEquals(c.getOrElseUpdate("k", v), 7)
    assertEquals(calls, 1)
  }

  test("stats reports entries and weight against the ceilings") {
    val c = BoundedCache[String, String](com.histmersion.web.BoundedCache.MaxEntries(4), maxWeight = com.histmersion.web.BoundedCache.MaxWeight(9), weigh = _.length.toLong)
    c.put("a", "abc")
    assertEquals(BoundedCache.stats(c), BoundedCache.Stats(com.histmersion.web.BoundedCache.Stats.Entries(1), com.histmersion.web.BoundedCache.Stats.MaxEntries(4), com.histmersion.web.BoundedCache.Stats.Weight(3L), com.histmersion.web.BoundedCache.Stats.MaxWeight(9L)))
  }
