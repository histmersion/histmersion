package com.histmersion.domain.library.acquisition

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.{AuthorName, CandidateCount, CandidateWatch, Doi, Title}
import com.histmersion.api.library.Source

/** What the new-source search's store answers, the same on the database and in memory: a watch read keeps its watermark
  * when a read failed, a candidate found again keeps its state and gathers every watch that found it, a page in id
  * order, the counts in the states' order, a mark by id and by DOI.
  */
abstract class CandidateServiceContract extends AcquisitionStoreSuite:

  private def candidate(goal: Source.Candidate.Goal, doi: Doi, by: Source.Candidate.FoundBy) =
    Source.Candidate(
      Source.Candidate.Id(0),
      goal,
      doi,
      Title(s"Work ${doi.value}"),
      Some(Year(2025)),
      List(AuthorName("Zq Author")),
      None,
      None,
      Source.Candidate.IsOa(false),
      None,
      List(by)
    )

  stest("a watch keeps its watermark when a read fails, and is listed by kind, subject and index") { s =>
    s.goal("zq-goal")
    val w = CandidateWatch(com.histmersion.api.library.CandidateWatch.Goal("zq-goal"), com.histmersion.api.library.CandidateWatch.Kind("query"), com.histmersion.api.library.CandidateWatch.Subject("zq tin"), com.histmersion.api.library.CandidateWatch.Index("openalex"))
    s.candidates.recordWatch(w, Some(com.histmersion.api.library.CandidateWatch.Watermark("2026-10-01")), None, com.histmersion.api.library.CandidateWatch.LastFound(3))
    s.candidates.recordWatch(w, None, Some(com.histmersion.api.library.CandidateWatch.LastError("HTTP 503")), com.histmersion.api.library.CandidateWatch.LastFound(0))
    s.candidates.recordWatch(
      CandidateWatch(com.histmersion.api.library.CandidateWatch.Goal("zq-goal"), com.histmersion.api.library.CandidateWatch.Kind("citing"), com.histmersion.api.library.CandidateWatch.Subject("10.5555/zq.x"), com.histmersion.api.library.CandidateWatch.Index("openalex")),
      Some(com.histmersion.api.library.CandidateWatch.Watermark("2026-10-02")),
      None,
      com.histmersion.api.library.CandidateWatch.LastFound(1)
    )
    val got = s.candidates.watches(com.histmersion.domain.automation.Goal.Slug("zq-goal"))
    assertEquals(
      got.map(w => (w.kind, w.subject, w.watermark, w.lastError, w.lastFound)),
      List(
        ("citing", "10.5555/zq.x", Some("2026-10-02"), None, 1),
        ("query", "zq tin", Some("2026-10-01"), Some("HTTP 503"), 0)
      )
    )
    assert(got.forall(_.lastRun.isDefined))
    assertEquals(s.candidates.watches(com.histmersion.domain.automation.Goal.Slug("zq-other")), Nil)
  }

  stest("a candidate found again keeps its state, gathers its watches; pages, counts and marks") { s =>
    s.goal("zq-goal")
    s.goal("zq-two")
    assert(s.candidates.upsert(candidate(Source.Candidate.Goal("zq-goal"), Doi("10.5555/zq.a"), Source.Candidate.FoundBy("openalex query"))))
    assert(s.candidates.upsert(candidate(Source.Candidate.Goal("zq-goal"), Doi("10.5555/zq.b"), Source.Candidate.FoundBy("crossref query"))))
    assert(s.candidates.upsert(candidate(Source.Candidate.Goal("zq-two"), Doi("10.5555/zq.a"), Source.Candidate.FoundBy("openalex query"))))
    assert(!s.candidates.upsert(candidate(Source.Candidate.Goal("zq-goal"), Doi("10.5555/zq.a"), Source.Candidate.FoundBy("crossref query"))))
    assertEquals(s.candidates.dois(com.histmersion.domain.automation.Goal.Slug("zq-goal")), Set("10.5555/zq.a", "10.5555/zq.b"))
    val page = s.candidates.list(Some(com.histmersion.domain.automation.Goal.Slug("zq-goal")), com.histmersion.api.library.Source.Candidate.State("new"), com.histmersion.domain.library.acquisition.CandidateService.ListLimit(10), com.histmersion.Offset(0))
    assertEquals(page.map(_.doi), List("10.5555/zq.a", "10.5555/zq.b"))
    assertEquals(page.head.foundBy.sortBy(_.value), List("crossref query", "openalex query"))
    assertEquals(s.candidates.list(Some(com.histmersion.domain.automation.Goal.Slug("zq-goal")), com.histmersion.api.library.Source.Candidate.State("new"), com.histmersion.domain.library.acquisition.CandidateService.ListLimit(1), com.histmersion.Offset(1)).map(_.doi), List("10.5555/zq.b"))
    assertEquals(s.candidates.list(None, com.histmersion.api.library.Source.Candidate.State("new"), com.histmersion.domain.library.acquisition.CandidateService.ListLimit(10), com.histmersion.Offset(0)).size, 3)
    assertEquals(s.candidates.mark(List(page.head.id), com.histmersion.api.library.Source.Candidate.State("seen")), 1)
    assertEquals(s.candidates.mark(Nil, com.histmersion.api.library.Source.Candidate.State("seen")), 0)
    assertEquals(s.candidates.markDois(com.histmersion.domain.automation.Goal.Slug("zq-goal"), List(com.histmersion.domain.library.Doi("10.5555/zq.b")), com.histmersion.api.library.Source.Candidate.State("dismissed")), 1)
    assertEquals(
      s.candidates.counts(Some(com.histmersion.domain.automation.Goal.Slug("zq-goal"))),
      List(CandidateCount(com.histmersion.api.library.Source.Candidate.State("seen"), com.histmersion.api.library.CandidateCount.Count(1)), CandidateCount(com.histmersion.api.library.Source.Candidate.State("dismissed"), com.histmersion.api.library.CandidateCount.Count(1)))
    )
    assertEquals(
      s.candidates.counts(None),
      List(CandidateCount(com.histmersion.api.library.Source.Candidate.State("new"), com.histmersion.api.library.CandidateCount.Count(1)), CandidateCount(com.histmersion.api.library.Source.Candidate.State("seen"), com.histmersion.api.library.CandidateCount.Count(1)), CandidateCount(com.histmersion.api.library.Source.Candidate.State("dismissed"), com.histmersion.api.library.CandidateCount.Count(1)))
    )
    assert(!s.candidates.upsert(candidate(Source.Candidate.Goal("zq-goal"), Doi("10.5555/zq.a"), Source.Candidate.FoundBy("europepmc query"))))
    assertEquals(s.candidates.list(Some(com.histmersion.domain.automation.Goal.Slug("zq-goal")), com.histmersion.api.library.Source.Candidate.State("seen"), com.histmersion.domain.library.acquisition.CandidateService.ListLimit(10), com.histmersion.Offset(0)).map(_.doi), List("10.5555/zq.a"))
  }

class CandidateServiceInMemorySuite extends CandidateServiceContract with AcquisitionStoreSuite.InMemory
class CandidateServiceDbSuite extends CandidateServiceContract with AcquisitionStoreSuite.OnDatabase
