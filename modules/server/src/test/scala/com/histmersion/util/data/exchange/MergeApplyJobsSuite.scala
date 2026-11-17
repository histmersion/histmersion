package com.histmersion
package util.data.exchange
import com.histmersion.util.problem.{Legacy, Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

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
      applied = MergeApplyResult.Applied(true),
      MergeApplyResult.PlanHash("abc")
    )

  private def sha(c: Char): Sha256 = Sha256.applyUnsafe(c.toString * 64)
  private def plan(c: Char): MergeRow.PlanHash = MergeRow.PlanHash(c.toString * 64)

  private def status(jobs: MergeApplyJobs, id: MergeApplyJob.Job): Option[MergeApplyJob] =
    jobs.status(Job.Id.applyUnsafe(id.value))

  test("done within the wait: the answer is its report"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 5)
    val j = jobs
      .start(sha('b'), plan('a'))(() => Right(result))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(j.state, MergeJobState.Done)
    assertEquals(j.result, Some(result))
    assertEquals(status(jobs, j.job).map(_.state), Some(MergeJobState.Done))

  test("still running after the wait: answered as running, and a second apply is refused until it ends"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 0)
    val release = CountDownLatch(1)
    val j = jobs
      .start(sha('b'), plan('a')) { () =>
        release.await()
        Left(Problem.Unclassified(Legacy.Text("nothing to apply")))
      }
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(j.state, MergeJobState.Running)
    assert(
      jobs
        .start(sha('c'), plan('d'))(() => Right(result))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("still running"))
    )
    release.countDown()
    val ended = Iterator
      .continually { Thread.sleep(10); status(jobs, j.job) }
      .take(500)
      .find(_.exists(_.state != MergeJobState.Running))
    assertEquals(ended.flatten.flatMap(_.problems).map(_.sentences), Some(Vector("nothing to apply")))

  test("a run that fails with several problems ends failed with every one"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 5)
    val two = Problems.of(Problem.Run.Interrupted, Problem.Conflict.Busy(Problem.Conflict.Activity.Backup))
    val j =
      jobs.start(sha('b'), plan('a'))(() => Left(two)).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(j.state, MergeJobState.Failed)
    assertEquals(j.problems, Some(two.toNonEmpty))

  test("a run that throws ends failed, with why"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 5)
    val j = jobs
      .start(sha('b'), plan('a'))(() => throw IllegalStateException("boom"))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(j.state, MergeJobState.Failed)
    assert(j.problems.exists(_.sentences.exists(_.contains("boom"))), j.toString)
    assertEquals(jobs.status(Job.Id.applyUnsafe(UUID.randomUUID().toString)), None)
