package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.PlacesMarkdown

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given
import com.histmersion.api.views.PlaceDetail
import com.histmersion.domain.catalogue.Place

/** The time rule of the place views (2026-10-09): on what ground a view at a time shows a place. */
class PlaceTimeBasisSuite extends munit.FunSuite:
  import com.histmersion.api.catalogue.Place.TimeBasis.*

  private def of(
      kind: OfKind,
      modern: Boolean = false,
      imported: Boolean = false,
      undated: Boolean = false,
      geometry: Boolean = true
  ) = com.histmersion.api.catalogue.Place.TimeBasis.of(kind, modern, imported, undated, geometry)

  test("a dated place, also one whose unknown start is a stored window, is judged by its period"):
    assertEquals(of(OfKind(Place.Kind.Site)), Dated)
    assertEquals(of(OfKind(Place.Kind.Polity)), Dated)
    assertEquals(of(OfKind(Place.Kind.Polity), imported = true), Dated, "a dated Wikidata state")

  test("physical features are timeless: any seed feature, and a Natural Earth physical region"):
    assertEquals(of(OfKind(Place.Kind.Feature)), Timeless)
    assertEquals(of(OfKind(Place.Kind.Feature), undated = true), Timeless)
    assertEquals(of(OfKind(Place.Kind.Feature), modern = true, undated = true), Timeless, "a Natural Earth sea")
    assertEquals(of(OfKind(Place.Kind.Region), modern = true, undated = true), Timeless, "a Natural Earth peninsula")
    assertEquals(of(OfKind(Place.Kind.Feature), imported = true, undated = true), Findings, "never a GeoNames/Wikidata place")

  test("a present-day place is modern: the Natural Earth cities, an undated GeoNames or Wikidata place"):
    assertEquals(of(OfKind(Place.Kind.Site), modern = true, undated = true), Modern)
    assertEquals(of(OfKind(Place.Kind.Region), modern = true, imported = true, undated = true), Modern)

  test("any other undated place is there by its findings; with no shape it is a grouping node"):
    assertEquals(of(OfKind(Place.Kind.Site), undated = true), Findings, "an undated Pleiades site")
    assertEquals(of(OfKind(Place.Kind.Region), undated = true), Findings, "a curated region, a Natural Earth country")
    assertEquals(of(OfKind(Place.Kind.Region), undated = true, geometry = false), Grouping, "a continent")

  test("a people (region-cleanup's kind) is never timeless: dated by its period, else by its findings"):
    assertEquals(of(OfKind(Place.Kind.People), undated = true), Findings, "the Ubii, undated in Pleiades")
    assertEquals(of(OfKind(Place.Kind.People)), Dated, "a people Pleiades dates")
    assertEquals(of(OfKind(Place.Kind.People), undated = true, geometry = false), Grouping)
    assertEquals(of(OfKind(Place.Kind.People), imported = true, undated = true), Findings)

  test("visible: only what the basis allows"):
    def vis(b: com.histmersion.api.catalogue.Place.TimeBasis, hi: VisibleEnd, overlaps: Boolean, evidence: Boolean) =
      com.histmersion.api.catalogue.Place.TimeBasis.visible(b, hi, modernFrom = com.histmersion.api.catalogue.Place.TimeBasis.ModernFrom(1500), overlaps, evidence)
    assert(vis(Dated, VisibleEnd(-2500), overlaps = true, evidence = false))
    assert(!vis(Dated, VisibleEnd(-2500), overlaps = false, evidence = true), "a finding does not date a dated place")
    assert(vis(Timeless, VisibleEnd(-2500), overlaps = false, evidence = false))
    assert(vis(Grouping, VisibleEnd(-2500), overlaps = false, evidence = false))
    assert(!vis(Modern, VisibleEnd(1499), overlaps = true, evidence = true))
    assert(vis(Modern, VisibleEnd(1500), overlaps = true, evidence = false))
    assert(vis(Findings, VisibleEnd(-2500), overlaps = true, evidence = true))
    assert(!vis(Findings, VisibleEnd(-2500), overlaps = true, evidence = false), "undated and no dated evidence: hidden")

  test("the wire names round-trip"):
    assertEquals(values.toList.flatMap(b => parse(b.wire)), values.toList)
    assertEquals(parse(com.histmersion.api.catalogue.Place.TimeBasis.Wire("Findings")), Some(Findings))
    assertEquals(parse(com.histmersion.api.catalogue.Place.TimeBasis.Wire("sometimes")), None)

  test("a card at a time says a place is there by its findings; with no time, or dated, it says nothing"):
    val p = com.histmersion.api.catalogue.Place.Summary(com.histmersion.api.catalogue.Place.Id("x"), com.histmersion.api.catalogue.Place.Summary.Label("Anatolia"), com.histmersion.api.catalogue.Place.Summary.Kind(Place.Kind.Region), None, None, None, com.histmersion.api.catalogue.Place.Summary.Names(""))
    val d = PlaceDetail(p, None, Nil, Nil, com.histmersion.api.catalogue.Place.Counts.Findings(0), Some(com.histmersion.api.catalogue.Year(-2500L)), timeBasis = Findings)
    assert(d.datedByFindings)
    assert(!d.modify(_.asOf).setTo((None: _root_.scala.Option[_root_.scala.Long])).datedByFindings)
    assert(!d.modify(_.timeBasis).setTo(Dated).datedByFindings)
    assert(PlacesMarkdown.place(d).contains(FindingsNote), PlacesMarkdown.place(d))
    assert(!PlacesMarkdown.place(d.modify(_.asOf).setTo((None: _root_.scala.Option[_root_.scala.Long]))).contains(FindingsNote))

  test("places.modern-from is configured, 1500 by default"):
    assertEquals(AppConfig.load().places.modernFrom, 1500L)
