package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.instance.UiNotices.*
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class UiNoticesSuite extends munit.FunSuite:

  private val t1 = "2026-10-11T08:00:00.000000Z"
  private val t2 = "2026-10-11T08:00:00.000001Z"
  private val t3 = "2026-10-12T00:00:00.000000Z"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val l1 = Counter.Latest(t1)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val l2 = Counter.Latest(t2)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val s1 = Counter.Seen(t1)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val s2 = Counter.Seen(t2)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val s3 = Counter.Seen(t3)

  test("something is new only when its newest change is after the mark"):
    assert(fresh(Some(l2), Some(s1)), "a microsecond later is news")
    assert(!fresh(Some(l1), Some(s1)), "what the window showed is not")
    assert(!fresh(Some(l1), Some(s3)), "nor anything older")
    assert(!fresh(None, Some(s1)), "an empty list never is")
    assert(!fresh(None, None))
    assert(fresh(Some(l1), None), "never opened, with something in it: new")

  test("the store's fixed format compares as its instants do"):
    val ts = List(t3, t1, "2025-01-01T23:59:59.999999Z", t2)
    assertEquals(ts.sorted, ts.sortBy(java.time.Instant.parse))

  test("a counter carries a count only for a counted board"):
    assertEquals(
      counter(Board.Tasks, Some(Counter.Count(3)), Some(l2), Some(s1)),
      Counter(Board.Wire("tasks"), Some(Counter.Count(3)), Some(l2), Some(s1), fresh = Counter.Fresh(true))
    )
    assertEquals(counter(Board.Goals, Some(Counter.Count(26)), Some(l1), Some(s1)).count, None)
    assertEquals(
      counter(Board.Log, Some(Counter.Count(9)), None, None),
      Counter(Board.Wire("log"), None, None, None, fresh = Counter.Fresh(false))
    )

  test("boards parse from their wire keys, which are the windows' own"):
    assertEquals(Board.parse(Board.Wire("approvals")), Some(Board.Approvals))
    assertEquals(Board.parse(Board.Wire(" Acquire ")), Some(Board.Acquire))
    assertEquals(Board.parse(Board.Wire("jobs")), None)
    assertEquals(Board.parse(Board.Wire(null)), None)
    assertEquals(Board.wires, List("goals", "tasks", "approvals", "acquire", "log"))
    assertEquals(Board.values.filter(_.counted.value).map(_.wire).toList, List("tasks", "approvals", "acquire"))

  test("the mark only moves forward"):
    assertEquals(advance(None, s1), t1)
    assertEquals(advance(Some(s1), s2), t2)
    assertEquals(advance(Some(s3), s2), t3)

  test("a count reads as nothing at zero and 99+ past 99"):
    assertEquals(badge(None), None)
    assertEquals(badge(Some(Counter.Count(0))), None)
    assertEquals(badge(Some(Counter.Count(7))), Some("7"))
    assertEquals(badge(Some(Counter.Count(99))), Some("99"))
    assertEquals(badge(Some(Counter.Count(1291))), Some("99+"))
