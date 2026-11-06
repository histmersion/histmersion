package com.histmersion.files

import com.histmersion.api.GeoPoint
import com.histmersion.api.given
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.domain.library.sources.SourceNote
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source

class SourceNoteParseSuite extends munit.FunSuite:
  val note =
    """---
      |citekey: heggarty2023_language
      |title: Language trees
      |authors: [Paul Heggarty, Cormac Anderson]
      |year: 2023
      |venue: null
      |status: current
      |topics: [indo-european, refutes_anthony2007]
      |regions: [Anatolia]
      |tags: [phylogeny]
      |period_start: -6000
      |period_end: -3000
      |superseded_by: null
      |findings:
      |  - claim: Hybrid origin
      |    region: Caucasus
      |    lat: 42.5
      |    lon: 44.0
      |    period_start: -6000
      |    period_end: -5000
      |    tags: [origin]
      |    confidence: medium
      |  - claim: undated one
      |    lat: null
      |---
      |Body citing [[anthony2007_horse|Anthony]] and [[bouckaert2012_mapping]].
      |""".stripMargin

  test("parses frontmatter with null values safely"):
    val s = SourceNote.parseNote(SourceNote.Text(note), com.histmersion.api.library.Citekey("fallback")).get
    assertEquals(s.citekey, "heggarty2023_language")
    assertEquals(s.venue, None)
    assertEquals(s.year, Some(2023))
    assertEquals(s.period, Some(Period(com.histmersion.api.catalogue.Year(-6000), com.histmersion.api.catalogue.Year(-3000))))
    assertEquals(s.status, Source.Status.Current)

  test("findings: geo + period, and null geo → None"):
    val fs = SourceNote.parseNote(SourceNote.Text(note), com.histmersion.api.library.Citekey("x")).get.findings
    assertEquals(fs.map(_.id), List("heggarty2023_language#0", "heggarty2023_language#1"))
    assertEquals(fs.head.geo, Some(GeoPoint(com.histmersion.api.catalogue.Longitude(44.0), com.histmersion.api.catalogue.Latitude(42.5))))
    assertEquals(fs(1).geo, None)
    assertEquals(fs(1).period, None)

  test("edges: relation tags and wikilinks (aliased or not), never self"):
    val es = SourceNote.parseNote(SourceNote.Text(note), com.histmersion.api.library.Citekey("x")).get.edges.toSet
    assertEquals(
      es,
      Set(
        Edge(com.histmersion.api.library.Citekey("heggarty2023_language"), com.histmersion.api.library.Citekey("anthony2007"), Edge.Kind.Refutes),
        Edge(com.histmersion.api.library.Citekey("heggarty2023_language"), com.histmersion.api.library.Citekey("anthony2007_horse"), Edge.Kind.References),
        Edge(com.histmersion.api.library.Citekey("heggarty2023_language"), com.histmersion.api.library.Citekey("bouckaert2012_mapping"), Edge.Kind.References)
      )
    )

  test("a topic-slug wikilink becomes a TOPIC, not a references edge to something that is not a source"):
    val withTopic = note.replace(
      "and [[bouckaert2012_mapping]].",
      "and [[bouckaert2012_mapping]], see [[bronze_age_collapse]] and [[Indo_European]]."
    )
    val s = SourceNote.parseNote(SourceNote.Text(withTopic), com.histmersion.api.library.Citekey("x")).get
    assertEquals(
      s.edges.filter(_.kind == Edge.Kind.References).map(_.dst).toSet,
      Set("anthony2007_horse", "bouckaert2012_mapping")
    )
    // appended after the declared topics; `Indo_European` is not citekey-shaped (upper case) so the
    // link pattern never sees it
    assertEquals(s.topics, List("indo-european", "refutes_anthony2007", "bronze_age_collapse"))
    // an already-declared topic is not duplicated
    val dup = withTopic.replace("topics: [indo-european,", "topics: [Bronze_Age_Collapse, indo-european,")
    assertEquals(SourceNote.parseNote(SourceNote.Text(dup), com.histmersion.api.library.Citekey("x")).get.topics.count(_.value.equalsIgnoreCase("bronze_age_collapse")), 1)

  test("no frontmatter → None; fallback citekey from filename"):
    assertEquals(SourceNote.parseNote(com.histmersion.domain.library.sources.SourceNote.Text("just text"), com.histmersion.api.library.Citekey("x")), None)
    assertEquals(SourceNote.parseNote(com.histmersion.domain.library.sources.SourceNote.Text("---\ntitle: T\n---\n"), com.histmersion.api.library.Citekey("fromfile")).get.citekey, "fromfile")

  // ── a note whose deep-time bounds are the wrong way round ────────────────────────────────────

  test("inverted period_start/period_end are ORDERED, not passed through to Postgres"):
    // an easy slip when both numbers are negative. An inverted int8range is refused by Postgres, so
    // one such note used to fail the whole import request with a 500.
    val note =
      """---
        |citekey: zz_inverted
        |title: Backwards
        |period_start: -1200
        |period_end: -1400
        |findings:
        |  - claim: also backwards
        |    period_start: -900
        |    period_end: -1100
        |---
        |body
        |""".stripMargin
    val s = SourceNote.parseNote(SourceNote.Text(note), com.histmersion.api.library.Citekey("zz_inverted")).get
    assertEquals(s.period, Some(Period(com.histmersion.api.catalogue.Year(-1400), com.histmersion.api.catalogue.Year(-1200))))
    assertEquals(s.findings.head.period, Some(Period(com.histmersion.api.catalogue.Year(-1100), com.histmersion.api.catalogue.Year(-900))))

  test("a correctly-ordered period is untouched"):
    val note =
      """---
        |citekey: zz_ordered
        |period_start: -1400
        |period_end: -1200
        |---
        |""".stripMargin
    assertEquals(SourceNote.parseNote(SourceNote.Text(note), com.histmersion.api.library.Citekey("zz_ordered")).get.period, Some(Period(com.histmersion.api.catalogue.Year(-1400), com.histmersion.api.catalogue.Year(-1200))))
