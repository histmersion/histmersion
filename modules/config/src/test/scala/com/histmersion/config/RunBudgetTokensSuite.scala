package com.histmersion.config

/** #1461 what counts against a run's token budget: a pipeline's own default `run-tokens`. */
class RunBudgetTokensSuite extends munit.FunSuite:

  test("a pipeline's own run-tokens default, else run-tokens; malformed entries are ignored"):
    val b = AgentBudgetsConfig(
      2.0,
      com.histmersion.config.AgentBudgetsConfig.RunTokens(400000),
      com.histmersion.config.AgentBudgetsConfig.DailyUsd(10),
      com.histmersion.config.AgentBudgetsConfig.MonthlyUsd(150),
      com.histmersion.config.AgentBudgetsConfig.PipelineRunTokens("review-article=1_200_000, compile-article = 800000, bad, x=-1, y=z"),
      0.1
    )
    assertEquals(b.runTokensFor("review-article"), 1200000L)
    assertEquals(b.runTokensFor("compile-article"), 800000L)
    assertEquals(b.runTokensFor("enrich-source"), 400000L)
    assertEquals(b.runTokensFor("x"), 400000L)
    assertEquals(AgentBudgetsConfig.parsePipelineTokens(com.histmersion.config.AgentBudgetsConfig.PipelineRunTokens("")), Map.empty)

  test("the reference config: review-article 1.2M, cache reads at 0.1"):
    val b = AppConfig.load().agents.budgets
    assertEquals(
      (b.runTokensFor(com.histmersion.domain.automation.Pipeline.Wire("review-article")), b.runTokensFor(com.histmersion.domain.automation.Pipeline.Wire("enrich-source")), b.cacheReadWeight),
      (1200000L, 400000L, 0.1)
    )
