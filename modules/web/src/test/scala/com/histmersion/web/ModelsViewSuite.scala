package com.histmersion.web

import com.histmersion.core.Agents.*
import com.histmersion.core.ModelChains.*

/** The pure parts of the agent models window: what a person types (a chain, limits, a reserve), the
  * used-share of a limit, a chained model's state, and an allowance paused run's words. */
class ModelsViewSuite extends munit.FunSuite:

  test("a chain as typed: > or , separate the models; blanks are dropped"):
    assertEquals(ModelsView.chainInput(" claude-code:sonnet > local:qwen2.5-coder:32b ,anthropic:x "),
      List("claude-code:sonnet", "local:qwen2.5-coder:32b", "anthropic:x"))
    assertEquals(ModelsView.chainInput("  "), Nil)

  test("limits and a reserve as typed, with the problem when they do not read"):
    assertEquals(ModelsView.limitsInput("2M tokens/5h, 5 usd/day"),
      Right(List(LimitSpec(2000000, Measure.Tokens, "5h"), LimitSpec(5, Measure.Usd, "1d"))))
    assertEquals(ModelsView.limitsInput(""), Right(Nil))
    assert(ModelsView.limitsInput("2M tokens").isLeft)
    assertEquals(ModelsView.reserveInput(" 20 % "), Right(20))
    assertEquals(ModelsView.reserveInput(""), Right(0))
    assert(ModelsView.reserveInput("120").isLeft)

  test("the used share of a limit, clamped; a chained model's state"):
    val l = LimitStatus("1k tokens/1h", Measure.Tokens, "1h", 1000, 250, 750, 0, usable = true)
    assertEquals(ModelsView.usedPct(l), 25)
    assertEquals(ModelsView.usedPct(l.copy(used = 5000)), 100)
    assertEquals(ModelsView.entryState(ChainEntry("local:a", ready = true, usable = true)), "is-usable")
    assertEquals(ModelsView.entryState(ChainEntry("local:a", ready = true, usable = false)), "is-paused")
    assertEquals(ModelsView.entryState(ChainEntry("local:a", ready = false, usable = false)), "is-unready")
    assertEquals(ModelsView.limitsText(AllowanceStatus("local:a", "saved", 0, List(l, l.copy(limit = "5 usd/1d")), usable = true)),
      "1k tokens/1h, 5 usd/1d")

  test("a run paused for an allowance says when it goes on by itself"):
    val r = RunInfo("r", "enrich-source", "a2020_b", "paused", error = Some("allowance: all models …"),
      resumeAt = Some("2026-10-05T14:00:00Z"))
    assertEquals(RunsView.pausedFor(r), Some("allowance"))
    assert(RunsView.pausedText(r, "allowance").contains("resumes by itself at 2026-10-05T14:00:00Z"))
    assert(RunsView.pausedText(r, "budget").contains("Raise the budget"))
