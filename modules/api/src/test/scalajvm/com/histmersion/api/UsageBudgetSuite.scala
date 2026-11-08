package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Agents.Usage

/** #1461 what counts against a run's token budget: cache reads at their weight. */
class UsageBudgetSuite extends munit.FunSuite:

  test("cache reads count at their weight; uncached input, cache writes and output in full"):
    // run 6fc12123: 487188 in (395021 of them cache reads) / 39200 out — paused at a 400k budget
    val u = Usage(input = Usage.Input(487188), output = Usage.Output(39200), cacheRead = Usage.CacheRead(395021), cacheWrite = Usage.CacheWrite(92137))
    assertEquals(u.total, 526388L)
    assertEquals(u.budgeted(Usage.CacheReadWeight(1.0)), 526388L, "weight 1 = the old count")
    assertEquals(u.budgeted(Usage.CacheReadWeight(0.1)), 92167L + 39200L + 39502L)
    assertEquals(Usage(input = Usage.Input(10), output = Usage.Output(5)).budgeted(Usage.CacheReadWeight(0.1)), 15L, "no cache: unchanged")
    assertEquals(Usage(input = Usage.Input(0), output = Usage.Output(0), cacheRead = Usage.CacheRead(50)).budgeted(Usage.CacheReadWeight(0.1)), 5L, "never negative")
