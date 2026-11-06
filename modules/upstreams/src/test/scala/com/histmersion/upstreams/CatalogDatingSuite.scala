package com.histmersion.upstreams

import com.histmersion.upstreams.dating.CatalogDating.{Bound, Limits, Span}
import com.histmersion.upstreams.dating.CatalogDating
import com.histmersion.upstreams.wikidata.WikidataClasses

/** How a catalogue's dating becomes a four-point span, and how it is held inside its limits. */
class CatalogDatingSuite extends munit.FunSuite:

  test("years: no year zero across the BCE/CE boundary"):
    assertEquals(CatalogDating.shift(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-10), com.histmersion.upstreams.dating.CatalogDating.YearShift(30)), 21L)
    assertEquals(CatalogDating.shift(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(5), com.histmersion.upstreams.dating.CatalogDating.YearShift(-10)), -6L)
    assertEquals(CatalogDating.fromAstronomical(com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(-51)), -52L)
    assertEquals(CatalogDating.fromAstronomical(com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(0)), -1L)
    assertEquals(CatalogDating.toAstronomical(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-52)), -51L)

  test("an uncertainty widens the outer bounds and never invents a certain core"):
    assertEquals(
      CatalogDating.span(Some(CatalogDating.year(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-1610), Some(com.histmersion.upstreams.dating.CatalogDating.YearMargin(14)))), None),
      Span(Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-1624)), None, None, Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-1596)))
    )
    assertEquals(CatalogDating.span(Some(CatalogDating.year(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(79))), None), Span(Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(79)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(79)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(79)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(79))))
    assertEquals(
      CatalogDating.span(Some(CatalogDating.year(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-500), Some(com.histmersion.upstreams.dating.CatalogDating.YearMargin(10)))), Some(CatalogDating.year(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-450), Some(com.histmersion.upstreams.dating.CatalogDating.YearMargin(10))))),
      Span(Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-510)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-490)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-460)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-440)))
    )
    assertEquals(CatalogDating.span(None, None), Span())

  test("Wikidata: an astronomical ISO year and its precision"):
    assertEquals(CatalogDating.isoYear(com.histmersion.upstreams.dating.CatalogDating.XsdYearText("-0051-07-01T00:00:00Z")), Some(-51L))
    assertEquals(CatalogDating.isoYear(com.histmersion.upstreams.dating.CatalogDating.XsdYearText("+0410-08-24T00:00:00Z")), Some(410L))
    assertEquals(CatalogDating.wikidata(com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(-51), com.histmersion.upstreams.dating.CatalogDating.WikidataPrecision(9)), Bound(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-52), com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-52)))
    assertEquals(CatalogDating.wikidata(com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(-51), com.histmersion.upstreams.dating.CatalogDating.WikidataPrecision(11)), Bound(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-52), com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-52)), "a day is its year")
    // a decade: the one the year is in (-60…-51 astronomical), widened by one decade each way
    assertEquals(
      CatalogDating.wikidata(com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(-51), com.histmersion.upstreams.dating.CatalogDating.WikidataPrecision(8)),
      Bound(CatalogDating.fromAstronomical(com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(-70)), CatalogDating.fromAstronomical(com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(-41)))
    )
    val c = CatalogDating.wikidata(com.histmersion.upstreams.dating.CatalogDating.AstronomicalYear(-450), com.histmersion.upstreams.dating.CatalogDating.WikidataPrecision(7))
    assert(c.lo <= com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-551) && c.hi >= -450, c.toString)

  test("clamp: the present always, a floor where the catalogue has one; monotone"):
    val wide = Span(Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-11000)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-10000)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(1990)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(2100)))
    assertEquals(
      CatalogDating.clamp(wide, Limits(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(2026), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-9700)))),
      Span(Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-9700)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-9700)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(1990)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(2026)))
    )
    assertEquals(CatalogDating.clamp(wide, Limits(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(2026))), Span(Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-11000)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-10000)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(1990)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(2026))))

  test("a central year and ± read back from a span: exact years exact, a cored start window centred"):
    assertEquals(CatalogDating.centralOf(Span(Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(79)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(79)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(79)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(79)))), Some((79L, 0L)))
    assertEquals(CatalogDating.centralOf(Span(Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-510)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-490)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-460)), Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-440)))), Some((-500L, 10L)))
    assertEquals(CatalogDating.centralOf(Span(Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(-2251)), None, None, Some(com.histmersion.upstreams.dating.CatalogDating.HistoricalYear(2750)))), Some((250L, 2500L)))
    assertEquals(CatalogDating.centralOf(Span()), None)

  test("the Wikidata class table from config: well-formed entries, each class once, the kind as written"):
    assertEquals(
      WikidataClasses.parse(com.histmersion.upstreams.wikidata.WikidataClasses.WikidataText("Q178561:battle, bad, Q188055:Siege,Q178561:war")),
      List("Q178561" -> "battle", "Q188055" -> "Siege"),
      "the kind as written; the host canonicalises it"
    )
    assertEquals(WikidataClasses.parse(com.histmersion.upstreams.wikidata.WikidataClasses.WikidataText("")), Nil)
