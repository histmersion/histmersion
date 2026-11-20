package com.histmersion
package web.map.maplibre

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.web.BaseMaps

import scala.scalajs.js
import com.histmersion.web.map.BaseMapSpec

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
    assertEquals(
      BaseMaps.creditsHtml(
        Seq(
          BaseMapSpec.Raster.Attribution(" © OSM "),
          BaseMapSpec.Raster.Attribution("Mapterhorn"),
          BaseMapSpec.Raster.Attribution("© OSM"),
          BaseMapSpec.Raster.Attribution("")
        )
      ),
      "© OSM · Mapterhorn"
    )
    assertEquals(BaseMaps.creditsHtml(Nil), "")

  test("a credit is markup through the allow-list: its links stay, a script, a handler or a script link never runs"):
    assertEquals(
      BaseMaps.creditsHtml(
        Seq(
          BaseMapSpec.Raster.Attribution("""© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>"""),
          BaseMapSpec.Raster.Attribution("""<img src=x onerror="alert(1)"><a href="javascript:alert(2)">x</a>"""),
          BaseMapSpec.Raster.Attribution("<script>alert(3)</script>")
        )
      ),
      """© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · """ +
        """&lt;img src=x onerror=&quot;alert(1)&quot;&gt;<a>x</a> · &lt;script&gt;alert(3)&lt;/script&gt;"""
    )
