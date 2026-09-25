package histdb.web

import histdb.core.GeoHit

class MapViewSuite extends munit.FunSuite:

  private def hit(start: Long, end: Long, conf: String = "high"): GeoHit =
    GeoHit("test", "Test", "claim", None, Some(30.0), Some(37.0),
      Some(start), Some(end), Some(conf))

  private def undatedHit: GeoHit =
    GeoHit("test", "Test", "claim", None, Some(30.0), Some(37.0), None, None, None)

  // ── midOf ─────────────────────────────────────────────────────────────────────

  test("midOf computes midpoint of a dated finding") {
    assertEquals(MapView.midOf(hit(-3000, -2000)), Some(-2500.0))
  }

  test("midOf returns None for undated finding") {
    assertEquals(MapView.midOf(undatedHit), None)
  }

  test("midOf handles single-year findings") {
    assertEquals(MapView.midOf(hit(-776, -776)), Some(-776.0))
  }

  // ── avgMid ────────────────────────────────────────────────────────────────────

  test("avgMid averages midpoints of dated findings") {
    val items = List(hit(-3000, -2000), hit(-1000, -500))
    val avg = MapView.avgMid(items)
    assert(avg.isDefined)
    assertEqualsDouble(avg.get, (-2500.0 + -750.0) / 2, 0.01)
  }

  test("avgMid skips undated findings") {
    val items = List(hit(-3000, -2000), undatedHit)
    assertEquals(MapView.avgMid(items), Some(-2500.0))
  }

  test("avgMid returns None for all undated") {
    assertEquals(MapView.avgMid(List(undatedHit, undatedHit)), None)
  }

  // ── midRange ──────────────────────────────────────────────────────────────────

  test("midRange returns min/max midpoints") {
    val items = List(hit(-3000, -2000), hit(-1000, -500), hit(-2000, -1500))
    val range = MapView.midRange(items)
    assert(range.isDefined)
    val (lo, hi) = range.get
    assertEqualsDouble(lo, -2500.0, 0.01) // earliest midpoint
    assertEqualsDouble(hi, -750.0, 0.01)  // latest midpoint
  }

  test("midRange returns None for empty input") {
    assertEquals(MapView.midRange(Nil), None)
  }

  test("midRange returns None for all undated") {
    assertEquals(MapView.midRange(List(undatedHit)), None)
  }

  // ── colorFor ──────────────────────────────────────────────────────────────────

  test("colorFor at span start returns first viridis color") {
    val c = MapView.colorFor(Some(-3500.0), (-3500L, -500L), "high")
    assertEquals(c, Fmt.color(0.0))
  }

  test("colorFor at span end returns last viridis color") {
    val c = MapView.colorFor(Some(-500.0), (-3500L, -500L), "high")
    assertEquals(c, Fmt.color(1.0))
  }

  test("colorFor at span midpoint returns middle viridis color") {
    val c = MapView.colorFor(Some(-2000.0), (-3500L, -500L), "high")
    assertEquals(c, Fmt.color(0.5))
  }

  test("colorFor with None mid returns first viridis stop") {
    val c = MapView.colorFor(None, (-3500L, -500L), "high")
    assertEquals(c, Fmt.color(0.0))
  }

  test("colorFor desaturates for low confidence") {
    val highC = MapView.colorFor(Some(-2000.0), (-3500L, -500L), "high")
    val lowC  = MapView.colorFor(Some(-2000.0), (-3500L, -500L), "low")
    assert(highC != lowC, "low confidence should desaturate")
  }

  test("colorFor uses viewport span, not global span") {
    val wide = MapView.colorFor(Some(-2000.0), (-12000L, 2026L), "high")
    val narrow = MapView.colorFor(Some(-2000.0), (-3500L, -500L), "high")
    assert(wide != narrow, "different spans should produce different colors for the same year")
  }

  // ── gradientBg ────────────────────────────────────────────────────────────────

  test("gradientBg returns flat color for single item") {
    val items = List(hit(-3000, -2000))
    val bg = MapView.gradientBg(items, (-3500L, -500L), "high")
    assert(bg.startsWith("background:rgb("), s"single item should be flat: $bg")
    assert(!bg.contains("gradient"), s"single item should not have gradient: $bg")
  }

  test("gradientBg returns gradient for items spanning a range") {
    val items = List(hit(-3000, -2000), hit(-1000, -500))
    val bg = MapView.gradientBg(items, (-3500L, -500L), "high")
    assert(bg.contains("linear-gradient"), s"expected gradient but got: $bg")
    assert(bg.contains("135deg"), s"expected 135deg angle: $bg")
  }

  test("gradientBg returns flat color for undated items") {
    val items = List(undatedHit, undatedHit)
    val bg = MapView.gradientBg(items, (-3500L, -500L), "high")
    assert(bg.startsWith("background:rgb("), s"undated should be flat: $bg")
  }

  test("gradientBg returns flat color when all items have same midpoint") {
    val items = List(hit(-2000, -1000), hit(-2000, -1000))
    val bg = MapView.gradientBg(items, (-3500L, -500L), "high")
    assert(!bg.contains("gradient"), s"same midpoints should be flat: $bg")
  }

  test("tile attribution credits OSM, and the HOT style's makers when it is on") {
    assertEquals(MapView.attributionFor("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"), "© OpenStreetMap contributors")
    assert(MapView.attributionFor("https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png").contains("Humanitarian"))
    assert(!MapView.attributionFor("anything").contains("Carto"))
  }
