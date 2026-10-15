package com.histmersion.web

import com.histmersion.core.Managers.*

/** The managers window's pure parts: the badge, the item actions per status, the settings form, the
  * hint box, the pager and the window keys. */
class ManagersViewSuite extends munit.FunSuite:

  test("badge: the person's state, else what the supervisor derived"):
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", state = "paused", statusNote = Some("paused"))), "paused")
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", statusNote = Some("starved: skeptic chain below its plan floor"))), "starved")
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", statusNote = Some("at capacity (2 runs in flight, 3 pending)"))), "at capacity")
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", statusNote = Some("idle"))), "idle")
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", statusNote = Some("3 pending"))), "active")

  test("item actions by status"):
    assertEquals(ManagersView.itemActions("pending"), List("hold", "priority", "skip"))
    assertEquals(ManagersView.itemActions("held"), List("release", "priority", "skip"))
    assertEquals(ManagersView.itemActions("active"), List("priority", "skip"))
    assertEquals(ManagersView.itemActions("stopped"), List("reopen"))
    assertEquals(ManagersView.itemActions("nonsense"), Nil)

  test("settings form: blank is the default; it reads back what it shows; a bad number is refused"):
    val c = ManagerConfig(maxRuns = Some(3), includeTags = Some(List("a", "b")),
      models = Some(List(com.histmersion.core.Agents.ModelOverride("fixer", "claude-code:opus"))))
    assertEquals(ManagersView.config(ManagersView.fields(c)), Right(c))
    assertEquals(ManagersView.config(Map.empty), Right(ManagerConfig()))
    assert(ManagersView.config(Map("rounds" -> "x")).isLeft)
    assert(ManagersView.config(Map("models" -> "fixer")).isLeft)
    assert(ManagersView.config(Map("budgetUsd" -> "-1")).isLeft)

  test("hint box: text alone, scoped, with an action; a hold needs a date or a source"):
    assertEquals(ManagersView.hint("prefer 2019", "", once = false, "none", "", "", "", ""), Right(HintAdd("prefer 2019", None, Some(false))))
    assertEquals(ManagersView.hint("x", "tin", once = true, "none", "", "", "", "").map(_.item), Right(Some("tin")))
    val p = ManagersView.hint("first", "", once = false, "prioritise", "a, b", "", "", "")
    assertEquals(p.map(_.effect.map(e => (e.action, e.targets))), Right(Some(("prioritise", List("a", "b")))))
    assert(ManagersView.hint("wait", "", once = false, "hold", "a", "", "", "").isLeft)
    assertEquals(ManagersView.hint("wait", "", once = false, "hold", "a", "", "2026-11-01", "").map(_.effect.flatMap(_.until)), Right(Some("2026-11-01")))
    assert(ManagersView.hint("skip", "", once = false, "skip", "", "", "", "").isLeft)
    assert(ManagersView.hint(" ", "", once = false, "none", "", "", "", "").isLeft)
    assert(ManagerPolicyText.isDate("2026-11-01") && !ManagerPolicyText.isDate("10.1000/x"))

  test("pager: the range line and the offsets"):
    assertEquals(Pager.range(0, 25, 112), "1–25 of 112")
    assertEquals(Pager.range(100, 12, 112), "101–112 of 112")
    assertEquals(Pager.range(0, 0, 0), "none")
    assertEquals(Pager.previous(10, 25), 0)
    assertEquals(Pager.next(75, 25, 112), Some(100))
    assertEquals(Pager.next(100, 25, 112), None)

  test("window keys: managers and one manager round-trip through the hash"):
    assertEquals(WindowKey.parse(WindowKey.Managers.wire), Some(WindowKey.Managers))
    assertEquals(WindowKey.parse(WindowKey.Manager("review").wire), Some(WindowKey.Manager("review")))
    assertEquals(WindowKey.Manager("review").initialTitle, "manager · review")
