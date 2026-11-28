package com.histmersion
package web

import com.histmersion.api.automation.modelchains.LimitSpec
import com.histmersion.api.automation.modelchains.Measure
import com.histmersion.api.automation.modelchains.LimitStatus
import com.histmersion.api.automation.modelchains.ChainEntry
import com.histmersion.api.automation.modelchains.AllowanceStatus
import com.histmersion.api.automation.modelchains.SettingSource
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.agents.PauseReason
import com.histmersion.api.automation.planquota.WindowStatus
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.ModelChains.*
import com.histmersion.api.automation.ModelChains
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.PlanQuota
import com.histmersion.lint.{tolerated, LintRule}

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
          LimitSpec(LimitSpec.Amount(2000000), Measure.Tokens, LimitSpec.Window("5h")),
          LimitSpec(LimitSpec.Amount(5), Measure.Usd, LimitSpec.Window("1d"))
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
      LimitStatus.Limit("1k tokens/1h"),
      Measure.Tokens,
      LimitStatus.Window("1h"),
      LimitStatus.Amount(1000),
      LimitStatus.Used(250),
      LimitStatus.Remaining(750),
      LimitStatus.Reserve(0),
      usable = LimitStatus.Usable.Usable
    )
    assertEquals(ModelsView.usedPct(l), 25)
    assertEquals(ModelsView.usedPct(l.modify(_.used).setTo(LimitStatus.Used(5000.0))), 100)
    assertEquals(
      ModelsView.entryState(
        ChainEntry(
          ChainEntry.Model("local:a"),
          ready = ChainEntry.Ready.Ready,
          usable = ChainEntry.Usable.Usable
        )
      ),
      "is-usable"
    )
    assertEquals(
      ModelsView.entryState(
        ChainEntry(
          ChainEntry.Model("local:a"),
          ready = ChainEntry.Ready.Ready,
          usable = ChainEntry.Usable.Unusable
        )
      ),
      "is-paused"
    )
    assertEquals(
      ModelsView.entryState(
        ChainEntry(
          ChainEntry.Model("local:a"),
          ready = ChainEntry.Ready.Unready,
          usable = ChainEntry.Usable.Unusable
        )
      ),
      "is-unready"
    )
    assertEquals(
      ModelsView.limitsText(
        AllowanceStatus(
          AllowanceStatus.Target("local:a"),
          SettingSource.Wire("saved"),
          AllowanceStatus.ReservePct(0),
          List(l, l.modify(_.limit).setTo(LimitStatus.Limit("5 usd/1d"))),
          usable = AllowanceStatus.Usable.Usable
        )
      ),
      "1k tokens/1h, 5 usd/1d"
    )

  test("a run paused for an allowance says when it goes on by itself"):
    val r = RunInfo(
      RunInfo.Id("r"),
      RunInfo.Pipeline("enrich-source"),
      RunInfo.Target("a2020_b"),
      RunInfo.Status("paused"),
      error = Some(Problem.Unclassified(Legacy.Text("allowance: all models …"))),
      resumeAt = Some(RunInfo.ResumeAt("2026-10-05T14:00:00Z")),
      pausedFor = Some(PauseReason.Allowance)
    )
    assertEquals(RunsView.pausedFor(r), Some(PauseReason.Allowance))
    assert(
      RunsView
        .pausedText(r, PauseReason.Allowance)
        .value
        .contains("resumes by itself at 2026-10-05T14:00:00Z")
    )
    assert(RunsView.pausedText(r, PauseReason.Budget).value.contains("Raise the budget"))

  test("a plan window's bar: the used share and where the floor sits; a reading's age in words"):
    val w = WindowStatus(
      WindowStatus.Limit("claude"),
      WindowStatus.Kind("five_hour"),
      WindowStatus.UsedPct(62.4),
      WindowStatus.LeftPct(37.6),
      WindowStatus.Floor(25),
      usable = WindowStatus.Usable.Usable,
      stale = WindowStatus.Stale.Fresh,
      Some(WindowStatus.ResetsAt("2026-10-05T21:40:00Z")),
      WindowStatus.ObservedAt("2026-10-05T19:00:00Z"),
      WindowStatus.AgeSeconds(90),
      WindowStatus.Source("run")
    )
    assertEquals(ModelsView.windowBar(w), (62, 75))
    assertEquals(ModelsView.windowBar(w.modify(_.usedPct).setTo(WindowStatus.UsedPct(130.0))), (100, 75))
    assertEquals(ModelsView.ageText(90), "90 s ago")
    assertEquals(ModelsView.ageText(600), "10 min ago")
    assertEquals(ModelsView.ageText(7200), "2 h ago")

  test("a run paused for a plan says when it goes on by itself"):
    val r = RunInfo(
      RunInfo.Id("r"),
      RunInfo.Pipeline("review-article"),
      RunInfo.Target("x"),
      RunInfo.Status("paused"),
      error = Some(Problem.Unclassified(Legacy.Text("plan-quota: …"))),
      resumeAt = Some(RunInfo.ResumeAt("2026-10-05T21:40:00Z")),
      pausedFor = Some(PauseReason.PlanQuota)
    )
    assertEquals(RunsView.pausedFor(r), Some(PauseReason.PlanQuota))
    assert(
      RunsView
        .pausedText(r, PauseReason.PlanQuota)
        .value
        .contains("resumes by itself at 2026-10-05T21:40:00Z")
    )
