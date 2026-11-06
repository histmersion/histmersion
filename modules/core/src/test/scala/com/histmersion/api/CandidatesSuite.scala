package com.histmersion.api

import hearth.kindlings.optics.syntax.*

import io.circe.parser.parse

import java.time.LocalDate
import com.histmersion.api.library.{
  CandidateCount,
  CandidateList,
  Candidates,
  CandidatesMarkdown,
  CandidateSweep,
  CandidateWatch,
  WorkRef
}
import com.histmersion.api.library.Source

/** The deterministic new-source search's pure half (#3761): keys, watermarks, deduplication, the indexes' answers, the
  * Markdown.
  */
class CandidatesSuite extends munit.FunSuite:

  private def w(doi: String, title: String = "T", by: List[String] = Nil) =
    WorkRef(doi = (Some(doi)).map(com.histmersion.api.library.Doi.apply), title = (Some(title)).map(com.histmersion.api.library.Title.apply), authors = (by).map(com.histmersion.api.library.AuthorName.apply))

  test(
    "a work's key is its DOI, lower case without a resolver; an arXiv preprint without one gets arXiv's DataCite DOI"
  ):
    assertEquals(com.histmersion.domain.library.acquisition.CandidateDedupe.keyOf(w("https://doi.org/10.1234/ABC")), Some("10.1234/abc"))
    assertEquals(com.histmersion.domain.library.acquisition.CandidateDedupe.keyOf(WorkRef(arxivId = Some("2401.01234v3"))), Some("10.48550/arxiv.2401.01234"))
    assertEquals(com.histmersion.domain.library.acquisition.CandidateDedupe.keyOf(WorkRef(title = (Some("no id")).map(com.histmersion.api.library.Title.apply))), None)

  test("a watch reads from its watermark, or (never read) the look-back before today"):
    val today = LocalDate.of(2026, 10, 24)
    assertEquals(Candidates.since(Some("2026-10-01"), today, 365), LocalDate.of(2026, 10, 1))
    assertEquals(Candidates.since(None, today, 365), LocalDate.of(2025, 10, 24))
    assertEquals(Candidates.since(Some("not a date"), today, 30), LocalDate.of(2026, 9, 24))

  test("one sweep's works: once each by key with every watch that found it; no id, retracted and known left out"):
    val found = List(
      "openalex query \"x\"" -> w("10.1234/a", by = List("Ann")),
      "crossref query \"x\"" -> w("10.1234/A", title = "Other title"),
      "openalex citing 10.9999/z" -> w("10.1234/a"),
      "openalex query \"x\"" -> WorkRef(title = (Some("no doi")).map(com.histmersion.api.library.Title.apply)),
      "openalex query \"x\"" -> w("10.1234/r").modify(_.isRetracted).setTo(true),
      "europepmc query \"x\"" -> w("10.1234/known"),
      "arxiv query \"x\"" -> WorkRef(arxivId = Some("2401.00001"), title = (Some("P")).map(com.histmersion.api.library.Title.apply))
    )
    val (kept, skipped) = com.histmersion.domain.library.acquisition.CandidateDedupe.dedupe(found, Set("10.1234/known"))
    assertEquals(
      kept.map(k => (k._1, k._3)),
      List(
        "10.1234/a" -> List("openalex query \"x\"", "crossref query \"x\"", "openalex citing 10.9999/z"),
        "10.48550/arxiv.2401.00001" -> List("arxiv query \"x\"")
      )
    )
    assertEquals(kept.head._2.authors, List(com.histmersion.api.library.AuthorName("Ann")), "the first record, filled from the others")
    assertEquals(skipped, Map("no DOI" -> 1, "retracted" -> 1, "in the corpus or queued" -> 1))

  test("Europe PMC's answer: DOI, title, authors, year, journal, open access, abstract"):
    val j = parse(
      """{"resultList":{"result":[{"doi":"10.1234/e","title":"Bronze.","authorString":"Doe J, Roe K.","pubYear":"2026",
      "journalTitle":"J Arch","isOpenAccess":"Y","abstractText":"<p>Text</p>"},{"title":"No DOI","pubYear":"2025"}]}}"""
    ).toOption.get
    val ws = Candidates.europepmcWorks(j)
    assertEquals(
      ws.head,
      WorkRef(
        doi = (Some("10.1234/e")).map(com.histmersion.api.library.Doi.apply),
        title = (Some("Bronze")).map(com.histmersion.api.library.Title.apply),
        year = (Some(2026)).map(com.histmersion.api.catalogue.Year.apply),
        authors = (List("Doe J", "Roe K")).map(com.histmersion.api.library.AuthorName.apply),
        venue = (Some("J Arch")).map(com.histmersion.api.library.Source.Venue.apply),
        isOa = true,
        abstractText = Some("Text")
      )
    )
    assertEquals(ws(1).doi, None)

  test("CORE's answer: DOI, title, authors, year, publisher, a full text is open access"):
    val j = parse("""{"results":[{"doi":"10.1234/c","title":"T","yearPublished":2026,"authors":[{"name":"A B"}],
      "publisher":"P","downloadUrl":"https://core.ac.uk/x.pdf","arxivId":null}]}""").toOption.get
    assertEquals(
      Candidates.coreWorks(j),
      List(
        WorkRef(
          doi = (Some("10.1234/c")).map(com.histmersion.api.library.Doi.apply),
          title = (Some("T")).map(com.histmersion.api.library.Title.apply),
          year = (Some(2026)).map(com.histmersion.api.catalogue.Year.apply),
          authors = (List("A B")).map(com.histmersion.api.library.AuthorName.apply),
          venue = (Some("P")).map(com.histmersion.api.library.Source.Venue.apply),
          isOa = true
        )
      )
    )

  test("arXiv's Atom feed: id without its version, title, authors, year, DOI, abstract"):
    val atom =
      """<feed><entry><id>http://arxiv.org/abs/2410.01234v2</id><published>2026-10-02T00:00:00Z</published>
        |<title>Late Bronze
        |  Age &amp; climate</title><summary>About it.</summary>
        |<author><name>Ann Lee</name></author><author><name>Bo Ng</name></author>
        |<arxiv:doi xmlns:arxiv="http://arxiv.org/schemas/atom">10.1234/ax</arxiv:doi></entry></feed>""".stripMargin
    assertEquals(
      Candidates.arxivWorks(atom),
      List(
        WorkRef(
          doi = (Some("10.1234/ax")).map(com.histmersion.api.library.Doi.apply),
          arxivId = Some("2410.01234"),
          title = (Some("Late Bronze Age & climate")).map(com.histmersion.api.library.Title.apply),
          year = (Some(2026)).map(com.histmersion.api.catalogue.Year.apply),
          authors = (List("Ann Lee", "Bo Ng")).map(com.histmersion.api.library.AuthorName.apply),
          venue = (Some("arXiv")).map(com.histmersion.api.library.Source.Venue.apply),
          isOa = true,
          abstractText = Some("About it.")
        )
      )
    )

  test("OpenAlex's work: its authors' ids in byline order"):
    val j = parse(
      """{"authorships":[{"author":{"id":"https://openalex.org/A1"}},{"author":{"id":"https://openalex.org/A22"}}]}"""
    ).toOption.get
    assertEquals(Candidates.openalexAuthorIds(j), List("A1", "A22"))

  test("the list and the sweep as Markdown"):
    val c = Source.Candidate(
      7,
      "g",
      com.histmersion.api.library.Doi("10.1234/a"),
      com.histmersion.api.library.Title("A title"),
      (Some(2026)).map(com.histmersion.api.catalogue.Year.apply),
      (List("Ann", "Bo")).map(com.histmersion.api.library.AuthorName.apply),
      (Some("J")).map(com.histmersion.api.library.Source.Venue.apply),
      Some("abs"),
      isOa = true,
      foundBy = List("openalex query \"x\""),
      firstSeen = "2026-10-24T10:00:00Z"
    )
    val md = CandidatesMarkdown.list(
      CandidateList(
        Some("g"),
        "new",
        List(c),
        List(CandidateCount("new", 1), CandidateCount("seen", 4)),
        next = Some(25)
      )
    )
    assert(md.contains("**#7** `10.1234/a` — A title (Ann et al., 2026, J) · open access"), md)
    assert(md.contains("Counts: 1 new, 4 seen.") && md.contains("`--offset 25`"), md)
    val sw = CandidatesMarkdown.sweep(
      CandidateSweep(
        "g",
        List(
          CandidateWatch("g", "query", "x", "crossref", Some("2026-10-24"), lastFound = 3),
          CandidateWatch("g", "query", "x", "arxiv", lastError = Some("timeout"))
        ),
        3,
        2,
        1,
        Map("retracted" -> 1),
        5
      )
    )
    assert(sw.contains("3 work(s) found by 2 watch(es): 2 new candidate(s), 1 found again. Left out: 1 retracted."), sw)
    assert(
      sw.contains("- crossref query `x`: 3 found (reads from 2026-10-24 next)") && sw.contains(
        "- arxiv query `x`: not read — timeout"
      ),
      sw
    )
