package com.histmersion.util.jobs

import java.time.Instant

/** The contract the runs' rows keep, on [[JobRuns.InMemory]] and on the database (a scratch database per test, gated on
  * HISTMERSION_TEST_DB_URL): a run is created queued, moves only from the state it is in, is annotated, is paused and
  * re-queued at a start, resumes when its time comes — each for its own kind only.
  */
abstract class JobRunsContract(name: String) extends com.histmersion.db.DbSuite:

  /** A new, empty store and the moment it calls now; None when this run has no such store. */
  protected def fresh(test: String): Option[(JobRuns, Instant)]

  /** What the store holds of run `id`: (status, error, progress, origin). */
  protected def row(runs: JobRuns, id: String): (String, Option[String], Option[String], Option[String])

  protected def rtest(title: String)(body: (JobRuns, Instant) => Unit): Unit =
    test(title)(fresh(title).foreach(body.tupled))

  private val Kind = "agent"

  private def run(target: String, origin: String = "person", kind: String = Kind) =
    RunRow(
      kind,
      "queued",
      "enrich-source",
      3,
      "source",
      target,
      Some(target),
      None,
      BigDecimal(1),
      1000L,
      "{}",
      None,
      None,
      None,
      origin,
      false,
      None,
      None
    )

  rtest("a run is created queued; it moves only from the state it is in, and only for its kind") { (r, _) =>
    val id = r.create(run("zzjobs_a"))
    assertEquals(r.pipelineVersion(id), Some(3))
    assertEquals(r.count(Kind, ("queued", "running")), 1)
    assert(!r.move(Kind, id, "running", "done", None, None, None), "not running")
    assert(!r.move("other", id, "queued", "running", None, None, None), "another kind")
    assert(r.move(Kind, id, "queued", "running", None, Some("step 1"), None))
    assertEquals(row(r, id), ("running", None, Some("step 1"), Some("person")))
    r.progress(Kind, id, "step 2")
    r.warn(Kind, id, "a warning")
    assertEquals(row(r, id)._3, Some("step 2"))
    assert(r.move(Kind, id, "running", "failed", Some("boom"), None, None))
    assertEquals(row(r, id)._1 -> row(r, id)._2, "failed" -> Some("boom"))
    assertEquals(r.count(Kind, ("queued", "running")), 0)
  }

  rtest("a start pauses the running runs (but those it keeps) and lists the queued ones, oldest first") { (r, _) =>
    val a = r.create(run("zzjobs_b1", "manager"))
    val b = r.create(run("zzjobs_b2"))
    val c = r.create(run("zzjobs_b3"))
    val kept = r.create(run("zzjobs_b4"))
    val _ = r.create(run("zzjobs_b5", kind = "other"))
    assert(r.move(Kind, a, "queued", "running", None, None, None))
    assert(r.move(Kind, kept, "queued", "running", None, None, None))
    val (paused, queued) = r.reconcile(Kind, "running", "paused", "interrupted", "queued", List(kept))
    assertEquals(paused, Vector(a -> Some("manager")))
    assertEquals(queued, Vector(b, c))
    assertEquals(row(r, a)._1 -> row(r, a)._2, "paused" -> Some("interrupted"))
    assertEquals(row(r, kept)._1, "running")
  }

  rtest("a paused run resumes when its time comes; one paused for a reason waits until it does") { (r, now) =>
    val soon = r.create(run("zzjobs_c1"))
    val later = r.create(run("zzjobs_c2"))
    val held = r.create(run("zzjobs_c3"))
    List(soon, later, held).foreach(id => assert(r.move(Kind, id, "queued", "running", None, None, None)))
    assert(r.move(Kind, soon, "running", "paused", Some("allowance: x"), None, Some(now.minusSeconds(60))))
    assert(r.move(Kind, later, "running", "paused", Some("allowance: y"), None, Some(now.plusSeconds(3600))))
    assert(r.move(Kind, held, "running", "paused", Some("plan-quota: z"), None, None))
    assertEquals(r.due(Kind, "paused", now), Vector(soon))
    assertEquals(r.pausedWith(Kind, "paused", "plan-quota:", now).map(_._1), Vector(held))
    assertEquals(r.pausedWith(Kind, "paused", "allowance:", now).map(_._1), Vector(later))
    assert(r.resumeAt(Kind, later, "paused", now.minusSeconds(1)))
    assertEquals(r.due(Kind, "paused", now).toSet, Set(soon, later))
    assert(!r.resumeAt(Kind, soon, "queued", now), "not in that state")
    r.budget(Kind, held, Some(BigDecimal(5)), None)
    r.heartbeat(Kind, held)
    assertEquals(row(r, held)._1, "paused")
  }
