package com.histmersion.domain.library.findings

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.MergeFixture
import com.histmersion.api.given

import MergeFixture.{uid, work}
import com.histmersion.api.library.Confidence
import com.histmersion.api.exchange.Bundle

/** The findings of a pair that is one matched, folded and numbered (docs/research-merge-import.md §5.4). */
class FindingPlanSuite extends munit.FunSuite:

  private val At = "2026-10-01T09:00:00.000000Z"
  private def finding(
      id: Bundle.Finding.Id,
      ck: String,
      claim: Bundle.Finding.Claim,
      period: Option[Bundle.Range] = Some(Bundle.Range(Some(com.histmersion.api.catalogue.Year(-1400)), Some(com.histmersion.api.catalogue.Year(-1200)))),
      lonLat: Option[(com.histmersion.api.catalogue.Longitude, com.histmersion.api.catalogue.Latitude)] = Some((com.histmersion.api.catalogue.Longitude(34.6), com.histmersion.api.catalogue.Latitude(40.0))),
      tags: List[Bundle.Finding.Tags] = Nil,
      confidence: Option[Confidence] = Some(Confidence.High),
      region: Option[Bundle.Finding.Region] = Some(Bundle.Finding.Region("Anatolia"))
  ): Bundle.Finding =
    Bundle.Finding(
      uid(id.value),
      work,
      uid(ck),
      id,
      claim,
      region,
      lonLat.map(_._1),
      lonLat.map(_._2),
      period,
      None,
      tags,
      confidence,
      None,
      Bundle.Finding.UpdatedAt(At)
    )

  test("findings: the same claim folds, a near duplicate is offered, the rest are new — one to one"):
    val lefts = List(
      finding(Bundle.Finding.Id("h#1"), "h", Bundle.Finding.Claim("Tarhunna is the storm god of Hatti"), tags = List(Bundle.Finding.Tags("home"))),
      finding(Bundle.Finding.Id("h#2"), "h", Bundle.Finding.Claim("The storm god Tarhunna ruled Hatti"))
    )
    val rights = List(
      finding(Bundle.Finding.Id("w#1"), "w", Bundle.Finding.Claim("the storm god of Hatti is Tarhunna"), tags = List(Bundle.Finding.Tags("work")), confidence = Some(Confidence.Low)),
      finding(Bundle.Finding.Id("w#2"), "w", Bundle.Finding.Claim("Tarhunna the storm god ruled Hatti and Arinna")),
      finding(Bundle.Finding.Id("w#3"), "w", Bundle.Finding.Claim("Hattusa burned around 1180 BC")),
      finding(Bundle.Finding.Id("w#4"), "w", Bundle.Finding.Claim("Tarhunna is the storm god of Hatti"), period = Some(Bundle.Range(Some(com.histmersion.api.catalogue.Year(-500)), Some(com.histmersion.api.catalogue.Year(-400)))))
    )
    val ms = FindingPlan.matches(lefts, rights, com.histmersion.domain.MergeSettings.FindingWithinKm(50), com.histmersion.domain.MergeSettings.FindingNearAtLeast(0.6))
    assertEquals(ms.collect { case FindingPlan.Match.Same(r, l) => r.id -> l.id }, List("w#1" -> "h#1"))
    assertEquals(ms.collect { case FindingPlan.Match.Near(r, l, _) => r.id -> l.id }, List("w#2" -> "h#2"))
    // a different period is not the same finding
    assertEquals(ms.collect { case FindingPlan.Match.Unmatched(r) => r.id }, List("w#3", "w#4"))
    val f = FindingPlan.fold(lefts.head, rights.head)
    assertEquals(f.uid, lefts.head.uid)
    assertEquals(f.tags, List("home", "work"))
    assertEquals(f.confidence, Some(Confidence.Low)) // the more cautious
    // far apart, and in different regions: not the same place
    val far =
      finding(Bundle.Finding.Id("w#5"), "w", Bundle.Finding.Claim("Tarhunna is the storm god of Hatti"), lonLat = Some((com.histmersion.api.catalogue.Longitude(10.0), com.histmersion.api.catalogue.Latitude(50.0))), region = Some(Bundle.Finding.Region("Bavaria")))
    assert(!FindingPlan.samePlace(lefts.head, far, com.histmersion.domain.MergeSettings.FindingWithinKm(50)))
    assert(FindingPlan.samePlace(lefts.head, far.modify(_.region).setTo(Some(Bundle.Finding.Region("anatolia"))), com.histmersion.domain.MergeSettings.FindingWithinKm(50)))

  test("finding ids continue after the highest"):
    assertEquals(
      FindingPlan.nextIds(com.histmersion.api.library.Citekey("a2000_x"), Set(com.histmersion.api.library.Finding.Id("a2000_x#1"), com.histmersion.api.library.Finding.Id("a2000_x#7"), com.histmersion.api.library.Finding.Id("b2000_y#9")), com.histmersion.domain.library.findings.FindingPlan.IdCount(2)),
      List("a2000_x#8", "a2000_x#9")
    )
    assertEquals(FindingPlan.nextIds(com.histmersion.api.library.Citekey("a2000_x"), Set.empty, com.histmersion.domain.library.findings.FindingPlan.IdCount(1)), List("a2000_x#1"))
