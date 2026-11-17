package com.histmersion
package api

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import java.util.UUID
import com.histmersion.api.exchange.{MergeApplyJob, MergeApplyResult, MergeJobState, MergeMarkdown}
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.exchange.ExchangeJobKinds
import com.histmersion.api.exchange.MergePlan

/** #888: a merge apply is a `jobs` row, so `GET /api/merge/apply/{job}` answers across a restart. How a stored row
  * reads back ([[MergeApplyJob.of]]) — above all, an apply a restart cut short is `interrupted`, never `running`
  * forever — and how it is written for a person.
  */
class MergeApplyJobSuite extends munit.FunSuite:
  private val Crash: Problem = Problem.Internal.Unexpected(
    Problem.Internal.Id("3f2a9c1e"),
    Problem.Where("merge apply"),
    Problem.Internal.ExceptionName("java.lang.IllegalStateException"),
    External.Reported("java.lang.IllegalStateException: boom")
  )

  private val result = MergeApplyResult(
    UUID.fromString("00000000-0000-0000-0000-000000000001"),
    Bundle.Instance(UUID.fromString("00000000-0000-0000-0000-000000000002"), Some(Bundle.Instance.Label("work"))),
    applied = MergeApplyResult.Applied(true),
    MergeApplyResult.PlanHash("abc123")
  )

  private def of(status: JobState, error: Option[MergeApplyJob.Failure] = None, r: Option[MergeApplyResult] = None) =
    MergeApplyJob.of(
      SpatialHierarchyResult.Job.Id("j1"),
      status,
      MergeApplyJob.OfBundle("f" * 64),
      MergeApplyJob.OfPlanHash("abc123"),
      MergeApplyJob.OfElapsedMs(1500),
      error,
      r
    )

  test("a running row is running; a done one carries its report"):
    assertEquals(of(JobState.Running).state, MergeJobState.Running)
    val done = of(JobState.Done, r = Some(result))
    assertEquals(
      (done.state, done.result, done.planHash, done.bundle),
      (MergeJobState.Done, Some(result), "abc123", "f" * 64)
    )

  test("a row a restart failed is interrupted, and says nothing was half applied"):
    val j = of(JobState.Failed, error = Some(MergeApplyJob.Failure.Interrupted))
    assertEquals(j.state, MergeJobState.Interrupted)
    assertEquals(
      j.problems,
      Some(NonEmptyVector.of[Problem](Problem.Run.CutShort(Problem.Conflict.Activity.MergeApply)))
    )
    assert(
      j.problems.exists(ps =>
        NonEmptyVector
          .toVector(ps)
          .exists(e => e.sentence.contains("never half applied") && e.sentence.contains("merge list"))
      ),
      j.problems.toString
    )
    assert(MergeMarkdown.applyJob(j).startsWith("**The merge apply was interrupted**"), MergeMarkdown.applyJob(j))

  test("a crash is failed with its reason; done without a recorded report is failed, not done"):
    assertEquals(
      of(JobState.Failed, error = Some(MergeApplyJob.Failure.Other(NonEmptyVector.of(Crash)))).problems,
      Some(NonEmptyVector.of(Crash))
    )
    assertEquals(
      of(JobState.Failed, error = Some(MergeApplyJob.Failure.Other(NonEmptyVector.of(Crash)))).state,
      MergeJobState.Failed
    )
    assertEquals(of(JobState.Done).state, MergeJobState.Failed)
    assertEquals(
      of(JobState.Done).problems,
      Some(NonEmptyVector.of[Problem](Problem.Run.Unrecorded(Problem.Conflict.Activity.MergeApply)))
    )

  test("an apply that failed with several problems keeps and shows every one"):
    val backupRefused = Problem.Conflict.Busy(Problem.Conflict.Activity.Backup)
    val j = of(JobState.Failed, error = Some(MergeApplyJob.Failure.Other(NonEmptyVector.of(Crash, backupRefused))))
    assertEquals(j.problems, Some(NonEmptyVector.of(Crash, backupRefused)))
    assertEquals(
      MergeMarkdown.applyJob(j),
      s"**The merge apply failed** (job `j1`): ${Crash.sentence}; ${backupRefused.sentence}"
    )

  test("the jobs list names what a merge apply is about: its plan"):
    val j = JobInfo(
      SpatialHierarchyResult.Job.Id("j1"),
      JobKinds.Wire(MergeApplyJob.JobKind),
      JobInfo.Status("running"),
      detail = Some(ExchangeJobKinds.PlanHash(Some(MergePlan.PlanHash("abc123"))))
    )
    assertEquals(JobDetail.subject(j), Some("merge plan abc123"))
