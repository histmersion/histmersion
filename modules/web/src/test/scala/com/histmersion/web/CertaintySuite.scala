package com.histmersion.web

import com.histmersion.domain.library.Confidence
import com.histmersion.api.library.GeoHit

/** [[Certainty]] (#1946): a finding that states no confidence is drawn in its own neutral look — grey, dashed, its own
  * legend pip — never as high, and never lets a bubble look high. Pure.
  */
class CertaintySuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  private val span = (-3500L, -500L)
  private def hit(conf: Option[Confidence]) =
    GeoHit("zz2020_x", "Zz", "a claim", None, Some(30.0), Some(37.0), Some(-2100L), Some(-1900L), conf)
  private val high = Certainty.Stated(Confidence.High)
  private val low = Certainty.Stated(Confidence.Low)
  private val contested = Certainty.Stated(Confidence.Contested)

  test("a missing confidence is unstated, not high"):
    assertEquals(Certainty.of(None), Certainty.Unstated)
    assertEquals(Certainty.of(Some(Confidence.Medium)), Certainty.Stated(Confidence.Medium))
    assertEquals(PointKinds.Findings.confOf(Spot(37.0, 30.0, List(hit(None)))), Certainty.Unstated)

  test("a group: its weakest — unstated only outranks high, a stated low or contested says more"):
    assertEquals(Certainty.ofAll(List(Some(Confidence.High), None)), Certainty.Unstated)
    assertEquals(Certainty.ofAll(List(None, Some(Confidence.Low))), low)
    assertEquals(Certainty.ofAll(List(Some(Confidence.Contested), None, Some(Confidence.Medium))), contested)
    assertEquals(Certainty.ofAll(Nil), Certainty.NoSay)
    assertEquals(Certainty.NoSay, high)
    val ranks = List(contested, low, Certainty.Stated(Confidence.Medium), Certainty.Unstated, high).map(Certainty.rank)
    assertEquals(ranks, ranks.sorted)
    assertEquals(ranks.distinct.size, 5)

  test("a bubble of sites and an unstated finding is unstated; sites alone have no say"):
    val site = Member(DotKind.Site, 1, Nil, Certainty.NoSay)
    val finding = Member(DotKind.Finding, 1, Nil, Certainty.Unstated, findings = List(hit(None)))
    assertEquals(DotCluster.Contents(List(site, finding)).certainty, Certainty.Unstated)
    assertEquals(DotCluster.Contents(List(site)).certainty, Certainty.NoSay)

  test("drawn: unstated is the colour's luminance grey — unlike high, unlike every stated level"):
    val stated = Confidence.values.toList.map(c => MapView.colorFor(Some(-2000.0), span, Certainty.Stated(c)))
    val unstated = MapView.colorFor(Some(-2000.0), span, Certainty.Unstated)
    assert(!stated.contains(unstated), s"$unstated among $stated")
    val parts = "\\d+".r.findAllIn(unstated).toList
    assertEquals(parts.distinct.size, 1, s"a grey: $unstated")
    assertEquals(
      Certainty.desaturate("rgb(33,145,140)", Certainty.Unstated),
      "rgb(111,111,111)",
      "the stylesheet's --conf-unstated is the ramp's midpoint drawn this way"
    )
    assertEquals(Certainty.desaturate("var(--x)", Certainty.Unstated), "var(--x)")
    assertEquals(Certainty.desaturate("rgb(33,145,140)", high), "rgb(33,145,140)")
    // the older and the newer still differ in lightness
    assert(
      MapView.colorFor(Some(-3400.0), span, Certainty.Unstated) != MapView.colorFor(
        Some(-600.0),
        span,
        Certainty.Unstated
      )
    )

  test("the marker: an unstated finding carries the dashed class; a stated one does not"):
    assert(PointKinds.Findings.classes(Spot(37.0, 30.0, List(hit(None)))).split(' ').contains("hd-unstated"))
    assert(!PointKinds.Findings.classes(Spot(37.0, 30.0, List(hit(Some(Confidence.High))))).contains("hd-unstated"))
    assertEquals(Certainty.markerClass(low), "")

  test("the popup says the confidence is not stated instead of showing nothing"):
    val h = MapView.popupHtml(hit(None))
    assert(h.contains("confidence not stated"), h)
    assert(!MapView.popupHtml(hit(Some(Confidence.High))).contains("not stated"))
