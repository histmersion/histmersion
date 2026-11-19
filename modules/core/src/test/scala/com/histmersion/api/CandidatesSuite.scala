package com.histmersion
package api
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

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
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.domain.library.acquisition.CandidateDedupe
import com.histmersion.api.catalogue.Year
import com.histmersion.api.research.ReviewGoal

/** The deterministic new-source search's pure half (#3761): keys, watermarks, deduplication, the indexes' answers, the
  * Markdown.
  */
class CandidatesSuite extends munit.FunSuite:

  private def w(doi: String, title: String = "T", by: List[String] = Nil) =
    WorkRef(
      doi = (Some(doi)).map(Doi.apply),
      title = (Some(title)).map(Title.apply),
      authors = (by).map(AuthorName.apply)
    )

  test(
    "a work's key is its DOI, lower case without a resolver; an arXiv preprint without one gets arXiv's DataCite DOI"
  ):
    assertEquals(CandidateDedupe.keyOf(w("https://doi.org/10.1234/ABC")), Some("10.1234/abc"))
    assertEquals(
      CandidateDedupe.keyOf(WorkRef(arxivId = Some(Source.Candidate.ArxivId("2401.01234v3")))),
      Some("10.48550/arxiv.2401.01234")
    )
    assertEquals(CandidateDedupe.keyOf(WorkRef(title = (Some("no id")).map(Title.apply))), None)

  test("a watch reads from its watermark, or (never read) the look-back before today"):
    val today = LocalDate.of(2026, 10, 24)
    assertEquals(
      Candidates.since(Some(Candidates.Watermark("2026-10-01")), today, Candidates.Lookback(365)),
      LocalDate.of(2026, 10, 1)
    )
    assertEquals(Candidates.since(None, today, Candidates.Lookback(365)), LocalDate.of(2025, 10, 24))
    assertEquals(
      Candidates.since(Some(Candidates.Watermark("not a date")), today, Candidates.Lookback(30)),
      LocalDate.of(2026, 9, 24)
    )

  test("one sweep's works: once each by key with every watch that found it; no id, retracted and known left out"):
    val found = List(
      CandidateDedupe.FoundBy("openalex query \"x\"") -> w("10.1234/a", by = List("Ann")),
      CandidateDedupe.FoundBy("crossref query \"x\"") -> w("10.1234/A", title = "Other title"),
      CandidateDedupe.FoundBy("openalex citing 10.9999/z") -> w("10.1234/a"),
      CandidateDedupe.FoundBy("openalex query \"x\"") -> WorkRef(title = (Some("no doi")).map(Title.apply)),
      CandidateDedupe
        .FoundBy("openalex query \"x\"") -> w("10.1234/r").modify(_.isRetracted).setTo(WorkRef.IsRetracted(true)),
      CandidateDedupe.FoundBy("europepmc query \"x\"") -> w("10.1234/known"),
      CandidateDedupe.FoundBy("arxiv query \"x\"") -> WorkRef(
        arxivId = Some(Source.Candidate.ArxivId("2401.00001")),
        title = (Some("P")).map(Title.apply)
      )
    )
    val (kept, skipped) = CandidateDedupe.dedupe(found, Set(domain.library.Doi("10.1234/known")))
    assertEquals(
      kept.map(k => (k._1, k._3)),
      List(
        "10.1234/a" -> List("openalex query \"x\"", "crossref query \"x\"", "openalex citing 10.9999/z"),
        "10.48550/arxiv.2401.00001" -> List("arxiv query \"x\"")
      )
    )
    assertEquals(kept.head._2.authors, List(AuthorName("Ann")), "the first record, filled from the others")
    assertEquals(
      skipped.map((k, v) => k.said -> v),
      Map("no DOI" -> 1, "retracted" -> 1, "in the corpus or queued" -> 1)
    )

  test("Europe PMC's answer: DOI, title, authors, year, journal, open access, abstract"):
    val j = RawJson
      .parse(
        """{"resultList":{"result":[{"doi":"10.1234/e","title":"Bronze.","authorString":"Doe J, Roe K.","pubYear":"2026",
      "journalTitle":"J Arch","isOpenAccess":"Y","abstractText":"<p>Text</p>"},{"title":"No DOI","pubYear":"2025"}]}}"""
      )
      .toOption
      .get
    val ws = Candidates.europepmcWorks(j)
    assertEquals(
      ws.head,
      WorkRef(
        doi = (Some("10.1234/e")).map(Doi.apply),
        title = (Some("Bronze")).map(Title.apply),
        year = (Some(2026)).map(Year.apply),
        authors = (List("Doe J", "Roe K")).map(AuthorName.apply),
        venue = (Some("J Arch")).map(Source.Venue.apply),
        isOa = WorkRef.IsOa(true),
        abstractText = Some(Source.Candidate.AbstractText("Text"))
      )
    )
    assertEquals(ws(1).doi, None)

  test("CORE's answer: DOI, title, authors, year, publisher, a full text is open access"):
    val j = RawJson
      .parse("""{"results":[{"doi":"10.1234/c","title":"T","yearPublished":2026,"authors":[{"name":"A B"}],
      "publisher":"P","downloadUrl":"https://core.ac.uk/x.pdf","arxivId":null}]}""")
      .toOption
      .get
    assertEquals(
      Candidates.coreWorks(j),
      List(
        WorkRef(
          doi = (Some("10.1234/c")).map(Doi.apply),
          title = (Some("T")).map(Title.apply),
          year = (Some(2026)).map(Year.apply),
          authors = (List("A B")).map(AuthorName.apply),
          venue = (Some("P")).map(Source.Venue.apply),
          isOa = WorkRef.IsOa(true)
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
          doi = (Some("10.1234/ax")).map(Doi.apply),
          arxivId = Some(Source.Candidate.ArxivId("2410.01234")),
          title = (Some("Late Bronze Age & climate")).map(Title.apply),
          year = (Some(2026)).map(Year.apply),
          authors = (List("Ann Lee", "Bo Ng")).map(AuthorName.apply),
          venue = (Some("arXiv")).map(Source.Venue.apply),
          isOa = WorkRef.IsOa(true),
          abstractText = Some(Source.Candidate.AbstractText("About it."))
        )
      )
    )

  test("OpenAlex's work: its authors' ids in byline order"):
    val j = RawJson
      .parse(
        """{"authorships":[{"author":{"id":"https://openalex.org/A1"}},{"author":{"id":"https://openalex.org/A22"}}]}"""
      )
      .toOption
      .get
    assertEquals(Candidates.openalexAuthorIds(j), List("A1", "A22"))

  test("the list and the sweep as Markdown"):
    val c = Source.Candidate(
      Source.Candidate.Id(7),
      Source.Candidate.Goal("g"),
      Doi("10.1234/a"),
      Title("A title"),
      (Some(2026)).map(Year.apply),
      (List("Ann", "Bo")).map(AuthorName.apply),
      (Some("J")).map(Source.Venue.apply),
      Some(Source.Candidate.AbstractText("abs")),
      isOa = Source.Candidate.IsOa(true),
      foundBy = List(Source.Candidate.FoundBy("openalex query \"x\"")),
      firstSeen = Source.Candidate.FirstSeen("2026-10-24T10:00:00Z")
    )
    val md = CandidatesMarkdown.list(
      CandidateList(
        Some(ReviewGoal.Slug("g")),
        CandidateList.State("new"),
        List(c),
        List(
          CandidateCount(Source.Candidate.State("new"), CandidateCount.Count(1)),
          CandidateCount(Source.Candidate.State("seen"), CandidateCount.Count(4))
        ),
        next = Some(CandidateList.Next(25))
      )
    )
    assert(md.contains("**#7** `10.1234/a` — A title (Ann et al., 2026, J) · open access"), md)
    assert(md.contains("Counts: 1 new, 4 seen.") && md.contains("`--offset 25`"), md)
    val sw = CandidatesMarkdown.sweep(
      CandidateSweep(
        CandidateSweep.Goal("g"),
        List(
          CandidateWatch(
            CandidateWatch.Goal("g"),
            CandidateWatch.Kind("query"),
            CandidateWatch.Subject("x"),
            CandidateWatch.Index("crossref"),
            Some(CandidateWatch.Watermark("2026-10-24")),
            lastFound = CandidateWatch.LastFound(3)
          ),
          CandidateWatch(
            CandidateWatch.Goal("g"),
            CandidateWatch.Kind("query"),
            CandidateWatch.Subject("x"),
            CandidateWatch.Index("arxiv"),
            lastError = Some(Problem.Unclassified(Legacy.Text("timeout")))
          )
        ),
        CandidateSweep.Found(3),
        CandidateSweep.Added(2),
        CandidateSweep.Again(1),
        Map(CandidateSweep.SkippedKey("retracted") -> CandidateSweep.SkippedValue(1)),
        CandidateSweep.Unseen(5)
      )
    )
    assert(sw.contains("3 work(s) found by 2 watch(es): 2 new candidate(s), 1 found again. Left out: 1 retracted."), sw)
    assert(
      sw.contains("- crossref query `x`: 3 found (reads from 2026-10-24 next)") && sw.contains(
        "- arxiv query `x`: not read — timeout"
      ),
      sw
    )
