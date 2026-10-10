package com.histmersion.core

import com.histmersion.core.Agents.Usage

/** #1461 what counts against a run's token budget: cache reads at their weight, and a pipeline's own
  * default `run-tokens`. */
class RunBudgetTokensSuite extends munit.FunSuite:

  test("cache reads count at their weight; uncached input, cache writes and output in full"):
    // run 6fc12123: 487188 in (395021 of them cache reads) / 39200 out — paused at a 400k budget
    val u = Usage(input = 487188, output = 39200, cacheRead = 395021, cacheWrite = 92137)
    assertEquals(u.total, 526388L)
    assertEquals(u.budgeted(1.0), 526388L, "weight 1 = the old count")
    assertEquals(u.budgeted(0.1), 92167L + 39200L + 39502L)
    assertEquals(Usage(input = 10, output = 5).budgeted(0.1), 15L, "no cache: unchanged")
    assertEquals(Usage(input = 0, output = 0, cacheRead = 50).budgeted(0.1), 5L, "never negative")

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
