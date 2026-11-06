package com.histmersion.api

import java.util.UUID
import com.histmersion.api.exchange.{MergeApplyJob, MergeApplyResult, MergeJobState, MergeMarkdown}
import com.histmersion.api.jobs.{JobDetail, JobInfo, JobState}
import com.histmersion.api.exchange.Bundle

/** #888: a merge apply is a `jobs` row, so `GET /api/merge/apply/{job}` answers across a restart. How a stored row
  * reads back ([[MergeApplyJob.of]]) — above all, an apply a restart cut short is `interrupted`, never `running`
  * forever — and how it is written for a person.
  */
class MergeApplyJobSuite extends munit.FunSuite:

  private val result = MergeApplyResult(
    UUID.fromString("00000000-0000-0000-0000-000000000001"),
    Bundle.Instance(UUID.fromString("00000000-0000-0000-0000-000000000002"), Some(com.histmersion.api.exchange.Bundle.Instance.Label("work"))),
    applied = com.histmersion.api.exchange.MergeApplyResult.Applied(true),
    com.histmersion.api.exchange.MergeApplyResult.PlanHash("abc123")
  )

  private def of(status: JobState, error: Option[String] = None, r: Option[MergeApplyResult] = None) =
    MergeApplyJob.of(com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"), status, "f" * 64, com.histmersion.api.exchange.MergeApplyJob.OfPlanHash("abc123"), com.histmersion.api.exchange.MergeApplyJob.OfElapsedMs(1500), error, r)

  test("a running row is running; a done one carries its report"):
    assertEquals(of(JobState.Running).state, MergeJobState.Running)
    val done = of(JobState.Done, r = Some(result))
    assertEquals(
      (done.state, done.result, done.planHash, done.bundle),
      (MergeJobState.Done, Some(result), "abc123", "f" * 64)
    )

  test("a row a restart failed is interrupted, and says nothing was half applied"):
    val j = of(JobState.Failed, error = Some(JobState.Interrupted))
    assertEquals(j.state, MergeJobState.Interrupted)
    assert(j.error.exists(e => e.contains("never half applied") && e.contains("merge list")), j.error.toString)
    assert(MergeMarkdown.applyJob(j).startsWith("**The merge apply was interrupted**"), MergeMarkdown.applyJob(j))

  test("a crash is failed with its reason; done without a recorded report is failed, not done"):
    assertEquals(
      of(JobState.Failed, error = Some("merge apply crashed: boom")).error,
      Some("merge apply crashed: boom")
    )
    assertEquals(of(JobState.Failed, error = Some("merge apply crashed: boom")).state, MergeJobState.Failed)
    assertEquals(of(JobState.Done).state, MergeJobState.Failed)

  test("the jobs list names what a merge apply is about: its plan"):
    val j = JobInfo(
      com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"),
      com.histmersion.api.jobs.JobKinds.Wire(MergeApplyJob.JobKind),
      com.histmersion.api.jobs.JobInfo.Status("running"),
      detail = Some(com.histmersion.api.exchange.ExchangeJobKinds.PlanHash(Some(com.histmersion.api.exchange.MergePlan.PlanHash("abc123"))))
    )
    assertEquals(JobDetail.subject(j), Some("merge plan abc123"))
