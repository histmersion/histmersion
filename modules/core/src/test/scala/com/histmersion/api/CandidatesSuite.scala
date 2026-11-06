package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

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
    assertEquals(com.histmersion.domain.library.acquisition.CandidateDedupe.keyOf(WorkRef(arxivId = Some(com.histmersion.api.library.Source.Candidate.ArxivId("2401.01234v3")))), Some("10.48550/arxiv.2401.01234"))
    assertEquals(com.histmersion.domain.library.acquisition.CandidateDedupe.keyOf(WorkRef(title = (Some("no id")).map(com.histmersion.api.library.Title.apply))), None)

  test("a watch reads from its watermark, or (never read) the look-back before today"):
    val today = LocalDate.of(2026, 10, 24)
    assertEquals(Candidates.since(Some(com.histmersion.api.library.Candidates.Watermark("2026-10-01")), today, com.histmersion.api.library.Candidates.Lookback(365)), LocalDate.of(2026, 10, 1))
    assertEquals(Candidates.since(None, today, com.histmersion.api.library.Candidates.Lookback(365)), LocalDate.of(2025, 10, 24))
    assertEquals(Candidates.since(Some(com.histmersion.api.library.Candidates.Watermark("not a date")), today, com.histmersion.api.library.Candidates.Lookback(30)), LocalDate.of(2026, 9, 24))

  test("one sweep's works: once each by key with every watch that found it; no id, retracted and known left out"):
    val found = List(
      com.histmersion.domain.library.acquisition.CandidateDedupe.FoundBy("openalex query \"x\"") -> w("10.1234/a", by = List("Ann")),
      com.histmersion.domain.library.acquisition.CandidateDedupe.FoundBy("crossref query \"x\"") -> w("10.1234/A", title = "Other title"),
      com.histmersion.domain.library.acquisition.CandidateDedupe.FoundBy("openalex citing 10.9999/z") -> w("10.1234/a"),
      com.histmersion.domain.library.acquisition.CandidateDedupe.FoundBy("openalex query \"x\"") -> WorkRef(title = (Some("no doi")).map(com.histmersion.api.library.Title.apply)),
      com.histmersion.domain.library.acquisition.CandidateDedupe.FoundBy("openalex query \"x\"") -> w("10.1234/r").modify(_.isRetracted).setTo(true),
      com.histmersion.domain.library.acquisition.CandidateDedupe.FoundBy("europepmc query \"x\"") -> w("10.1234/known"),
      com.histmersion.domain.library.acquisition.CandidateDedupe.FoundBy("arxiv query \"x\"") -> WorkRef(arxivId = Some(com.histmersion.api.library.Source.Candidate.ArxivId("2401.00001")), title = (Some("P")).map(com.histmersion.api.library.Title.apply))
    )
    val (kept, skipped) = com.histmersion.domain.library.acquisition.CandidateDedupe.dedupe(found, Set(com.histmersion.domain.library.Doi("10.1234/known")))
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
        isOa = com.histmersion.api.library.WorkRef.IsOa(true),
        abstractText = Some(com.histmersion.api.library.Source.Candidate.AbstractText("Text"))
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
          isOa = com.histmersion.api.library.WorkRef.IsOa(true)
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
          arxivId = Some(com.histmersion.api.library.Source.Candidate.ArxivId("2410.01234")),
          title = (Some("Late Bronze Age & climate")).map(com.histmersion.api.library.Title.apply),
          year = (Some(2026)).map(com.histmersion.api.catalogue.Year.apply),
          authors = (List("Ann Lee", "Bo Ng")).map(com.histmersion.api.library.AuthorName.apply),
          venue = (Some("arXiv")).map(com.histmersion.api.library.Source.Venue.apply),
          isOa = com.histmersion.api.library.WorkRef.IsOa(true),
          abstractText = Some(com.histmersion.api.library.Source.Candidate.AbstractText("About it."))
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
      com.histmersion.api.library.Source.Candidate.Id(7),
      com.histmersion.api.library.Source.Candidate.Goal("g"),
      com.histmersion.api.library.Doi("10.1234/a"),
      com.histmersion.api.library.Title("A title"),
      (Some(2026)).map(com.histmersion.api.catalogue.Year.apply),
      (List("Ann", "Bo")).map(com.histmersion.api.library.AuthorName.apply),
      (Some("J")).map(com.histmersion.api.library.Source.Venue.apply),
      Some(com.histmersion.api.library.Source.Candidate.AbstractText("abs")),
      isOa = com.histmersion.api.library.Source.Candidate.IsOa(true),
      foundBy = List(com.histmersion.api.library.Source.Candidate.FoundBy("openalex query \"x\"")),
      firstSeen = com.histmersion.api.library.Source.Candidate.FirstSeen("2026-10-24T10:00:00Z")
    )
    val md = CandidatesMarkdown.list(
      CandidateList(
        Some(com.histmersion.api.research.ReviewGoal.Slug("g")),
        com.histmersion.api.library.CandidateList.State("new"),
        List(c),
        List(CandidateCount(com.histmersion.api.library.Source.Candidate.State("new"), com.histmersion.api.library.CandidateCount.Count(1)), CandidateCount(com.histmersion.api.library.Source.Candidate.State("seen"), com.histmersion.api.library.CandidateCount.Count(4))),
        next = Some(com.histmersion.api.library.CandidateList.Next(25))
      )
    )
    assert(md.contains("**#7** `10.1234/a` — A title (Ann et al., 2026, J) · open access"), md)
    assert(md.contains("Counts: 1 new, 4 seen.") && md.contains("`--offset 25`"), md)
    val sw = CandidatesMarkdown.sweep(
      CandidateSweep(
        com.histmersion.api.library.CandidateSweep.Goal("g"),
        List(
          CandidateWatch(com.histmersion.api.library.CandidateWatch.Goal("g"), com.histmersion.api.library.CandidateWatch.Kind("query"), com.histmersion.api.library.CandidateWatch.Subject("x"), com.histmersion.api.library.CandidateWatch.Index("crossref"), Some(com.histmersion.api.library.CandidateWatch.Watermark("2026-10-24")), lastFound = com.histmersion.api.library.CandidateWatch.LastFound(3)),
          CandidateWatch(com.histmersion.api.library.CandidateWatch.Goal("g"), com.histmersion.api.library.CandidateWatch.Kind("query"), com.histmersion.api.library.CandidateWatch.Subject("x"), com.histmersion.api.library.CandidateWatch.Index("arxiv"), lastError = Some(com.histmersion.api.library.CandidateWatch.LastError("timeout")))
        ),
        com.histmersion.api.library.CandidateSweep.Found(3),
        com.histmersion.api.library.CandidateSweep.Added(2),
        com.histmersion.api.library.CandidateSweep.Again(1),
        Map(com.histmersion.api.library.CandidateSweep.SkippedKey("retracted") -> com.histmersion.api.library.CandidateSweep.SkippedValue(1)),
        com.histmersion.api.library.CandidateSweep.Unseen(5)
      )
    )
    assert(sw.contains("3 work(s) found by 2 watch(es): 2 new candidate(s), 1 found again. Left out: 1 retracted."), sw)
    assert(
      sw.contains("- crossref query `x`: 3 found (reads from 2026-10-24 next)") && sw.contains(
        "- arxiv query `x`: not read — timeout"
      ),
      sw
    )
