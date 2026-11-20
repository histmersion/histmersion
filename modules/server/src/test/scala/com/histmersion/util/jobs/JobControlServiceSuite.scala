package com.histmersion
package util.jobs
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.database.{Transactor, WritePermit}

import com.histmersion.util.jobs.BackgroundJob

import com.histmersion.api.jobs.JobInfo
import com.histmersion.config.AppConfig
import com.histmersion.util.database.Migrate
import com.histmersion.util.jobs.JobRepo

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.util.jobs.Job
import com.histmersion.api.Api
import com.histmersion.db.DbSuite
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.api.basemap.BasemapJobKinds
import com.histmersion.api.library.AcquisitionJobKinds
import com.histmersion.api.Write
import com.histmersion.api.automation.AgentJobKinds
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.api.jobs.JobState

/** Pause, resume and cancel: the in-memory service, then a long background job on a test database (gated on
  * HISTMERSION_TEST_DB_URL) that stops at a part boundary and, resumed, does only the parts it had not done.
  */
class JobControlServiceSuite extends DbSuite:

  private val M1 = Job.Id.applyUnsafe("00000000-0000-0000-0000-0000000000a1")
  private val P1 = Job.Id.applyUnsafe("00000000-0000-0000-0000-0000000000a2")
  private val R1 = Job.Id.applyUnsafe("00000000-0000-0000-0000-0000000000a3")
  private val A1 = Job.Id.applyUnsafe("00000000-0000-0000-0000-0000000000a4")
  private val X = Job.Id.applyUnsafe("00000000-0000-0000-0000-0000000000a5")
  private val Nope = Job.Id.applyUnsafe("00000000-0000-0000-0000-0000000000a6")

  private def job(id: Job.Id, kind: JobInfo.Kind, status: JobInfo.Status) =
    JobInfo(
      SpatialHierarchyResult.Job.Id(id.value),
      kind,
      status,
      None,
      progress = Some(JobInfo.Progress("2/6"))
    )

  private def jobId(info: JobInfo): Job.Id = Job.Id.applyUnsafe(info.id.value)

  test("in memory: a pause is asked of a running job, which stops at its next part and resumes later"):
    val stops = JobStops()
    val mem = JobControlService.InMemory(AtomicReference(Map.empty), stops)
    mem.put(job(M1, ImportJobKinds.MediaImport.kind, JobInfo.Status("running")))
    assertEquals(mem.control(M1, JobAction.Pause).map(_.status), Right("running"), "it stops after the part it is on")
    assertEquals(stops.requestedFor(M1), Some(JobStop.Pause))
    var resumed = 0
    mem.stopped(M1, Some(JobInfo.Progress("3/6")), () => { resumed += 1; Right(()) })
    assertEquals(mem.get(M1).map(j => (j.status, j.progress)), Some(("paused", Some("paused after: 3/6"))))
    assertEquals(mem.control(M1, JobAction.Resume).map(_.status), Right("running"))
    assertEquals(resumed, 1)
    assert(
      mem.control(M1, JobAction.Resume).left.exists(_.code.exists(_.value == "conflict")),
      "a running job is not resumed"
    )

  test("in memory: cancel — a paused job at once, a running one at its next part; an ended one is refused"):
    val stops = JobStops()
    val mem = JobControlService.InMemory(AtomicReference(Map.empty), stops)
    mem.put(job(P1, BasemapJobKinds.BasemapFetch.kind, JobInfo.Status("paused")))
    assertEquals(
      mem.control(P1, JobAction.Cancel).map(j => (j.status, j.problems.map(_.sentences))),
      Right(("cancelled", Some(Vector("cancelled by a person"))))
    )
    mem.put(job(R1, AcquisitionJobKinds.AcquireRecheck.kind, JobInfo.Status("running")))
    assertEquals(mem.control(R1, JobAction.Cancel).map(_.status), Right("running"))
    mem.stopped(R1, Some(JobInfo.Progress("5 of 9 items")), () => Right(()))
    assertEquals(mem.get(R1).map(_.status), Some("cancelled"))
    assert(mem.control(R1, JobAction.Pause).left.exists(_.problems.sentences.mkString("; ").contains("ended")))
    assertEquals(
      mem.control(Nope, JobAction.Pause).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )

  test("in memory: an agent run takes its own control"):
    val asked = AtomicReference(List.empty[(Job.Id, JobAction)])
    val mem = JobControlService.InMemory(
      AtomicReference(Map.empty),
      JobStops(),
      (id, a) => { asked.updateAndGet((id, a) :: _); Right(()) }
    )
    mem.put(job(A1, AgentJobKinds.Agent.kind, JobInfo.Status("running")))
    assert(mem.control(A1, JobAction.Cancel).isRight)
    assertEquals(asked.get, List((A1, JobAction.Cancel)))

  test("the controller: an unknown action is a 400 naming the three"):
    val c = JobControlControllers(JobControlService.InMemory(AtomicReference(Map.empty), JobStops()))
    assert(
      c.control(X, JobAction.Wire("stop"))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("pause, resume, cancel"))
    )

  // ── a real job on a test database ────────────────────────────────────────────────────────────
  private val testDb = TestDb.url
  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private def dbTest(name: String)(body: => Unit): Unit =
    test(name)(if testDb.isEmpty then assume(false, "HISTMERSION_TEST_DB_URL is unset") else body)

  private def until(id: Job.Id, status: JobInfo.Status, left: Int = 400): JobInfo =
    val j = Transactor(ds).read(JobRepo.get(id)).getOrElse(fail(s"no job $id"))
    if j.status == status then j
    else if left == 0 then fail(s"job $id is ${j.status}, not $status (${j.progress})")
    else { Thread.sleep(25); until(id, status, left - 1) }

  /** A job of six parts, resumable by parts: it skips a part it has done, reports after each. `hold` = the part that
    * waits for the test before it finishes.
    */
  final private class SixParts(hold: Int):
    val done = AtomicReference(Set.empty[Int])
    val ran = AtomicReference(Vector.empty[Int])
    val holding = CountDownLatch(1)
    val release = CountDownLatch(1)
    def body(progress: JobInfo.Progress => Unit): BackgroundJob.Outcome =
      (1 to 6).foreach { n =>
        if !done.get.contains(n) then
          if n == hold && release.getCount > 0 then { holding.countDown(); release.await(10, TimeUnit.SECONDS) }
          ran.updateAndGet(_ :+ n)
          done.updateAndGet(_ + n)
          progress(JobInfo.Progress(s"$n/6 parts"))
      }
      BackgroundJob.Outcome.done(BackgroundJob.Outcome.Summary("6/6 parts"))

  private def service(stops: JobStops) =
    JobControlService(ds, stops, (_, _) => Left(Api.Error(Problem.Unclassified(Legacy.Text("no agent runs here")))))

  dbTest("a paused job stops at a part boundary, keeps its progress, and resumed does only the parts it had not done"):
    val stops = JobStops()
    val parts = SixParts(hold = 3)
    val j = BackgroundJob
      .start(
        ds,
        Workers(WritePermit.Gate()),
        Job.Kind.applyUnsafe(ImportJobKinds.MediaImport.kind.value),
        None,
        BackgroundJob.Slot(),
        BackgroundJob.Work("a test job"),
        stops = Some(stops)
      )((_, progress) => parts.body(progress))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(parts.holding.await(10, TimeUnit.SECONDS), "part 3 started")
    assertEquals(service(stops).control(jobId(j), JobAction.Pause).map(_.status), Right("running"))
    parts.release.countDown()
    val paused = until(jobId(j), JobInfo.Status("paused"))
    assertEquals(paused.progress, Some("paused after: 3/6 parts"))
    assertEquals(parts.ran.get, Vector(1, 2, 3), "part 3 was finished, part 4 never started")
    assertEquals(service(stops).control(jobId(j), JobAction.Resume).map(_.id), Right(j.id))
    val done = until(jobId(j), JobInfo.Status("done"))
    assertEquals(done.progress, Some("6/6 parts"))
    assertEquals(parts.ran.get, Vector(1, 2, 3, 4, 5, 6), "no part ran twice")
    assert(service(stops).control(jobId(j), JobAction.Resume).isLeft, "a finished job is not resumed")

  dbTest("a cancelled job stops at a part boundary and is recorded as cancelled; it cannot be resumed"):
    val stops = JobStops()
    val parts = SixParts(hold = 2)
    val j = BackgroundJob
      .start(
        ds,
        Workers(WritePermit.Gate()),
        Job.Kind.applyUnsafe(BasemapJobKinds.BasemapFetch.kind.value),
        None,
        BackgroundJob.Slot(),
        BackgroundJob.Work("a test job"),
        stops = Some(stops)
      )((_, progress) => parts.body(progress))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(parts.holding.await(10, TimeUnit.SECONDS))
    assert(service(stops).control(jobId(j), JobAction.Cancel).isRight)
    parts.release.countDown()
    val cancelled = until(jobId(j), JobInfo.Status("cancelled"))
    assertEquals(
      (cancelled.problems.map(_.sentences), cancelled.progress),
      (Some(Vector("cancelled by a person")), Some("cancelled after: 2/6 parts"))
    )
    assertEquals(parts.ran.get, Vector(1, 2))
    assert(service(stops).control(jobId(j), JobAction.Resume).isLeft)

  dbTest("a job that runs as one piece is refused; a paused job is failed as interrupted by a restart"):
    val vac = Transactor(ds).transact(JobRepo.create(Job.Kind("vacuum"), None, None))
    assert(
      service(JobStops())
        .control(jobId(vac), JobAction.Pause)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("one piece"))
    )
    val p = Transactor(ds).transact(
      JobRepo.create(Job.Kind.applyUnsafe(ImportJobKinds.MediaImport.kind.value), None, None)
    )
    val _ = Transactor(ds).transact(
      JobRepo.transition(jobId(p), JobState.Queued, JobState.Running)
    )
    val _ = Transactor(ds).transact(
      JobRepo.transition(
        jobId(p),
        JobState.Running,
        JobState.Paused,
        Some(JobRow.Progress("paused after: 1/6"))
      )
    )
    assert(
      service(JobStops())
        .control(jobId(p), JobAction.Resume)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("restarted"))
    )
    val _ = Transactor(ds).transact(JobRepo.failInterrupted())
    assertEquals(
      Transactor(ds).read(JobRepo.get(jobId(p))).map(j => (j.status, j.problems)),
      Some(("failed", Some(Problem.Run.Interrupted.toNonEmpty)))
    )
