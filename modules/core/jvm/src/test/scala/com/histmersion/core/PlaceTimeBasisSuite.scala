package com.histmersion.core

/** The time rule of the place views (2026-10-09): on what ground a view at a time shows a place. */
class PlaceTimeBasisSuite extends munit.FunSuite:
  import PlaceTimeBasis.*

  private def of(kind: String, modern: Boolean = false, imported: Boolean = false, undated: Boolean = false,
      geometry: Boolean = true) = PlaceTimeBasis.of(kind, modern, imported, undated, geometry)

  test("a dated place, also one whose unknown start is a stored window, is judged by its period"):
    assertEquals(of(PlaceKind.Site), Dated)
    assertEquals(of(PlaceKind.Polity), Dated)
    assertEquals(of(PlaceKind.Polity, imported = true), Dated, "a dated Wikidata state")

  test("physical features are timeless: any seed feature, and a Natural Earth physical region"):
    assertEquals(of(PlaceKind.Feature), Timeless)
    assertEquals(of(PlaceKind.Feature, undated = true), Timeless)
    assertEquals(of(PlaceKind.Feature, modern = true, undated = true), Timeless, "a Natural Earth sea")
    assertEquals(of(PlaceKind.Region, modern = true, undated = true), Timeless, "a Natural Earth peninsula")
    assertEquals(of(PlaceKind.Feature, imported = true, undated = true), Findings, "never a GeoNames/Wikidata place")

  test("a present-day place is modern: the Natural Earth cities, an undated GeoNames or Wikidata place"):
    assertEquals(of(PlaceKind.Site, modern = true, undated = true), Modern)
    assertEquals(of(PlaceKind.Region, modern = true, imported = true, undated = true), Modern)

  test("any other undated place is there by its findings; with no shape it is a grouping node"):
    assertEquals(of(PlaceKind.Site, undated = true), Findings, "an undated Pleiades site")
    assertEquals(of(PlaceKind.Region, undated = true), Findings, "a curated region, a Natural Earth country")
    assertEquals(of(PlaceKind.Region, undated = true, geometry = false), Grouping, "a continent")

  test("visible: only what the basis allows"):
    def vis(b: PlaceTimeBasis, hi: Long, overlaps: Boolean, evidence: Boolean) =
      PlaceTimeBasis.visible(b, hi, modernFrom = 1500, overlaps, evidence)
    assert(vis(Dated, -2500, overlaps = true, evidence = false))
    assert(!vis(Dated, -2500, overlaps = false, evidence = true), "a finding does not date a dated place")
    assert(vis(Timeless, -2500, overlaps = false, evidence = false))
    assert(vis(Grouping, -2500, overlaps = false, evidence = false))
    assert(!vis(Modern, 1499, overlaps = true, evidence = true))
    assert(vis(Modern, 1500, overlaps = true, evidence = false))
    assert(vis(Findings, -2500, overlaps = true, evidence = true))
    assert(!vis(Findings, -2500, overlaps = true, evidence = false), "undated and no dated evidence: hidden")

  test("the wire names round-trip"):
    assertEquals(values.toList.flatMap(b => parse(b.wire)), values.toList)
    assertEquals(parse("Findings"), Some(Findings))
    assertEquals(parse("sometimes"), None)

  test("a card at a time says a place is there by its findings; with no time, or dated, it says nothing"):
    val p = PlaceSummary("x", "Anatolia", PlaceKind.Region, None, None, None, "")
    val d = PlaceDetail(p, None, Nil, Nil, 0, Some(-2500L), timeBasis = Findings)
    assert(d.datedByFindings)
    assert(!d.copy(asOf = None).datedByFindings)
    assert(!d.copy(timeBasis = Dated).datedByFindings)
    assert(Markdown.place(d).contains(FindingsNote), Markdown.place(d))
    assert(!Markdown.place(d.copy(asOf = None)).contains(FindingsNote))

  test("places.modern-from is configured, 1500 by default"):
    assertEquals(AppConfig.load().places.modernFrom, 1500L)
