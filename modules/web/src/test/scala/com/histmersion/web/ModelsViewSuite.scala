package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.ModelChains.*

/** The pure parts of the agent models window: what a person types (a chain, limits, a reserve), the used-share of a
  * limit, a chained model's state, and an allowance paused run's words.
  */
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
      Right(List(LimitSpec(com.histmersion.api.automation.ModelChains.LimitSpec.Amount(2000000), Measure.Tokens, com.histmersion.api.automation.ModelChains.LimitSpec.Window("5h")), LimitSpec(com.histmersion.api.automation.ModelChains.LimitSpec.Amount(5), Measure.Usd, com.histmersion.api.automation.ModelChains.LimitSpec.Window("1d"))))
    )
    assertEquals(ModelsView.limitsInput(""), Right(Nil))
    assert(ModelsView.limitsInput("2M tokens").isLeft)
    assertEquals(ModelsView.reserveInput(" 20 % "), Right(20))
    assertEquals(ModelsView.reserveInput(""), Right(0))
    assert(ModelsView.reserveInput("120").isLeft)

  test("the used share of a limit, clamped; a chained model's state"):
    val l = LimitStatus(com.histmersion.api.automation.ModelChains.LimitStatus.Limit("1k tokens/1h"), Measure.Tokens, com.histmersion.api.automation.ModelChains.LimitStatus.Window("1h"), com.histmersion.api.automation.ModelChains.LimitStatus.Amount(1000), com.histmersion.api.automation.ModelChains.LimitStatus.Used(250), com.histmersion.api.automation.ModelChains.LimitStatus.Remaining(750), com.histmersion.api.automation.ModelChains.LimitStatus.Reserve(0), usable = com.histmersion.api.automation.ModelChains.LimitStatus.Usable(true))
    assertEquals(ModelsView.usedPct(l), 25)
    assertEquals(ModelsView.usedPct(l.modify(_.used).setTo(LimitStatus.Used(5000.0))), 100)
    assertEquals(ModelsView.entryState(ChainEntry(com.histmersion.api.automation.ModelChains.ChainEntry.Model("local:a"), ready = com.histmersion.api.automation.ModelChains.ChainEntry.Ready(true), usable = com.histmersion.api.automation.ModelChains.ChainEntry.Usable(true))), "is-usable")
    assertEquals(ModelsView.entryState(ChainEntry(com.histmersion.api.automation.ModelChains.ChainEntry.Model("local:a"), ready = com.histmersion.api.automation.ModelChains.ChainEntry.Ready(true), usable = com.histmersion.api.automation.ModelChains.ChainEntry.Usable(false))), "is-paused")
    assertEquals(ModelsView.entryState(ChainEntry(com.histmersion.api.automation.ModelChains.ChainEntry.Model("local:a"), ready = com.histmersion.api.automation.ModelChains.ChainEntry.Ready(false), usable = com.histmersion.api.automation.ModelChains.ChainEntry.Usable(false))), "is-unready")
    assertEquals(
      ModelsView.limitsText(AllowanceStatus(com.histmersion.api.automation.ModelChains.AllowanceStatus.Target("local:a"), com.histmersion.api.automation.ModelChains.AllowanceStatus.Source("saved"), com.histmersion.api.automation.ModelChains.AllowanceStatus.ReservePct(0), List(l, l.modify(_.limit).setTo("5 usd/1d")), usable = com.histmersion.api.automation.ModelChains.AllowanceStatus.Usable(true))),
      "1k tokens/1h, 5 usd/1d"
    )

  test("a run paused for an allowance says when it goes on by itself"):
    val r = RunInfo(
      com.histmersion.api.automation.Agents.RunInfo.Id("r"),
      com.histmersion.api.automation.Agents.RunInfo.Pipeline("enrich-source"),
      com.histmersion.api.automation.Agents.RunInfo.Target("a2020_b"),
      com.histmersion.api.automation.Agents.RunInfo.Status("paused"),
      error = Some(com.histmersion.api.automation.Agents.RunInfo.Error("allowance: all models …")),
      resumeAt = Some(RunInfo.ResumeAt("2026-10-05T14:00:00Z"))
    )
    assertEquals(RunsView.pausedFor(r), Some("allowance"))
    assert(RunsView.pausedText(r, com.histmersion.api.automation.Agents.PauseReason.Wire("allowance")).value.contains("resumes by itself at 2026-10-05T14:00:00Z"))
    assert(RunsView.pausedText(r, com.histmersion.api.automation.Agents.PauseReason.Wire("budget")).value.contains("Raise the budget"))

  test("a plan window's bar: the used share and where the floor sits; a reading's age in words"):
    val w = com.histmersion.api.automation.PlanQuota.WindowStatus(
      com.histmersion.api.automation.PlanQuota.WindowStatus.Limit("claude"),
      com.histmersion.api.automation.PlanQuota.WindowStatus.Kind("five_hour"),
      com.histmersion.api.automation.PlanQuota.WindowStatus.UsedPct(62.4),
      com.histmersion.api.automation.PlanQuota.WindowStatus.LeftPct(37.6),
      com.histmersion.api.automation.PlanQuota.WindowStatus.Floor(25),
      usable = com.histmersion.api.automation.PlanQuota.WindowStatus.Usable(true),
      stale = com.histmersion.api.automation.PlanQuota.WindowStatus.Stale(false),
      Some(com.histmersion.api.automation.PlanQuota.WindowStatus.ResetsAt("2026-10-05T21:40:00Z")),
      com.histmersion.api.automation.PlanQuota.WindowStatus.ObservedAt("2026-10-05T19:00:00Z"),
      com.histmersion.api.automation.PlanQuota.WindowStatus.AgeSeconds(90),
      com.histmersion.api.automation.PlanQuota.WindowStatus.Source("run")
    )
    assertEquals(ModelsView.windowBar(w), (62, 75))
    assertEquals(ModelsView.windowBar(w.modify(_.usedPct).setTo(com.histmersion.api.automation.PlanQuota.WindowStatus.UsedPct(130.0))), (100, 75))
    assertEquals(ModelsView.ageText(90), "90 s ago")
    assertEquals(ModelsView.ageText(600), "10 min ago")
    assertEquals(ModelsView.ageText(7200), "2 h ago")

  test("a run paused for a plan says when it goes on by itself"):
    val r = RunInfo(
      com.histmersion.api.automation.Agents.RunInfo.Id("r"),
      com.histmersion.api.automation.Agents.RunInfo.Pipeline("review-article"),
      com.histmersion.api.automation.Agents.RunInfo.Target("x"),
      com.histmersion.api.automation.Agents.RunInfo.Status("paused"),
      error = Some(com.histmersion.api.automation.Agents.RunInfo.Error("plan-quota: …")),
      resumeAt = Some(RunInfo.ResumeAt("2026-10-05T21:40:00Z"))
    )
    assertEquals(RunsView.pausedFor(r), Some("plan-quota"))
    assert(RunsView.pausedText(r, com.histmersion.api.automation.Agents.PauseReason.Wire("plan-quota")).value.contains("resumes by itself at 2026-10-05T21:40:00Z"))
