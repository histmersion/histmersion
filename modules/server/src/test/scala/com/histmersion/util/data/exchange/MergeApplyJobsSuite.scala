package com.histmersion.util.data.exchange

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.Sha256
import com.histmersion.api.exchange.{MergeApplyJob, MergeApplyResult, MergeJobState}
import com.histmersion.util.jobs.{Job, MergeApplyRuns}

import java.util.UUID
import java.util.concurrent.CountDownLatch

/** A merge apply as a job: it answers once done within the wait, or as running; a second is refused while one runs; its
  * row keeps how it ended. Over the in-memory rows.
  */
class MergeApplyJobsSuite extends munit.FunSuite:

  private val result =
    MergeApplyResult(
      UUID.randomUUID(),
      com.histmersion.api.exchange.Bundle.Instance(UUID.randomUUID(), None),
      applied = com.histmersion.api.exchange.MergeApplyResult.Applied(true),
      com.histmersion.api.exchange.MergeApplyResult.PlanHash("abc")
    )

  private def sha(c: Char): Sha256 = Sha256.applyUnsafe(c.toString * 64)

  private def status(jobs: MergeApplyJobs, id: MergeApplyJob.Job): Option[MergeApplyJob] =
    jobs.status(Job.Id.applyUnsafe(id.value))

  test("done within the wait: the answer is its report"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 5)
    val j = jobs.start(sha('b'), sha('a'))(() => Right(result)).fold(e => fail(e.message.value), identity)
    assertEquals(j.state, MergeJobState.Done)
    assertEquals(j.result, Some(result))
    assertEquals(status(jobs, j.job).map(_.state), Some(MergeJobState.Done))

  test("still running after the wait: answered as running, and a second apply is refused until it ends"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 0)
    val release = CountDownLatch(1)
    val j = jobs
      .start(sha('b'), sha('a')) { () =>
        release.await()
        Left(MergeApplyRuns.Failure("nothing to apply"))
      }
      .fold(e => fail(e.message.value), identity)
    assertEquals(j.state, MergeJobState.Running)
    assert(jobs.start(sha('c'), sha('d'))(() => Right(result)).left.exists(_.message.value.contains("still running")))
    release.countDown()
    val ended = Iterator
      .continually { Thread.sleep(10); status(jobs, j.job) }
      .take(500)
      .find(_.exists(_.state != MergeJobState.Running))
    assertEquals(ended.flatten.flatMap(_.error), Some("nothing to apply"))

  test("a run that throws ends failed, with why"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 5)
    val j = jobs.start(sha('b'), sha('a'))(() => throw IllegalStateException("boom")).fold(e => fail(e.message.value), identity)
    assertEquals(j.state, MergeJobState.Failed)
    assert(j.error.exists(_.value.contains("boom")), j.toString)
    assertEquals(jobs.status(Job.Id.applyUnsafe(UUID.randomUUID().toString)), None)
