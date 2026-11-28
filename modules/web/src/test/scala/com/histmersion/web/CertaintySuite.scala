package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.markupText
import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.GeoHit
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.ConfidenceStyle
import com.histmersion.lint.{tolerated, LintRule}

/** [[Certainty]] (#1946): a finding that states no confidence is drawn in its own neutral look — grey, dashed, its own
  * legend pip — never as high, and never lets a bubble look high. Pure.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CertaintySuite extends munit.FunSuite:

  override def beforeAll(): Unit = TestTokens.install()

  private val span = (Year.Astronomical(-3500L), Year.Astronomical(-500L))
  private def hit(conf: Option[Confidence]) =
    GeoHit(
      Citekey("zz2020_x"),
      GeoHit.Title("Zz"),
      GeoHit.Claim("a claim"),
      None,
      Some(Longitude(30.0)),
      Some(Latitude(37.0)),
      Some(Year(-2100L)),
      Some(Year(-1900L)),
      conf
    )
  private val high = Certainty.Stated(Confidence.High)
  private val low = Certainty.Stated(Confidence.Low)
  private val contested = Certainty.Stated(Confidence.Contested)

  test("a missing confidence is unstated, not high"):
    assertEquals(Certainty.of(None), Certainty.Unstated)
    assertEquals(Certainty.of(Some(Confidence.Medium)), Certainty.Stated(Confidence.Medium))
    assertEquals(PointKinds.Findings.confOf(Spot(Latitude(37.0), Longitude(30.0), List(hit(None)))), Certainty.Unstated)

  test("a group: its weakest — unstated only outranks high, a stated low or contested says more"):
    assertEquals(Certainty.ofAll(List(Some(Confidence.High), None)), Certainty.Unstated)
    assertEquals(Certainty.ofAll(List(None, Some(Confidence.Low))), low)
    assertEquals(Certainty.ofAll(List(Some(Confidence.Contested), None, Some(Confidence.Medium))), contested)
    assertEquals(Certainty.ofAll(Nil), Certainty.NoSay)
    assertEquals(Certainty.NoSay, high)
    val ranks = List(contested, low, Certainty.Stated(Confidence.Medium), Certainty.Unstated, high).map(c =>
      Certainty.rank(c).value
    )
    assertEquals(ranks, ranks.sorted)
    assertEquals(ranks.distinct.size, 5)

  test("a bubble of sites and an unstated finding is unstated; sites alone have no say"):
    val site = Member(DotKind.Site, Member.N(1), Nil, Certainty.NoSay)
    val finding = Member(DotKind.Finding, Member.N(1), Nil, Certainty.Unstated, findings = List(hit(None)))
    assertEquals(DotCluster.Contents(List(site, finding)).certainty, Certainty.Unstated)
    assertEquals(DotCluster.Contents(List(site)).certainty, Certainty.NoSay)

  test("drawn: unstated is the colour's luminance grey — unlike high, unlike every stated level"):
    val stated =
      Confidence.values.toList.map(c => MapView.colorFor(Some(MapView.Mid(-2000.0)), span, Certainty.Stated(c)))
    val unstated = MapView.colorFor(Some(MapView.Mid(-2000.0)), span, Certainty.Unstated)
    assert(!stated.contains(unstated), s"$unstated among $stated")
    val parts = "\\d+".r.findAllIn(unstated.value).toList
    assertEquals(parts.distinct.size, 1, s"a grey: $unstated")
    assertEquals(
      Certainty.desaturate(ConfidenceStyle.Rgb("rgb(33,145,140)"), Certainty.Unstated).value,
      "rgb(111,111,111)",
      "the stylesheet's --conf-unstated is the ramp's midpoint drawn this way"
    )
    assertEquals(Certainty.desaturate(ConfidenceStyle.Rgb("var(--x)"), Certainty.Unstated).value, "var(--x)")
    assertEquals(Certainty.desaturate(ConfidenceStyle.Rgb("rgb(33,145,140)"), high).value, "rgb(33,145,140)")
    // the older and the newer still differ in lightness
    assert(
      MapView.colorFor(Some(MapView.Mid(-3400.0)), span, Certainty.Unstated) != MapView.colorFor(
        Some(MapView.Mid(-600.0)),
        span,
        Certainty.Unstated
      )
    )

  test("the marker: an unstated finding carries the dashed class; a stated one does not"):
    assert(
      PointKinds.Findings
        .classes(Spot(Latitude(37.0), Longitude(30.0), List(hit(None))))
        .split(' ')
        .contains("hd-unstated")
    )
    assert(
      !PointKinds.Findings
        .classes(Spot(Latitude(37.0), Longitude(30.0), List(hit(Some(Confidence.High)))))
        .contains("hd-unstated")
    )
    assertEquals(Certainty.markerClass(low).value, "")

  test("the popup says the confidence is not stated instead of showing nothing"):
    val h = MapView.popupHtml(hit(None)).value
    assert(h.contains("confidence not stated"), h)
    assert(!MapView.popupHtml(hit(Some(Confidence.High))).value.contains("not stated"))
