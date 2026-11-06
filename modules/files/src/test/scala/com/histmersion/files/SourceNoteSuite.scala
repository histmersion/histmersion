package com.histmersion.files

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.GeoPoint
import com.histmersion.api.given
import com.histmersion.api.library.Confidence
import com.histmersion.domain.library.sources.SourceNote
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Edge, Finding, Source}

/** `SourceNote.note` must be the inverse of `SourceNote.parseNote` — that round trip is what lets the Markdown wiki
  * stay a faithful mirror of the database (plan §1.9a `export` / `seed --force`).
  */
class SourceNoteSuite extends munit.FunSuite:

  private val ck = "haak2015_massive"

  private val source = Source(
    citekey = com.histmersion.api.library.Citekey(ck),
    title = com.histmersion.api.library.Title("Massive migration from the steppe"),
    authors = (List("Wolfgang Haak", "David Reich")).map(com.histmersion.api.library.AuthorName.apply),
    doi = (Some("10.1038/nature14317")).map(com.histmersion.api.library.Doi.apply),
    year = (Some(2015)).map(com.histmersion.api.catalogue.Year.apply),
    venue = (Some("Nature")).map(com.histmersion.api.library.Source.Venue.apply),
    status = com.histmersion.api.library.Source.Status.Foundational,
    topics = List(com.histmersion.api.library.Source.Topics("ancient_dna"), com.histmersion.api.library.Source.Topics("yamnaya_culture")),
    regions = List(com.histmersion.api.library.Source.Regions("Eurasian Steppe"), com.histmersion.api.library.Source.Regions("Central Europe")),
    tags = List(com.histmersion.api.library.Source.Tags("adna")),
    period = Some(Period(com.histmersion.api.catalogue.Year(-8000), com.histmersion.api.catalogue.Year(-900))),
    summary = Some(com.histmersion.api.library.Source.Summary("Genome-wide data from 69 Europeans, 8000-3000 years ago.")),
    citedByCount = Some(com.histmersion.api.library.Source.CitedByCount(2038)),
    influentialCitations = None,
    provenance = Some(com.histmersion.api.library.Source.Provenance("unpaywall")),
    calibreId = Some(com.histmersion.api.library.Source.CalibreId("412")),
    ocrMarkdown = Some(com.histmersion.api.library.Source.OcrMarkdown("MEGABYTES of converted paper that must NOT be exported")),
    findings = List(
      Finding(
        s"$ck#0",
        com.histmersion.api.library.Citekey(ck),
        com.histmersion.api.library.Finding.Claim("Corded Ware derived ~75% of ancestry from Yamnaya."),
        Some(com.histmersion.api.library.Finding.Region("Central Europe (Germany)")),
        Some(GeoPoint(10.4, 51.2)),
        Some(Period(com.histmersion.api.catalogue.Year(-2500), com.histmersion.api.catalogue.Year(-2200))),
        Some(com.histmersion.api.library.Finding.PeriodName("Late Neolithic / Corded Ware")),
        List(com.histmersion.api.library.Finding.Tags("corded_ware"), com.histmersion.api.library.Finding.Tags("yamnaya_culture")),
        Some(Confidence.High)
      ),
      Finding(s"$ck#1", com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Finding.Claim("An undated, unplaced claim."), None, None, None, None, Nil, None)
    ),
    edges = List(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("anthony2007_horse"), com.histmersion.api.library.Edge.Kind("references"))),
    notes = Some("# Massive migration\n\nSynthesis citing [[anthony2007_horse]].\n\n## References\n\n- one"),
    updatedAt = Some(com.histmersion.api.library.Source.UpdatedAt("2026-08-25T10:00:00.000000Z"))
  )

  test("export then parse round-trips every frontmatter field"):
    val back = SourceNote.parseNote(SourceNote.note(source), com.histmersion.api.library.Citekey("wrong_fallback")).get
    assertEquals(back.citekey, source.citekey)
    assertEquals(back.title, source.title)
    assertEquals(back.authors, source.authors)
    assertEquals(back.doi, source.doi)
    assertEquals(back.year, source.year)
    assertEquals(back.venue, source.venue)
    assertEquals(back.status, source.status)
    assertEquals(back.topics, source.topics)
    assertEquals(back.regions, source.regions)
    assertEquals(back.tags, source.tags)
    assertEquals(back.period, source.period)
    assertEquals(back.summary, source.summary)
    assertEquals(back.citedByCount, source.citedByCount)
    assertEquals(back.influentialCitations, source.influentialCitations)
    assertEquals(back.provenance, source.provenance)
    assertEquals(back.calibreId, source.calibreId)

  test("export then parse round-trips findings, including the empty one"):
    val back = SourceNote.parseNote(SourceNote.note(source), ck).get
    assertEquals(back.findings, source.findings)

  test("the notes article is the BODY, and comes back as notes"):
    val text = SourceNote.note(source)
    assert(text.contains("\n---\n"), text.take(200))
    assert(text.endsWith("- one\n"), text.takeRight(80))
    assertEquals(SourceNote.parseNote(text, ck).get.notes, source.notes)

  test("ocrMarkdown is NOT exported — the paper body lives in md/<citekey>/paper.md"):
    assert(!SourceNote.note(source).contains("MEGABYTES"))

  test("export is stable: exporting a parsed export gives the identical text"):
    val once = SourceNote.note(source)
    val twice = SourceNote.note(SourceNote.parseNote(once, ck).get)
    assertEquals(twice, once)

  test("a source with no findings, notes or optional metadata still round-trips"):
    val bare = Source(
      citekey = com.histmersion.api.library.Citekey("bare_one"),
      title = com.histmersion.api.library.Title("T"),
      authors = (Nil).map(com.histmersion.api.library.AuthorName.apply),
      doi = None,
      year = None,
      venue = None,
      status = com.histmersion.api.library.Source.Status.Unassessed,
      topics = Nil,
      regions = Nil,
      tags = Nil,
      period = None,
      summary = None,
      citedByCount = None,
      influentialCitations = None,
      provenance = None,
      calibreId = None
    )
    val back = SourceNote.parseNote(SourceNote.note(bare), com.histmersion.api.library.Citekey("fallback")).get
    assertEquals(back.citekey, "bare_one")
    assertEquals(back.findings, Nil)
    assertEquals(back.notes, None)
    assertEquals(back.period, None)

  test("superseded_by is exported from the edge and parsed back into it"):
    val s = source.modify(_.edges).setTo(List(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("reich2018_who"), "superseded_by")))
    val back = SourceNote.parseNote(SourceNote.note(s), ck).get
    assert(back.edges.contains(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("reich2018_who"), "superseded_by")), back.edges.toString)

  test("wikilinks in the notes body are re-derived as reference edges"):
    val back = SourceNote.parseNote(SourceNote.note(source), ck).get
    assert(back.edges.contains(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey("anthony2007_horse"), "references")), back.edges.toString)
