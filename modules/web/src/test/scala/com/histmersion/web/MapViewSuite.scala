package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.library.GeoHit
import com.histmersion.api.library.Confidence
import com.histmersion.api.catalogue.{Latitude, Longitude, MapTilesUiConfig, Year}
import com.histmersion.api.library.Citekey
import com.histmersion.api.Years
import com.histmersion.api.library.Doi
import com.histmersion.api.Html.Atoms
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Period
import com.histmersion.api.Paging
import com.histmersion.api.GeoPick
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentInterp, "pending G35 (a vocabulary renders through its wire string)")
class MapViewSuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  private def hit(start: Year, end: Year, conf: Confidence = Confidence.High): GeoHit =
    GeoHit(
      Citekey("test"),
      GeoHit.Title("Test"),
      GeoHit.Claim("claim"),
      None,
      Some(Longitude(30.0)),
      Some(Latitude(37.0)),
      Some(start),
      Some(end),
      Some(conf)
    )

  private def undatedHit: GeoHit =
    GeoHit(
      Citekey("test"),
      GeoHit.Title("Test"),
      GeoHit.Claim("claim"),
      None,
      Some(Longitude(30.0)),
      Some(Latitude(37.0)),
      None,
      None,
      None
    )

  // ── midOf ─────────────────────────────────────────────────────────────────────

  test("midOf computes midpoint of a dated finding") {
    assertEquals(MapView.midOf(hit(Year(-3000), Year(-2000))), Some(-2500.0))
  }

  test("midOf returns None for undated finding") {
    assertEquals(MapView.midOf(undatedHit), None)
  }

  test("midOf handles single-year findings") {
    assertEquals(MapView.midOf(hit(Year(-776), Year(-776))), Some(-776.0))
  }

  // ── avgMid ────────────────────────────────────────────────────────────────────

  test("avgMid averages midpoints of dated findings") {
    val items = List(hit(Year(-3000), Year(-2000)), hit(Year(-1000), Year(-500)))
    val avg = MapView.avgMid(items)
    assert(avg.isDefined)
    assertEqualsDouble(avg.get.value, (-2500.0 + -750.0) / 2, 0.01)
  }

  test("avgMid skips undated findings") {
    val items = List(hit(Year(-3000), Year(-2000)), undatedHit)
    assertEquals(MapView.avgMid(items), Some(-2500.0))
  }

  test("avgMid returns None for all undated") {
    assertEquals(MapView.avgMid(List(undatedHit, undatedHit)), None)
  }

  // ── midRange ──────────────────────────────────────────────────────────────────

  test("midRange returns min/max midpoints") {
    val items = List(hit(Year(-3000), Year(-2000)), hit(Year(-1000), Year(-500)), hit(Year(-2000), Year(-1500)))
    val range = MapView.midRange(items)
    assert(range.isDefined)
    val (lo, hi) = range.get
    assertEqualsDouble(lo.value, -2500.0, 0.01) // earliest midpoint
    assertEqualsDouble(hi.value, -750.0, 0.01) // latest midpoint
  }

  test("midRange returns None for empty input") {
    assertEquals(MapView.midRange(Nil), None)
  }

  test("midRange returns None for all undated") {
    assertEquals(MapView.midRange(List(undatedHit)), None)
  }

  // ── colorFor ──────────────────────────────────────────────────────────────────

  test("colorFor at span start returns first viridis color") {
    val c = MapView.colorFor(
      Some(MapView.Mid(-3500.0)),
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.High)
    )
    assertEquals(c.value, Fmt.color(Fmt.RampPosition(0.0)).value)
  }

  test("colorFor at span end returns last viridis color") {
    val c = MapView.colorFor(
      Some(MapView.Mid(-500.0)),
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.High)
    )
    assertEquals(c.value, Fmt.color(Fmt.RampPosition(1.0)).value)
  }

  test("colorFor at span midpoint returns middle viridis color") {
    val c = MapView.colorFor(
      Some(MapView.Mid(-2000.0)),
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.High)
    )
    assertEquals(c.value, Fmt.color(Fmt.RampPosition(0.5)).value)
  }

  test("colorFor with None mid returns first viridis stop") {
    val c =
      MapView.colorFor(None, (Years.Astronomical(-3500L), Years.Astronomical(-500L)), Certainty.Stated(Confidence.High))
    assertEquals(c.value, Fmt.color(Fmt.RampPosition(0.0)).value)
  }

  test("colorFor desaturates for low confidence") {
    val highC = MapView.colorFor(
      Some(MapView.Mid(-2000.0)),
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.High)
    )
    val lowC = MapView.colorFor(
      Some(MapView.Mid(-2000.0)),
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.Low)
    )
    assert(highC != lowC, "low confidence should desaturate")
  }

  test("colorFor uses viewport span, not global span") {
    val wide = MapView.colorFor(
      Some(MapView.Mid(-2000.0)),
      (Years.Astronomical(-12000L), Years.Astronomical(2026L)),
      Certainty.Stated(Confidence.High)
    )
    val narrow = MapView.colorFor(
      Some(MapView.Mid(-2000.0)),
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.High)
    )
    assert(wide != narrow, "different spans should produce different colors for the same year")
  }

  // ── gradientBg ────────────────────────────────────────────────────────────────

  test("gradientBg returns flat color for single item") {
    val items = List(hit(Year(-3000), Year(-2000)))
    val bg = MapView.gradientBg(
      items,
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.High)
    )
    assert(bg.value.startsWith("rgb("), s"single item should be flat: $bg")
    assert(!bg.value.contains("gradient"), s"single item should not have gradient: $bg")
  }

  test("gradientBg returns gradient for items spanning a range") {
    val items = List(hit(Year(-3000), Year(-2000)), hit(Year(-1000), Year(-500)))
    val bg = MapView.gradientBg(
      items,
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.High)
    )
    assert(bg.value.contains("linear-gradient"), s"expected gradient but got: $bg")
    assert(bg.value.contains("135deg"), s"expected 135deg angle: $bg")
    // a VALUE for the marker's --dot-bg, not a declaration
    assert(bg.value.startsWith("linear-gradient("), bg.value)
  }

  test("gradientBg returns flat color for undated items") {
    val items = List(undatedHit, undatedHit)
    val bg = MapView.gradientBg(
      items,
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.High)
    )
    assert(bg.value.startsWith("rgb("), s"undated should be flat: $bg")
  }

  test("gradientBg returns flat color when all items have same midpoint") {
    val items = List(hit(Year(-2000), Year(-1000)), hit(Year(-2000), Year(-1000)))
    val bg = MapView.gradientBg(
      items,
      (Years.Astronomical(-3500L), Years.Astronomical(-500L)),
      Certainty.Stated(Confidence.High)
    )
    assert(!bg.value.contains("gradient"), s"same midpoints should be flat: $bg")
  }

  // ── the popup uses the shared atoms ────────────────────────────────────────────────────────────

  test("the finding popup links the source by DOI (else citekey) and shows a confidence badge") {
    val withDoi = hit(Year(-1200), Year(-1190), Confidence.Medium).modify(_.doi).setTo(Some(Doi("10.1/x")))
    val html = MapView.popupHtml(withDoi)
    assert(html.value.contains(Atoms.sourceLink(Citekey("test"), Some(Doi("10.1/x")), "10.1/x").value), html.value)
    assert(html.value.contains("conf-badge conf-medium"), html.value)
    assert(!html.value.contains("(medium)"), "the bare parenthesised word is gone: " + html.value)
    assert(MapView.popupHtml(hit(Year(-1200), Year(-1190))).value.contains(">test</a>"))
  }

  // ── a dot's hover: the source's card, never a bare citekey ──────────────────────────────────────

  test("a single finding's dot has no native tooltip; its hover is its source's card") {
    val one = List(
      hit(Year(-3000), Year(-2000))
        .modify(_.citekey)
        .setTo(Citekey("mathieson2018_genomic"))
        .modify(_.title)
        .setTo(GeoHit.Title("The genomic history"))
    )
    assertEquals(MapView.markerTitle(one), None)
    assertEquals(MapView.hoverSource(one), Some("mathieson2018_genomic"))
  }

  test("coincident findings keep a readable native tooltip and no single source's card") {
    val three = List(
      hit(Year(-3000), Year(-2000)),
      hit(Year(-1500), Year(-1400)).modify(_.citekey).setTo(Citekey("b")),
      hit(Year(-1200), Year(-1100)).modify(_.citekey).setTo(Citekey("c"))
    )
    assertEquals(MapView.markerTitle(three), Some("3 findings here"))
    assertEquals(MapView.hoverSource(three), None)
    assert(!MapView.markerTitle(three).exists(_.value.contains("test")), "no citekey in it")
    assertEquals(MapView.markerTitle(Nil), None)
    assertEquals(MapView.hoverSource(Nil), None)
  }

  test("a single dot is named by its source's title and period; DOI, else citekey, only when untitled") {
    val pt = hit(Year(-1200), Year(-1190))
      .modify(_.citekey)
      .setTo(Citekey("zz2026_x"))
      .modify(_.title)
      .setTo(GeoHit.Title("Zz title"))
    assertEquals(MapView.markerLabel(pt), s"Zz title (${Fmt.periodStr(Year(-1200L), Year(-1190L))})")
    assertEquals(MapView.markerLabel(undatedHit.copy(title = GeoHit.Title("Zz"))), "Zz")
    assertEquals(
      MapView.markerLabel(pt.modify(_.title).setTo(GeoHit.Title(" ")).modify(_.doi).setTo(Some(Doi("10.1/x")))),
      s"10.1/x (${Fmt.periodStr(Year(-1200L), Year(-1190L))})"
    )
    assertEquals(MapView.markerLabel(pt.modify(_.title).setTo(GeoHit.Title(""))).value.startsWith("zz2026_x"), true)
  }

  // markerDiff (#887) is gone: the findings are a PointLayer like every kind, diffed by FadingLayer.plan
  // (ThrottleSuite), which takes snapshots of both key sets

  // ── the time rule: a place dated by its findings is drawn apart ─────────────────────────────────

  test("placeClasses adds hd-by-findings only for a place shown by its findings"):
    assertEquals(
      MapView.placeClasses(MapView.ClassName("hd-site-marker"), Some(Place.TimeBasis.Wire("findings"))),
      "hd-site-marker hd-by-findings"
    )
    assertEquals(MapView.placeClasses(MapView.ClassName("hd-polity"), Some(Place.TimeBasis.Wire("dated"))), "hd-polity")
    assertEquals(
      MapView.placeClasses(MapView.ClassName("hd-polity"), Some(Place.TimeBasis.Wire("timeless"))),
      "hd-polity"
    )
    assertEquals(MapView.placeClasses(MapView.ClassName("hd-polity"), None), "hd-polity")

  // ── the "States shown" menu (GeoPick) ──────────────────────────────────────────

  test("the boundaries follow the menu over the span; playback always draws the playhead's one year"):
    import com.histmersion.api.GeoPick
    import com.histmersion.api.catalogue.GeoTime
    assertEquals(
      MapView.areaTime(
        (Years.Astronomical(-2000L), Years.Astronomical(-1000L)),
        GeoPick.Mid,
        playback = MapView.Playback(false)
      ),
      GeoTime.At(Year(-1500))
    )
    assertEquals(
      MapView.areaTime(
        (Years.Astronomical(-2000L), Years.Astronomical(-1000L)),
        GeoPick.Start,
        playback = MapView.Playback(false)
      ),
      GeoTime.At(Year(-2000))
    )
    assertEquals(
      MapView.areaTime(
        (Years.Astronomical(-2000L), Years.Astronomical(-1000L)),
        GeoPick.End,
        playback = MapView.Playback(false)
      ),
      GeoTime.At(Year(-1000))
    )
    assertEquals(
      MapView.areaTime(
        (Years.Astronomical(-2000L), Years.Astronomical(-1000L)),
        GeoPick.Latest,
        playback = MapView.Playback(false)
      ),
      GeoTime.Within(Year(-2000), Year(-1000), latest = GeoTime.Within.Latest(true))
    )
    GeoPick.values.foreach(p =>
      assertEquals(
        MapView
          .areaTime((Years.Astronomical(-1210L), Years.Astronomical(-1190L)), p, playback = MapView.Playback(true)),
        GeoTime.At(Year(-1200))
      )
    )

  test("the areas of a whole range are all asked for; a year keeps the year's limit"):
    import com.histmersion.api.catalogue.GeoTime
    import com.histmersion.web.ui.Layout
    assertEquals(
      MapView.overlayLimit(GeoTime.Within(Year(-3000), Year(2000), latest = GeoTime.Within.Latest(false))).value,
      Layout.overlayRangeFetchLimit.value
    )
    assertEquals(MapView.overlayLimit(GeoTime.At(Year(1500))).value, Layout.overlayFetchLimit.value)
    assert(Layout.overlayRangeFetchLimit.value > Layout.overlayFetchLimit.value)

  test("the tiled layers are asked for the span itself; in playback for its window widened to whole buckets"):
    assertEquals(
      MapView.tileSpanOf(
        (Years.Astronomical(-1500L), Years.Astronomical(-1600L)),
        playback = MapView.Playback(false),
        MapView.YearsPerSecond(0.0),
        MapTilesUiConfig.PlaybackBucketSeconds(1.0)
      ),
      (-1600L, -1500L)
    )
    // 166 years a second, a bucket of one second: 200-year buckets
    assertEquals(
      MapView.tileSpanOf(
        (Years.Astronomical(-1650L), Years.Astronomical(-1550L)),
        playback = MapView.Playback(true),
        MapView.YearsPerSecond(166.0),
        MapTilesUiConfig.PlaybackBucketSeconds(1.0)
      ),
      (-1800L, -1401L)
    )
    // the next frames stay in the same buckets: the same tiles
    assertEquals(
      MapView.tileSpanOf(
        (Years.Astronomical(-1640L), Years.Astronomical(-1540L)),
        playback = MapView.Playback(true),
        MapView.YearsPerSecond(166.0),
        MapTilesUiConfig.PlaybackBucketSeconds(1.0)
      ),
      (-1800L, -1401L)
    )
    assertEquals(
      MapView.tileFilter((Years.Astronomical(-10L), Years.Astronomical(20L))).period,
      Some(Period(Year(-10L), Year(20L)))
    )

  test("the query carries a snapshot as asOf and a whole range as from/to/pick"):
    import com.histmersion.api.Api
    import com.histmersion.api.catalogue.GeoTime
    assertEquals(
      MapView.geoQuery(Place.Kind.Wire("polity"), GeoTime.At(Year(-1200)), Paging.Limit(400)),
      Api.PlacesGeoQuery(
        kind = Some(Place.Kind.Wire("polity")),
        asOf = Some(Api.AggregateQuery.AsOf(-1200L)),
        limit = Some(Api.PlacesGeoQuery.Limit(400))
      )
    )
    assertEquals(
      MapView.geoQuery(
        Place.Kind.Wire("polity"),
        GeoTime.Within(Year(-2000), Year(-1000), latest = GeoTime.Within.Latest(false)),
        Paging.Limit(400)
      ),
      Api.PlacesGeoQuery(
        kind = Some(Place.Kind.Wire("polity")),
        limit = Some(Api.PlacesGeoQuery.Limit(400)),
        from = Some(Api.PlacesGeoQuery.From(-2000L)),
        to = Some(Api.PlacesGeoQuery.To(-1000L)),
        pick = Some(GeoPick.Wire("earliest"))
      )
    )

  test("a disabled layer option says which switch turns it on"):
    import com.histmersion.web.MapLayer

  // ── waitsForQuiet (#1949) ───────────────────────────────────────────────────────

  test("waitsForQuiet: the boundaries over a whole range and the tiled layers wait for the span to settle") {
    import com.histmersion.api.GeoPick
    assert(MapView.waitsForQuiet(MapLayer.Boundaries, GeoPick.Latest, playback = MapView.Playback(false)))
    assert(MapView.waitsForQuiet(MapLayer.Boundaries, GeoPick.Earliest, playback = MapView.Playback(false)))
    for p <- List(GeoPick.Mid, GeoPick.Start, GeoPick.End) do
      assert(
        !MapView.waitsForQuiet(MapLayer.Boundaries, p, playback = MapView.Playback(false)),
        s"a snapshot ($p) follows the drag"
      )
    assert(
      !MapView.waitsForQuiet(MapLayer.Boundaries, GeoPick.Latest, playback = MapView.Playback(true)),
      "playback draws one year"
    )
    for l <- List(MapLayer.Sites, MapLayer.Media, MapLayer.Events) do
      assert(
        MapView.waitsForQuiet(l, GeoPick.Mid, playback = MapView.Playback(false)),
        s"$l waits: a new span is every tile cut again"
      )
      assert(
        !MapView.waitsForQuiet(l, GeoPick.Mid, playback = MapView.Playback(true)),
        s"$l follows playback by its buckets"
      )
  }
