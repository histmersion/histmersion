package com.histmersion
package web

import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Doi

class ComponentsSuite extends munit.FunSuite:

  test("a source link is labelled by DOI when present, else by citekey") {
    assertEquals(SourceLink.idLabel(Citekey("a2020_b"), Some(Doi("10.1/x"))).value, "10.1/x")
    assertEquals(SourceLink.idLabel(Citekey("a2020_b"), Some(Doi("  "))).value, "a2020_b")
    assertEquals(SourceLink.idLabel(Citekey("a2020_b"), None).value, "a2020_b")
    assertEquals(SourceLink.href(Citekey("a b")), "/source/a%20b")
  }

  test("map layers round-trip through their wire names") {
    MapLayer.values.foreach(l => assertEquals(MapLayer.fromWire(l.wire), Some(l)))
    assertEquals(MapLayer.fromWire(MapLayer.Wire(" Sites ")), Some(MapLayer.Sites))
    assertEquals(MapLayer.fromWire(MapLayer.Wire("nope")), None)
  }

  test("every layer has its own icon, and icons are stroke paths (no emoji)") {
    assertEquals(MapLayer.values.map(_.icon).distinct.length, MapLayer.values.length)
    Icon.values.foreach(i => assert(i.paths.nonEmpty && i.paths.forall(_.startsWith("M")), i))
  }
