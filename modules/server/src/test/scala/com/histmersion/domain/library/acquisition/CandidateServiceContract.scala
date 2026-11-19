package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.{AuthorName, CandidateCount, CandidateWatch, Doi, Title}
import com.histmersion.api.library.Source
import com.histmersion.domain.automation.Goal

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
    val w = CandidateWatch(
      CandidateWatch.Goal("zq-goal"),
      CandidateWatch.Kind("query"),
      CandidateWatch.Subject("zq tin"),
      CandidateWatch.Index("openalex")
    )
    s.candidates.recordWatch(w, Some(CandidateWatch.Watermark("2026-10-01")), None, CandidateWatch.LastFound(3))
    s.candidates.recordWatch(w, None, Some(Problem.Unclassified(Legacy.Text("HTTP 503"))), CandidateWatch.LastFound(0))
    s.candidates.recordWatch(
      CandidateWatch(
        CandidateWatch.Goal("zq-goal"),
        CandidateWatch.Kind("citing"),
        CandidateWatch.Subject("10.5555/zq.x"),
        CandidateWatch.Index("openalex")
      ),
      Some(CandidateWatch.Watermark("2026-10-02")),
      None,
      CandidateWatch.LastFound(1)
    )
    val got = s.candidates.watches(Goal.Slug("zq-goal"))
    assertEquals(
      got.map(w => (w.kind, w.subject, w.watermark, w.lastError.map(_.sentence), w.lastFound)),
      List(
        ("citing", "10.5555/zq.x", Some("2026-10-02"), None, 1),
        ("query", "zq tin", Some("2026-10-01"), Some("HTTP 503"), 0)
      )
    )
    assert(got.forall(_.lastRun.isDefined))
    assertEquals(s.candidates.watches(Goal.Slug("zq-other")), Nil)
  }

  stest("a candidate found again keeps its state, gathers its watches; pages, counts and marks") { s =>
    s.goal("zq-goal")
    s.goal("zq-two")
    assert(
      s.candidates.upsert(
        candidate(Source.Candidate.Goal("zq-goal"), Doi("10.5555/zq.a"), Source.Candidate.FoundBy("openalex query"))
      )
    )
    assert(
      s.candidates.upsert(
        candidate(Source.Candidate.Goal("zq-goal"), Doi("10.5555/zq.b"), Source.Candidate.FoundBy("crossref query"))
      )
    )
    assert(
      s.candidates.upsert(
        candidate(Source.Candidate.Goal("zq-two"), Doi("10.5555/zq.a"), Source.Candidate.FoundBy("openalex query"))
      )
    )
    assert(
      !s.candidates.upsert(
        candidate(Source.Candidate.Goal("zq-goal"), Doi("10.5555/zq.a"), Source.Candidate.FoundBy("crossref query"))
      )
    )
    assertEquals(s.candidates.dois(Goal.Slug("zq-goal")), Set("10.5555/zq.a", "10.5555/zq.b"))
    val page = s.candidates.list(
      Some(Goal.Slug("zq-goal")),
      Source.Candidate.State("new"),
      CandidateService.ListLimit(10),
      Offset(0)
    )
    assertEquals(page.map(_.doi), List("10.5555/zq.a", "10.5555/zq.b"))
    assertEquals(page.head.foundBy.sorted, List("crossref query", "openalex query"))
    assertEquals(
      s.candidates
        .list(Some(Goal.Slug("zq-goal")), Source.Candidate.State("new"), CandidateService.ListLimit(1), Offset(1))
        .map(_.doi),
      List("10.5555/zq.b")
    )
    assertEquals(
      s.candidates.list(None, Source.Candidate.State("new"), CandidateService.ListLimit(10), Offset(0)).size,
      3
    )
    assertEquals(s.candidates.mark(List(page.head.id), Source.Candidate.State("seen")), 1)
    assertEquals(s.candidates.mark(Nil, Source.Candidate.State("seen")), 0)
    assertEquals(
      s.candidates.markDois(
        Goal.Slug("zq-goal"),
        List(domain.library.Doi("10.5555/zq.b")),
        Source.Candidate.State("dismissed")
      ),
      1
    )
    assertEquals(
      s.candidates.counts(Some(Goal.Slug("zq-goal"))),
      List(
        CandidateCount(Source.Candidate.State("seen"), CandidateCount.Count(1)),
        CandidateCount(Source.Candidate.State("dismissed"), CandidateCount.Count(1))
      )
    )
    assertEquals(
      s.candidates.counts(None),
      List(
        CandidateCount(Source.Candidate.State("new"), CandidateCount.Count(1)),
        CandidateCount(Source.Candidate.State("seen"), CandidateCount.Count(1)),
        CandidateCount(Source.Candidate.State("dismissed"), CandidateCount.Count(1))
      )
    )
    assert(
      !s.candidates.upsert(
        candidate(Source.Candidate.Goal("zq-goal"), Doi("10.5555/zq.a"), Source.Candidate.FoundBy("europepmc query"))
      )
    )
    assertEquals(
      s.candidates
        .list(Some(Goal.Slug("zq-goal")), Source.Candidate.State("seen"), CandidateService.ListLimit(10), Offset(0))
        .map(_.doi),
      List("10.5555/zq.a")
    )
  }

class CandidateServiceInMemorySuite extends CandidateServiceContract with AcquisitionStoreSuite.InMemory
class CandidateServiceDbSuite extends CandidateServiceContract with AcquisitionStoreSuite.OnDatabase
