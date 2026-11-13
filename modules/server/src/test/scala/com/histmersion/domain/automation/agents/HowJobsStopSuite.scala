package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import Halt.Stopped
import com.histmersion.api.automation.AgentRole
import com.histmersion.api.automation.Agents.PauseReason
import com.histmersion.testsupport.ComparedByValue.given

/** The stored `jobs.error` sentence: every sentence a writer produced, now or in the history, reads as a cause and
  * writes back as the same sentence.
  */
class HowJobsStopSuite extends munit.FunSuite:

  private def cause(p: Problem) = Stopped.from(p).cause

  /** What the engine wrote as `<reason>: <detail>` (the first engine's budget sentences too), a queued run paused
    * before it started, an upload no longer held, the drain's stop, a restart's `interrupted`.
    */
  private val written: List[Problem] = List(
    Problem.Unclassified(Legacy.Text("requested: paused on request")),
    Problem.Unclassified(Legacy.Text("requested: paused before it started")),
    Problem.Unclassified(Legacy.Text("interrupted: interrupted")),
    Problem.Unclassified(Legacy.Text("interrupted: the upload is no longer held by this process")),
    Problem.Unclassified(Legacy.Text("budget: the run spent $1.2000 of its $1.00 USD")),
    Problem.Unclassified(Legacy.Text("budget: the run used 600001 of its 600000 tokens")),
    Problem.Unclassified(Legacy.Text("budget: today's agent spend reached agents.budgets.daily-usd (5.0)")),
    Problem.Unclassified(Legacy.Text("budget: this month's agent spend reached agents.budgets.monthly-usd (50.0)")),
    Problem.Unclassified(Legacy.Text("budget: enrich: the run's budget is exhausted")),
    Problem.Unclassified(Legacy.Text("provider: enrich: HTTP 529 — fix the provider, then resume")),
    Problem.Unclassified(
      Legacy.Text(
        "allowance: all models for role extractor are below their allowance; next reset at 2026-10-05T14:00:00Z"
      )
    ),
    Problem.Unclassified(
      Legacy.Text("allowance: all models for role writer are below their allowance; no window frees by itself")
    ),
    Problem.Unclassified(
      Legacy.Text("plan-quota: all models for role reviewer are below their plan floor or allowance; the plan is held")
    ),
    Problem.Unclassified(
      Legacy.Text("plan-quota: enrich: all models for role extractor are below their plan floor or allowance; …")
    ),
    Problem.Unclassified(Legacy.Text("plan-quota: review: all models for role cold-reader are below their allowance")),
    Problem.Unclassified(Legacy.Text("stalled: no sign of life for 10 min at step 4 (stall 1 of 3)")),
    Problem.Unclassified(Legacy.Text("requested: ")),
    Problem.Unclassified(Legacy.Text("stalled: : ")),
    Problem.Unclassified(Legacy.Text("interrupted")),
    Problem.Unclassified(Legacy.Text("stopped while the autopilot drains: it resumes once the drain is lifted"))
  )

  /** Sentences no writer's pause produced: failures, and near misses of the pause form. */
  private val others: List[Problem] = List(
    Problem.Unclassified(Legacy.Text("cancelled")),
    Problem.Unclassified(
      Legacy.Text("stalled 3 times at step 2 (no sign of life for 10 min each time; agents.watchdog)")
    ),
    Problem.Unclassified(Legacy.Text("IllegalStateException: boom")),
    Problem.Unclassified(Legacy.Text("a restore started; stopped")),
    Problem.Unclassified(Legacy.Text("the acquisition toolset is not connected: nothing tried")),
    Problem.Unclassified(Legacy.Text("allowance:no space")),
    Problem.Unclassified(Legacy.Text("stalled:")),
    Problem.Unclassified(Legacy.Text("budget")),
    Problem.Unclassified(Legacy.Text(" interrupted")),
    Problem.Unclassified(Legacy.Text("interrupted ")),
    Problem.Unclassified(Legacy.Text("Interrupted")),
    Problem.Unclassified(Legacy.Text(""))
  )

  test("a stored sentence reads as a cause and writes back as the same sentence"):
    (written ++ others).foreach(p => assertEquals(Stopped.write(cause(p)), p))

  test("a pause is the reason and what follows it"):
    assertEquals(
      cause(Problem.Unclassified(Legacy.Text("allowance: all models for role writer are below their allowance"))),
      Stopped.Cause.Exhausted(
        PauseReason.Allowance,
        Stopped.Lead(""),
        AgentRole.Writer,
        Stopped.Rest("below their allowance")
      )
    )
    assert(
      cause(Problem.Unclassified(Legacy.Text("requested: paused before it started"))).pausedOn(PauseReason.Requested)
    )
    assert(!cause(Problem.Unclassified(Legacy.Text("plan-quota: x"))).pausedOn(PauseReason.Allowance))
    assertEquals(
      cause(Problem.Unclassified(Legacy.Text("interrupted: interrupted"))),
      Stopped.Cause.Paused(PauseReason.Interrupted, Problem.Unclassified(Legacy.Text("interrupted")))
    )
    assert(cause(Problem.Unclassified(Legacy.Text("stalled: "))).pausedOn(PauseReason.Stalled))

  test("every pause reason reads from what it writes"):
    PauseReason.values.foreach { r =>
      val c = Stopped.Cause.Paused(r, Problem.Unclassified(Legacy.Text("why")))
      assertEquals(Stopped.from(Stopped.write(c)).cause, c)
    }

  test("a restart's interruption and the drain's stop are what a later job takes up; nothing else is"):
    assertEquals(cause(Problem.Unclassified(Legacy.Text("interrupted"))), Stopped.Cause.Interrupted)
    assertEquals(
      cause(
        Problem.Unclassified(Legacy.Text("stopped while the autopilot drains: it resumes once the drain is lifted"))
      ),
      Stopped.Cause.Draining
    )
    assert(cause(Problem.Unclassified(Legacy.Text("interrupted"))).resumable)
    assert(
      cause(
        Problem.Unclassified(Legacy.Text("stopped while the autopilot drains: it resumes once the drain is lifted"))
      ).resumable
    )
    (written ++ others)
      .filterNot(p =>
        p == Problem.Unclassified(Legacy.Text("interrupted")) || p == Problem.Unclassified(
          Legacy.Text("stopped while the autopilot drains: it resumes once the drain is lifted")
        )
      )
      .foreach(p => assert(!cause(p).resumable, p.sentence))

  test("a sentence no writer produced is a failure, as written"):
    others.foreach(p => assertEquals(cause(p), Stopped.Cause.Failed(p)))

  test("a paused run's reason is the one its stored sentence writes; no sentence means a person paused it"):
    PauseReason.values.foreach { r =>
      assertEquals(
        Stopped.pausedFor(Some(Stopped.write(Stopped.Cause.Paused(r, Problem.Unclassified(Legacy.Text("why")))))),
        Some(r)
      )
    }
    assertEquals(Stopped.pausedFor(None), Some(PauseReason.Requested))
    assertEquals(
      Stopped.pausedFor(Some(Problem.Unclassified(Legacy.Text("interrupted")))),
      Some(PauseReason.Interrupted)
    )
    assertEquals(Stopped.pausedFor(Some(Problem.Unclassified(Legacy.Text("cancelled")))), None)

  test("the pattern a column is filtered by matches the sentences that read as that pause, and only those"):
    PauseReason.values.foreach { r =>
      val prefix = Stopped.pausedPattern(r).stripSuffix("%")
      val written = s"${Stopped.write(Stopped.Cause.Paused(r, Problem.Unclassified(Legacy.Text("why")))).sentence}"
      assert(written.startsWith(prefix), written)
      assertEquals(cause(Problem.Unclassified(Legacy.Text(s"${r.wire}:no space"))).pausedOn(r), false)
      assert(!"%_\\".exists(prefix.contains(_)), prefix)
    }

  test("the role an exhausted pause names is read from its sentence, with the step that put its lead in front"):
    assertEquals(
      cause(
        Problem.Unclassified(
          Legacy.Text("plan-quota: enrich: all models for role extractor are below their plan floor or allowance; …")
        )
      ),
      Stopped.Cause.Exhausted(
        PauseReason.PlanQuota,
        Stopped.Lead("enrich: "),
        AgentRole.Extractor,
        Stopped.Rest("below their plan floor or allowance; …")
      )
    )
    assert(
      !cause(Problem.Unclassified(Legacy.Text("plan-quota: all models for role nobody are below")))
        .isInstanceOf[Stopped.Cause.Exhausted]
    )
    assertEquals(
      cause(Problem.Unclassified(Legacy.Text("plan-quota: something else"))),
      Stopped.Cause.Paused(PauseReason.PlanQuota, Problem.Unclassified(Legacy.Text("something else")))
    )
