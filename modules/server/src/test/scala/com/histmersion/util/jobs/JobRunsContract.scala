package com.histmersion.util.jobs

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.automation.Run

import java.time.Instant

/** The contract the runs' rows keep, on [[JobRuns.InMemory]] and on the database (a scratch database per test, gated on
  * HISTMERSION_TEST_DB_URL): a run is created queued, moves only from the state it is in, is annotated, is paused and
  * re-queued at a start, resumes when its time comes — each for its own kind only.
  */
abstract class JobRunsContract(name: String) extends com.histmersion.db.DbSuite:

  /** A new, empty store and the moment it calls now; None when this run has no such store. */
  protected def fresh(test: String): Option[(JobRuns, Instant)]

  /** What the store holds of run `id`: (status, error, progress, origin). */
  protected def row(
      runs: JobRuns,
      id: Run.Id
  ): (RunRow.Status, Option[JobRunState.Error], Option[JobRunState.Progress], Option[RunRow.Origin])

  protected def rtest(title: String)(body: (JobRuns, Instant) => Unit): Unit =
    test(title)(fresh(title).foreach(body.tupled))

  private val Kind = Job.Kind("agent")

  private def run(
      target: RunRow.Target,
      origin: RunRow.Origin = RunRow.Origin("person"),
      kind: RunRow.Kind = RunRow.Kind("agent")
  ) =
    RunRow(
      kind,
      com.histmersion.util.jobs.RunRow.Status("queued"),
      com.histmersion.util.jobs.RunRow.Pipeline("enrich-source"),
      com.histmersion.util.jobs.RunRow.PipelineVersion(3),
      com.histmersion.util.jobs.RunRow.TargetKind("source"),
      target,
      Some(RunRow.Citekey.applyUnsafe(target.value)),
      None,
      BigDecimal(1),
      com.histmersion.util.jobs.RunRow.BudgetTokens(1000L),
      com.histmersion.util.jobs.RunRow.Params("{}"),
      None,
      None,
      None,
      origin,
      com.histmersion.util.jobs.RunRow.DryRun(false),
      None,
      None
    )

  rtest("a run is created queued; it moves only from the state it is in, and only for its kind") { (r, _) =>
    val id = r.create(run(RunRow.Target("zzjobs_a")))
    assertEquals(r.pipelineVersion(id), Some(3))
    assertEquals(r.count(Kind, (com.histmersion.util.jobs.RunRow.Status("queued"), com.histmersion.util.jobs.RunRow.Status("running"))), 1)
    assert(!r.move(Kind, id, com.histmersion.util.jobs.RunRow.Status("running"), com.histmersion.util.jobs.RunRow.Status("done"), None, None, None), "not running")
    assert(!r.move(com.histmersion.util.jobs.Job.Kind("other"), id, com.histmersion.util.jobs.RunRow.Status("queued"), com.histmersion.util.jobs.RunRow.Status("running"), None, None, None), "another kind")
    assert(r.move(Kind, id, com.histmersion.util.jobs.RunRow.Status("queued"), com.histmersion.util.jobs.RunRow.Status("running"), None, Some(com.histmersion.util.jobs.JobRow.Progress("step 1")), None))
    assertEquals(row(r, id), ("running", None, Some("step 1"), Some("person")))
    r.progress(Kind, id, com.histmersion.util.jobs.JobRow.Progress("step 2"))
    r.warn(Kind, id, com.histmersion.util.jobs.JobRepo.WarningText("a warning"))
    assertEquals(row(r, id)._3, Some("step 2"))
    assert(r.move(Kind, id, com.histmersion.util.jobs.RunRow.Status("running"), com.histmersion.util.jobs.RunRow.Status("failed"), Some(com.histmersion.util.jobs.JobRow.Error("boom")), None, None))
    assertEquals(row(r, id)._1 -> row(r, id)._2, "failed" -> Some("boom"))
    assertEquals(r.count(Kind, (com.histmersion.util.jobs.RunRow.Status("queued"), com.histmersion.util.jobs.RunRow.Status("running"))), 0)
  }

  rtest("a start pauses the running runs (but those it keeps) and lists the queued ones, oldest first") { (r, _) =>
    val a = r.create(run(RunRow.Target("zzjobs_b1"), RunRow.Origin("manager")))
    val b = r.create(run(RunRow.Target("zzjobs_b2")))
    val c = r.create(run(RunRow.Target("zzjobs_b3")))
    val kept = r.create(run(RunRow.Target("zzjobs_b4")))
    val _ = r.create(run(RunRow.Target("zzjobs_b5"), kind = RunRow.Kind("other")))
    assert(r.move(Kind, a, com.histmersion.util.jobs.RunRow.Status("queued"), com.histmersion.util.jobs.RunRow.Status("running"), None, None, None))
    assert(r.move(Kind, kept, com.histmersion.util.jobs.RunRow.Status("queued"), com.histmersion.util.jobs.RunRow.Status("running"), None, None, None))
    val (paused, queued) = r.reconcile(Kind, com.histmersion.util.jobs.RunRow.Status("running"), com.histmersion.util.jobs.RunRow.Status("paused"), com.histmersion.util.jobs.JobRow.Error("interrupted"), com.histmersion.util.jobs.RunRow.Status("queued"), List(kept))
    assertEquals(paused, Vector(a -> Some("manager")))
    assertEquals(queued, Vector(b, c))
    assertEquals(row(r, a)._1 -> row(r, a)._2, "paused" -> Some("interrupted"))
    assertEquals(row(r, kept)._1, "running")
  }

  rtest("a paused run resumes when its time comes; one paused for a reason waits until it does") { (r, now) =>
    val soon = r.create(run(RunRow.Target("zzjobs_c1")))
    val later = r.create(run(RunRow.Target("zzjobs_c2")))
    val held = r.create(run(RunRow.Target("zzjobs_c3")))
    List(soon, later, held).foreach(id => assert(r.move(Kind, id, com.histmersion.util.jobs.RunRow.Status("queued"), com.histmersion.util.jobs.RunRow.Status("running"), None, None, None)))
    assert(r.move(Kind, soon, com.histmersion.util.jobs.RunRow.Status("running"), com.histmersion.util.jobs.RunRow.Status("paused"), Some(com.histmersion.util.jobs.JobRow.Error("allowance: x")), None, Some(now.minusSeconds(60))))
    assert(r.move(Kind, later, com.histmersion.util.jobs.RunRow.Status("running"), com.histmersion.util.jobs.RunRow.Status("paused"), Some(com.histmersion.util.jobs.JobRow.Error("allowance: y")), None, Some(now.plusSeconds(3600))))
    assert(r.move(Kind, held, com.histmersion.util.jobs.RunRow.Status("running"), com.histmersion.util.jobs.RunRow.Status("paused"), Some(com.histmersion.util.jobs.JobRow.Error("plan-quota: z")), None, None))
    assertEquals(r.due(Kind, com.histmersion.util.jobs.RunRow.Status("paused"), now), Vector(soon))
    assertEquals(r.pausedWith(Kind, com.histmersion.util.jobs.RunRow.Status("paused"), com.histmersion.util.jobs.JobRuns.Prefix("plan-quota:"), now).map(_._1), Vector(held))
    assertEquals(r.pausedWith(Kind, com.histmersion.util.jobs.RunRow.Status("paused"), com.histmersion.util.jobs.JobRuns.Prefix("allowance:"), now).map(_._1), Vector(later))
    assert(r.resumeAt(Kind, later, com.histmersion.util.jobs.RunRow.Status("paused"), now.minusSeconds(1)))
    assertEquals(r.due(Kind, com.histmersion.util.jobs.RunRow.Status("paused"), now).toSet, Set(soon, later))
    assert(!r.resumeAt(Kind, soon, com.histmersion.util.jobs.RunRow.Status("queued"), now), "not in that state")
    r.budget(Kind, held, Some(BigDecimal(5)), None)
    r.heartbeat(Kind, held)
    assertEquals(row(r, held)._1, "paused")
  }
