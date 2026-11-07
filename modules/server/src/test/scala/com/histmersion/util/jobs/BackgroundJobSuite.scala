package com.histmersion.util.jobs

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

/** [[BackgroundJob]] (#1946): the one runner of the server's long work. Pure parts always; the job rows on a test
  * database (gated on HISTMERSION_TEST_DB_URL).
  */
class BackgroundJobSuite extends com.histmersion.db.DbSuite:
  import BackgroundJob.Outcome

  test("an outcome: a summary, and why it failed when it did"):
    assertEquals(Outcome.of(Right(com.histmersion.util.jobs.BackgroundJob.Outcome.Summary("3 places"))), Outcome(Some(com.histmersion.util.jobs.BackgroundJob.Outcome.Summary("3 places")), None))
    assertEquals(Outcome.of(Left(com.histmersion.util.jobs.BackgroundJob.Outcome.Error("HTTP 404"))), Outcome(None, Some(com.histmersion.util.jobs.BackgroundJob.Outcome.Error("HTTP 404"))))
    assert(Outcome.failed(com.histmersion.util.jobs.BackgroundJob.Outcome.Error("x")).failed && !Outcome.done(com.histmersion.util.jobs.BackgroundJob.Outcome.Summary("y")).failed)

  test("a refusal names the running job; an exception names its class"):
    assertEquals(
      BackgroundJob.busy(com.histmersion.util.jobs.BackgroundJob.Work("a vacuum job"), Some(com.histmersion.util.jobs.BackgroundJob.Work("job 42"))).message,
      "a vacuum job is already running (job 42) — see `histmersion jobs`"
    )
    assertEquals(BackgroundJob.describe(InterruptedException()), "interrupted")
    assertEquals(BackgroundJob.describe(IllegalStateException("boom")), "IllegalStateException: boom")

  // ── on a test database ────────────────────────────────────────────────────────────────────────
  private val testDb = com.histmersion.db.TestDb.url
  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private def dbTest(name: String)(body: => Unit): Unit =
    test(name)(if testDb.isEmpty then assume(false, "HISTMERSION_TEST_DB_URL is unset") else body)

  private def await(id: com.histmersion.api.SpatialHierarchyResult.Job.Id, left: Int = 200): JobInfo =
    val j = Transactor(ds)
      .read(JobRepo.get(com.histmersion.util.jobs.Job.Id.applyUnsafe(id.value)))
      .getOrElse(fail(s"no job $id"))
    if j.isTerminal || left == 0 then j else { Thread.sleep(25); await(id, left - 1) }

  dbTest("a job runs on its own thread: its row moves queued → running → done with the summary"):
    val slot = BackgroundJob.Slot()
    val go = CountDownLatch(1)
    val j = BackgroundJob
      .start(ds, com.histmersion.util.jobs.Job.Kind("zz-test"), Some(JobDetail.Purpose(Some(com.histmersion.api.jobs.JobKinds.Subject("a test")))), slot, com.histmersion.util.jobs.BackgroundJob.Work("a test job")) { (job, progress) =>
        go.await()
        progress(JobInfo.Progress(s"working on ${job.kind}"))
        Outcome.done(com.histmersion.util.jobs.BackgroundJob.Outcome.Summary("all done"))
      }
      .fold(e => fail(e.message.value), identity)
    assertEquals(j.detail.flatMap(JobDetail.Purpose.of), Some("a test"))
    assert(slot.isRunning, "the slot is held while the job runs")
    assertEquals(
      BackgroundJob.start(ds, com.histmersion.util.jobs.Job.Kind("zz-test"), None, slot, com.histmersion.util.jobs.BackgroundJob.Work("a test job"))((_, _) => Outcome.done(com.histmersion.util.jobs.BackgroundJob.Outcome.Summary("no"))).left.map(_.message),
      Left(s"a test job is already running (job ${j.id}) — see `histmersion jobs`")
    )
    go.countDown()
    val done = await(j.id)
    assertEquals((done.status, done.progress, done.error), ("done", Some("all done"), None))
    Thread.sleep(50)
    assert(!slot.isRunning, "the slot is freed when the job ends")

  dbTest("a failure keeps its summary and its reason; an exception fails the job with its message"):
    val slot = BackgroundJob.Slot()
    val f = BackgroundJob
      .start(ds, com.histmersion.util.jobs.Job.Kind("zz-test"), None, slot, com.histmersion.util.jobs.BackgroundJob.Work("a test job"))((_, _) => Outcome(Some(com.histmersion.util.jobs.BackgroundJob.Outcome.Summary("half")), Some(com.histmersion.util.jobs.BackgroundJob.Outcome.Error("disk full"))))
      .fold(e => fail(e.message.value), identity)
    assertEquals(await(f.id).error, Some("disk full"))
    Thread.sleep(50)
    val x = BackgroundJob
      .start(ds, com.histmersion.util.jobs.Job.Kind("zz-test"), None, slot, com.histmersion.util.jobs.BackgroundJob.Work("a test job"))((_, _) => throw IllegalStateException("boom"))
      .fold(e => fail(e.message.value), identity)
    val failed = await(x.id)
    assertEquals((failed.status, failed.error), ("failed", Some("IllegalStateException: boom")))
