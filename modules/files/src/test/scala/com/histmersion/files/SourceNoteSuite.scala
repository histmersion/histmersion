package com.histmersion
package files

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.GeoPoint
import com.histmersion.api.given
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.library.Confidence
import com.histmersion.domain.library.sources.SourceNote
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Edge, Finding, Source}
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude

/** `SourceNote.note` must be the inverse of `SourceNote.parseNote` — that round trip is what lets the Markdown wiki
  * stay a faithful mirror of the database (plan §1.9a `export` / `seed --force`).
  */
class SourceNoteSuite extends munit.FunSuite:

  private val ck = Citekey("haak2015_massive")

  private val source = Source(
    citekey = ck,
    title = Title("Massive migration from the steppe"),
    authors = (List("Wolfgang Haak", "David Reich")).map(AuthorName.apply),
    doi = (Some("10.1038/nature14317")).map(Doi.apply),
    year = (Some(2015)).map(Year.apply),
    venue = (Some("Nature")).map(Source.Venue.apply),
    status = Source.Status.Foundational,
    topics = List(Source.Topics("ancient_dna"), Source.Topics("yamnaya_culture")),
    regions = List(Source.Regions("Eurasian Steppe"), Source.Regions("Central Europe")),
    tags = List(Source.Tags("adna")),
    period = Some(Period(Year(-8000), Year(-900))),
    summary = Some(Source.Summary("Genome-wide data from 69 Europeans, 8000-3000 years ago.")),
    citedByCount = Some(Source.CitedByCount(2038)),
    influentialCitations = None,
    provenance = Some(Source.Provenance("unpaywall")),
    calibreId = Some(Source.CalibreId("412")),
    ocrMarkdown = Some(Source.OcrMarkdown("MEGABYTES of converted paper that must NOT be exported")),
    findings = List(
      Finding(
        Finding.Id(s"$ck#0"),
        ck,
        Finding.Claim("Corded Ware derived ~75% of ancestry from Yamnaya."),
        Some(Finding.Region("Central Europe (Germany)")),
        Some(GeoPoint(Longitude(10.4), Latitude(51.2))),
        Some(Period(Year(-2500), Year(-2200))),
        Some(Finding.PeriodName("Late Neolithic / Corded Ware")),
        List(Finding.Tags("corded_ware"), Finding.Tags("yamnaya_culture")),
        Some(Confidence.High)
      ),
      Finding(Finding.Id(s"$ck#1"), ck, Finding.Claim("An undated, unplaced claim."), None, None, None, None, Nil, None)
    ),
    edges = List(Edge(ck, Citekey("anthony2007_horse"), Edge.Kind.References)),
    notes =
      Some(Source.Notes("# Massive migration\n\nSynthesis citing [[anthony2007_horse]].\n\n## References\n\n- one")),
    updatedAt = Some(Source.UpdatedAt("2026-08-25T10:00:00.000000Z"))
  )

  test("export then parse round-trips every frontmatter field"):
    val back = SourceNote.parseNote(SourceNote.note(source), Citekey("wrong_fallback")).get
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
    assert(text.value.contains("\n---\n"), text.value.take(200))
    assert(text.value.endsWith("- one\n"), text.value.takeRight(80))
    assertEquals(SourceNote.parseNote(text, ck).get.notes, source.notes)

  test("ocrMarkdown is NOT exported — the paper body lives in md/<citekey>/paper.md"):
    assert(!SourceNote.note(source).value.contains("MEGABYTES"))

  test("export is stable: exporting a parsed export gives the identical text"):
    val once = SourceNote.note(source)
    val twice = SourceNote.note(SourceNote.parseNote(once, ck).get)
    assertEquals(twice, once)

  test("a source with no findings, notes or optional metadata still round-trips"):
    val bare = Source(
      citekey = Citekey("bare_one"),
      title = Title("T"),
      authors = (Nil).map(AuthorName.apply),
      doi = None,
      year = None,
      venue = None,
      status = Source.Status.Unassessed,
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
    val back = SourceNote.parseNote(SourceNote.note(bare), Citekey("fallback")).get
    assertEquals(back.citekey, "bare_one")
    assertEquals(back.findings, Nil)
    assertEquals(back.notes, None)
    assertEquals(back.period, None)

  test("superseded_by is exported from the edge and parsed back into it"):
    val s = source.modify(_.edges).setTo(List(Edge(ck, Citekey("reich2018_who"), Edge.Kind.SupersededBy)))
    val back = SourceNote.parseNote(SourceNote.note(s), ck).get
    assert(back.edges.contains(Edge(ck, Citekey("reich2018_who"), Edge.Kind.SupersededBy)), back.edges.toString)

  test("wikilinks in the notes body are re-derived as reference edges"):
    val back = SourceNote.parseNote(SourceNote.note(source), ck).get
    assert(back.edges.contains(Edge(ck, Citekey("anthony2007_horse"), Edge.Kind.References)), back.edges.toString)
