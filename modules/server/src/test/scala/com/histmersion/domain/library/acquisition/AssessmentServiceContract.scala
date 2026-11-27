package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.library.{Acquire, RetractionNotice}
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Source
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.Caller
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** What the assessments' store answers, the same on the database and in memory: the mirror replaced whole (a DOI listed
  * twice keeps its last row), its notices by DOI whatever its case, a verdict recorded and replaced.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
abstract class AssessmentServiceContract extends AcquisitionStoreSuite:

  private def row(doi: Doi, reason: RetractionNotice.Account, date: RetractionNotice.Date) =
    Acquire.RetractionRow(doi, Some(reason), Some(date), Some(RetractionNotice.Nature("Retraction")))

  stest("the mirror is replaced whole; a DOI listed twice keeps its last row") { s =>
    assertEquals(s.assessments.retractionCount, 0)
    assertEquals(
      s.assessments.replaceRetractionWatch(
        List(
          row(Doi("10.5555/zq.a"), RetractionNotice.Account("first"), RetractionNotice.Date("1/2/2019 0:00")),
          row(Doi("10.5555/zq.b"), RetractionNotice.Account("other"), RetractionNotice.Date("2019-03-04")),
          row(Doi("10.5555/zq.a"), RetractionNotice.Account("again"), RetractionNotice.Date("12/15/2021 0:00"))
        )
      ),
      2
    )
    assertEquals(s.assessments.retractionCount, 2)
    assertEquals(
      s.assessments.retractionsFor(domain.library.Doi("10.5555/ZQ.A")),
      List(
        RetractionNotice(
          Some(RetractionNotice.Nature("Retraction")),
          Some(RetractionNotice.Account("again")),
          Some(RetractionNotice.Date("2021-12-15"))
        )
      )
    )
    assertEquals(
      s.assessments.replaceRetractionWatch(
        List(row(Doi("10.5555/zq.c"), RetractionNotice.Account("c"), RetractionNotice.Date("1/1/2020 0:00")))
      ),
      1
    )
    assertEquals(s.assessments.retractionsFor(domain.library.Doi("10.5555/zq.a")), Nil)
    assertEquals(s.assessments.retractionCount, 1)
  }

  stest("a verdict is recorded with its stamp, and a later one replaces it") { s =>
    val _ = s.sources.put(
      Source(
        Citekey("zqa_one"),
        Title("One"),
        Nil,
        None,
        Some(Year(2000)),
        None,
        Source.Status.Current,
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
      Caller.Local
    )
    assertEquals(s.assessments.lastFor(domain.library.Citekey("zqa_one")), None)
    val first = s.assessments.record(
      AssessmentRecord(
        domain.library.Citekey("zqa_one"),
        AssessmentRecord.Verdict("current"),
        AssessmentRecord.Retracted(false),
        AssessmentRecord.NoticesJson("{}"),
        Some(AssessmentRecord.CitedBy(3)),
        None
      )
    )
    assert(first.value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"), first)
    assertEquals(s.assessments.lastFor(domain.library.Citekey("zqa_one")).map(_._1), Some("current"))
    val _ = s.assessments.record(
      AssessmentRecord(
        domain.library.Citekey("zqa_one"),
        AssessmentRecord.Verdict("retracted"),
        AssessmentRecord.Retracted(true),
        AssessmentRecord.NoticesJson("""{"n":1}"""),
        Some(AssessmentRecord.CitedBy(4)),
        Some(AssessmentRecord.Influential(1))
      )
    )
    assertEquals(s.assessments.lastFor(domain.library.Citekey("zqa_one")).map(_._1), Some("retracted"))
  }

class AssessmentServiceInMemorySuite extends AssessmentServiceContract with AcquisitionStoreSuite.InMemory
class AssessmentServiceDbSuite extends AssessmentServiceContract with AcquisitionStoreSuite.OnDatabase
