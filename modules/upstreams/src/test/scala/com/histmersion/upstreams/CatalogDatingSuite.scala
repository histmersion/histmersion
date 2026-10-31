package com.histmersion.upstreams

import CatalogDating.{Bound, Limits, Span}

/** How a catalogue's dating becomes a four-point span, and how it is held inside its limits. */
class CatalogDatingSuite extends munit.FunSuite:

  test("years: no year zero across the BCE/CE boundary"):
    assertEquals(CatalogDating.shift(-10, 30), 21L)
    assertEquals(CatalogDating.shift(5, -10), -6L)
    assertEquals(CatalogDating.fromAstronomical(-51), -52L)
    assertEquals(CatalogDating.fromAstronomical(0), -1L)
    assertEquals(CatalogDating.toAstronomical(-52), -51L)

  test("an uncertainty widens the outer bounds and never invents a certain core"):
    assertEquals(
      CatalogDating.span(Some(CatalogDating.year(-1610, Some(14))), None),
      Span(Some(-1624), None, None, Some(-1596))
    )
    assertEquals(CatalogDating.span(Some(CatalogDating.year(79)), None), Span(Some(79), Some(79), Some(79), Some(79)))
    assertEquals(
      CatalogDating.span(Some(CatalogDating.year(-500, Some(10))), Some(CatalogDating.year(-450, Some(10)))),
      Span(Some(-510), Some(-490), Some(-460), Some(-440))
    )
    assertEquals(CatalogDating.span(None, None), Span())

  test("Wikidata: an astronomical ISO year and its precision"):
    assertEquals(CatalogDating.isoYear("-0051-07-01T00:00:00Z"), Some(-51L))
    assertEquals(CatalogDating.isoYear("+0410-08-24T00:00:00Z"), Some(410L))
    assertEquals(CatalogDating.wikidata(-51, 9), Bound(-52, -52))
    assertEquals(CatalogDating.wikidata(-51, 11), Bound(-52, -52), "a day is its year")
    // a decade: the one the year is in (-60…-51 astronomical), widened by one decade each way
    assertEquals(
      CatalogDating.wikidata(-51, 8),
      Bound(CatalogDating.fromAstronomical(-70), CatalogDating.fromAstronomical(-41))
    )
    val c = CatalogDating.wikidata(-450, 7)
    assert(c.lo <= -551 && c.hi >= -450, c.toString)

  test("clamp: the present always, a floor where the catalogue has one; monotone"):
    val wide = Span(Some(-11000), Some(-10000), Some(1990), Some(2100))
    assertEquals(
      CatalogDating.clamp(wide, Limits(2026, Some(-9700))),
      Span(Some(-9700), Some(-9700), Some(1990), Some(2026))
    )
    assertEquals(CatalogDating.clamp(wide, Limits(2026)), Span(Some(-11000), Some(-10000), Some(1990), Some(2026)))

  test("a central year and ± read back from a span: exact years exact, a cored start window centred"):
    assertEquals(CatalogDating.centralOf(Span(Some(79), Some(79), Some(79), Some(79))), Some((79L, 0L)))
    assertEquals(CatalogDating.centralOf(Span(Some(-510), Some(-490), Some(-460), Some(-440))), Some((-500L, 10L)))
    assertEquals(CatalogDating.centralOf(Span(Some(-2251), None, None, Some(2750))), Some((250L, 2500L)))
    assertEquals(CatalogDating.centralOf(Span()), None)

  test("the Wikidata class table from config: well-formed entries, each class once, the kind as written"):
    assertEquals(
      WikidataClasses.parse("Q178561:battle, bad, Q188055:Siege,Q178561:war"),
      List("Q178561" -> "battle", "Q188055" -> "Siege"),
      "the kind as written; the host canonicalises it"
    )
    assertEquals(WikidataClasses.parse(""), Nil)
