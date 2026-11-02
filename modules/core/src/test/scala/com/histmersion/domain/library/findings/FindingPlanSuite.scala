package com.histmersion.domain.library.findings

import com.histmersion.api.MergeFixture
import com.histmersion.api.given

import MergeFixture.{uid, work}
import com.histmersion.values.Confidence
import com.histmersion.api.exchange.{BundleFinding, BundleRange}

/** The findings of a pair that is one matched, folded and numbered (docs/research-merge-import.md §5.4). */
class FindingPlanSuite extends munit.FunSuite:

  private val At = "2026-10-01T09:00:00.000000Z"
  private def finding(
      id: String,
      ck: String,
      claim: String,
      period: Option[BundleRange] = Some(BundleRange(Some(-1400), Some(-1200))),
      lonLat: Option[(Double, Double)] = Some((34.6, 40.0)),
      tags: List[String] = Nil,
      confidence: Option[Confidence] = Some(Confidence.High),
      region: Option[String] = Some("Anatolia")
  ): BundleFinding =
    BundleFinding(
      uid(id),
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
      At
    )

  test("findings: the same claim folds, a near duplicate is offered, the rest are new — one to one"):
    val lefts = List(
      finding("h#1", "h", "Tarhunna is the storm god of Hatti", tags = List("home")),
      finding("h#2", "h", "The storm god Tarhunna ruled Hatti")
    )
    val rights = List(
      finding("w#1", "w", "the storm god of Hatti is Tarhunna", tags = List("work"), confidence = Some(Confidence.Low)),
      finding("w#2", "w", "Tarhunna the storm god ruled Hatti and Arinna"),
      finding("w#3", "w", "Hattusa burned around 1180 BC"),
      finding("w#4", "w", "Tarhunna is the storm god of Hatti", period = Some(BundleRange(Some(-500), Some(-400))))
    )
    val ms = FindingPlan.matches(lefts, rights, 50, 0.6)
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
      finding("w#5", "w", "Tarhunna is the storm god of Hatti", lonLat = Some((10.0, 50.0)), region = Some("Bavaria"))
    assert(!FindingPlan.samePlace(lefts.head, far, 50))
    assert(FindingPlan.samePlace(lefts.head, far.copy(region = Some("anatolia")), 50))

  test("finding ids continue after the highest"):
    assertEquals(
      FindingPlan.nextIds("a2000_x", Set("a2000_x#1", "a2000_x#7", "b2000_y#9"), 2),
      List("a2000_x#8", "a2000_x#9")
    )
    assertEquals(FindingPlan.nextIds("a2000_x", Set.empty, 1), List("a2000_x#1"))
