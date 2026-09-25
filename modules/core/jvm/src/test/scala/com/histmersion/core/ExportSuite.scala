package com.histmersion.core

/** `Export.note` must be the inverse of `Frontmatter.parseNote` — that round trip is what lets the
  * Markdown wiki stay a faithful mirror of the database (plan §1.9a `export` / `seed --force`). */
class ExportSuite extends munit.FunSuite:

  private val ck = "haak2015_massive"

  private val source = Source(
    citekey = ck,
    title = "Massive migration from the steppe",
    authors = List("Wolfgang Haak", "David Reich"),
    doi = Some("10.1038/nature14317"),
    year = Some(2015),
    venue = Some("Nature"),
    status = SourceStatus.Foundational,
    topics = List("ancient_dna", "yamnaya_culture"),
    regions = List("Eurasian Steppe", "Central Europe"),
    tags = List("adna"),
    period = Some(Period(-8000, -900)),
    summary = Some("Genome-wide data from 69 Europeans, 8000-3000 years ago."),
    citedByCount = Some(2038),
    influentialCitations = None,
    provenance = Some("unpaywall"),
    calibreId = Some("412"),
    ocrMarkdown = Some("MEGABYTES of converted paper that must NOT be exported"),
    findings = List(
      Finding(s"$ck#0", ck, "Corded Ware derived ~75% of ancestry from Yamnaya.",
        Some("Central Europe (Germany)"), Some(GeoPoint(10.4, 51.2)), Some(Period(-2500, -2200)),
        Some("Late Neolithic / Corded Ware"), List("corded_ware", "yamnaya_culture"), Some("high")),
      Finding(s"$ck#1", ck, "An undated, unplaced claim.", None, None, None, None, Nil, None)),
    edges = List(Edge(ck, "anthony2007_horse", "references")),
    notes = Some("# Massive migration\n\nSynthesis citing [[anthony2007_horse]].\n\n## References\n\n- one"),
    updatedAt = Some("2026-08-25T10:00:00.000000Z"))

  test("export then parse round-trips every frontmatter field"):
    val back = Frontmatter.parseNote(Export.note(source), "wrong_fallback").get
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
    val back = Frontmatter.parseNote(Export.note(source), ck).get
    assertEquals(back.findings, source.findings)

  test("the notes article is the BODY, and comes back as notes"):
    val text = Export.note(source)
    assert(text.contains("\n---\n"), text.take(200))
    assert(text.endsWith("- one\n"), text.takeRight(80))
    assertEquals(Frontmatter.parseNote(text, ck).get.notes, source.notes)

  test("ocrMarkdown is NOT exported — the paper body lives in md/<citekey>/paper.md"):
    assert(!Export.note(source).contains("MEGABYTES"))

  test("export is stable: exporting a parsed export gives the identical text"):
    val once  = Export.note(source)
    val twice = Export.note(Frontmatter.parseNote(once, ck).get)
    assertEquals(twice, once)

  test("a source with no findings, notes or optional metadata still round-trips"):
    val bare = Source(
      citekey = "bare_one", title = "T", authors = Nil, doi = None, year = None, venue = None,
      status = SourceStatus.Unassessed, topics = Nil, regions = Nil, tags = Nil, period = None,
      summary = None, citedByCount = None, influentialCitations = None, provenance = None,
      calibreId = None)
    val back = Frontmatter.parseNote(Export.note(bare), "fallback").get
    assertEquals(back.citekey, "bare_one")
    assertEquals(back.findings, Nil)
    assertEquals(back.notes, None)
    assertEquals(back.period, None)

  test("superseded_by is exported from the edge and parsed back into it"):
    val s    = source.copy(edges = List(Edge(ck, "reich2018_who", "superseded_by")))
    val back = Frontmatter.parseNote(Export.note(s), ck).get
    assert(back.edges.contains(Edge(ck, "reich2018_who", "superseded_by")), back.edges.toString)

  test("wikilinks in the notes body are re-derived as reference edges"):
    val back = Frontmatter.parseNote(Export.note(source), ck).get
    assert(back.edges.contains(Edge(ck, "anthony2007_horse", "references")), back.edges.toString)
