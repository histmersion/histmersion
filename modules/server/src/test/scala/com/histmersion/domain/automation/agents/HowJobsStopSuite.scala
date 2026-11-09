package com.histmersion
package domain.automation.agents

import com.histmersion.api.Problem
import Halt.Stopped
import com.histmersion.api.automation.Agents.PauseReason
import com.histmersion.testsupport.ComparedByValue.given

/** The stored `jobs.error` sentence: every sentence a writer produced, now or in the history, reads as a cause and writes
  * back as the same sentence.
  */
class HowJobsStopSuite extends munit.FunSuite:

  private def cause(p: Problem) = Stopped.from(p).cause

  /** What the engine wrote as `<reason>: <detail>` (the first engine's budget sentences too), a queued run paused before
    * it started, an upload no longer held, the drain's stop, a restart's `interrupted`.
    */
  private val written: List[Problem] = List(
    Problem("requested: paused on request"),
    Problem("requested: paused before it started"),
    Problem("interrupted: interrupted"),
    Problem("interrupted: the upload is no longer held by this process"),
    Problem("budget: the run spent $1.2000 of its $1.00 USD"),
    Problem("budget: the run used 600001 of its 600000 tokens"),
    Problem("budget: today's agent spend reached agents.budgets.daily-usd (5.0)"),
    Problem("budget: this month's agent spend reached agents.budgets.monthly-usd (50.0)"),
    Problem("budget: enrich: the run's budget is exhausted"),
    Problem("provider: enrich: HTTP 529 — fix the provider, then resume"),
    Problem("allowance: all models for role extractor are below their allowance; next reset at 2026-10-05T14:00:00Z"),
    Problem("allowance: all models for role writer are below their allowance; no window frees by itself"),
    Problem("plan-quota: all models for role reviewer are below their plan floor or allowance; the plan is held"),
    Problem("stalled: no sign of life for 10 min at step 4 (stall 1 of 3)"),
    Problem("requested: "),
    Problem("stalled: : "),
    Problem("interrupted"),
    Problem("stopped while the autopilot drains: it resumes once the drain is lifted")
  )

  /** Sentences no writer's pause produced: failures, and near misses of the pause form. */
  private val others: List[Problem] = List(
    Problem("cancelled"),
    Problem("stalled 3 times at step 2 (no sign of life for 10 min each time; agents.watchdog)"),
    Problem("IllegalStateException: boom"),
    Problem("a restore started; stopped"),
    Problem("the acquisition toolset is not connected: nothing tried"),
    Problem("allowance:no space"),
    Problem("stalled:"),
    Problem("budget"),
    Problem(" interrupted"),
    Problem("interrupted "),
    Problem("Interrupted"),
    Problem("")
  )

  test("a stored sentence reads as a cause and writes back as the same sentence"):
    (written ++ others).foreach(p => assertEquals(Stopped.write(cause(p)), p))

  test("a pause is the reason and what follows it"):
    assertEquals(
      cause(Problem("allowance: all models for role writer are below their allowance")),
      Stopped.Cause.Paused(PauseReason.Allowance, Problem("all models for role writer are below their allowance"))
    )
    assert(cause(Problem("requested: paused before it started")).pausedOn(PauseReason.Requested))
    assert(!cause(Problem("plan-quota: x")).pausedOn(PauseReason.Allowance))
    assertEquals(
      cause(Problem("interrupted: interrupted")),
      Stopped.Cause.Paused(PauseReason.Interrupted, Problem("interrupted"))
    )
    assert(cause(Problem("stalled: ")).pausedOn(PauseReason.Stalled))

  test("every pause reason reads from what it writes"):
    PauseReason.values.foreach { r =>
      val c = Stopped.Cause.Paused(r, Problem("why"))
      assertEquals(Stopped.from(Stopped.write(c)).cause, c)
    }

  test("a restart's interruption and the drain's stop are what a later job takes up; nothing else is"):
    assertEquals(cause(Problem("interrupted")), Stopped.Cause.Interrupted)
    assertEquals(
      cause(Problem("stopped while the autopilot drains: it resumes once the drain is lifted")),
      Stopped.Cause.Draining
    )
    assert(cause(Problem("interrupted")).resumable)
    assert(cause(Problem("stopped while the autopilot drains: it resumes once the drain is lifted")).resumable)
    (written ++ others)
      .filterNot(p => p == Problem("interrupted") || p == Problem("stopped while the autopilot drains: it resumes once the drain is lifted"))
      .foreach(p => assert(!cause(p).resumable, p.toString))

  test("a sentence no writer produced is a failure, as written"):
    others.foreach(p => assertEquals(cause(p), Stopped.Cause.Failed(p)))
