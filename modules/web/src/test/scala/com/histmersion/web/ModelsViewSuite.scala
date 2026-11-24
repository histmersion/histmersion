package com.histmersion
package web
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.ModelChains.*
import com.histmersion.api.automation.ModelChains
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.PlanQuota
import com.histmersion.lint.{LintRule, tolerated}

/** The pure parts of the agent models window: what a person types (a chain, limits, a reserve), the used-share of a
  * limit, a chained model's state, and an allowance paused run's words.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class ModelsViewSuite extends munit.FunSuite:

  test("a chain as typed: > or , separate the models; blanks are dropped"):
    assertEquals(
      ModelsView.chainInput(" claude-code:sonnet > local:qwen2.5-coder:32b ,anthropic:x "),
      List("claude-code:sonnet", "local:qwen2.5-coder:32b", "anthropic:x")
    )
    assertEquals(ModelsView.chainInput("  "), Nil)

  test("limits and a reserve as typed, with the problem when they do not read"):
    assertEquals(
      ModelsView.limitsInput("2M tokens/5h, 5 usd/day"),
      Right(
        List(
          LimitSpec(ModelChains.LimitSpec.Amount(2000000), Measure.Tokens, ModelChains.LimitSpec.Window("5h")),
          LimitSpec(ModelChains.LimitSpec.Amount(5), Measure.Usd, ModelChains.LimitSpec.Window("1d"))
        )
      )
    )
    assertEquals(ModelsView.limitsInput(""), Right(Nil))
    assert(ModelsView.limitsInput("2M tokens").isLeft)
    assertEquals(ModelsView.reserveInput(" 20 % "), Right(20))
    assertEquals(ModelsView.reserveInput(""), Right(0))
    assert(ModelsView.reserveInput("120").isLeft)

  test("the used share of a limit, clamped; a chained model's state"):
    val l = LimitStatus(
      ModelChains.LimitStatus.Limit("1k tokens/1h"),
      Measure.Tokens,
      ModelChains.LimitStatus.Window("1h"),
      ModelChains.LimitStatus.Amount(1000),
      ModelChains.LimitStatus.Used(250),
      ModelChains.LimitStatus.Remaining(750),
      ModelChains.LimitStatus.Reserve(0),
      usable = ModelChains.LimitStatus.Usable(true)
    )
    assertEquals(ModelsView.usedPct(l), 25)
    assertEquals(ModelsView.usedPct(l.modify(_.used).setTo(LimitStatus.Used(5000.0))), 100)
    assertEquals(
      ModelsView.entryState(
        ChainEntry(
          ModelChains.ChainEntry.Model("local:a"),
          ready = ModelChains.ChainEntry.Ready(true),
          usable = ModelChains.ChainEntry.Usable(true)
        )
      ),
      "is-usable"
    )
    assertEquals(
      ModelsView.entryState(
        ChainEntry(
          ModelChains.ChainEntry.Model("local:a"),
          ready = ModelChains.ChainEntry.Ready(true),
          usable = ModelChains.ChainEntry.Usable(false)
        )
      ),
      "is-paused"
    )
    assertEquals(
      ModelsView.entryState(
        ChainEntry(
          ModelChains.ChainEntry.Model("local:a"),
          ready = ModelChains.ChainEntry.Ready(false),
          usable = ModelChains.ChainEntry.Usable(false)
        )
      ),
      "is-unready"
    )
    assertEquals(
      ModelsView.limitsText(
        AllowanceStatus(
          ModelChains.AllowanceStatus.Target("local:a"),
          ModelChains.SettingSource.Wire("saved"),
          ModelChains.AllowanceStatus.ReservePct(0),
          List(l, l.modify(_.limit).setTo(ModelChains.LimitStatus.Limit("5 usd/1d"))),
          usable = ModelChains.AllowanceStatus.Usable(true)
        )
      ),
      "1k tokens/1h, 5 usd/1d"
    )

  test("a run paused for an allowance says when it goes on by itself"):
    val r = RunInfo(
      Agents.RunInfo.Id("r"),
      Agents.RunInfo.Pipeline("enrich-source"),
      Agents.RunInfo.Target("a2020_b"),
      Agents.RunInfo.Status("paused"),
      error = Some(Problem.Unclassified(Legacy.Text("allowance: all models …"))),
      resumeAt = Some(RunInfo.ResumeAt("2026-10-05T14:00:00Z")),
      pausedFor = Some(Agents.PauseReason.Allowance)
    )
    assertEquals(RunsView.pausedFor(r), Some(Agents.PauseReason.Allowance))
    assert(
      RunsView
        .pausedText(r, Agents.PauseReason.Allowance)
        .value
        .contains("resumes by itself at 2026-10-05T14:00:00Z")
    )
    assert(RunsView.pausedText(r, Agents.PauseReason.Budget).value.contains("Raise the budget"))

  test("a plan window's bar: the used share and where the floor sits; a reading's age in words"):
    val w = PlanQuota.WindowStatus(
      PlanQuota.WindowStatus.Limit("claude"),
      PlanQuota.WindowStatus.Kind("five_hour"),
      PlanQuota.WindowStatus.UsedPct(62.4),
      PlanQuota.WindowStatus.LeftPct(37.6),
      PlanQuota.WindowStatus.Floor(25),
      usable = PlanQuota.WindowStatus.Usable(true),
      stale = PlanQuota.WindowStatus.Stale(false),
      Some(PlanQuota.WindowStatus.ResetsAt("2026-10-05T21:40:00Z")),
      PlanQuota.WindowStatus.ObservedAt("2026-10-05T19:00:00Z"),
      PlanQuota.WindowStatus.AgeSeconds(90),
      PlanQuota.WindowStatus.Source("run")
    )
    assertEquals(ModelsView.windowBar(w), (62, 75))
    assertEquals(ModelsView.windowBar(w.modify(_.usedPct).setTo(PlanQuota.WindowStatus.UsedPct(130.0))), (100, 75))
    assertEquals(ModelsView.ageText(90), "90 s ago")
    assertEquals(ModelsView.ageText(600), "10 min ago")
    assertEquals(ModelsView.ageText(7200), "2 h ago")

  test("a run paused for a plan says when it goes on by itself"):
    val r = RunInfo(
      Agents.RunInfo.Id("r"),
      Agents.RunInfo.Pipeline("review-article"),
      Agents.RunInfo.Target("x"),
      Agents.RunInfo.Status("paused"),
      error = Some(Problem.Unclassified(Legacy.Text("plan-quota: …"))),
      resumeAt = Some(RunInfo.ResumeAt("2026-10-05T21:40:00Z")),
      pausedFor = Some(Agents.PauseReason.PlanQuota)
    )
    assertEquals(RunsView.pausedFor(r), Some(Agents.PauseReason.PlanQuota))
    assert(
      RunsView
        .pausedText(r, Agents.PauseReason.PlanQuota)
        .value
        .contains("resumes by itself at 2026-10-05T21:40:00Z")
    )
