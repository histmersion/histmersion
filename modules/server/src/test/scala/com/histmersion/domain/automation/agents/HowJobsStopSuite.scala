package com.histmersion
package domain.automation.agents
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Problem
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
    Problem.Stored.Unrecognised(Problem.Stored.Text("requested: paused on request")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("requested: paused before it started")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("interrupted: interrupted")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("interrupted: the upload is no longer held by this process")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("budget: the run spent $1.2000 of its $1.00 USD")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("budget: the run used 600001 of its 600000 tokens")),
    Problem.Stored.Unrecognised(
      Problem.Stored.Text("budget: today's agent spend reached agents.budgets.daily-usd (5.0)")
    ),
    Problem.Stored.Unrecognised(
      Problem.Stored.Text("budget: this month's agent spend reached agents.budgets.monthly-usd (50.0)")
    ),
    Problem.Stored.Unrecognised(Problem.Stored.Text("budget: enrich: the run's budget is exhausted")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("provider: enrich: HTTP 529 — fix the provider, then resume")),
    Problem.Stored.Unrecognised(
      Problem.Stored.Text(
        "allowance: all models for role extractor are below their allowance; next reset at 2026-10-05T14:00:00Z"
      )
    ),
    Problem.Stored.Unrecognised(
      Problem.Stored.Text("allowance: all models for role writer are below their allowance; no window frees by itself")
    ),
    Problem.Stored.Unrecognised(
      Problem.Stored.Text(
        "plan-quota: all models for role reviewer are below their plan floor or allowance; the plan is held"
      )
    ),
    Problem.Stored.Unrecognised(
      Problem.Stored.Text(
        "plan-quota: enrich: all models for role extractor are below their plan floor or allowance; …"
      )
    ),
    Problem.Stored.Unrecognised(
      Problem.Stored.Text("plan-quota: review: all models for role cold-reader are below their allowance")
    ),
    Problem.Stored.Unrecognised(Problem.Stored.Text("stalled: no sign of life for 10 min at step 4 (stall 1 of 3)")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("requested: ")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("stalled: : ")),
    Problem.Run.Interrupted,
    Problem.Stored.Unrecognised(
      Problem.Stored.Text("stopped while the autopilot drains: it resumes once the drain is lifted")
    )
  )

  /** Sentences no writer's pause produced: failures, and near misses of the pause form. */
  private val others: List[Problem] = List(
    Problem.Stored.Unrecognised(Problem.Stored.Text("cancelled")),
    Problem.Stored.Unrecognised(
      Problem.Stored.Text("stalled 3 times at step 2 (no sign of life for 10 min each time; agents.watchdog)")
    ),
    Problem.Stored.Unrecognised(Problem.Stored.Text("IllegalStateException: boom")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("a restore started; stopped")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("the acquisition toolset is not connected: nothing tried")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("allowance:no space")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("stalled:")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("budget")),
    Problem.Stored.Unrecognised(Problem.Stored.Text(" interrupted")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("interrupted ")),
    Problem.Stored.Unrecognised(Problem.Stored.Text("Interrupted")),
    Problem.Stored.Unrecognised(Problem.Stored.Text(""))
  )

  test("a stored sentence reads as a cause and writes back as the same sentence"):
    (written ++ others).foreach(p => assertEquals(Stopped.write(cause(p)), p))

  test("a pause is the reason and what follows it"):
    assertEquals(
      cause(
        Problem.Stored.Unrecognised(
          Problem.Stored.Text("allowance: all models for role writer are below their allowance")
        )
      ),
      Stopped.Cause.Exhausted(
        PauseReason.Allowance,
        Stopped.Lead(""),
        AgentRole.Writer,
        Stopped.Rest("below their allowance")
      )
    )
    assert(
      cause(Problem.Stored.Unrecognised(Problem.Stored.Text("requested: paused before it started")))
        .pausedOn(PauseReason.Requested)
    )
    assert(!cause(Problem.Stored.Unrecognised(Problem.Stored.Text("plan-quota: x"))).pausedOn(PauseReason.Allowance))
    assertEquals(
      cause(Problem.Stored.Unrecognised(Problem.Stored.Text("interrupted: interrupted"))),
      Stopped.Cause.Paused(PauseReason.Interrupted, Stopped.Detail("interrupted"))
    )
    assert(cause(Problem.Stored.Unrecognised(Problem.Stored.Text("stalled: "))).pausedOn(PauseReason.Stalled))

  test("every pause reason reads from what it writes"):
    PauseReason.values.foreach { r =>
      val c = Stopped.Cause.Paused(r, Stopped.Detail("why"))
      assertEquals(Stopped.from(Stopped.write(c)).cause, c)
    }

  test("a restart's interruption and the drain's stop are what a later job takes up; nothing else is"):
    assertEquals(cause(Problem.Stored.Unrecognised(Problem.Stored.Text("interrupted"))), Stopped.Cause.Interrupted)
    assertEquals(
      cause(
        Problem.Stored.Unrecognised(
          Problem.Stored.Text("stopped while the autopilot drains: it resumes once the drain is lifted")
        )
      ),
      Stopped.Cause.Draining
    )
    assert(cause(Problem.Stored.Unrecognised(Problem.Stored.Text("interrupted"))).resumable)
    assert(
      cause(
        Problem.Stored.Unrecognised(
          Problem.Stored.Text("stopped while the autopilot drains: it resumes once the drain is lifted")
        )
      ).resumable
    )
    (written ++ others)
      .filterNot(p =>
        p == Problem.Run.Interrupted || p == Problem.Stored.Unrecognised(
          Problem.Stored.Text("stopped while the autopilot drains: it resumes once the drain is lifted")
        )
      )
      .foreach(p => assert(!cause(p).resumable, p.sentence))

  test("a sentence no writer produced is a failure, as written"):
    others.foreach(p => assertEquals(cause(p), Stopped.Cause.Failed(p)))

  test("a paused run's reason is the one its stored sentence writes; no sentence means a person paused it"):
    PauseReason.values.foreach { r =>
      assertEquals(
        Stopped.pausedFor(Some(Stopped.write(Stopped.Cause.Paused(r, Stopped.Detail("why"))))),
        Some(r)
      )
    }
    assertEquals(Stopped.pausedFor(None), Some(PauseReason.Requested))
    assertEquals(
      Stopped.pausedFor(Some(Problem.Stored.Unrecognised(Problem.Stored.Text("interrupted")))),
      Some(PauseReason.Interrupted)
    )
    assertEquals(Stopped.pausedFor(Some(Problem.Stored.Unrecognised(Problem.Stored.Text("cancelled")))), None)

  test("the pattern a column is filtered by matches the sentences that read as that pause, and only those"):
    PauseReason.values.foreach { r =>
      val prefix = Stopped.pausedPattern(r).stripSuffix("%")
      val written = s"${Stopped.write(Stopped.Cause.Paused(r, Stopped.Detail("why"))).sentence}"
      assert(written.startsWith(prefix), written)
      assertEquals(cause(Problem.Stored.Unrecognised(Problem.Stored.Text(s"${r.wire}:no space"))).pausedOn(r), false)
      assert(!"%_\\".exists(prefix.contains(_)), prefix)
    }

  test("a chain out of headroom, typed, says its sentence as the pause stores it: the role is read back from it"):
    val exhausted = Problem.Run.Exhausted(
      Problem.Run.Role("extractor"),
      Problem.Run.Floor.Allowance,
      Problem.Run.Frees.Never,
      Vector(
        Problem.Run.PassedOver(
          Problem.Run.Model("local:a"),
          NonEmptyVector.of(Problem.Run.Cooling(Problem.EpochMs(1762732800000L)))
        )
      )
    )
    val stored = Stopped.write(
      Stopped.Cause.Paused(PauseReason.Allowance, Stopped.Detail(s"enrich: ${exhausted.sentence}"))
    )
    assertEquals(
      cause(stored),
      Stopped.Cause.Exhausted(
        PauseReason.Allowance,
        Stopped.Lead("enrich: "),
        AgentRole.Extractor,
        Stopped.Rest(
          "below their allowance; no window frees by itself — raise or reset the allowance (local:a: its plan " +
            "reported its usage limit; skipped until 2025-11-10T00:00:00Z)"
        )
      )
    )

  test("the role an exhausted pause names is read from its sentence, with the step that put its lead in front"):
    assertEquals(
      cause(
        Problem.Stored.Unrecognised(
          Problem.Stored.Text(
            "plan-quota: enrich: all models for role extractor are below their plan floor or allowance; …"
          )
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
      !cause(Problem.Stored.Unrecognised(Problem.Stored.Text("plan-quota: all models for role nobody are below")))
        .isInstanceOf[Stopped.Cause.Exhausted]
    )
    assertEquals(
      cause(Problem.Stored.Unrecognised(Problem.Stored.Text("plan-quota: something else"))),
      Stopped.Cause.Paused(PauseReason.PlanQuota, Stopped.Detail("something else"))
    )
