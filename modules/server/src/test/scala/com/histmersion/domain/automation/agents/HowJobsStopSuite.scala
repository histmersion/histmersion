package com.histmersion
package domain.automation.agents
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.json.RawJson.*
import com.histmersion.api.problem.StoredShapes
import com.histmersion.api.problem.Answer.entry
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.jsoniterjson.Json

import Halt.Stopped
import com.histmersion.api.automation.AgentRole
import com.histmersion.api.automation.Agents.PauseReason
import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.lint.{tolerated, LintRule}

/** How a job stopped, as the problem its `jobs.error` keeps: a pause (its reason and what it stopped on), a restart's
  * cut, the drain's stop, or a failure. What the engine writes now is typed; what a master version wrote as a sentence
  * reads into the same cases ([[StoredShapes]]).
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class HowJobsStopSuite extends munit.FunSuite:

  private def text(t: String): Problem = StoredShapes.read(t)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def unrecognised(t: String): Problem = Problem.Stored.Unrecognised(Problem.Stored.Text(t))

  /** What the master engine wrote as `<reason>: <detail>`, a queued run paused before it started, an upload no longer
    * held, the drain's stop, a restart's `interrupted`: each with the cause it reads as.
    */
  private val masterPauses: List[(String, Stopped)] = List(
    "requested: paused on request" ->
      Stopped.Paused(PauseReason.Requested, Problem.Run.Paused(Problem.Run.PauseReason.Requested, None)),
    "requested: paused before it started" -> Stopped.Paused(PauseReason.Requested, Problem.Run.Unstarted),
    "interrupted: interrupted" -> Stopped.Paused(PauseReason.Interrupted, Problem.Run.Interrupted),
    "interrupted: the upload is no longer held by this process" ->
      Stopped.Paused(PauseReason.Interrupted, Problem.Run.UploadUnheld),
    "budget: the run spent $1.2000 of its $1.00 USD" ->
      Stopped.Paused(PauseReason.Budget, unrecognised("the run spent $1.2000 of its $1.00 USD")),
    "budget: enrich: the run's budget is exhausted" ->
      Stopped.Paused(PauseReason.Budget, unrecognised("enrich: the run's budget is exhausted")),
    "provider: enrich: HTTP 529 — fix the provider, then resume" ->
      Stopped.Paused(PauseReason.Provider, unrecognised("enrich: HTTP 529 — fix the provider, then resume")),
    "allowance: all models for role writer are below their allowance; no window frees by itself" ->
      Stopped.Paused(
        PauseReason.Allowance,
        unrecognised("all models for role writer are below their allowance; no window frees by itself")
      ),
    "plan-quota: enrich: all models for role extractor are below their plan floor or allowance; …" ->
      Stopped.Paused(
        PauseReason.PlanQuota,
        unrecognised("enrich: all models for role extractor are below their plan floor or allowance; …")
      ),
    "stalled: no sign of life for 10 min at step 4 (stall 1 of 3)" ->
      Stopped.Paused(PauseReason.Stalled, unrecognised("no sign of life for 10 min at step 4 (stall 1 of 3)")),
    "requested: " -> Stopped.Paused(PauseReason.Requested, unrecognised("")),
    "stalled: : " -> Stopped.Paused(PauseReason.Stalled, unrecognised(": ")),
    "interrupted" -> Stopped.Interrupted,
    "stopped while the autopilot drains: it resumes once the drain is lifted" -> Stopped.Draining
  )

  /** Texts no master pause produced: failures, and near misses of the pause form. */
  private val others: List[String] = List(
    "cancelled",
    "stalled 3 times at step 2 (no sign of life for 10 min each time; agents.watchdog)",
    "IllegalStateException: boom",
    "a restore started; stopped",
    "the acquisition toolset is not connected: nothing tried",
    "allowance:no space",
    "stalled:",
    "budget",
    " interrupted",
    "interrupted ",
    "Interrupted",
    ""
  )

  test("a master pause reads as its cause, and the cause writes the problem it was read from"):
    masterPauses.foreach { (t, c) =>
      assertEquals(Stopped.of(text(t)), c, t)
      assertEquals(c.problem, text(t), t)
    }

  test("a master text no pause produced is a failure, its text kept whole"):
    others.foreach(t => assertEquals(Stopped.of(text(t)), Stopped.Failed(NonEmptyVector.of(unrecognised(t)))))

  test("every pause reason reads from what it writes, with the problem it stopped on"):
    val why: Problem = Problem.Run.AtStep(Problem.Run.Step("enrich"), Problem.Run.Failure.BudgetSpent)
    PauseReason.values.foreach { r =>
      val c = Stopped.Paused(r, why)
      assertEquals(Stopped.of(c.problem), c)
      assertEquals(c.problem.sentence, s"${r.wire}: ${why.sentence}", "the words a pause always said")
      PauseReason.values.foreach(o => assertEquals(Stopped.of(c.problem).pausedOn(o), o == r))
    }

  test("a restart's interruption and the drain's stop are what a later job takes up; nothing else is"):
    assert(Stopped.of(Problem.Run.Interrupted).resumable)
    assert(Stopped.of(Problem.Run.Draining(None)).resumable)
    assert(Stopped.of(Problem.Run.Draining(Some(Problem.EpochMs(0L)))).resumable)
    masterPauses
      .filterNot((_, c) =>
        c match
          case Stopped.Interrupted | Stopped.Draining => true
          case _                                      => false
      )
      .foreach((t, _) => assert(!Stopped.of(text(t)).resumable, t))
    others.foreach(t => assert(!Stopped.of(text(t)).resumable, t))

  test("a job's several problems are a failure, every one kept"):
    val two = NonEmptyVector.of[Problem](Problem.Run.Interrupted, unrecognised("boom"))
    assertEquals(Stopped.of(two), Stopped.Failed(two))
    assertEquals(Stopped.of(NonEmptyVector.of[Problem](Problem.Run.Interrupted)), Stopped.Interrupted)
    assertEquals(Problems.asOne(two), Problem.Run.Failed(two))
    assertEquals(Problems.asOne(NonEmptyVector.of[Problem](Problem.Run.Interrupted)), Problem.Run.Interrupted)
    assertEquals(Stopped.Failed(two).problem, Problem.Run.Failed(two), "a run shows every one")

  test("a paused run's reason is the one its stored problem names; none means a person paused it"):
    PauseReason.values.foreach { r =>
      assertEquals(Stopped.pausedFor(Some(Stopped.Paused(r, Problem.Run.Unstarted).problem)), Some(r))
    }
    assertEquals(Stopped.pausedFor(None), Some(PauseReason.Requested))
    assertEquals(Stopped.pausedFor(Some(text("interrupted"))), Some(PauseReason.Interrupted))
    assertEquals(Stopped.pausedFor(Some(text("cancelled"))), None)

  test("the JSON a column is filtered by is contained in a pause for that reason's entry, and in no other's"):
    def contains(whole: Json, part: Json): Boolean =
      (whole.asObject, part.asObject, whole.asArray, part.asArray) match
        case (Some(w), Some(p), _, _) => p.toList.forall((k, v) => w(k).exists(contains(_, v)))
        case (_, _, Some(w), Some(p)) => p.forall(x => w.exists(contains(_, x)))
        case _                        => whole.noSpaces == part.noSpaces
    PauseReason.values.foreach { r =>
      val pattern = Stopped.pausedMatch(r)
      PauseReason.values.foreach { o =>
        val row = Json.arr(Stopped.Paused(o, Problem.Run.Unstarted).problem.entry.asJson)
        assertEquals(contains(row, pattern), o == r, s"${r.wire} in a ${o.wire} pause")
      }
      assert(!contains(Json.arr(unrecognised(s"${r.wire}: x").entry.asJson), pattern))
    }

  test("a chain out of headroom names its role: typed, inside the step it stopped at, or in a master sentence"):
    val exhausted = Problem.Run.NoModel(
      Problem.Run.Role("extractor"),
      Problem.Run.Shortfall.Exhausted(Problem.Run.Floor.Allowance, Problem.Run.Frees.Never),
      Vector(
        Problem.Run.PassedOver(
          Problem.Run.Model("local:a"),
          NonEmptyVector.of(Problem.Run.Skipped(Problem.Run.Skip.Cooling(Problem.EpochMs(1762732800000L))))
        )
      )
    )
    val sentence =
      "all models for role extractor are below their allowance; no window frees by itself — raise or reset the allowance (local:a: its plan reported its usage limit; skipped until 2025-11-10T00:00:00Z)"
    assertEquals(exhausted.sentence, sentence)
    // what the engine stores for the pause RunCtx halts with: the step's AtStep, typed, whole
    val atStep =
      Problem.Run.AtStep(Problem.Run.Step("enrich"), Problem.Run.Failure.Failed(NonEmptyVector.of(exhausted)))
    val stored = Stopped.Paused(PauseReason.Allowance, atStep).problem
    assertEquals(stored.sentence, s"allowance: enrich: $sentence", "the words the pause said before it was typed")
    assertEquals(ModelPicker.pausedRole(stored), Some(AgentRole.Extractor))
    assertEquals(
      ModelPicker.pausedRole(Stopped.Paused(PauseReason.Allowance, exhausted).problem),
      Some(AgentRole.Extractor)
    )
    assertEquals(
      ModelPicker.pausedRole(text("plan-quota: enrich: all models for role extractor are below their plan floor; …")),
      Some(AgentRole.Extractor)
    )
    assertEquals(ModelPicker.pausedRole(text("plan-quota: all models for role nobody are below")), None)
    assertEquals(ModelPicker.pausedRole(text("plan-quota: something else")), None)
    assertEquals(ModelPicker.pausedRole(Problem.Run.Interrupted), None)
