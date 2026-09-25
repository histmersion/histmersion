package com.histmersion.web

class ComponentsSuite extends munit.FunSuite:

  test("a source link is labelled by DOI when present, else by citekey") {
    assertEquals(SourceLink.idLabel("a2020_b", Some("10.1/x")), "10.1/x")
    assertEquals(SourceLink.idLabel("a2020_b", Some("  ")), "a2020_b")
    assertEquals(SourceLink.idLabel("a2020_b", None), "a2020_b")
    assertEquals(SourceLink.href("a b"), "/source/a%20b")
  }

  test("map layers round-trip through their wire names") {
    MapLayer.values.foreach(l => assertEquals(MapLayer.fromWire(l.wire), Some(l)))
    assertEquals(MapLayer.fromWire(" Sites "), Some(MapLayer.Sites))
    assertEquals(MapLayer.fromWire("nope"), None)
  }

  test("every layer has its own icon, and icons are stroke paths (no emoji)") {
    assertEquals(MapLayer.values.map(_.icon).distinct.length, MapLayer.values.length)
    Icon.values.foreach(i => assert(i.paths.nonEmpty && i.paths.forall(_.startsWith("M")), i))
  }
