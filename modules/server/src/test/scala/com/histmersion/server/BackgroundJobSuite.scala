package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.{Db, JobRepo, Migrate}

import java.util.concurrent.CountDownLatch

/** [[BackgroundJob]] (#1946): the one runner of the server's long work. Pure parts always; the job rows
  * on a test database (gated on HISTMERSION_TEST_DB_URL). */
class BackgroundJobSuite extends munit.FunSuite:
  import BackgroundJob.Outcome

  test("an outcome: a summary, and why it failed when it did"):
    assertEquals(Outcome.of(Right("3 places")), Outcome(Some("3 places"), None))
    assertEquals(Outcome.of(Left("HTTP 404")), Outcome(None, Some("HTTP 404")))
    assert(Outcome.failed("x").failed && !Outcome.done("y").failed)

  test("a refusal names the running job; an exception names its class"):
    assertEquals(BackgroundJob.busy("a vacuum job", Some("job 42")).message,
      "a vacuum job is already running (job 42) — see `histmersion jobs`")
    assertEquals(BackgroundJob.describe(InterruptedException()), "interrupted")
    assertEquals(BackgroundJob.describe(IllegalStateException("boom")), "IllegalStateException: boom")

  // ── on a test database ────────────────────────────────────────────────────────────────────────
  private val testDb = com.histmersion.db.TestDb.url
  private lazy val ds =
    val d = Db.dataSource(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d); d

  private def dbTest(name: String)(body: => Unit): Unit =
    test(name)(if testDb.isEmpty then assume(false, "HISTMERSION_TEST_DB_URL is unset") else body)

  private def await(id: String, left: Int = 200): JobInfo =
    val j = JobRepo.get(ds, id).getOrElse(fail(s"no job $id"))
    if j.isTerminal || left == 0 then j else { Thread.sleep(25); await(id, left - 1) }

  dbTest("a job runs on its own thread: its row moves queued → running → done with the summary"):
    val slot = BackgroundJob.Slot()
    val go   = CountDownLatch(1)
    val j = BackgroundJob.start(ds, "zz-test", Some(JobDetail(about = Some("a test"))), slot, "a test job") { (job, progress) =>
      go.await()
      progress(s"working on ${job.kind}")
      Outcome.done("all done")
    }.fold(e => fail(e.message), identity)
    assertEquals(j.detail.flatMap(_.about), Some("a test"))
    assert(slot.isRunning, "the slot is held while the job runs")
    assertEquals(BackgroundJob.start(ds, "zz-test", None, slot, "a test job")((_, _) => Outcome.done("no")).left.map(_.message),
      Left(s"a test job is already running (job ${j.id}) — see `histmersion jobs`"))
    go.countDown()
    val done = await(j.id)
    assertEquals((done.status, done.progress, done.error), ("done", Some("all done"), None))
    Thread.sleep(50)
    assert(!slot.isRunning, "the slot is freed when the job ends")

  dbTest("a failure keeps its summary and its reason; an exception fails the job with its message"):
    val slot = BackgroundJob.Slot()
    val f = BackgroundJob.start(ds, "zz-test", None, slot, "a test job")((_, _) => Outcome(Some("half"), Some("disk full")))
      .fold(e => fail(e.message), identity)
    assertEquals(await(f.id).error, Some("disk full"))
    Thread.sleep(50)
    val x = BackgroundJob.start(ds, "zz-test", None, slot, "a test job")((_, _) => throw IllegalStateException("boom"))
      .fold(e => fail(e.message), identity)
    val failed = await(x.id)
    assertEquals((failed.status, failed.error), ("failed", Some("IllegalStateException: boom")))
