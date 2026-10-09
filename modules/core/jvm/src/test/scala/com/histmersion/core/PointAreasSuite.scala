package com.histmersion.core

import com.histmersion.core.PointAreas.{Dating, Group, Listed}

/** `lint --rule place-imported-point-area`'s pure half: how a period reads, the listing (group counts
  * and every finding holder always, then a sample) and the fix line. */
class PointAreasSuite extends munit.FunSuite:

  private val rule = Lint.rule(Lint.PlaceImportedPointArea).get

  test("Dating: an unknown start is end-only, an open range undated, anything else dated"):
    assertEquals(Dating.of(startUnknown = true, lowerInf = false, upperInf = false), Dating.EndOnly)
    assertEquals(Dating.of(startUnknown = false, lowerInf = true, upperInf = true), Dating.Undated)
    assertEquals(Dating.of(startUnknown = false, lowerInf = false, upperInf = true), Dating.Dated)
    assertEquals(Dating.of(startUnknown = false, lowerInf = true, upperInf = false), Dating.Dated)

  test("a holder says how many of its findings it is the only place of, and evidence keeps a place"):
    val l = Listed("wikidata_q17167", "Roman Republic", "wikidata", "polity", Dating.Dated, findings = 27)
    assertEquals(l.detail, "Roman Republic (wikidata polity, dated): 27 findings, each also at another place")
    assertEquals(l.copy(alone = 3).detail, "Roman Republic (wikidata polity, dated): 27 findings, 3 of them at no other place")
    assertEquals(l.copy(findings = 0).detail, "Roman Republic (wikidata polity, dated)")
    assertEquals(l.copy(findings = 1).detail, "Roman Republic (wikidata polity, dated): 1 finding, also at another place")
    assertEquals(l.copy(findings = 1, alone = 1).detail, "Roman Republic (wikidata polity, dated): 1 finding, at no other place")
    assert(l.copy(pinned = 1).detail.contains("kept: named by 1 attestation(s)/asset link(s)/place override(s)"))

  test("report: the true count, the groups and every holder always, the sample fills up to the cap"):
    val groups = List(Group("wikidata", "region", Dating.Undated, 3), Group("geonames", "region", Dating.Undated, 5))
    val holders = List(
      Listed("wikidata_q2", "B", "wikidata", "region", Dating.Undated, findings = 1),
      Listed("wikidata_q1", "A", "wikidata", "region", Dating.Undated, findings = 9))
    val sample = List(Listed("geonames_1", "C", "geonames", "region", Dating.Undated),
      Listed("wikidata_q1", "A", "wikidata", "region", Dating.Undated), Listed("geonames_2", "D", "geonames", "region", Dating.Undated))
    val r = PointAreas.report(rule, groups, holders, sample, maxItems = 5)
    assertEquals(r.count, 8)
    assertEquals(r.items.map(_.citekey), List("geonames · region · undated", "wikidata · region · undated",
      "wikidata_q1", "wikidata_q2", "geonames_1"))
    assertEquals(r.items.head.detail, "5 places")
    // a cap below the must-show lines still shows them all
    assertEquals(PointAreas.report(rule, groups, holders, sample, maxItems = 1).items.size, 4)
    assertEquals(PointAreas.report(rule, Nil, Nil, Nil, 50).count, 0)

  test("the fix line: removed, the findings matched again, what is left"):
    assertEquals(PointAreas.fixed(10, 0, 0, 0), "place-imported-point-area: 10 point-only imported area(s) removed")
    assertEquals(PointAreas.fixed(10, 5, 3, 2),
      "place-imported-point-area: 10 point-only imported area(s) removed, 3 findings of 2 sources matched to places again, 5 still to remove")
