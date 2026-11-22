package com.histmersion
package web.map.maplibre

import scala.scalajs.js

class CarryOverlaysSuite extends munit.FunSuite:

  private def style(json: String): js.Dynamic = js.JSON.parse(json).asInstanceOf[js.Dynamic]

  private def ids(s: js.Dynamic): Seq[String] = s.layers.asInstanceOf[js.Array[js.Dynamic]].toSeq.map(_.id.toString)

  private def sourceIds(s: js.Dynamic): Seq[String] = js.Object.keys(s.sources.asInstanceOf[js.Object]).toSeq

  private val drawn = style("""{"version":8,"sources":{"v":{"type":"vector"},"contours":{"type":"vector"},
    "polities":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}},"tiles-events":{"type":"vector"}},
    "layers":[{"id":"bg","type":"background"},{"id":"land","source":"v"},{"id":"contours","source":"contours"},
    {"id":"names","source":"v"},{"id":"polities-fill","source":"polities"},{"id":"polities-line","source":"polities"},
    {"id":"tiles-events-points","source":"tiles-events"}]}""")

  private val overlays = Set("polities", "tiles-events")

  test("the overlays' sources and layers go into the new style, on top, in their order"):
    val next = style("""{"version":8,"sources":{"v":{"type":"vector"}},
      "layers":[{"id":"bg","type":"background"},{"id":"land","source":"v"}]}""")
    val out = MapLibreStyles.carryOverlays(drawn, next, overlays)
    assertEquals(ids(out), Seq("bg", "land", "polities-fill", "polities-line", "tiles-events-points"))
    assertEquals(sourceIds(out), Seq("v", "polities", "tiles-events"))
    assert(out.sources.polities eq drawn.sources.polities, "the same source object: a diff finds it unchanged")

  test("a base-map source or layer the new style leaves out stays out; the new style is not changed"):
    val next = style("""{"version":8,"sources":{"v":{"type":"vector"}},"layers":[{"id":"land","source":"v"}]}""")
    val out = MapLibreStyles.carryOverlays(drawn, next, overlays)
    assert(!sourceIds(out).contains("contours"))
    assert(!ids(out).contains("contours") && !ids(out).contains("names"))
    assertEquals(ids(next), Seq("land"))
    assertEquals(sourceIds(next), Seq("v"))

  test("an overlay layer the new style already has is not doubled; no style drawn yet answers the new one"):
    val next = style("""{"version":8,"sources":{"v":{"type":"vector"},"polities":{"type":"geojson"}},
      "layers":[{"id":"land","source":"v"},{"id":"polities-line","source":"polities"}]}""")
    val out = MapLibreStyles.carryOverlays(drawn, next, overlays)
    assertEquals(ids(out), Seq("land", "polities-line", "polities-fill", "tiles-events-points"))
    assert(out.sources.polities eq next.sources.polities)
    assert(MapLibreStyles.carryOverlays(js.undefined.asInstanceOf[js.Dynamic], next, overlays) eq next)
