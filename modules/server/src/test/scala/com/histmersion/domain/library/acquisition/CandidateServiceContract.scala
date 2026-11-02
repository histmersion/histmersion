package com.histmersion.domain.library.acquisition

import com.histmersion.api.library.{CandidateCount, CandidateWatch}
import com.histmersion.api.library.Source

/** What the new-source search's store answers, the same on the database and in memory: a watch read keeps its watermark
  * when a read failed, a candidate found again keeps its state and gathers every watch that found it, a page in id
  * order, the counts in the states' order, a mark by id and by DOI.
  */
abstract class CandidateServiceContract extends AcquisitionStoreSuite:

  private def candidate(goal: String, doi: String, by: String) =
    Source.Candidate(0, goal, doi, s"Work $doi", Some(2025), List("Zq Author"), None, None, false, None, List(by))

  stest("a watch keeps its watermark when a read fails, and is listed by kind, subject and index") { s =>
    s.goal("zq-goal")
    val w = CandidateWatch("zq-goal", "query", "zq tin", "openalex")
    s.candidates.recordWatch(w, Some("2026-10-01"), None, 3)
    s.candidates.recordWatch(w, None, Some("HTTP 503"), 0)
    s.candidates.recordWatch(
      CandidateWatch("zq-goal", "citing", "10.5555/zq.x", "openalex"),
      Some("2026-10-02"),
      None,
      1
    )
    val got = s.candidates.watches("zq-goal")
    assertEquals(
      got.map(w => (w.kind, w.subject, w.watermark, w.lastError, w.lastFound)),
      List(
        ("citing", "10.5555/zq.x", Some("2026-10-02"), None, 1),
        ("query", "zq tin", Some("2026-10-01"), Some("HTTP 503"), 0)
      )
    )
    assert(got.forall(_.lastRun.isDefined))
    assertEquals(s.candidates.watches("zq-other"), Nil)
  }

  stest("a candidate found again keeps its state, gathers its watches; pages, counts and marks") { s =>
    s.goal("zq-goal")
    s.goal("zq-two")
    assert(s.candidates.upsert(candidate("zq-goal", "10.5555/zq.a", "openalex query")))
    assert(s.candidates.upsert(candidate("zq-goal", "10.5555/zq.b", "crossref query")))
    assert(s.candidates.upsert(candidate("zq-two", "10.5555/zq.a", "openalex query")))
    assert(!s.candidates.upsert(candidate("zq-goal", "10.5555/zq.a", "crossref query")))
    assertEquals(s.candidates.dois("zq-goal"), Set("10.5555/zq.a", "10.5555/zq.b"))
    val page = s.candidates.list(Some("zq-goal"), "new", 10, 0)
    assertEquals(page.map(_.doi), List("10.5555/zq.a", "10.5555/zq.b"))
    assertEquals(page.head.foundBy.sorted, List("crossref query", "openalex query"))
    assertEquals(s.candidates.list(Some("zq-goal"), "new", 1, 1).map(_.doi), List("10.5555/zq.b"))
    assertEquals(s.candidates.list(None, "new", 10, 0).size, 3)
    assertEquals(s.candidates.mark(List(page.head.id), "seen"), 1)
    assertEquals(s.candidates.mark(Nil, "seen"), 0)
    assertEquals(s.candidates.markDois("zq-goal", List("10.5555/zq.b"), "dismissed"), 1)
    assertEquals(
      s.candidates.counts(Some("zq-goal")),
      List(CandidateCount("seen", 1), CandidateCount("dismissed", 1))
    )
    assertEquals(
      s.candidates.counts(None),
      List(CandidateCount("new", 1), CandidateCount("seen", 1), CandidateCount("dismissed", 1))
    )
    assert(!s.candidates.upsert(candidate("zq-goal", "10.5555/zq.a", "europepmc query")))
    assertEquals(s.candidates.list(Some("zq-goal"), "seen", 10, 0).map(_.doi), List("10.5555/zq.a"))
  }

class CandidateServiceInMemorySuite extends CandidateServiceContract with AcquisitionStoreSuite.InMemory
class CandidateServiceDbSuite extends CandidateServiceContract with AcquisitionStoreSuite.OnDatabase
