package com.histmersion
package util.jobs
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.Problem

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.automation.Run

import java.time.Instant
import com.histmersion.db.DbSuite
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The contract the runs' rows keep, on [[JobRuns.InMemory]] and on the database (a scratch database per test, gated on
  * HISTMERSION_TEST_DB_URL): a run is created queued, moves only from the state it is in, is annotated, is paused and
  * re-queued at a start, resumes when its time comes — each for its own kind only.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
abstract class JobRunsContract(name: String) extends DbSuite:

  /** A new, empty store and the moment it calls now; None when this run has no such store. */
  protected def fresh(test: String): Option[(JobRuns, Instant)]

  /** What the store holds of run `id`: (status, error, progress, origin). */
  protected def row(
      runs: JobRuns,
      id: Run.Id
  ): (RunRow.Status, Option[Problem], Option[JobRunState.Progress], Option[RunRow.Origin])

  protected def rtest(title: String)(body: (JobRuns, Instant) => Unit): Unit =
    test(title)(fresh(title).foreach(body.tupled))

  private val Kind = Job.Kind("agent")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def run(
      target: RunRow.Target,
      origin: RunRow.Origin = RunRow.Origin("person"),
      kind: RunRow.Kind = RunRow.Kind("agent")
  ) =
    RunRow(
      kind,
      RunRow.Status("queued"),
      RunRow.Pipeline("enrich-source"),
      RunRow.PipelineVersion(3),
      RunRow.TargetKind("source"),
      target,
      Some(RunRow.Citekey.applyUnsafe(target.value)),
      None,
      BigDecimal(1),
      RunRow.BudgetTokens(1000L),
      RunRow.Params("{}"),
      None,
      None,
      None,
      origin,
      RunRow.DryRun(false),
      None,
      None
    )

  rtest("a run is created queued; it moves only from the state it is in, and only for its kind") { (r, _) =>
    val id = r.create(run(RunRow.Target("zzjobs_a")))
    assertEquals(r.pipelineVersion(id), Some(3))
    assertEquals(r.count(Kind, (RunRow.Status("queued"), RunRow.Status("running"))), 1)
    assert(!r.move(Kind, id, RunRow.Status("running"), RunRow.Status("done"), None, None, None), "not running")
    assert(
      !r.move(Job.Kind("other"), id, RunRow.Status("queued"), RunRow.Status("running"), None, None, None),
      "another kind"
    )
    assert(
      r.move(Kind, id, RunRow.Status("queued"), RunRow.Status("running"), None, Some(JobRow.Progress("step 1")), None)
    )
    assertEquals(row(r, id), ("running", None, Some("step 1"), Some("person")))
    r.progress(Kind, id, JobRow.Progress("step 2"))
    r.warn(Kind, id, JobRepo.WarningText("a warning"))
    assertEquals(row(r, id)._3, Some("step 2"))
    assert(
      r.move(
        Kind,
        id,
        RunRow.Status("running"),
        RunRow.Status("failed"),
        Some(Problem.Stored.Unrecognised(Problem.Stored.Text("boom"))),
        None,
        None
      )
    )
    assertEquals(row(r, id)._1 -> row(r, id)._2.map(_.sentence), "failed" -> Some("boom"))
    assertEquals(r.count(Kind, (RunRow.Status("queued"), RunRow.Status("running"))), 0)
  }

  rtest("a start pauses the running runs (but those it keeps) and lists the queued ones, oldest first") { (r, _) =>
    val a = r.create(run(RunRow.Target("zzjobs_b1"), RunRow.Origin("manager")))
    val b = r.create(run(RunRow.Target("zzjobs_b2")))
    val c = r.create(run(RunRow.Target("zzjobs_b3")))
    val kept = r.create(run(RunRow.Target("zzjobs_b4")))
    val _ = r.create(run(RunRow.Target("zzjobs_b5"), kind = RunRow.Kind("other")))
    assert(r.move(Kind, a, RunRow.Status("queued"), RunRow.Status("running"), None, None, None))
    assert(r.move(Kind, kept, RunRow.Status("queued"), RunRow.Status("running"), None, None, None))
    val (paused, queued) = r.reconcile(
      Kind,
      RunRow.Status("running"),
      RunRow.Status("paused"),
      Problem.Run.Interrupted,
      RunRow.Status("queued"),
      List(kept)
    )
    assertEquals(paused, Vector(a -> Some("manager")))
    assertEquals(queued, Vector(b, c))
    assertEquals(row(r, a)._1 -> row(r, a)._2, "paused" -> Some(Problem.Run.Interrupted))
    assertEquals(row(r, kept)._1, "running")
  }

  rtest("a paused run resumes when its time comes; one paused for a reason waits until it does") { (r, now) =>
    val soon = r.create(run(RunRow.Target("zzjobs_c1")))
    val later = r.create(run(RunRow.Target("zzjobs_c2")))
    val held = r.create(run(RunRow.Target("zzjobs_c3")))
    List(soon, later, held).foreach(id =>
      assert(r.move(Kind, id, RunRow.Status("queued"), RunRow.Status("running"), None, None, None))
    )
    def pause(cause: Problem.Run.PauseCause, why: String): Problem =
      Problem.Run.Halted(cause, Problem.Stored.Unrecognised(Problem.Stored.Text(why)))
    assert(
      r.move(
        Kind,
        soon,
        RunRow.Status("running"),
        RunRow.Status("paused"),
        Some(pause(Problem.Run.PauseCause.Allowance, "x")),
        None,
        Some(now.minusSeconds(60))
      )
    )
    assert(
      r.move(
        Kind,
        later,
        RunRow.Status("running"),
        RunRow.Status("paused"),
        Some(pause(Problem.Run.PauseCause.Allowance, "y")),
        None,
        Some(now.plusSeconds(3600))
      )
    )
    assert(
      r.move(
        Kind,
        held,
        RunRow.Status("running"),
        RunRow.Status("paused"),
        Some(pause(Problem.Run.PauseCause.PlanQuota, "z")),
        None,
        None
      )
    )
    assertEquals(r.due(Kind, RunRow.Status("paused"), now), Vector(soon))
    assertEquals(r.pausedWaiting(Kind, RunRow.Status("paused"), now).map(_._1).toSet, Set(held, later))
    assertEquals(
      r.pausedWaiting(Kind, RunRow.Status("paused"), now).map(r => (r._1, r._4)).toSet,
      Set(
        (held, Some(pause(Problem.Run.PauseCause.PlanQuota, "z"))),
        (later, Some(pause(Problem.Run.PauseCause.Allowance, "y")))
      )
    )
    assertEquals(
      r.pausedWaiting(Kind, RunRow.Status("paused"), now)
        .map(r => (r._1, r._5.flatMap(_.instant).map(_.getEpochSecond)))
        .toSet,
      Set((held, None), (later, Some(now.plusSeconds(3600).getEpochSecond)))
    )
    assert(r.resumeAt(Kind, later, RunRow.Status("paused"), now.minusSeconds(1)))
    assertEquals(r.due(Kind, RunRow.Status("paused"), now).toSet, Set(soon, later))
    assert(!r.resumeAt(Kind, soon, RunRow.Status("queued"), now), "not in that state")
    r.budget(Kind, held, Some(BigDecimal(5)), None)
    r.heartbeat(Kind, held)
    assertEquals(row(r, held)._1, "paused")
  }
