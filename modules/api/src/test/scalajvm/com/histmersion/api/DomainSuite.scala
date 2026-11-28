package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Label
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Doi
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Title
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.SilentInterp, "pending G35 (a vocabulary renders through its wire string)")
class DomainSuite extends munit.FunSuite:

  // ── Label ──────────────────────────────────────────────────────────────────────────────────────
  test("Label.display replaces underscores with spaces"):
    assertEquals(Label.display(Label.Text("bronze_age_collapse")), "bronze age collapse")

  test("Label.display passes through strings without underscores"):
    assertEquals(Label.display(Label.Text("concept")), "concept")

  test("Label.display handles empty string"):
    assertEquals(Label.display(Label.Text("")), "")

  // ── YearFmt ────────────────────────────────────────────────────────────────────────────────────
  test("YearFmt positive year"):
    assertEquals(Year.show(Year(530L)), "530 CE")

  test("YearFmt negative year is BCE"):
    assertEquals(Year.show(Year(-1300L)), "1300 BCE")

  test("YearFmt year 0 is 1 BCE (no year zero in historical reckoning)"):
    assertEquals(Year.show(Year(0L)), "1 BCE")

  test("YearFmt year 1 is 1 CE"):
    assertEquals(Year.show(Year(1L)), "1 CE")

  test("YearFmt year -1 is 1 BCE"):
    assertEquals(Year.show(Year(-1L)), "1 BCE")

  test("YearFmt.period renders a range"):
    assertEquals(Year.period(Some(Year(-1300L)), Some(Year(-1100L))), "1300 BCE–1100 BCE")

  test("YearFmt.period renders a point"):
    assertEquals(Year.period(Some(Year(-1200L)), Some(Year(-1200L))), "1200 BCE")

  test("YearFmt.period is empty when either bound is missing"):
    assertEquals(Year.period(None, Some(Year(-1200L))), "")
    assertEquals(Year.period(Some(Year(-1200L)), None), "")

  // ── Confidence ─────────────────────────────────────────────────────────────────────────────────
  test("Confidence.fromString case-insensitive"):
    assertEquals(Confidence.fromString(Confidence.Wire("High")), Some(Confidence.High))
    assertEquals(Confidence.fromString(Confidence.Wire("high")), Some(Confidence.High))
    assertEquals(Confidence.fromString(Confidence.Wire("CONTESTED")), Some(Confidence.Contested))

  test("Confidence.fromString returns None for unknown"):
    assertEquals(Confidence.fromString(Confidence.Wire("unknown")), None)

  test("Confidence wire representation is lowercase"):
    val wev = summon[WireEnumValues[Confidence]]
    assertEquals(wev.wire(Confidence.High), "high")
    assertEquals(wev.wire(Confidence.Contested), "contested")

  // ── SourceStatus ───────────────────────────────────────────────────────────────────────────────
  test("SourceStatus.fromString case-insensitive"):
    assertEquals(Source.Status.fromString(Source.Status.Wire("foundational")), Source.Status.Foundational)
    assertEquals(Source.Status.fromString(Source.Status.Wire("Contested")), Source.Status.Contested)

  test("SourceStatus.fromString defaults to Unassessed for unknown"):
    assertEquals(Source.Status.fromString(Source.Status.Wire("bogus")), Source.Status.Unassessed)

  test("SourceStatus wire representation is lowercase"):
    val wev = summon[WireEnumValues[Source.Status]]
    assertEquals(wev.wire(Source.Status.Foundational), "foundational")
    assertEquals(wev.wire(Source.Status.Superseded), "superseded")

  // ── EdgeKind ───────────────────────────────────────────────────────────────────────────────────
  test("EdgeKind.wireOf uses underscores for multi-word kinds"):
    assertEquals(Edge.Kind.wireOf(Edge.Kind.SupersededBy), "superseded_by")
    assertEquals(Edge.Kind.wireOf(Edge.Kind.FoundationalFor), "foundational_for")

  test("EdgeKind.fromWire round-trips"):
    for e <- Edge.Kind.values do
      assertEquals(Edge.Kind.fromWire(Edge.Kind.wireOf(e)), Some(e), s"EdgeKind round-trip failed for $e")

  test("EdgeKind.allWire contains all values"):
    assertEquals(Edge.Kind.allWire.size, Edge.Kind.values.length)

  // ── ArticleKind ────────────────────────────────────────────────────────────────────────────────
  test("ArticleKind.fromString case-insensitive"):
    assertEquals(Article.Kind.fromString(Article.Kind.Wire("concept")), Some(Article.Kind.Concept))
    assertEquals(Article.Kind.fromString(Article.Kind.Wire("TIMELINE")), Some(Article.Kind.Timeline))

  test("ArticleKind.fromString returns None for unknown"):
    assertEquals(Article.Kind.fromString(Article.Kind.Wire("bogus")), None)

  // ── SourceRef ──────────────────────────────────────────────────────────────────────────────────
  test("SourceRef.idLabel prefers DOI"):
    val r = Source.Ref(
      Citekey("haak2015"),
      (Some("10.1038/nature14317")).map(Doi.apply),
      (Nil).map(AuthorName.apply),
      (Some(2015)).map(Year.apply),
      Title("Title"),
      Source.Status.Current
    )
    assertEquals(r.idLabel, "10.1038/nature14317")

  test("SourceRef.idLabel falls back to citekey"):
    val r = Source.Ref(
      Citekey("haak2015"),
      None,
      (Nil).map(AuthorName.apply),
      (Some(2015)).map(Year.apply),
      Title("Title"),
      Source.Status.Current
    )
    assertEquals(r.idLabel, "haak2015")

  test("SourceRef.surname handles Firstname Lastname"):
    assertEquals(Source.Ref.surname(AuthorName("Walter Burkert")), "Burkert")

  test("SourceRef.surname handles Lastname, Firstname"):
    assertEquals(Source.Ref.surname(AuthorName("Beekes, Robert")), "Beekes")

  test("SourceRef.shortAuthors single author"):
    assertEquals(Source.Ref.shortAuthors(List(AuthorName("Walter Burkert"))), "Burkert")

  test("SourceRef.shortAuthors two authors"):
    assertEquals(
      Source.Ref.shortAuthors(List(AuthorName("Haak, Wolfgang"), AuthorName("Lazaridis, Iosif"))),
      "Haak & Lazaridis"
    )

  test("SourceRef.shortAuthors three+ authors"):
    assertEquals(
      Source.Ref
        .shortAuthors(
          List(AuthorName("Haak, Wolfgang"), AuthorName("Lazaridis, Iosif"), AuthorName("Patterson, Nick"))
        ),
      "Haak et al."
    )

  test("SourceRef.shortAuthors empty list"):
    assertEquals(Source.Ref.shortAuthors(Nil), "")

  test("SourceRef.shortCite combines authors and year"):
    val r =
      Source.Ref(
        Citekey("haak2015"),
        None,
        (List("Haak, Wolfgang", "Lazaridis, Iosif")).map(AuthorName.apply),
        (Some(2015)).map(Year.apply),
        Title("Title"),
        Source.Status.Current
      )
    assertEquals(r.shortCite, "Haak & Lazaridis 2015")

  // ── the shared HTML atoms ─────────────────────────────────────────────────────────────────────

  test("Html.Atoms.segment percent-encodes everything but the unreserved characters"):
    assertEquals(Html.Atoms.segment("cline2014_year"), "cline2014_year")
    assertEquals(Html.Atoms.segment("a b/c"), "a%20b%2Fc")
    assertEquals(Html.Atoms.segment("ł"), "%C5%82")

  test("Html.Atoms.sourceLink is the one anchor shape; the DOI attribute only when there is one"):
    assertEquals(
      Html.Atoms.sourceLink(Citekey("a2020_b"), Some(Doi("10.1/x")), "10.1/x").markup,
      """<a class="src-ref" data-ck="a2020_b" data-doi="10.1/x" href="/source/a2020_b">10.1/x</a>"""
    )
    assertEquals(
      Html.Atoms.sourceLink(Citekey("a2020_b"), Some(Doi(" ")), "a2020_b").markup,
      """<a class="src-ref" data-ck="a2020_b" href="/source/a2020_b">a2020_b</a>"""
    )

  test("every confidence level has a distinct icon, drawn in currentColor"):
    val icons = Confidence.values.toList.map(c => ConfidenceStyle.icon(c).markup)
    assertEquals(icons.distinct.size, Confidence.values.length)
    assert(icons.forall(_.contains("currentColor")))
    assert(Html.Atoms.confidenceBadge(Confidence.Low).markup.contains("conf-badge conf-low"))
