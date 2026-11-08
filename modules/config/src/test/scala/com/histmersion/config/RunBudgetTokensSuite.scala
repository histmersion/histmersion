package com.histmersion
package config

import com.histmersion.config.testsupport.ComparedByValue.given
import com.histmersion.domain.automation.Pipeline

/** #1461 what counts against a run's token budget: a pipeline's own default `run-tokens`. */
class RunBudgetTokensSuite extends munit.FunSuite:

  test("a pipeline's own run-tokens default, else run-tokens; malformed entries are ignored"):
    val b = AgentBudgetsConfig(
      AgentBudgetsConfig.RunUsd(2.0),
      AgentBudgetsConfig.RunTokens(400000),
      AgentBudgetsConfig.DailyUsd(10),
      AgentBudgetsConfig.MonthlyUsd(150),
      AgentBudgetsConfig.PipelineRunTokens("review-article=1_200_000, compile-article = 800000, bad, x=-1, y=z"),
      AgentBudgetsConfig.CacheReadWeight(0.1)
    )
    assertEquals(b.runTokensFor(Pipeline.Wire("review-article")), 1200000L)
    assertEquals(b.runTokensFor(Pipeline.Wire("compile-article")), 800000L)
    assertEquals(b.runTokensFor(Pipeline.Wire("enrich-source")), 400000L)
    assertEquals(b.runTokensFor(Pipeline.Wire("x")), 400000L)
    assertEquals(AgentBudgetsConfig.parsePipelineTokens(AgentBudgetsConfig.PipelineRunTokens("")), Map.empty)

  test("the reference config: review-article 1.2M, cache reads at 0.1"):
    val b = AppConfig.load().agents.budgets
    assertEquals(
      (b.runTokensFor(Pipeline.Wire("review-article")), b.runTokensFor(Pipeline.Wire("enrich-source")), b.cacheReadWeight),
      (1200000L, 400000L, 0.1)
    )
