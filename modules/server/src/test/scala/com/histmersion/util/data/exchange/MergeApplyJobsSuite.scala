package com.histmersion.util.data.exchange

import com.histmersion.api.{BundleInstance, MergeApplyResult, MergeJobState}
import com.histmersion.util.jobs.MergeApplyRuns

import java.util.UUID
import java.util.concurrent.CountDownLatch

/** A merge apply as a job: it answers once done within the wait, or as running; a second is refused while one runs;
  * its row keeps how it ended. Over the in-memory rows.
  */
class MergeApplyJobsSuite extends munit.FunSuite:

  private val result =
    MergeApplyResult(UUID.randomUUID(), BundleInstance(UUID.randomUUID(), None), applied = true, "abc")

  test("done within the wait: the answer is its report"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 5)
    val j = jobs.start("b" * 64, "abc")(() => Right(result)).fold(e => fail(e.message), identity)
    assertEquals(j.state, MergeJobState.Done)
    assertEquals(j.result, Some(result))
    assertEquals(jobs.status(j.job).map(_.state), Some(MergeJobState.Done))

  test("still running after the wait: answered as running, and a second apply is refused until it ends"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 0)
    val release = CountDownLatch(1)
    val j = jobs
      .start("b" * 64, "abc") { () =>
        release.await()
        Left("nothing to apply")
      }
      .fold(e => fail(e.message), identity)
    assertEquals(j.state, MergeJobState.Running)
    assert(jobs.start("c" * 64, "def")(() => Right(result)).left.exists(_.message.contains("still running")))
    release.countDown()
    val ended = Iterator.continually { Thread.sleep(10); jobs.status(j.job) }.take(500).find(_.exists(_.state != MergeJobState.Running))
    assertEquals(ended.flatten.flatMap(_.error), Some("nothing to apply"))

  test("a run that throws ends failed, with why"):
    val jobs = MergeApplyJobs(MergeApplyRuns.InMemory(), 5)
    val j = jobs.start("b" * 64, "abc")(() => throw IllegalStateException("boom")).fold(e => fail(e.message), identity)
    assertEquals(j.state, MergeJobState.Failed)
    assert(j.error.exists(_.contains("boom")), j.toString)
    assertEquals(jobs.status("nope"), None)
