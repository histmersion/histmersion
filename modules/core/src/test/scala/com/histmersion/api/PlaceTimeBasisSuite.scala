package com.histmersion.api

import com.histmersion.api.catalogue.PlacesMarkdown

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given
import com.histmersion.api.catalogue.{PlaceSummary, PlaceTimeBasis}
import com.histmersion.api.views.PlaceDetail
import com.histmersion.domain.catalogue.Place

/** The time rule of the place views (2026-10-09): on what ground a view at a time shows a place. */
class PlaceTimeBasisSuite extends munit.FunSuite:
  import com.histmersion.api.catalogue.PlaceTimeBasis.*

  private def of(
      kind: String,
      modern: Boolean = false,
      imported: Boolean = false,
      undated: Boolean = false,
      geometry: Boolean = true
  ) = PlaceTimeBasis.of(kind, modern, imported, undated, geometry)

  test("a dated place, also one whose unknown start is a stored window, is judged by its period"):
    assertEquals(of(Place.Kind.Site), Dated)
    assertEquals(of(Place.Kind.Polity), Dated)
    assertEquals(of(Place.Kind.Polity, imported = true), Dated, "a dated Wikidata state")

  test("physical features are timeless: any seed feature, and a Natural Earth physical region"):
    assertEquals(of(Place.Kind.Feature), Timeless)
    assertEquals(of(Place.Kind.Feature, undated = true), Timeless)
    assertEquals(of(Place.Kind.Feature, modern = true, undated = true), Timeless, "a Natural Earth sea")
    assertEquals(of(Place.Kind.Region, modern = true, undated = true), Timeless, "a Natural Earth peninsula")
    assertEquals(of(Place.Kind.Feature, imported = true, undated = true), Findings, "never a GeoNames/Wikidata place")

  test("a present-day place is modern: the Natural Earth cities, an undated GeoNames or Wikidata place"):
    assertEquals(of(Place.Kind.Site, modern = true, undated = true), Modern)
    assertEquals(of(Place.Kind.Region, modern = true, imported = true, undated = true), Modern)

  test("any other undated place is there by its findings; with no shape it is a grouping node"):
    assertEquals(of(Place.Kind.Site, undated = true), Findings, "an undated Pleiades site")
    assertEquals(of(Place.Kind.Region, undated = true), Findings, "a curated region, a Natural Earth country")
    assertEquals(of(Place.Kind.Region, undated = true, geometry = false), Grouping, "a continent")

  test("a people (region-cleanup's kind) is never timeless: dated by its period, else by its findings"):
    assertEquals(of(Place.Kind.People, undated = true), Findings, "the Ubii, undated in Pleiades")
    assertEquals(of(Place.Kind.People), Dated, "a people Pleiades dates")
    assertEquals(of(Place.Kind.People, undated = true, geometry = false), Grouping)
    assertEquals(of(Place.Kind.People, imported = true, undated = true), Findings)

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
    val p = PlaceSummary("x", "Anatolia", Place.Kind.Region, None, None, None, "")
    val d = PlaceDetail(p, None, Nil, Nil, 0, Some(-2500L), timeBasis = Findings)
    assert(d.datedByFindings)
    assert(!d.copy(asOf = None).datedByFindings)
    assert(!d.copy(timeBasis = Dated).datedByFindings)
    assert(PlacesMarkdown.place(d).contains(FindingsNote), PlacesMarkdown.place(d))
    assert(!PlacesMarkdown.place(d.copy(asOf = None)).contains(FindingsNote))

  test("places.modern-from is configured, 1500 by default"):
    assertEquals(AppConfig.load().places.modernFrom, 1500L)
