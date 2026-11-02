package com.histmersion.web

import com.histmersion.api.library.GeoHit
import com.histmersion.domain.library.Confidence

class MapViewSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  private def hit(start: Long, end: Long, conf: Confidence = Confidence.High): GeoHit =
    GeoHit("test", "Test", "claim", None, Some(30.0), Some(37.0), Some(start), Some(end), Some(conf))

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
    assertEqualsDouble(hi, -750.0, 0.01) // latest midpoint
  }

  test("midRange returns None for empty input") {
    assertEquals(MapView.midRange(Nil), None)
  }

  test("midRange returns None for all undated") {
    assertEquals(MapView.midRange(List(undatedHit)), None)
  }

  // ── colorFor ──────────────────────────────────────────────────────────────────

  test("colorFor at span start returns first viridis color") {
    val c = MapView.colorFor(Some(-3500.0), (-3500L, -500L), Certainty.Stated(Confidence.High))
    assertEquals(c, Fmt.color(0.0))
  }

  test("colorFor at span end returns last viridis color") {
    val c = MapView.colorFor(Some(-500.0), (-3500L, -500L), Certainty.Stated(Confidence.High))
    assertEquals(c, Fmt.color(1.0))
  }

  test("colorFor at span midpoint returns middle viridis color") {
    val c = MapView.colorFor(Some(-2000.0), (-3500L, -500L), Certainty.Stated(Confidence.High))
    assertEquals(c, Fmt.color(0.5))
  }

  test("colorFor with None mid returns first viridis stop") {
    val c = MapView.colorFor(None, (-3500L, -500L), Certainty.Stated(Confidence.High))
    assertEquals(c, Fmt.color(0.0))
  }

  test("colorFor desaturates for low confidence") {
    val highC = MapView.colorFor(Some(-2000.0), (-3500L, -500L), Certainty.Stated(Confidence.High))
    val lowC = MapView.colorFor(Some(-2000.0), (-3500L, -500L), Certainty.Stated(Confidence.Low))
    assert(highC != lowC, "low confidence should desaturate")
  }

  test("colorFor uses viewport span, not global span") {
    val wide = MapView.colorFor(Some(-2000.0), (-12000L, 2026L), Certainty.Stated(Confidence.High))
    val narrow = MapView.colorFor(Some(-2000.0), (-3500L, -500L), Certainty.Stated(Confidence.High))
    assert(wide != narrow, "different spans should produce different colors for the same year")
  }

  // ── gradientBg ────────────────────────────────────────────────────────────────

  test("gradientBg returns flat color for single item") {
    val items = List(hit(-3000, -2000))
    val bg = MapView.gradientBg(items, (-3500L, -500L), Certainty.Stated(Confidence.High))
    assert(bg.startsWith("rgb("), s"single item should be flat: $bg")
    assert(!bg.contains("gradient"), s"single item should not have gradient: $bg")
  }

  test("gradientBg returns gradient for items spanning a range") {
    val items = List(hit(-3000, -2000), hit(-1000, -500))
    val bg = MapView.gradientBg(items, (-3500L, -500L), Certainty.Stated(Confidence.High))
    assert(bg.contains("linear-gradient"), s"expected gradient but got: $bg")
    assert(bg.contains("135deg"), s"expected 135deg angle: $bg")
    // a VALUE for the marker's --dot-bg, not a declaration
    assert(bg.startsWith("linear-gradient("), bg)
  }

  test("gradientBg returns flat color for undated items") {
    val items = List(undatedHit, undatedHit)
    val bg = MapView.gradientBg(items, (-3500L, -500L), Certainty.Stated(Confidence.High))
    assert(bg.startsWith("rgb("), s"undated should be flat: $bg")
  }

  test("gradientBg returns flat color when all items have same midpoint") {
    val items = List(hit(-2000, -1000), hit(-2000, -1000))
    val bg = MapView.gradientBg(items, (-3500L, -500L), Certainty.Stated(Confidence.High))
    assert(!bg.contains("gradient"), s"same midpoints should be flat: $bg")
  }

  // ── the popup uses the shared atoms ────────────────────────────────────────────────────────────

  test("the finding popup links the source by DOI (else citekey) and shows a confidence badge") {
    val withDoi = hit(-1200, -1190, Confidence.Medium).copy(doi = Some("10.1/x"))
    val html = MapView.popupHtml(withDoi)
    assert(html.contains(com.histmersion.api.HtmlAtoms.sourceLink("test", Some("10.1/x"), "10.1/x")), html)
    assert(html.contains("conf-badge conf-medium"), html)
    assert(!html.contains("(medium)"), "the bare parenthesised word is gone: " + html)
    assert(MapView.popupHtml(hit(-1200, -1190)).contains(">test</a>"))
  }

  // ── a dot's hover: the source's card, never a bare citekey ──────────────────────────────────────

  test("a single finding's dot has no native tooltip; its hover is its source's card") {
    val one = List(hit(-3000, -2000).copy(citekey = "mathieson2018_genomic", title = "The genomic history"))
    assertEquals(MapView.markerTitle(one), None)
    assertEquals(MapView.hoverSource(one), Some("mathieson2018_genomic"))
  }

  test("coincident findings keep a readable native tooltip and no single source's card") {
    val three = List(hit(-3000, -2000), hit(-1500, -1400).copy(citekey = "b"), hit(-1200, -1100).copy(citekey = "c"))
    assertEquals(MapView.markerTitle(three), Some("3 findings here"))
    assertEquals(MapView.hoverSource(three), None)
    assert(!MapView.markerTitle(three).exists(_.contains("test")), "no citekey in it")
    assertEquals(MapView.markerTitle(Nil), None)
    assertEquals(MapView.hoverSource(Nil), None)
  }

  test("a single dot is named by its source's title and period; DOI, else citekey, only when untitled") {
    val pt = hit(-1200, -1190).copy(citekey = "zz2026_x", title = "Zz title")
    assertEquals(MapView.markerLabel(pt), s"Zz title (${Fmt.periodStr(-1200L, -1190L)})")
    assertEquals(MapView.markerLabel(undatedHit.copy(title = "Zz")), "Zz")
    assertEquals(
      MapView.markerLabel(pt.copy(title = " ", doi = Some("10.1/x"))),
      s"10.1/x (${Fmt.periodStr(-1200L, -1190L)})"
    )
    assertEquals(MapView.markerLabel(pt.copy(title = "")).startsWith("zz2026_x"), true)
  }

  // markerDiff (#887) is gone: the findings are a PointLayer like every kind, diffed by FadingLayer.plan
  // (ThrottleSuite), which takes snapshots of both key sets

  // ── the time rule: a place dated by its findings is drawn apart ─────────────────────────────────

  test("placeClasses adds hd-by-findings only for a place shown by its findings"):
    assertEquals(MapView.placeClasses("hd-site-marker", Some("findings")), "hd-site-marker hd-by-findings")
    assertEquals(MapView.placeClasses("hd-polity", Some("dated")), "hd-polity")
    assertEquals(MapView.placeClasses("hd-polity", Some("timeless")), "hd-polity")
    assertEquals(MapView.placeClasses("hd-polity", None), "hd-polity")

  // ── the "States shown" menu (GeoPick) ──────────────────────────────────────────

  test("the boundaries follow the menu over the span; playback always draws the playhead's one year"):
    import com.histmersion.api.GeoPick
    import com.histmersion.api.catalogue.GeoTime
    assertEquals(MapView.areaTime((-2000L, -1000L), GeoPick.Mid, playback = false), GeoTime.At(-1500))
    assertEquals(MapView.areaTime((-2000L, -1000L), GeoPick.Start, playback = false), GeoTime.At(-2000))
    assertEquals(MapView.areaTime((-2000L, -1000L), GeoPick.End, playback = false), GeoTime.At(-1000))
    assertEquals(
      MapView.areaTime((-2000L, -1000L), GeoPick.Latest, playback = false),
      GeoTime.Within(-2000, -1000, latest = true)
    )
    GeoPick.values.foreach(p => assertEquals(MapView.areaTime((-1210L, -1190L), p, playback = true), GeoTime.At(-1200)))

  test("the areas of a whole range are all asked for; a year keeps the year's limit"):
    import com.histmersion.api.catalogue.GeoTime
    import com.histmersion.web.ui.Layout
    assertEquals(MapView.overlayLimit(GeoTime.Within(-3000, 2000, latest = false)), Layout.overlayRangeFetchLimit)
    assertEquals(MapView.overlayLimit(GeoTime.At(1500)), Layout.overlayFetchLimit)
    assert(Layout.overlayRangeFetchLimit > Layout.overlayFetchLimit)

  test("the tiled layers are asked for the span itself; in playback for its window widened to whole buckets"):
    assertEquals(MapView.tileSpanOf((-1500L, -1600L), playback = false, 0.0, 1.0), (-1600L, -1500L))
    // 166 years a second, a bucket of one second: 200-year buckets
    assertEquals(MapView.tileSpanOf((-1650L, -1550L), playback = true, 166.0, 1.0), (-1800L, -1401L))
    // the next frames stay in the same buckets: the same tiles
    assertEquals(MapView.tileSpanOf((-1640L, -1540L), playback = true, 166.0, 1.0), (-1800L, -1401L))
    assertEquals(MapView.tileFilter((-10L, 20L)).period, Some(com.histmersion.api.catalogue.Period(-10L, 20L)))

  test("the query carries a snapshot as asOf and a whole range as from/to/pick"):
    import com.histmersion.api.Api
    import com.histmersion.api.catalogue.GeoTime
    assertEquals(
      MapView.geoQuery("polity", GeoTime.At(-1200), 400),
      Api.PlacesGeoQuery(kind = Some("polity"), asOf = Some(-1200L), limit = Some(400))
    )
    assertEquals(
      MapView.geoQuery("polity", GeoTime.Within(-2000, -1000, latest = false), 400),
      Api.PlacesGeoQuery(
        kind = Some("polity"),
        limit = Some(400),
        from = Some(-2000L),
        to = Some(-1000L),
        pick = Some("earliest")
      )
    )

  test("a disabled layer option says which switch turns it on"):
    import com.histmersion.web.MapLayer

  // ── waitsForQuiet (#1949) ───────────────────────────────────────────────────────

  test("waitsForQuiet: the boundaries over a whole range and the tiled layers wait for the span to settle") {
    import com.histmersion.api.GeoPick
    assert(MapView.waitsForQuiet(MapLayer.Boundaries, GeoPick.Latest, playback = false))
    assert(MapView.waitsForQuiet(MapLayer.Boundaries, GeoPick.Earliest, playback = false))
    for p <- List(GeoPick.Mid, GeoPick.Start, GeoPick.End) do
      assert(!MapView.waitsForQuiet(MapLayer.Boundaries, p, playback = false), s"a snapshot ($p) follows the drag")
    assert(!MapView.waitsForQuiet(MapLayer.Boundaries, GeoPick.Latest, playback = true), "playback draws one year")
    for l <- List(MapLayer.Sites, MapLayer.Media, MapLayer.Events) do
      assert(MapView.waitsForQuiet(l, GeoPick.Mid, playback = false), s"$l waits: a new span is every tile cut again")
      assert(!MapView.waitsForQuiet(l, GeoPick.Mid, playback = true), s"$l follows playback by its buckets")
  }
