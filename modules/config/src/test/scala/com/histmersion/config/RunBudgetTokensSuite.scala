package com.histmersion.config

/** #1461 what counts against a run's token budget: a pipeline's own default `run-tokens`. */
class RunBudgetTokensSuite extends munit.FunSuite:

  test("a pipeline's own run-tokens default, else run-tokens; malformed entries are ignored"):
    val b = AgentBudgetsConfig(2.0, 400000, 10, 150, "review-article=1_200_000, compile-article = 800000, bad, x=-1, y=z", 0.1)
    assertEquals(b.runTokensFor("review-article"), 1200000L)
    assertEquals(b.runTokensFor("compile-article"), 800000L)
    assertEquals(b.runTokensFor("enrich-source"), 400000L)
    assertEquals(b.runTokensFor("x"), 400000L)
    assertEquals(AgentBudgetsConfig.parsePipelineTokens(""), Map.empty)

  test("the reference config: review-article 1.2M, cache reads at 0.1"):
    val b = AppConfig.load().agents.budgets
    assertEquals((b.runTokensFor("review-article"), b.runTokensFor("enrich-source"), b.cacheReadWeight),
      (1200000L, 400000L, 0.1))
