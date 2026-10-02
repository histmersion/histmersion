package com.histmersion.core

import io.circe.parser.parse

/** The upstream JSON parsers, driven by REAL responses captured from OpenAlex, Crossref, Unpaywall,
  * Semantic Scholar, archive.org and Wikipedia (trimmed, in `src/test/resources/acquire`). Fixtures
  * rather than mocks: when an upstream changes shape, re-capturing the fixture is what tells us. */
class AcquireParseSuite extends munit.FunSuite:

  private def fixture(name: String) =
    parse(scala.io.Source.fromResource(s"acquire/$name").mkString).fold(throw _, identity)

  // ── OpenAlex ────────────────────────────────────────────────────────────────────────────────
  test("OpenAlex work → WorkRef, with the citekey the corpus already uses"):
    val w = AcquireParse.openalexWork(fixture("openalex_work.json"))
    assertEquals(w.doi, Some("10.1038/nature14317"))
    assertEquals(w.openalexId, Some("W2050876420"))
    assertEquals(w.title.map(_.take(16)), Some("Massive migratio"))
    assertEquals(w.year, Some(2015))
    assertEquals(w.authors.head, "Wolfgang Haak")
    assertEquals(w.venue, Some("Nature"))
    assertEquals(w.citekey, Some("haak2015_massive"))
    assert(w.isOa)
    assertEquals(w.oaStatus, Some("green"))
    assert(!w.isRetracted)
    assertEquals(w.workType, Some("article"))
    assertEquals(w.referencedWorks.head, "W434762813")
    // the arXiv preprint is the OA location of this paywalled Nature paper
    assertEquals(w.arxivId, Some("1502.02783"))

  test("OpenAlex inverted-index abstracts are put back in word order"):
    val w = AcquireParse.openalexWork(fixture("openalex_work.json"))
    assert(w.abstractText.exists(_.startsWith("‘The collapse c. 1200 BC’")), w.abstractText)

  test("OpenAlex search page → several works"):
    val ws = AcquireParse.openalexWorks(fixture("openalex_search.json"))
    assertEquals(ws.size, 3)
    assert(ws.forall(_.citekey.isDefined))
    assert(ws.forall(_.title.isDefined))

  test("OpenAlex OA locations are offered best-first"):
    val urls = AcquireParse.openalexPdfUrls(fixture("openalex_work.json"))
    assertEquals(urls.head, "https://arxiv.org/pdf/1502.02783")

  test("OpenAlex citing works become follow-up rows with no intents (OpenAlex has none)"):
    val rows = AcquireParse.openalexCitations(fixture("openalex_search.json"))
    assertEquals(rows.size, 3)
    assert(rows.forall(r => r.intents.isEmpty && r.contexts.isEmpty && !r.isInfluential))

  // ── Crossref ────────────────────────────────────────────────────────────────────────────────
  test("Crossref work → WorkRef (the metadata fallback)"):
    val w = AcquireParse.crossrefWork(fixture("crossref_work.json"))
    assertEquals(w.doi, Some("10.1038/nature14317"))
    assertEquals(w.year, Some(2015))
    assertEquals(w.venue, Some("Nature"))
    assertEquals(w.authors.head, "Wolfgang Haak")
    assertEquals(w.citekey, Some("haak2015_massive"))
    assert(w.citedByCount.exists(_ > 1000))

  test("Crossref reference list keeps DOIs, years and the unstructured fallback"):
    val refs = AcquireParse.crossrefReferences(fixture("crossref_work.json"))
    assertEquals(refs.size, 6)
    assertEquals(refs.head.doi, Some("10.1038/nature13810"))
    assertEquals(refs.head.year, Some(2014))
    assert(refs.head.raw.exists(_.contains("Fu, Q.")))

  test("Crossref update-to notices are read (this DOI IS a retraction notice)"):
    val ups = AcquireParse.crossrefUpdates(fixture("crossref_retracted.json"))
    assert(ups.nonEmpty)
    assertEquals(ups.head.kind, Some("retraction"))
    assertEquals(ups.head.doi, Some("10.1177/1758835919874651"))
    // and the verdict logic reads it as a retraction
    assertEquals(
      Acquire.verdict(Nil, ups, false, Some(5), Some(2019), 50, 3, 2018),
      Acquire.Verdicts.Retracted)

  test("a work with no update-to yields no notices"):
    assertEquals(AcquireParse.crossrefUpdates(fixture("crossref_work.json")), Nil)

  // ── Unpaywall ───────────────────────────────────────────────────────────────────────────────
  test("Unpaywall → pdf urls best-first + oa_status"):
    val (urls, status) = AcquireParse.unpaywall(fixture("unpaywall.json"))
    assertEquals(status, Some("green"))
    assertEquals(urls.head, "https://arxiv.org/pdf/1502.02783")
    assertEquals(urls.distinct, urls)

  test("Unpaywall → whether a free copy exists, its status and the best location (#599)"):
    val a = AcquireParse.unpaywallAccess(fixture("unpaywall.json"))
    assertEquals(a, OaAccess(isOa = true, status = Some("green"), url = Some("https://arxiv.org/pdf/1502.02783")))
    val closed = io.circe.parser.parse("""{"is_oa": false, "oa_status": "closed", "best_oa_location": null}""")
      .toOption.get
    assertEquals(AcquireParse.unpaywallAccess(closed), OaAccess(isOa = false, status = Some("closed"), url = None))

  // ── Semantic Scholar ────────────────────────────────────────────────────────────────────────
  test("S2 citations carry intents, influence and contexts"):
    val rows = AcquireParse.s2Citations(fixture("s2_citations.json"))
    assertEquals(rows.size, 5)
    assert(rows.exists(_.isInfluential))
    assert(rows.exists(_.contexts.nonEmpty))
    assert(rows.forall(_.title.isDefined))
    assert(rows.head.doi.exists(_.startsWith("10.")))
    assertEquals(AcquireParse.s2Next(fixture("s2_citations.json")), Some(5))

  test("classification runs over the real contexts without exploding"):
    val rows = AcquireParse.s2Citations(fixture("s2_citations.json"))
    val kinds = rows.map(r => Acquire.classify(r.intents, r.contexts, r.isInfluential, r.year, Some(2015)))
    assert(kinds.forall(Acquire.EdgeKinds.all.contains), kinds)

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────
  test("a public archive.org item lists its downloadable files"):
    val i = AcquireParse.archiveItem("racesofeurope031695mbp", fixture("archive_metadata.json"))
    assertEquals(i.title, Some("The Races Of Europe"))
    assertEquals(i.year, Some(1939))
    assert(!i.lendingOnly)
    assertEquals(i.files.map(_.name).sorted,
      List("racesofeurope031695mbp.epub", "racesofeurope031695mbp.pdf"))

  test("a lending-only item is detected — it is NEVER downloaded"):
    val i = AcquireParse.archiveItem("blackathenaafroa00bern", fixture("archive_lending.json"))
    assert(i.lendingOnly, "access-restricted-item=true must set lendingOnly")

  test("archive.org search docs → items"):
    val items = AcquireParse.archiveSearch(fixture("archive_search.json"))
    assert(items.size >= 3)
    assert(items.forall(_.identifier.nonEmpty))

  test("file choice prefers clean text (epub / OCR _text.pdf) over the raw image scan"):
    val files = List(
      ArchiveFile("book.pdf", Some("Image Container PDF"), 400.0),
      ArchiveFile("book_text.pdf", Some("Text PDF"), 12.0),
      ArchiveFile("book.epub", Some("EPUB"), 3.0))
    assertEquals(AcquireParse.pickArchiveFile(files, None).map(_.name), Some("book.epub"))
    assertEquals(AcquireParse.pickArchiveFile(files, Some("pdf")).map(_.name), Some("book_text.pdf"))
    assertEquals(AcquireParse.pickArchiveFile(Nil, None), None)

  // ── Wikipedia ───────────────────────────────────────────────────────────────────────────────
  test("Wikipedia wikitext yields the DOIs it cites, cleaned of trailing markup"):
    val json = fixture("wikipedia_parse.json")
    assertEquals(AcquireParse.wikiTitle(json), Some("Late Bronze Age collapse"))
    val (dois, _) = AcquireParse.wikiRefs(AcquireParse.wikiText(json).get)
    assert(dois.contains("10.15184/aqy.2023.187"), dois)
    assert(dois.forall(d => d.startsWith("10.") && !d.endsWith(".") && !d.contains("}")), dois)
    assertEquals(dois.distinct, dois)

  test("inline markup in a title is dropped without splitting words"):
    assertEquals(AcquireParse.inlineMarkup(
      "Getting closer to the Late Bronze Age collapse in the Aegean and eastern Mediterranean, <i>c</i>. 1200 BC"),
      "Getting closer to the Late Bronze Age collapse in the Aegean and eastern Mediterranean, c. 1200 BC")
    assertEquals(AcquireParse.inlineMarkup("H<sub>2</sub>O  and <b>more</b>"), "H2O and more")
    val w = AcquireParse.crossrefWork(io.circe.parser.parse(
      """{"DOI":"10.1/x","title":["The <i>Iliad</i> and <scp>Troy</scp>"],"author":[{"given":"A","family":"B"}]}""").toOption.get)
    assertEquals(w.title, Some("The Iliad and Troy"))

  test("a Crossref search page parses to works, without the page number some publishers glue on"):
    val ws = AcquireParse.crossrefWorks(fixture("crossref_search.json"))
    assertEquals(ws.size, 3)
    assert(ws.forall(_.doi.exists(_.startsWith("10.1515/9783112213063"))), ws.map(_.doi))
    assert(ws.flatMap(_.title).exists(_.startsWith("Writing and Religion in Minoan and Mycenaean Society")), ws.map(_.title))
    assert(ws.flatMap(_.title).forall(t => !t.head.isDigit), ws.map(_.title))
    assertEquals(AcquireParse.gluedPageNumber("1177 B.C.: The Year Civilization Collapsed"), "1177 B.C.: The Year Civilization Collapsed")
    assertEquals(AcquireParse.gluedPageNumber("153From Bronze to Iron"), "From Bronze to Iron")

  test("the page title is taken from an article URL"):
    assertEquals(AcquireParse.wikiPageTitle("https://en.wikipedia.org/wiki/Late_Bronze_Age_collapse"),
      Some("Late_Bronze_Age_collapse"))
    assertEquals(AcquireParse.wikiPageTitle("https://en.wikipedia.org/wiki/Hittites#History"), Some("Hittites"))
    assertEquals(AcquireParse.wikiPageTitle("Hittites"), Some("Hittites"))
