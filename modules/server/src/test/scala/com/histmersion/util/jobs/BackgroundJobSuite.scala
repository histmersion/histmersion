package com.histmersion
package util.jobs
import com.histmersion.util.problem.{Legacy, Problem, Problems, Raised}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.database.{Transactor, WritePermit}

import com.histmersion.util.jobs.BackgroundJob

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.util.jobs.JobRepo

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import com.histmersion.api.jobs.JobState
import com.histmersion.db.*
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.JobKinds

/** [[BackgroundJob]] (#1946): the one runner of the server's long work. Pure parts always; the job rows on a test
  * database (gated on HISTMERSION_TEST_DB_URL).
  */
class BackgroundJobSuite extends DbSuite:
  import BackgroundJob.Outcome

  // the jobs' threads are waited for up to a minute each (a loaded machine is slow), past munit's 30 s
  override val munitTimeout = scala.concurrent.duration.Duration(3, "min")

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

  /** The workers the jobs run through: a gate of their own, with no restore. */
  private val workers = Workers(WritePermit.Gate())

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
        workers,
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
        .start(ds, workers, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job"))((_, _) =>
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
      .start(ds, workers, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job"))((_, _) =>
        Outcome(Some(Outcome.Summary("half")), Some(Problem.Unclassified(Legacy.Text("disk full"))))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(await(f.id).problems.map(_.sentences), Some(Vector("disk full")))
    Thread.sleep(50)
    val several = BackgroundJob
      .start(ds, workers, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job"))((_, _) =>
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
      .start(ds, workers, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job"))((_, _) =>
        throw IllegalStateException("boom")
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val failed = await(x.id)
    assertEquals(failed.status, "failed")
    assert(
      failed.problems.exists(_.sentences.exists(_.endsWith("java.lang.IllegalStateException: boom"))),
      failed.problems
    )

  // ── the outcomes of its own writes (A6) ───────────────────────────────────────────────────────

  /** The row of job `id` now. */
  private def row(id: SpatialHierarchyResult.Job.Id): JobInfo =
    Transactor(ds).read(JobRepo.get(Job.Id.applyUnsafe(id.value))).getOrElse(fail(s"no job $id"))

  /** Wait (bounded, a minute: the machine may be slow) for `slot` to be free: the job's thread has ended. */
  private def freed(slot: BackgroundJob.Slot, left: Int = 2400): Boolean =
    if !slot.isRunning then true
    else if left == 0 then false
    else { Thread.sleep(25); freed(slot, left - 1) }

  /** `under` (the suite's pool by default), failing the next `failing` connections asked of it; `asked` counts them
    * all.
    */
  final private class Flaky(val failing: AtomicInteger, under: => javax.sql.DataSource = ds)
      extends javax.sql.DataSource:
    val asked = AtomicInteger(0)
    def getConnection(): java.sql.Connection =
      val _ = asked.incrementAndGet()
      if failing.getAndDecrement() > 0 then throw java.sql.SQLException("the database is briefly unreachable")
      else under.getConnection()
    def getConnection(u: String, p: String): java.sql.Connection = getConnection()
    def getLogWriter: java.io.PrintWriter = under.getLogWriter
    def setLogWriter(out: java.io.PrintWriter): Unit = under.setLogWriter(out)
    def setLoginTimeout(seconds: Int): Unit = under.setLoginTimeout(seconds)
    def getLoginTimeout: Int = under.getLoginTimeout
    def getParentLogger: java.util.logging.Logger = under.getParentLogger
    def unwrap[T](iface: Class[T]): T = under.unwrap(iface)
    def isWrapperFor(iface: Class[?]): Boolean = under.isWrapperFor(iface)

  dbTest("a job cancelled before its thread ran does not run its body: its row stays cancelled, its slot is freed"):
    val go = CountDownLatch(1)
    val delayed: WorkCarrier = work => () => { go.await(10, TimeUnit.SECONDS); work() }
    val ran = AtomicBoolean(false)
    val slot = BackgroundJob.Slot()
    val j = BackgroundJob
      .start(ds, workers, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job"), pins = delayed) { (_, _) =>
        ran.set(true)
        Outcome.done(Outcome.Summary("ran"))
      }
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val id = Job.Id.applyUnsafe(j.id.value)
    assert(Transactor(ds).transact(JobRepo.transition(id, JobState.Queued, JobState.Cancelled)).isDefined)
    go.countDown()
    assert(freed(slot), "the slot is freed")
    assert(!ran.get, "the body of a cancelled job never runs")
    assertEquals(row(j.id).status, "cancelled")

  dbTest("how a job ended is written again while the database fails, and recorded once it answers"):
    val failing = AtomicInteger(0)
    val slot = BackgroundJob.Slot()
    val j = BackgroundJob
      .start(Flaky(failing), workers, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job")) { (_, _) =>
        failing.set(2) // the first two tries of the ending fail
        Outcome.done(Outcome.Summary("done at the third try"))
      }
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(freed(slot), "the slot is freed")
    assertEquals((row(j.id).status, row(j.id).progress), ("done", Some("done at the third try")))

  dbTest("an ending no try could write leaves the row running, logged, and the slot free"):
    val failing = AtomicInteger(0)
    val slot = BackgroundJob.Slot()
    val j = BackgroundJob
      .start(Flaky(failing), workers, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job")) { (_, _) =>
        failing.set(1000)
        Outcome.done(Outcome.Summary("never recorded"))
      }
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(freed(slot), "the slot is freed although the ending was never written")
    assertEquals(row(j.id).status, "running")
    val _ = Transactor(ds).transact(JobRepo.failInterrupted())

  dbTest("a job a restore fenced off writes no ending, and does not try again"):
    val gate = WritePermit.Gate()
    val own = Workers(gate)
    val slot = BackgroundJob.Slot()
    val fenced = Transactor(gate.dataSource(ds))
    // every connection the job asks for, refused ones included
    val counted = Flaky(AtomicInteger(0), gate.dataSource(ds))
    val askedAtRestore = AtomicInteger(-1)
    val j = BackgroundJob
      .start(counted, own, Job.Kind("zz-test"), None, slot, BackgroundJob.Work("a test job")) { (_, _) =>
        // a restore, on its own thread, while the job runs
        val restore = Thread.ofPlatform().start { () =>
          assert(gate.drain()); gate.close(); gate.fence(); gate.reopen()
          askedAtRestore.set(counted.asked.get)
        }
        restore.join()
        Outcome.done(Outcome.Summary("after the restore"))
      }
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(freed(slot), "the slot is freed")
    assertEquals(counted.asked.get - askedAtRestore.get, 1, "the ending was asked for once, refused, not tried again")
    assertEquals(
      fenced.read(JobRepo.get(Job.Id.applyUnsafe(j.id.value))).map(_.status),
      Some(JobInfo.Status("running"))
    )
    val _ = Transactor(ds).transact(JobRepo.failInterrupted())
