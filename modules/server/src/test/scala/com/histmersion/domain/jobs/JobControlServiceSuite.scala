package com.histmersion.domain.jobs

import com.histmersion.api.{ApiError, JobInfo, JobKind}
import com.histmersion.config.AppConfig
import com.histmersion.db.{JobRepo, Migrate}
import com.histmersion.server.BackgroundJob

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

/** Pause, resume and cancel: the in-memory service, then a long background job on a test database (gated on
  * HISTMERSION_TEST_DB_URL) that stops at a part boundary and, resumed, does only the parts it had not done.
  */
class JobControlServiceSuite extends com.histmersion.db.DbSuite:

  private def job(id: String, kind: JobKind, status: String) =
    JobInfo(id, kind.wire, status, None, progress = Some("2/6"))

  test("in memory: a pause is asked of a running job, which stops at its next part and resumes later"):
    val stops = JobStops()
    val mem = JobControlService.InMemory(AtomicReference(Map.empty), stops)
    mem.put(job("m1", JobKind.MediaImport, "running"))
    assertEquals(mem.control("m1", JobAction.Pause).map(_.status), Right("running"), "it stops after the part it is on")
    assertEquals(stops.requestedFor("m1"), Some(JobStop.Pause))
    var resumed = 0
    mem.stopped("m1", Some("3/6"), () => { resumed += 1; Right(()) })
    assertEquals(mem.get("m1").map(j => (j.status, j.progress)), Some(("paused", Some("paused after: 3/6"))))
    assertEquals(mem.control("m1", JobAction.Resume).map(_.status), Right("running"))
    assertEquals(resumed, 1)
    assert(mem.control("m1", JobAction.Resume).left.exists(_.code.contains("conflict")), "a running job is not resumed")

  test("in memory: cancel — a paused job at once, a running one at its next part; an ended one is refused"):
    val stops = JobStops()
    val mem = JobControlService.InMemory(AtomicReference(Map.empty), stops)
    mem.put(job("p1", JobKind.BasemapFetch, "paused"))
    assertEquals(
      mem.control("p1", JobAction.Cancel).map(j => (j.status, j.error)),
      Right(("cancelled", Some("cancelled by a person")))
    )
    mem.put(job("r1", JobKind.AcquireRecheck, "running"))
    assertEquals(mem.control("r1", JobAction.Cancel).map(_.status), Right("running"))
    mem.stopped("r1", Some("5 of 9 items"), () => Right(()))
    assertEquals(mem.get("r1").map(_.status), Some("cancelled"))
    assert(mem.control("r1", JobAction.Pause).left.exists(_.message.contains("ended")))
    assertEquals(
      mem.control("nope", JobAction.Pause).left.map(_.code),
      Left(Some(com.histmersion.api.Write.CodeNotFound))
    )

  test("in memory: an agent run takes its own control"):
    val asked = AtomicReference(List.empty[(String, JobAction)])
    val mem = JobControlService.InMemory(
      AtomicReference(Map.empty),
      JobStops(),
      (id, a) => { asked.updateAndGet((id, a) :: _); Right(()) }
    )
    mem.put(job("a1", JobKind.Agent, "running"))
    assert(mem.control("a1", JobAction.Cancel).isRight)
    assertEquals(asked.get, List(("a1", JobAction.Cancel)))

  test("the controller: an unknown action is a 400 naming the three"):
    val c = JobControlControllers(JobControlService.InMemory(AtomicReference(Map.empty), JobStops()))
    assert(c.control("x", "stop").left.exists(_.message.contains("pause, resume, cancel")))

  // ── a real job on a test database ────────────────────────────────────────────────────────────
  private val testDb = com.histmersion.db.TestDb.url
  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d); d

  private def dbTest(name: String)(body: => Unit): Unit =
    test(name)(if testDb.isEmpty then assume(false, "HISTMERSION_TEST_DB_URL is unset") else body)

  private def until(id: String, status: String, left: Int = 400): JobInfo =
    val j = JobRepo.get(ds, id).getOrElse(fail(s"no job $id"))
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
    def body(progress: String => Unit): BackgroundJob.Outcome =
      (1 to 6).foreach { n =>
        if !done.get.contains(n) then
          if n == hold && release.getCount > 0 then { holding.countDown(); release.await(10, TimeUnit.SECONDS) }
          ran.updateAndGet(_ :+ n)
          done.updateAndGet(_ + n)
          progress(s"$n/6 parts")
      }
      BackgroundJob.Outcome.done("6/6 parts")

  private def service(stops: JobStops) =
    JobControlService(ds, stops, (_, _) => Left(ApiError("no agent runs here")))

  dbTest("a paused job stops at a part boundary, keeps its progress, and resumed does only the parts it had not done"):
    val stops = JobStops()
    val parts = SixParts(hold = 3)
    val j = BackgroundJob
      .start(ds, JobKind.MediaImport.wire, None, BackgroundJob.Slot(), "a test job", stops = Some(stops))(
        (_, progress) => parts.body(progress)
      )
      .fold(e => fail(e.message), identity)
    assert(parts.holding.await(10, TimeUnit.SECONDS), "part 3 started")
    assertEquals(service(stops).control(j.id, JobAction.Pause).map(_.status), Right("running"))
    parts.release.countDown()
    val paused = until(j.id, "paused")
    assertEquals(paused.progress, Some("paused after: 3/6 parts"))
    assertEquals(parts.ran.get, Vector(1, 2, 3), "part 3 was finished, part 4 never started")
    assertEquals(service(stops).control(j.id, JobAction.Resume).map(_.id), Right(j.id))
    val done = until(j.id, "done")
    assertEquals(done.progress, Some("6/6 parts"))
    assertEquals(parts.ran.get, Vector(1, 2, 3, 4, 5, 6), "no part ran twice")
    assert(service(stops).control(j.id, JobAction.Resume).isLeft, "a finished job is not resumed")

  dbTest("a cancelled job stops at a part boundary and is recorded as cancelled; it cannot be resumed"):
    val stops = JobStops()
    val parts = SixParts(hold = 2)
    val j = BackgroundJob
      .start(ds, JobKind.BasemapFetch.wire, None, BackgroundJob.Slot(), "a test job", stops = Some(stops))(
        (_, progress) => parts.body(progress)
      )
      .fold(e => fail(e.message), identity)
    assert(parts.holding.await(10, TimeUnit.SECONDS))
    assert(service(stops).control(j.id, JobAction.Cancel).isRight)
    parts.release.countDown()
    val cancelled = until(j.id, "cancelled")
    assertEquals(
      (cancelled.error, cancelled.progress),
      (Some("cancelled by a person"), Some("cancelled after: 2/6 parts"))
    )
    assertEquals(parts.ran.get, Vector(1, 2))
    assert(service(stops).control(j.id, JobAction.Resume).isLeft)

  dbTest("a job that runs as one piece is refused; a paused job is failed as interrupted by a restart"):
    val vac = JobRepo.create(ds, "vacuum", None, None)
    assert(service(JobStops()).control(vac.id, JobAction.Pause).left.exists(_.message.contains("one piece")))
    val p = JobRepo.create(ds, JobKind.MediaImport.wire, None, None)
    val _ = JobRepo.transition(ds, p.id, com.histmersion.api.JobState.Queued, com.histmersion.api.JobState.Running)
    val _ = JobRepo.transition(
      ds,
      p.id,
      com.histmersion.api.JobState.Running,
      com.histmersion.api.JobState.Paused,
      Some("paused after: 1/6")
    )
    assert(service(JobStops()).control(p.id, JobAction.Resume).left.exists(_.message.contains("restarted")))
    val _ = JobRepo.failInterrupted(ds)
    assertEquals(JobRepo.get(ds, p.id).map(j => (j.status, j.error)), Some(("failed", Some("interrupted"))))
