package com.histmersion.util.storage

class StoreKeySuite extends munit.FunSuite:

  test("relative names of plain segments are keys; the empty name is the store itself"):
    List("a", "a/b", "vector-cache/tiles/4/0/0.mvt", ".vector.part", "terrain-focus/crete.pmtiles", "x..y", "...")
      .foreach(k => assert(StoreKey.either(k).isRight, k))
    assert(StoreKey.Root.isRoot)

  test("nothing outside the store can be named"):
    List("/etc/passwd", "../x", "a/../b", "a/..", "..", ".", "a/./b", "a//b", "a/", "/", "a\\b", "a\u0000b")
      .foreach(k => assert(StoreKey.either(k).isLeft, k))

  test("children, parents, names and suffixes"):
    val k = StoreKey.applyUnsafe("a/b/c.webp")
    assertEquals(k.name, "c.webp")
    assertEquals(k.parent.value: String, "a/b")
    assertEquals(k.withSuffix(".tmp").value: String, "a/b/c.webp.tmp")
    assertEquals(StoreKey.Root.child("x").value: String, "x")
    assertEquals((StoreKey.applyUnsafe("a") / "b/c").map(k => k.value: String), Right("a/b/c"))
    assert((StoreKey.applyUnsafe("a") / "../c").isLeft)
    assert(k.within(StoreKey.applyUnsafe("a")) && k.within(StoreKey.Root) && !k.within(StoreKey.applyUnsafe("a/bb")))
    assertEquals(k.relativeTo(StoreKey.applyUnsafe("a")).value: String, "b/c.webp")
    assertEquals(StoreKey.of("a", "", "b").map(k => k.value: String), Right("a/b"))
