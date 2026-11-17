package com.histmersion
package util.jobs
import com.histmersion.util.problem.{Legacy, Problem, Problems, Raised}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.database.Transactor

import com.histmersion.util.jobs.BackgroundJob

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.util.jobs.JobRepo

import java.util.concurrent.CountDownLatch
import com.histmersion.db.*
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.JobKinds

/** [[BackgroundJob]] (#1946): the one runner of the server's long work. Pure parts always; the job rows on a test
  * database (gated on HISTMERSION_TEST_DB_URL).
  */
class BackgroundJobSuite extends DbSuite:
  import BackgroundJob.Outcome

  test("an outcome: a summary, and why it failed when it did"):
    assertEquals(Outcome.of(Right(Outcome.Summary("3 places"))), Outcome(Some(Outcome.Summary("3 places")), None))
    assertEquals(
      Outcome.of(Left(Problem.Unclassified(Legacy.Text("HTTP 404")))),
      Outcome(None, Some(Problem.Unclassified(Legacy.Text("HTTP 404"))))
    )
    assert(Outcome.failed(Problem.Unclassified(Legacy.Text("x"))).failed && !Outcome.done(Outcome.Summary("y")).failed)

  test("a refusal names the running job; an exception names its class"):
    assertEquals(
      (BackgroundJob
        .busy(BackgroundJob.Work("a vacuum job"), Some(BackgroundJob.Work("job 42")))
        .problems
        .head)
        .sentence,
      "a vacuum job is already running (job 42) — see `histmersion jobs`"
    )
    assertEquals(BackgroundJob.describe(InterruptedException()).sentences, Vector("interrupted by a restart"))
    assert(
      BackgroundJob
        .describe(IllegalStateException("boom"))
        .sentences
        .mkString
        .endsWith("java.lang.IllegalStateException: boom")
    )
    // problems carried through an exception are each kept, not folded into one
    val two = Problems.of(Problem.Run.Interrupted, Problem.Conflict.Busy(Problem.Conflict.Activity.Backup))
    assertEquals(BackgroundJob.describe(Raised(two)), two)

  // ── on a test database ────────────────────────────────────────────────────────────────────────
  private val testDb = TestDb.url
  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private def dbTest(name: String)(body: => Unit): Unit =
    test(name)(if testDb.isEmpty then assume(false, "HISTMERSION_TEST_DB_URL is unset") else body)

  private def await(id: SpatialHierarchyResult.Job.Id, left: Int = 200): JobInfo =
    val j = Transactor(ds)
      .read(JobRepo.get(Job.Id.applyUnsafe(id.value)))
      .getOrElse(fail(s"no job $id"))
    if j.isTerminal || left == 0 then j else { Thread.sleep(25); await(id, left - 1) }

  dbTest("a job runs on its own thread: its row moves queued → running → done with the summary"):
    val slot = BackgroundJob.Slot()
    val go = CountDownLatch(1)
    val j = BackgroundJob
      .start(
        ds,
        Job.Kind("zz-test"),
        Some(JobDetail.Purpose(Some(JobKinds.Subject("a test")))),
        slot,
        BackgroundJob.Work("a test job")
      ) { (job, progress) =>
        go.await()
        progress(JobInfo.Progress(s"working on ${job.kind}"))
        Outcome.done(Outcome.Summary("all done"))
      }
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(j.detail.flatMap(JobDetail.Purpose.of), Some("a test"))
    assert(slot.isRunning, "the slot is held while the job runs")
    assertEquals(
      BackgroundJob
        .start(ds, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job"))((_, _) =>
          Outcome.done(Outcome.Summary("no"))
        )
        .left
        .map(_.problems.head)
        .left
        .map(_.sentence),
      Left(s"a test job is already running (job ${j.id}) — see `histmersion jobs`")
    )
    go.countDown()
    val done = await(j.id)
    assertEquals((done.status, done.progress, done.problems), ("done", Some("all done"), None))
    Thread.sleep(50)
    assert(!slot.isRunning, "the slot is freed when the job ends")

  dbTest("a failure keeps its summary and its reason; an exception fails the job with its message"):
    val slot = BackgroundJob.Slot()
    val f = BackgroundJob
      .start(ds, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job"))((_, _) =>
        Outcome(Some(Outcome.Summary("half")), Some(Problem.Unclassified(Legacy.Text("disk full"))))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(await(f.id).problems.map(_.sentences), Some(Vector("disk full")))
    Thread.sleep(50)
    val several = BackgroundJob
      .start(ds, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job"))((_, _) =>
        Outcome.failed(Problems.of(Problem.Conflict.Busy(Problem.Conflict.Activity.Backup), Problem.Run.Interrupted))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      await(several.id).problems,
      Some(
        NonEmptyVector.of[Problem](Problem.Conflict.Busy(Problem.Conflict.Activity.Backup), Problem.Run.Interrupted)
      ),
      "every problem, in order"
    )
    Thread.sleep(50)
    val x = BackgroundJob
      .start(ds, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job"))((_, _) =>
        throw IllegalStateException("boom")
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val failed = await(x.id)
    assertEquals(failed.status, "failed")
    assert(
      failed.problems.exists(_.sentences.exists(_.endsWith("java.lang.IllegalStateException: boom"))),
      failed.problems
    )
