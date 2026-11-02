package com.histmersion.domain.library.acquisition

import com.histmersion.api.library.{Acquire, RetractionNotice}

/** What the assessments' store answers, the same on the database and in memory: the mirror replaced whole (a DOI listed
  * twice keeps its last row), its notices by DOI whatever its case, a verdict recorded and replaced.
  */
abstract class AssessmentServiceContract extends AcquisitionStoreSuite:

  private def row(doi: String, reason: String, date: String) =
    Acquire.RetractionRow(doi, Some(reason), Some(date), Some("Retraction"))

  stest("the mirror is replaced whole; a DOI listed twice keeps its last row") { s =>
    assertEquals(s.assessments.retractionCount, 0)
    assertEquals(
      s.assessments.replaceRetractionWatch(
        List(
          row("10.5555/zq.a", "first", "1/2/2019 0:00"),
          row("10.5555/zq.b", "other", "2019-03-04"),
          row("10.5555/zq.a", "again", "12/15/2021 0:00")
        )
      ),
      2
    )
    assertEquals(s.assessments.retractionCount, 2)
    assertEquals(
      s.assessments.retractionsFor("10.5555/ZQ.A"),
      List(RetractionNotice(Some("Retraction"), Some("again"), Some("2021-12-15")))
    )
    assertEquals(s.assessments.replaceRetractionWatch(List(row("10.5555/zq.c", "c", "1/1/2020 0:00"))), 1)
    assertEquals(s.assessments.retractionsFor("10.5555/zq.a"), Nil)
    assertEquals(s.assessments.retractionCount, 1)
  }

  stest("a verdict is recorded with its stamp, and a later one replaces it") { s =>
    val _ = s.sources.put(
      com.histmersion.api.library.Source(
        "zqa_one",
        "One",
        Nil,
        None,
        Some(2000),
        None,
        com.histmersion.domain.library.Source.Status.Current,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        None,
        None,
        None
      ),
      com.histmersion.domain.Caller.Local
    )
    assertEquals(s.assessments.lastFor("zqa_one"), None)
    val first = s.assessments.record(AssessmentRecord("zqa_one", "current", false, "{}", Some(3), None))
    assert(first.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"), first)
    assertEquals(s.assessments.lastFor("zqa_one").map(_._1), Some("current"))
    val _ = s.assessments.record(AssessmentRecord("zqa_one", "retracted", true, """{"n":1}""", Some(4), Some(1)))
    assertEquals(s.assessments.lastFor("zqa_one").map(_._1), Some("retracted"))
  }

class AssessmentServiceInMemorySuite extends AssessmentServiceContract with AcquisitionStoreSuite.InMemory
class AssessmentServiceDbSuite extends AssessmentServiceContract with AcquisitionStoreSuite.OnDatabase
