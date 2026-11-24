package com.histmersion
package server

import com.histmersion.util.problem.{Legacy, Problem, Raised}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Api
import com.histmersion.api.backup.Backup
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.config.{AppConfig, DbConfig, ServerConfig}
import com.histmersion.db.{DbSuite, TestDb}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.database.{Migrate, Transactor, WritePermit}
import com.histmersion.util.jobs.{Job, JobRepo, Quiescent}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.util.storage.Blob
import ox.supervised

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import com.histmersion.util.database.TestPermits.given
import com.histmersion.domain.Creator
import com.histmersion.util.database.TestCreators.given

/** G90, G89 and G123 on the `*_test` database: one server per database, enforced at start; a restore that waits for the
  * writes in flight, refuses the writes and connections asked while it runs, holds every registered worker and has each
  * reconcile with the restored rows. The restore's body here is the test's own (the real `pg_restore` path is
  * BackupRestoreSuite's): what is asserted is the gate around it. Skipped without HISTMERSION_TEST_DB_URL.
  */
class RestoreSafetySuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg =
    val b = AppConfig.load()
    b.modify(_.db).setTo(b.db.modify(_.url).setTo(TestDb.dbUrl.get))
  private lazy val pool =
    val p = testPool(cfg.db)
    Migrate.run(p)
    p

  private object NoConverter extends ConverterClient:
    def health: Either[Problem, ConverterClient.HealthSummary] = Right(ConverterClient.HealthSummary("ok"))
    def convert(
        file: Blob,
        filename: ConverterClient.Filename,
        quality: ConverterClient.HighQuality
    ): Either[Problem, ConvertedDoc] = Left(Problem.Unclassified(Legacy.Text("not used")))

  /** A wiring of its own over the test database, behind a gate of its own. */
  private def wiring(): ServerWiring = ServerWiring(Deps(cfg, pool), NoConverter)

  private val Kind = Job.Kind("zz-restore-safety")

  /** A job row of `Kind`, moved to `state` (queued, or running). */
  private def jobIn(state: JobState): Job.Id =
    val j = Transactor(pool).write(JobRepo.create(Kind, None, None))
    val id = Job.Id.applyUnsafe(j.id.value)
    if state == JobState.Running then
      assert(Transactor(pool).write(JobRepo.transition(id, JobState.Queued, JobState.Running)).isDefined)
    id

  private def statusOf(id: Job.Id): JobInfo = Transactor(pool).read(JobRepo.get(id)).getOrElse(fail(s"no job $id"))

  override protected def afterSuite(): Unit =
    if testDb.nonEmpty then
      val c = pool.getConnection
      try { val _ = c.createStatement().execute("DELETE FROM jobs WHERE kind = 'zz-restore-safety'") }
      finally c.close()

  // ── G90: one server per database ─────────────────────────────────────────────────────────────

  test("a second server on the same database is refused, typed, and changes nothing; the first goes on"):
    val http = HttpClient.newHttpClient()
    supervised {
      val first = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      // a job the first server is running: the second's start would have failed it as interrupted
      val running = jobIn(JobState.Running)
      val refused = intercept[Raised](
        supervised(Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start())
      )
      refused.problems.toVector match
        case Vector(Problem.Conflict.ServerHeld(holder)) =>
          assert(s"$holder".startsWith("histmersion-server "), s"$holder")
          assert(s"$holder".contains(s":${ProcessHandle.current().pid()}, Postgres session "), s"$holder")
        case other => fail(s"not refused as another server's database: $other")
      assertEquals(statusOf(running).status, JobInfo.Status("running"), "the refused start reconciled nothing")
      val health =
        http.send(
          HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:${first.port}/api/health")).build(),
          HttpResponse.BodyHandlers.ofString()
        )
      assertEquals(health.statusCode, 200, health.body)
      assert(Transactor(pool).write(JobRepo.transition(running, JobState.Running, JobState.Done)).isDefined)
    }
    // the first server's scope ended, and its lock with it: a server starts again
    supervised { val _ = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start() }

  // ── G89: a restore waits for the writes in flight, and refuses those asked while it runs ─────

  test("a restore started during a slow write waits for it, and sees what it wrote"):
    val h = wiring()
    val gate = h.maintenance
    val inside = CountDownLatch(1)
    val writeEnded = AtomicLong(0L)
    val bodyStarted = AtomicLong(0L)
    val written = AtomicLong(0L)
    val writer = Thread.ofPlatform().start { () =>
      val _ = gate.guard(WriteGate.OperationName("slow write")) {
        val j = Transactor(pool).write {
          val created = JobRepo.create(Kind, None, None)
          inside.countDown()
          Thread.sleep(300) // the transaction is still open when the restore starts
          created
        }
        written.set(1L)
        writeEnded.set(System.nanoTime())
        Right(j)
      }
    }
    assert(inside.await(10, TimeUnit.SECONDS))
    val restored = gate.exclusively(WriteGate.OperationName("restore"), 10.seconds) {
      bodyStarted.set(System.nanoTime())
      Right(written.get)
    }
    writer.join()
    assertEquals(restored, Right(1L), "the write had committed when the restore's body ran")
    assert(writeEnded.get <= bodyStarted.get, "the write ended before the restore began")

  test("a restore gives up, changing nothing, when a write outlasts its wait"):
    val h = wiring()
    val gate = h.maintenance
    val inside = CountDownLatch(1)
    val finish = CountDownLatch(1)
    val writer = Thread.ofPlatform().start { () =>
      val _ = gate.guard(WriteGate.OperationName("long write")) {
        inside.countDown()
        finish.await(10, TimeUnit.SECONDS)
        Right(())
      }
    }
    assert(inside.await(10, TimeUnit.SECONDS))
    var ran = false // set by the body, which must not run
    val out = gate.exclusively(WriteGate.OperationName("restore"), 100.millis) { ran = true; Right(()) }
    finish.countDown()
    writer.join()
    out match
      case Left(e) =>
        e.problems.head match
          case Problem.Conflict.Undrained(busy, _) =>
            assert(busy.toVector.head.toString.contains("1 guarded write(s) in flight"), s"$busy")
          case other => fail(s"$other")
        assertEquals(e.code, Some(Api.Error.Code(Backup.CodeMaintenance)))
      case Right(_) => fail("the restore ran beside a write")
    assert(!ran)
    assert(!gate.active, "the gate is open again")

  test("a write or a connection asked while a restore runs is refused with a typed problem; afterwards both work"):
    val h = wiring()
    val gate = h.maintenance
    val deps = Deps(cfg, pool, pool, gate)
    def onAnotherThread[A](f: => A): Either[Throwable, A] =
      val out = new java.util.concurrent.atomic.AtomicReference[Either[Throwable, A]]()
      val t = Thread.ofPlatform().start(() => out.set(scala.util.Try(f).toEither))
      t.join(10000)
      out.get()
    val seen = gate.exclusively(WriteGate.OperationName("restore"), 5.seconds) {
      val guarded = onAnotherThread(gate.guard(WriteGate.OperationName("a write"))(Right(1)))
      val unguarded = onAnotherThread(
        Transactor(deps.ds).write(JobRepo.get(Job.Id.applyUnsafe("00000000-0000-0000-0000-000000000000")))
      )
      val own = Transactor(deps.ds).read(JobRepo.get(Job.Id.applyUnsafe("00000000-0000-0000-0000-000000000000")))
      Right((guarded, unguarded.left.map(_.getClass.getSimpleName), own))
    }
    seen match
      case Right((Right(Left(e)), unguarded, own)) =>
        assertEquals(e.problems.head, Problem.Conflict.Busy(Problem.Conflict.Activity.Restore))
        assertEquals(e.code, Some(Api.Error.Code(Backup.CodeMaintenance)))
        assertEquals(unguarded, Left("Raised"), "an unguarded transaction gets no connection")
        assertEquals(own, None, "the restore's own thread reads")
      case other => fail(s"$other")
    assertEquals(gate.guard(WriteGate.OperationName("a write"))(Right(1)), Right(1))
    assertEquals(
      Transactor(deps.ds).read(JobRepo.get(Job.Id.applyUnsafe("00000000-0000-0000-0000-000000000000"))),
      None
    )

  test(
    "X4: a worker's unit writes while the restore drains, is refused once it holds the database; reads need no permit"
  ):
    val h = wiring()
    val gate = h.maintenance
    val deps = Deps(cfg, pool, pool, gate)
    val db = Transactor(deps.ds)
    val begun = CountDownLatch(1)
    val drained = CountDownLatch(1)
    val recorded = CountDownLatch(1)
    val proceed = CountDownLatch(1)
    val out = new java.util.concurrent.atomic.AtomicReference[(Boolean, Either[String, Boolean])]()
    val worker = Thread.ofPlatform().start { () =>
      gate.workers.unit {
        begun.countDown()
        drained.await(10, TimeUnit.SECONDS)
        // while draining: the worker records where it stopped, through its unit's permit
        val stopped = scala.util.Try(db.write(JobRepo.create(Kind, None, None))).isSuccess
        recorded.countDown()
        proceed.await(10, TimeUnit.SECONDS)
        // the restore holds the database now: the same permit no longer writes
        val after = scala.util.Try(db.write(JobRepo.create(Kind, None, None))).toEither
        out.set((stopped, after.left.map(_.getClass.getSimpleName).map(_ => true)))
      }
    }
    assert(begun.await(10, TimeUnit.SECONDS))
    val restored = gate.exclusively(
      WriteGate.OperationName("restore"),
      10.seconds,
      refused = {
        // draining: no new operation gets a permit, but the worker's unit records where it stopped
        drained.countDown()
        assert(recorded.await(10, TimeUnit.SECONDS))
        None
      }
    ) {
      // the restore holds the database: the unit's next write is refused
      proceed.countDown()
      worker.join(10000)
      Right(gate.permits.stageNow)
    }
    assertEquals(restored, Right(WritePermit.Stage.Closed))
    assertEquals(out.get, (true, Left("Raised")))
    // a request's read, under no permit, before and after
    assertEquals(db.read(JobRepo.get(Job.Id.applyUnsafe("00000000-0000-0000-0000-000000000000"))), None)

  // ── G123: every worker held during the restore, resumed after it, reconciled with it ─────────

  test("every registered worker is held while the restore runs, reconciles after it and takes work again"):
    val h = wiring()
    val gate = h.maintenance
    val events = ConcurrentLinkedQueue[String]()
    val busyUntil = CountDownLatch(1)
    gate.workers.register(new Quiescent:
      val name: Quiescent.Name = Quiescent.Name("probe")
      def quiesce(): Unit = {
        val _ = events.add("quiesce");
        Thread.ofPlatform().start { () =>
          Thread.sleep(200); busyUntil.countDown()
        }
      }
      def busy: Vector[Quiescent.Busy] =
        if busyUntil.getCount > 0 then Vector(Quiescent.Busy("a unit of work")) else Vector.empty
      def restored()(using WritePermit)(using Creator.New): Unit = { val _ = events.add("restored") }
      def resume(): Unit = { val _ = events.add("resume") })
    assertEquals(
      gate.workers.names.map(n => s"$n"),
      List(
        "work started once",
        "the OCR worker pool",
        "the paused background jobs",
        "the agent run pool",
        "the process managers' loop",
        "the agent lease's renewal",
        "the structured log's writer",
        "probe"
      )
    )
    val during = gate.exclusively(WriteGate.OperationName("restore"), 5.seconds) {
      val _ = events.add("body")
      Right((h.ingest.queue.paused, busyUntil.getCount))
    }
    assertEquals(during, Right((true, 0L)), "the OCR pool held, and the probe came to rest before the body ran")
    assertEquals(events.asScala.toList, List("quiesce", "body", "restored", "resume"))
    assert(!h.ingest.queue.paused, "the OCR pool takes work again")

  test("after a restore, the restored database's unfinished jobs are failed and the paused jobs' ways forgotten"):
    val h = wiring()
    val gate = h.maintenance
    val queued = jobIn(JobState.Queued)
    val running = jobIn(JobState.Running)
    val stops = h.agents.jobStops
    stops.pausedWith(queued, () => Right(()))
    val reconciled = gate.exclusively(WriteGate.OperationName("restore"), 5.seconds) {
      // the restore's body would replace the database here; the rows above stand for what it brought back
      Right(summon[WriteGate.Quiet].reconcile())
    }
    assertEquals(reconciled, Right(Nil), "every worker reconciled")
    List(queued, running).foreach { id =>
      val j = statusOf(id)
      assertEquals(j.status, JobInfo.Status("failed"))
      assertEquals(j.problems, Some(NonEmptyVector.of[Problem](Problem.Run.Restored)))
    }
    assert(!stops.canResume(queued), "a paused job's way to go on named a row the restore replaced")
