package com.histmersion.web

import com.histmersion.core.*

import scala.scalajs.js.typedarray.Uint8ClampedArray

/** The terrain base map's pure parts: the option, its notices, the tile templates, the land-cover
  * paint groups, and the relief's arithmetic. */
class TerrainMapSuite extends munit.FunSuite:

  private def layer(l: BasemapLayer, present: Boolean, version: Option[String] = Some("abc")) =
    BasemapLayerInfo(l.wire, present, s"/x/${l.file}", maxZoom = Some(8), bbox = Some("22.0,35.0,29.0,41.0"),
      version = version, attribution = s"credit ${l.wire}")

  private def status(vector: Boolean, terrain: Boolean) =
    BasemapStatus(vector, "/x", "-12,20,90,62", List(layer(BasemapLayer.Vector, vector), layer(BasemapLayer.Terrain, terrain)),
      Basemap.FetchCommand)

  test("the option is chosen and kept like the other base maps; its labels name it and say when it is missing"):
    assertEquals(BaseMap.fromWire("terrain"), Some(BaseMap.Terrain))
    assertEquals(summon[FieldCodec[BaseMap]].decode(List("terrain")), Some(BaseMap.Terrain))
    assertEquals(BaseMap.tone(BaseMap.Terrain, MapTone.Light), MapTone.Dark)
    assert(!BaseMap.darkened(BaseMap.Terrain, MapTone.Light), "its own colours are dark already")
    assertEquals(BaseMap.Terrain.url, None)
    assertEquals(TerrainMap.optionLabel(None), "Terrain (no modern features)")
    assertEquals(TerrainMap.optionLabel(Some(status(true, false))), "Terrain (no modern features)")
    assertEquals(TerrainMap.optionLabel(Some(status(false, false))), "Terrain (no modern features) — not downloaded")

  test("the notices: the past was different; when missing, what to run"):
    assert(TerrainMap.PastNotice.contains("Coastlines, rivers, lakes and vegetation were different in the past"))
    assert(TerrainMap.missingNotice(Some(status(false, false))).contains("`histmersion basemap fetch`"))
    assertEquals(TerrainMap.docsLink("https://d/guide/"), "https://d/guide/exploring/web-ui.md#the-terrain-map")
    assertEquals(TerrainMap.docsLink("https://d/guide"), "https://d/guide/exploring/web-ui.md#the-terrain-map")

  test("tile templates carry the layer's version; a missing layer has none; credits are joined"):
    val s = status(true, true)
    assertEquals(TerrainMap.template(s, BasemapLayer.Vector), Some("/api/basemap/vector/{z}/{x}/{y}?v=abc"))
    assertEquals(TerrainMap.template(status(true, false), BasemapLayer.Terrain), None)
    assertEquals(TerrainMap.attribution(s), "credit vector · credit terrain")
    assertEquals(TerrainMap.bounds(s.layers.head).map(_.toList.map(_.toList)), Some(List(List(35.0, 22.0), List(41.0, 29.0))))

  test("land cover kinds fall into the tint groups; modern land use has none"):
    assertEquals(TerrainMap.coverToken("wood"), Some("--terrain-forest"))
    assertEquals(TerrainMap.coverToken("marsh"), Some("--terrain-wetland"))
    assertEquals(TerrainMap.coverToken("glacier"), Some("--terrain-ice"))
    assertEquals(TerrainMap.coverToken("residential"), None)
    assertEquals(TerrainMap.coverToken("farmland"), None)

  test("terrarium elevations and ground resolution"):
    assertEquals(Hillshade.elevation(128, 0, 0), 0.0)
    assertEquals(Hillshade.elevation(128, 100, 128), 100.5)
    assertEqualsDouble(Hillshade.metresPerPixel(0, 0, 256), 156543.03, 1.0)
    assert(Hillshade.metresPerPixel(8, 90, 512) < Hillshade.metresPerPixel(8, 128, 512), "narrower towards the poles")
    assert(Hillshade.exaggeration(4) > Hillshade.exaggeration(10))
    assertEquals(Hillshade.exaggeration(11), 1.0)

  test("flat ground is transparent; a slope away from the north-west light is shadowed, one towards it lit"):
    val light = Hillshade.Light()
    val flat  = Hillshade.illumination(5, 5, 5, 5, 5, 5, 5, 5, 30, 1, light)
    assertEqualsDouble(flat, math.cos(math.toRadians(45)), 1e-9)
    // rising towards the south-east, a slope faces the north-west light; rising towards it, away
    val towards = Hillshade.illumination(0, 10, 20, 10, 30, 20, 30, 40, 30, 1, light)
    val away    = Hillshade.illumination(40, 30, 20, 30, 10, 20, 10, 0, 30, 1, light)
    assert(away < flat && towards > flat, s"$away $flat $towards")

    def tile(f: (Int, Int) => Double): Uint8ClampedArray =
      val a = new Uint8ClampedArray(4 * 4 * 4)
      for y <- 0 until 4; x <- 0 until 4 do
        val e = f(x, y) + 32768
        val o = (y * 4 + x) * 4
        a(o) = (e / 256).toInt; a(o + 1) = (e % 256).toInt; a(o + 2) = 0; a(o + 3) = 255
      a
    val shadow = Hillshade.Tint(0, 0, 0, 150)
    val lit    = Hillshade.Tint(255, 255, 255, 40)
    val out    = new Uint8ClampedArray(4 * 4 * 4)
    Hillshade.shade(tile((_, _) => 100), 4, 4, 30, 1, light, shadow, lit, out)
    assert((0 until 16).forall(p => out(p * 4 + 3) == 0), "flat: transparent")
    Hillshade.shade(tile((x, y) => (6 - x - y) * 50.0), 4, 4, 30, 1, light, shadow, lit, out)
    assertEquals(out(5 * 4), 0)
    assert(out(5 * 4 + 3) > 0 && out(5 * 4 + 3) <= 150, out(5 * 4 + 3).toString)
    Hillshade.shade(tile((x, y) => (x + y) * 50.0), 4, 4, 30, 1, light, shadow, lit, out)
    assertEquals(out(5 * 4), 255)
    assert(out(5 * 4 + 3) > 0 && out(5 * 4 + 3) <= 40, out(5 * 4 + 3).toString)
