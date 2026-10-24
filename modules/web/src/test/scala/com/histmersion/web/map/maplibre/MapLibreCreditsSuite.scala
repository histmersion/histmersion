package com.histmersion.web.map.maplibre

import com.histmersion.web.BaseMaps

import scala.scalajs.js

class MapLibreCreditsSuite extends munit.FunSuite:

  private def style(json: String): js.Dynamic = js.JSON.parse(json).asInstanceOf[js.Dynamic]

  test("#3759: a style's credits are its drawn sources' attributions, once each, in order"):
    val s = style("""{"sources":{
      "v":{"type":"vector","attribution":"© OSM"},
      "sea":{"type":"raster-dem","attribution":"ETOPO1"},
      "dem":{"type":"raster-dem","attribution":"Mapterhorn"},
      "lc":{"type":"vector","attribution":"© OSM"},
      "none":{"type":"vector"}},
     "layers":[{"id":"bg","type":"background"},
      {"id":"a","source":"v"},{"id":"b","source":"lc"},
      {"id":"c","source":"sea","layout":{"visibility":"none"}},
      {"id":"d","source":"dem","layout":{"visibility":"visible"}},
      {"id":"e","source":"none"}]}""")
    assertEquals(MapLibreStyles.credits(s), Seq("© OSM", "Mapterhorn"))
    assertEquals(MapLibreStyles.credits(style("""{"version":8}""")), Nil)

  test("the credits line: each once, trimmed, middle dots between"):
    assertEquals(BaseMaps.creditsHtml(Seq(" © OSM ", "Mapterhorn", "© OSM", "")), "© OSM · Mapterhorn")
    assertEquals(BaseMaps.creditsHtml(Nil), "")
