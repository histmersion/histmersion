package com.histmersion.util.storage

import com.histmersion.testsupport.ComparedByValue.given

class StoreKeySuite extends munit.FunSuite:

  test("relative names of plain segments are keys; the empty name is the store itself"):
    List("a", "a/b", "vector-cache/tiles/4/0/0.mvt", ".vector.part", "terrain-focus/crete.pmtiles", "x..y", "...")
      .foreach(k => assert(Store.Key.either(k).isRight, k))
    assert(Store.Key.Root.isRoot)

  test("nothing outside the store can be named"):
    List("/etc/passwd", "../x", "a/../b", "a/..", "..", ".", "a/./b", "a//b", "a/", "/", "a\\b", "a\u0000b")
      .foreach(k => assert(Store.Key.either(k).isLeft, k))

  test("children, parents, names and suffixes"):
    val k = Store.Key.applyUnsafe("a/b/c.webp")
    assertEquals(k.name, "c.webp")
    assertEquals(k.parent.value: String, "a/b")
    assertEquals(k.withSuffix(com.histmersion.util.storage.TempName.Suffixed.Suffix(".tmp")).value: String, "a/b/c.webp.tmp")
    assertEquals(Store.Key.Root.child(com.histmersion.util.storage.Store.EntryName("x")).value: String, "x")
    assertEquals((Store.Key.applyUnsafe("a") / "b/c").map(k => k.value: String), Right("a/b/c"))
    assert((Store.Key.applyUnsafe("a") / "../c").isLeft)
    assert(k.within(Store.Key.applyUnsafe("a")) && k.within(Store.Key.Root) && !k.within(Store.Key.applyUnsafe("a/bb")))
    assertEquals(k.relativeTo(Store.Key.applyUnsafe("a")).value: String, "b/c.webp")
    assertEquals(Store.Key.of("a", "", "b").map(k => k.value: String), Right("a/b"))
