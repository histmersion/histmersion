package com.histmersion.domain.catalogue.places

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.catalogue.places.PointAreas.{Dating, Group, Listed}
import com.histmersion.api.checks.Lint

/** `lint --rule place-imported-point-area`'s pure half: how a period reads, the listing (group counts and every finding
  * holder always, then a sample) and the fix line.
  */
class PointAreasSuite extends munit.FunSuite:

  private val rule = Lint.rule(Lint.Rule.Name(Lint.PlaceImportedPointArea)).get

  test("Dating: an unknown start is end-only, an open range undated, anything else dated"):
    assertEquals(Dating.of(startUnknown = true, lowerInf = false, upperInf = false), Dating.EndOnly)
    assertEquals(Dating.of(startUnknown = false, lowerInf = true, upperInf = true), Dating.Undated)
    assertEquals(Dating.of(startUnknown = false, lowerInf = false, upperInf = true), Dating.Dated)
    assertEquals(Dating.of(startUnknown = false, lowerInf = true, upperInf = false), Dating.Dated)

  test("a holder says how many of its findings it is the only place of, and evidence keeps a place"):
    val l = Listed(com.histmersion.domain.catalogue.places.PointAreas.Listed.Id("wikidata_q17167"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Label("Roman Republic"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Source("wikidata"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Kind("polity"), Dating.Dated, findings = com.histmersion.domain.catalogue.places.PointAreas.Listed.Findings(27))
    assertEquals(l.detail, "Roman Republic (wikidata polity, dated): 27 findings, each also at another place")
    assertEquals(
      l.modify(_.alone).setTo(3).detail,
      "Roman Republic (wikidata polity, dated): 27 findings, 3 of them at no other place"
    )
    assertEquals(l.modify(_.findings).setTo(0).detail, "Roman Republic (wikidata polity, dated)")
    assertEquals(
      l.modify(_.findings).setTo(1).detail,
      "Roman Republic (wikidata polity, dated): 1 finding, also at another place"
    )
    assertEquals(
      l.modify(_.findings).setTo(1).modify(_.alone).setTo(1).detail,
      "Roman Republic (wikidata polity, dated): 1 finding, at no other place"
    )
    assert(l.modify(_.pinned).setTo(1).detail.value.contains("kept: named by 1 attestation(s)/asset link(s)/place override(s)"))

  test("report: the true count, the groups and every holder always, the sample fills up to the cap"):
    val groups = List(Group(com.histmersion.domain.catalogue.places.PointAreas.Group.Source("wikidata"), com.histmersion.domain.catalogue.places.PointAreas.Group.Kind("region"), Dating.Undated, com.histmersion.domain.catalogue.places.PointAreas.Group.Places(3)), Group(com.histmersion.domain.catalogue.places.PointAreas.Group.Source("geonames"), com.histmersion.domain.catalogue.places.PointAreas.Group.Kind("region"), Dating.Undated, com.histmersion.domain.catalogue.places.PointAreas.Group.Places(5)))
    val holders = List(
      Listed(com.histmersion.domain.catalogue.places.PointAreas.Listed.Id("wikidata_q2"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Label("B"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Source("wikidata"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Kind("region"), Dating.Undated, findings = com.histmersion.domain.catalogue.places.PointAreas.Listed.Findings(1)),
      Listed(com.histmersion.domain.catalogue.places.PointAreas.Listed.Id("wikidata_q1"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Label("A"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Source("wikidata"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Kind("region"), Dating.Undated, findings = com.histmersion.domain.catalogue.places.PointAreas.Listed.Findings(9))
    )
    val sample = List(
      Listed(com.histmersion.domain.catalogue.places.PointAreas.Listed.Id("geonames_1"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Label("C"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Source("geonames"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Kind("region"), Dating.Undated),
      Listed(com.histmersion.domain.catalogue.places.PointAreas.Listed.Id("wikidata_q1"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Label("A"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Source("wikidata"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Kind("region"), Dating.Undated),
      Listed(com.histmersion.domain.catalogue.places.PointAreas.Listed.Id("geonames_2"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Label("D"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Source("geonames"), com.histmersion.domain.catalogue.places.PointAreas.Listed.Kind("region"), Dating.Undated)
    )
    val r = PointAreas.report(rule, groups, holders, sample, maxItems = com.histmersion.domain.catalogue.places.PointAreas.ReportMaxItems(5))
    assertEquals(r.count, 8)
    assertEquals(
      r.items.map(_.citekey),
      List("geonames · region · undated", "wikidata · region · undated", "wikidata_q1", "wikidata_q2", "geonames_1")
    )
    assertEquals(r.items.head.detail, "5 places")
    // a cap below the must-show lines still shows them all
    assertEquals(PointAreas.report(rule, groups, holders, sample, maxItems = com.histmersion.domain.catalogue.places.PointAreas.ReportMaxItems(1)).items.size, 4)
    assertEquals(PointAreas.report(rule, Nil, Nil, Nil, com.histmersion.domain.catalogue.places.PointAreas.ReportMaxItems(50)).count, 0)

  test("the fix line: removed, the findings matched again, what is left"):
    assertEquals(PointAreas.fixed(com.histmersion.domain.catalogue.places.PointAreas.FixedRemoved(10), com.histmersion.domain.catalogue.places.PointAreas.FixedRemaining(0), com.histmersion.domain.catalogue.places.PointAreas.FixedFindings(0), com.histmersion.domain.catalogue.places.PointAreas.FixedSources(0)), "place-imported-point-area: 10 point-only imported area(s) removed")
    assertEquals(
      PointAreas.fixed(com.histmersion.domain.catalogue.places.PointAreas.FixedRemoved(10), com.histmersion.domain.catalogue.places.PointAreas.FixedRemaining(5), com.histmersion.domain.catalogue.places.PointAreas.FixedFindings(3), com.histmersion.domain.catalogue.places.PointAreas.FixedSources(2)),
      "place-imported-point-area: 10 point-only imported area(s) removed, 3 findings of 2 sources matched to places again, 5 still to remove"
    )
