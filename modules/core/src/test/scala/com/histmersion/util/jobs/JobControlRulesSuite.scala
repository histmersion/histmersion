package com.histmersion.util.jobs

import com.histmersion.api.{JobInfo, JobKindsAdapter}
import com.histmersion.values.JobKind

class JobControlRulesSuite extends munit.FunSuite:

  private def job(kind: JobKind, status: String) = JobInfo("j1", kind.value, status, None)

  test("an agent run takes its own control, whatever it is asked"):
    JobAction.values.foreach(a =>
      assertEquals(
        JobControlRules.decide(job(JobKindsAdapter.Agent, "running"), a, resumable = false),
        Right(JobControlStep.Run(a))
      )
    )

  test("a job that runs as one piece is refused"):
    assert(
      JobControlRules
        .decide(job(JobKindsAdapter.Ingest, "running"), JobAction.Pause, resumable = false)
        .left
        .exists(_.contains("one piece"))
    )
    assert(JobControlRules.decide(job(JobKindsAdapter.Vacuum, "running"), JobAction.Cancel, resumable = false).isLeft)

  test("the long jobs: pause and cancel are asked of a running one; a paused or queued one is cancelled at once"):
    List(
      JobKindsAdapter.MediaImport,
      JobKindsAdapter.EventsImport,
      JobKindsAdapter.BasemapFetch,
      JobKindsAdapter.AcquireRecheck,
      JobKindsAdapter.Init
    )
      .foreach { k =>
        assertEquals(
          JobControlRules.decide(job(k, "running"), JobAction.Pause, resumable = false),
          Right(JobControlStep.RequestStop(JobStop.Pause)),
          k.value
        )
        assertEquals(
          JobControlRules.decide(job(k, "running"), JobAction.Cancel, resumable = false),
          Right(JobControlStep.RequestStop(JobStop.Cancel)),
          k.value
        )
        assertEquals(
          JobControlRules.decide(job(k, "paused"), JobAction.Cancel, resumable = true),
          Right(JobControlStep.CancelNow)
        )
        assertEquals(
          JobControlRules.decide(job(k, "queued"), JobAction.Cancel, resumable = false),
          Right(JobControlStep.CancelNow)
        )
      }

  test("resume: only a paused job this server can go on with"):
    val paused = job(JobKindsAdapter.MediaImport, "paused")
    assertEquals(JobControlRules.decide(paused, JobAction.Resume, resumable = true), Right(JobControlStep.Resume))
    assert(JobControlRules.decide(paused, JobAction.Resume, resumable = false).left.exists(_.contains("restarted")))
    assert(
      JobControlRules
        .decide(job(JobKindsAdapter.MediaImport, "running"), JobAction.Resume, resumable = true)
        .left
        .exists(_.contains("not paused"))
    )

  test("an ended job is left as it ended; a paused one is not paused twice; a queued one is cancelled, not paused"):
    List("done", "failed", "cancelled").foreach(s =>
      JobAction.values.foreach(a =>
        assert(
          JobControlRules
            .decide(job(JobKindsAdapter.MediaImport, s), a, resumable = true)
            .left
            .exists(_.contains("ended")),
          s"$s $a"
        )
      )
    )
    assert(
      JobControlRules
        .decide(job(JobKindsAdapter.MediaImport, "paused"), JobAction.Pause, resumable = true)
        .left
        .exists(_.contains("already"))
    )
    assert(
      JobControlRules
        .decide(job(JobKindsAdapter.MediaImport, "queued"), JobAction.Pause, resumable = true)
        .left
        .exists(_.contains("cancel it"))
    )

  test("a stopped job keeps what it had done in its progress line"):
    assertEquals(JobControlRules.stoppedLine(JobStop.Pause, Some("12/40 places")), "paused after: 12/40 places")
    assertEquals(JobControlRules.stoppedLine(JobStop.Cancel, None), "cancelled")
    assertEquals(JobAction.parse(" Pause "), Some(JobAction.Pause))
    assertEquals(JobAction.parse("stop"), None)
