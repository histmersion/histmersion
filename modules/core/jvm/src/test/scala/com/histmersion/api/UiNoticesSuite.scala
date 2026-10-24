package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import UiNotices.*

class UiNoticesSuite extends munit.FunSuite:

  private val t1 = "2026-10-11T08:00:00.000000Z"
  private val t2 = "2026-10-11T08:00:00.000001Z"
  private val t3 = "2026-10-12T00:00:00.000000Z"

  test("something is new only when its newest change is after the mark"):
    assert(fresh(Some(t2), Some(t1)), "a microsecond later is news")
    assert(!fresh(Some(t1), Some(t1)), "what the window showed is not")
    assert(!fresh(Some(t1), Some(t3)), "nor anything older")
    assert(!fresh(None, Some(t1)), "an empty list never is")
    assert(!fresh(None, None))
    assert(fresh(Some(t1), None), "never opened, with something in it: new")

  test("the store's fixed format compares as its instants do"):
    val ts = List(t3, t1, "2025-01-01T23:59:59.999999Z", t2)
    assertEquals(ts.sorted, ts.sortBy(java.time.Instant.parse))

  test("a counter carries a count only for a counted board"):
    assertEquals(counter(Board.Tasks, Some(3), Some(t2), Some(t1)), Counter("tasks", Some(3), Some(t2), Some(t1), fresh = true))
    assertEquals(counter(Board.Goals, Some(26), Some(t1), Some(t1)).count, None)
    assertEquals(counter(Board.Log, Some(9), None, None), Counter("log", None, None, None, fresh = false))

  test("boards parse from their wire keys, which are the windows' own"):
    assertEquals(Board.parse("approvals"), Some(Board.Approvals))
    assertEquals(Board.parse(" Acquire "), Some(Board.Acquire))
    assertEquals(Board.parse("jobs"), None)
    assertEquals(Board.parse(null), None)
    assertEquals(Board.wires, List("goals", "tasks", "approvals", "acquire", "log"))
    assertEquals(Board.values.filter(_.counted).map(_.wire).toList, List("tasks", "approvals", "acquire"))

  test("the mark only moves forward"):
    assertEquals(advance(None, t1), t1)
    assertEquals(advance(Some(t1), t2), t2)
    assertEquals(advance(Some(t3), t2), t3)

  test("a count reads as nothing at zero and 99+ past 99"):
    assertEquals(badge(None), None)
    assertEquals(badge(Some(0)), None)
    assertEquals(badge(Some(7)), Some("7"))
    assertEquals(badge(Some(99)), Some("99"))
    assertEquals(badge(Some(1291)), Some("99+"))
