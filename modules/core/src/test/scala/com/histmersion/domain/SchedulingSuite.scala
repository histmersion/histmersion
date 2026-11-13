package com.histmersion
package domain

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.automation.Agents.ApprovalKind, com.histmersion.domain.automation.Run.State as RunState
import com.histmersion.domain.Scheduling.*
import com.histmersion.domain.library.Edge

/** The schedules' pure rules: when a due schedule may start, its cap, and the DOIs an acquisition item names. */
class SchedulingSuite extends munit.FunSuite:

  private val zero = BigDecimal(0)

  test("start only when the previous run has settled, the caps leave room and a slot is free"):
    assertEquals(
      decide(
        None,
        zero,
        zero,
        DailyBudget(10),
        MonthlyBudget(150),
        active = ActiveCount(0),
        maxConcurrent = MaxConcurrent(4)
      ),
      Decision.Start
    )
    assertEquals(
      decide(Some(RunState.Done), zero, zero, DailyBudget(10), MonthlyBudget(150), ActiveCount(0), MaxConcurrent(4)),
      Decision.Start
    )
    assertEquals(
      decide(Some(RunState.Failed), zero, zero, DailyBudget(10), MonthlyBudget(150), ActiveCount(0), MaxConcurrent(4)),
      Decision.Start
    )
    assert(
      decide(Some(RunState.Paused), zero, zero, DailyBudget(10), MonthlyBudget(150), ActiveCount(0), MaxConcurrent(4))
        .isInstanceOf[Decision.Skip]
    )
    assert(
      decide(
        Some(RunState.WaitingApproval),
        zero,
        zero,
        DailyBudget(10),
        MonthlyBudget(150),
        ActiveCount(0),
        MaxConcurrent(4)
      ).isInstanceOf[Decision.Skip]
    )

  test("the daily and monthly caps are checked BEFORE a start (skip); a full pool defers"):
    decide(None, BigDecimal(10), zero, DailyBudget(10), MonthlyBudget(150), ActiveCount(0), MaxConcurrent(4)) match
      case Decision.Skip(why) => assert(why.sentence.contains("daily-usd"), why)
      case other              => fail(s"expected a skip, got $other")
    decide(
      None,
      BigDecimal(1),
      BigDecimal(150),
      DailyBudget(10),
      MonthlyBudget(150),
      ActiveCount(0),
      MaxConcurrent(4)
    ) match
      case Decision.Skip(why) => assert(why.sentence.contains("monthly-usd"), why)
      case other              => fail(s"expected a skip, got $other")
    assert(
      decide(
        None,
        zero,
        zero,
        DailyBudget(10),
        MonthlyBudget(150),
        active = ActiveCount(4),
        maxConcurrent = MaxConcurrent(4)
      ).isInstanceOf[Decision.Defer]
    )

  test("a scheduled run's cap: the schedule's, else the goal's, else the server's"):
    assertEquals(budget(Some(Budget(0.5)), Some(Budget(1.0)), Budget(2.0)), 0.5)
    assertEquals(budget(None, Some(Budget(1.0)), Budget(2.0)), 1.0)
    assertEquals(budget(None, None, Budget(2.0)), 2.0)
    assertEquals(budget(Some(Budget(0)), None, Budget(2.0)), 2.0)

  test("DOIs are found in an item's text whatever their form"):
    assertEquals(dois("Smith (2020). Tin. https://doi.org/10.1000/ABC.1 — for goal g"), Set("10.1000/abc.1"))
    assertEquals(dois("doi:10.5555/x and 10.6666/y."), Set("10.5555/x", "10.6666/y"))
    assertEquals(dois("no doi here"), Set.empty[String])
