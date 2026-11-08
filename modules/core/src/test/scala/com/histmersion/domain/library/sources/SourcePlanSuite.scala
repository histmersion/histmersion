package com.histmersion
package domain.library.sources

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.MergeFixture
import com.histmersion.api.given

import MergeFixture.{home, src, uid, work}
import com.histmersion.domain.library.Confidence
import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.api.exchange.MergeDecision
import com.histmersion.api.library.Source
import com.histmersion.util.data.exchange.Issue
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.exchange.MergeIssue
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Year

/** A source folded field by field out of a pair that is one (docs/research-merge-import.md §5.3). */
class SourcePlanSuite extends munit.FunSuite:

  test(
    "a source folded: the richer title and authors, unions of tags and topics, the decisions where the sides differ"
  ):
    val l = src(
      "h",
      "guterbock1997_hittite",
      "Perspectives on Hittite civilization",
      List("Güterbock, H. G."),
      Some(1997),
      notes = Some("home's"),
      origin = home
    ).copy(
      tags = List(Bundle.Source.Tags("hittites"), Bundle.Source.Tags("Storm-God")),
      topics = List(Bundle.Source.Topics("Anatolia"))
    )
    val r = src(
      "w",
      "guterbock1997_perspectives",
      "Perspectives on Hittite Civilization: Selected Writings",
      List("Güterbock, Hans Gustav"),
      Some(1997),
      doi = Some("10.5555/oip26"),
      notes = Some("work's")
    )
      .copy(
        tags = List(Bundle.Source.Tags("storm_god"), Bundle.Source.Tags("religion")),
        topics = List(Bundle.Source.Topics("anatolia"), Bundle.Source.Topics("Religion")),
        status = Source.Status.Contested
      )
    val f = SourcePlan.fold(
      l,
      r,
      Map(MergeIssue.Kind.NotesDiffers -> MergeDecision.of(MergeDecision.Kind.Concat)),
      Citekey("guterbock1997_hittite")
    )
    assertEquals(f.uid, l.uid)
    assertEquals(f.origin, home)
    assertEquals(f.citekey, "guterbock1997_hittite")
    assertEquals(f.title, "Perspectives on Hittite Civilization: Selected Writings")
    assertEquals(f.authors, List("Güterbock, Hans Gustav"))
    assertEquals(f.doi, Some("10.5555/oip26")) // a DOI on one side only is carried over
    assertEquals(f.tags, List("hittites", "Storm-God", "religion"))
    assertEquals(f.topics, List("Anatolia", "Religion"))
    assertEquals(f.notes, Some("home's\n\nwork's"))
    assertEquals(f.status, l.status) // two verdicts: ours until decided…
    val taken =
      SourcePlan.fold(
        l,
        r,
        Map(MergeIssue.Kind.StatusDiffers -> MergeDecision.of(MergeDecision.Kind.KeepRight)),
        Citekey("guterbock1997_hittite")
      )
    assertEquals(taken.status, Source.Status.Contested) // …keep-right takes theirs
    assertEquals(taken.notes, Some("home's")) // no decision on the notes: ours
    // an unassessed side never wins over a verdict
    assertEquals(
      SourcePlan.fold(l.copy(status = Source.Status.Unassessed), r, Map.empty, Citekey("guterbock1997_hittite")).status,
      Source.Status.Contested
    )

  test("a source folded: the period by hull, the body and original from the side decided"):
    val l = src("h", "a2000_x", "X", Nil, Some(2000), body = Some("ours"), sha = Some("1" * 64), origin = home)
      .copy(period = Some(Bundle.Range(Some(Year(-1500)), Some(Year(-1300)))))
    val r = src("w", "a2000_y", "X", Nil, Some(2000), body = Some("theirs"), sha = Some("2" * 64))
      .copy(period = Some(Bundle.Range(Some(Year(-1400)), Some(Year(-1100)))))
    val hull =
      SourcePlan.fold(
        l,
        r,
        Map(MergeIssue.Kind.PeriodDiffers -> MergeDecision.of(MergeDecision.Kind.Hull)),
        Citekey("a2000_x")
      )
    assertEquals(hull.period, Some(Bundle.Range(Some(Year(-1500)), Some(Year(-1100)))))
    assertEquals(hull.bodySha256, l.bodySha256)
    val right =
      SourcePlan.fold(
        l,
        r,
        Map(MergeIssue.Kind.BodyDiffers -> MergeDecision.of(MergeDecision.Kind.KeepRight)),
        Citekey("a2000_x")
      )
    assertEquals(right.bodySha256, r.bodySha256)
    assertEquals(right.originalSha256, r.originalSha256)
    // one side without a body: the other's
    assertEquals(SourcePlan.fold(l.copy(bodySha256 = None), r, Map.empty, Citekey("a2000_x")).bodySha256, r.bodySha256)
