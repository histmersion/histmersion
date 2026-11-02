package com.histmersion.api

import com.histmersion.domain.library.Confidence
import com.histmersion.WireEnumValues
import com.histmersion.api.library.Label
import com.histmersion.domain.library.Edge
import com.histmersion.domain.library.Source
import com.histmersion.api.research.Article

class DomainSuite extends munit.FunSuite:

  // ── Label ──────────────────────────────────────────────────────────────────────────────────────
  test("Label.display replaces underscores with spaces"):
    assertEquals(Label.display("bronze_age_collapse"), "bronze age collapse")

  test("Label.display passes through strings without underscores"):
    assertEquals(Label.display("concept"), "concept")

  test("Label.display handles empty string"):
    assertEquals(Label.display(""), "")

  // ── YearFmt ────────────────────────────────────────────────────────────────────────────────────
  test("YearFmt positive year"):
    assertEquals(YearFmt(530L), "530 CE")

  test("YearFmt negative year is BCE"):
    assertEquals(YearFmt(-1300L), "1300 BCE")

  test("YearFmt year 0 is 1 BCE (no year zero in historical reckoning)"):
    assertEquals(YearFmt(0L), "1 BCE")

  test("YearFmt year 1 is 1 CE"):
    assertEquals(YearFmt(1L), "1 CE")

  test("YearFmt year -1 is 1 BCE"):
    assertEquals(YearFmt(-1L), "1 BCE")

  test("YearFmt.period renders a range"):
    assertEquals(YearFmt.period(Some(-1300L), Some(-1100L)), "1300 BCE–1100 BCE")

  test("YearFmt.period renders a point"):
    assertEquals(YearFmt.period(Some(-1200L), Some(-1200L)), "1200 BCE")

  test("YearFmt.period is empty when either bound is missing"):
    assertEquals(YearFmt.period(None, Some(-1200L)), "")
    assertEquals(YearFmt.period(Some(-1200L), None), "")

  // ── Confidence ─────────────────────────────────────────────────────────────────────────────────
  test("Confidence.fromString case-insensitive"):
    assertEquals(Confidence.fromString("High"), Some(Confidence.High))
    assertEquals(Confidence.fromString("high"), Some(Confidence.High))
    assertEquals(Confidence.fromString("CONTESTED"), Some(Confidence.Contested))

  test("Confidence.fromString returns None for unknown"):
    assertEquals(Confidence.fromString("unknown"), None)

  test("Confidence wire representation is lowercase"):
    val wev = summon[WireEnumValues[Confidence]]
    assertEquals(wev.wire(Confidence.High), "high")
    assertEquals(wev.wire(Confidence.Contested), "contested")

  // ── SourceStatus ───────────────────────────────────────────────────────────────────────────────
  test("SourceStatus.fromString case-insensitive"):
    assertEquals(Source.Status.fromString("foundational"), Source.Status.Foundational)
    assertEquals(Source.Status.fromString("Contested"), Source.Status.Contested)

  test("SourceStatus.fromString defaults to Unassessed for unknown"):
    assertEquals(Source.Status.fromString("bogus"), Source.Status.Unassessed)

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
    assertEquals(Article.Kind.fromString("concept"), Some(Article.Kind.Concept))
    assertEquals(Article.Kind.fromString("TIMELINE"), Some(Article.Kind.Timeline))

  test("ArticleKind.fromString returns None for unknown"):
    assertEquals(Article.Kind.fromString("bogus"), None)

  // ── SourceRef ──────────────────────────────────────────────────────────────────────────────────
  test("SourceRef.idLabel prefers DOI"):
    val r = com.histmersion.api.library.Source.Ref(
      "haak2015",
      Some("10.1038/nature14317"),
      Nil,
      Some(2015),
      "Title",
      Source.Status.Current
    )
    assertEquals(r.idLabel, "10.1038/nature14317")

  test("SourceRef.idLabel falls back to citekey"):
    val r = com.histmersion.api.library.Source.Ref("haak2015", None, Nil, Some(2015), "Title", Source.Status.Current)
    assertEquals(r.idLabel, "haak2015")

  test("SourceRef.surname handles Firstname Lastname"):
    assertEquals(com.histmersion.api.library.Source.Ref.surname("Walter Burkert"), "Burkert")

  test("SourceRef.surname handles Lastname, Firstname"):
    assertEquals(com.histmersion.api.library.Source.Ref.surname("Beekes, Robert"), "Beekes")

  test("SourceRef.shortAuthors single author"):
    assertEquals(com.histmersion.api.library.Source.Ref.shortAuthors(List("Walter Burkert")), "Burkert")

  test("SourceRef.shortAuthors two authors"):
    assertEquals(
      com.histmersion.api.library.Source.Ref.shortAuthors(List("Haak, Wolfgang", "Lazaridis, Iosif")),
      "Haak & Lazaridis"
    )

  test("SourceRef.shortAuthors three+ authors"):
    assertEquals(
      com.histmersion.api.library.Source.Ref
        .shortAuthors(List("Haak, Wolfgang", "Lazaridis, Iosif", "Patterson, Nick")),
      "Haak et al."
    )

  test("SourceRef.shortAuthors empty list"):
    assertEquals(com.histmersion.api.library.Source.Ref.shortAuthors(Nil), "")

  test("SourceRef.shortCite combines authors and year"):
    val r =
      com.histmersion.api.library.Source.Ref(
        "haak2015",
        None,
        List("Haak, Wolfgang", "Lazaridis, Iosif"),
        Some(2015),
        "Title",
        Source.Status.Current
      )
    assertEquals(r.shortCite, "Haak & Lazaridis 2015")

  // ── the shared HTML atoms ─────────────────────────────────────────────────────────────────────

  test("HtmlAtoms.segment percent-encodes everything but the unreserved characters"):
    assertEquals(HtmlAtoms.segment("cline2014_year"), "cline2014_year")
    assertEquals(HtmlAtoms.segment("a b/c"), "a%20b%2Fc")
    assertEquals(HtmlAtoms.segment("ł"), "%C5%82")

  test("HtmlAtoms.sourceLink is the one anchor shape; the DOI attribute only when there is one"):
    assertEquals(
      HtmlAtoms.sourceLink("a2020_b", Some("10.1/x"), "10.1/x"),
      """<a class="src-ref" data-ck="a2020_b" data-doi="10.1/x" href="/source/a2020_b">10.1/x</a>"""
    )
    assertEquals(
      HtmlAtoms.sourceLink("a2020_b", Some(" "), "a2020_b"),
      """<a class="src-ref" data-ck="a2020_b" href="/source/a2020_b">a2020_b</a>"""
    )

  test("every confidence level has a distinct icon, drawn in currentColor"):
    val icons = Confidence.values.toList.map(ConfidenceStyle.icon)
    assertEquals(icons.distinct.size, Confidence.values.length)
    assert(icons.forall(_.contains("currentColor")))
    assert(HtmlAtoms.confidenceBadge(Confidence.Low).contains("conf-badge conf-low"))
