package com.histmersion.web

import com.histmersion.api.{Autopilot, Managers, PlanQuota}
import com.histmersion.api.Agents.ApprovalInfo

/** The overview's pure parts: what to read before a decision, the oldest first, a goal's progress and what blocks it,
  * the weekly allowance.
  */
class ResearchOverviewSuite extends munit.FunSuite:

  private def approval(
      id: Long,
      kind: String,
      at: String,
      subject: Option[String] = None,
      target: Option[String] = None
  ) =
    ApprovalInfo(id, Some("r1"), kind, "drake2012", createdAt = at, subject = subject, target = target)

  test("a decision names what to read first, and the evidence comes before its accept button"):
    assertEquals(
      ResearchOverview.evidence(approval(1, "edge", "t", target = Some("kaniewski2013"))),
      List("See the evidence" -> "/source/drake2012", "Compare the two" -> "/source/kaniewski2013")
    )
    assertEquals(
      ResearchOverview.evidence(approval(2, "article-publish", "t", subject = Some("sea-peoples"))),
      List("Read it first" -> "/article/sea-peoples")
    )
    assertEquals(
      ResearchOverview.evidence(approval(3, "goal-plan", "t", subject = Some("collapse"))),
      List("Read the plan first" -> "/research/goals/collapse")
    )
    assertEquals(ResearchOverview.evidence(approval(4, "status-change", "t")).map(_._1), List("See the evidence"))
    assert(
      List(
        "edge",
        "status-change",
        "article-publish",
        "article-filter",
        "goal-plan",
        "article-plan",
        "hint-effect",
        "other"
      )
        .forall(k => ResearchOverview.evidence(approval(5, k, "t", subject = Some("x"))).nonEmpty),
      "every kind has something to read"
    )

  test("the oldest decision first; decided ones are not listed"):
    val as = List(
      approval(3, "edge", "2026-10-30T10:00:00Z"),
      approval(1, "edge", "2026-10-29T10:00:00Z"),
      approval(2, "edge", "2026-10-29T10:00:00Z").copy(state = "approved")
    )
    assertEquals(ResearchOverview.oldestFirst(as).map(_.id), List(1L, 3L))
    assertEquals(ResearchOverview.waited(0, 5 * 3600000.0), "5 hours")
    assertEquals(ResearchOverview.waited(0, 1800000.0), "less than an hour")
    assertEquals(ResearchOverview.waited(0, 72 * 3600000.0), "3 days")

  private def manager(id: String, goal: String, done: Int, pending: Int, note: Option[String] = None) =
    Managers.ManagerInfo(
      id,
      "seed",
      goal = Some(goal),
      counts = Managers.ItemCounts(pending = pending, done = done),
      statusNote = note
    )

  test("a goal's progress is its managers' steps done of all; what blocks it is said in words"):
    val ms = List(manager("seed:a", "a", 8, 3), manager("watch:a", "a", 0, 1), manager("seed:b", "b", 1, 0))
    assertEquals(ResearchOverview.progressOf("a", ms), ResearchOverview.Progress(8, 12))
    assertEquals(ResearchOverview.progressOf("a", ms).pct, 66)
    assertEquals(ResearchOverview.progressOf("none", ms), ResearchOverview.Progress(0, 0))
    val g = Autopilot.GoalProgress("a", "A", "seeding", openApprovals = 2)
    val focus = Autopilot.FocusInfo(
      on = true,
      waiting = List(Autopilot.FocusWaiting("a", "person", "an approval only a person decides"))
    )
    assertEquals(ResearchOverview.blocker(g, Some(focus), ms), Some("an approval only a person decides"))
    assertEquals(ResearchOverview.blocker(g, None, ms), Some("waits for 2 of your decisions"))
    assertEquals(
      ResearchOverview
        .blocker(g.copy(openApprovals = 0), None, List(manager("seed:a", "a", 0, 1, Some("starved: no model")))),
      Some("starved: no model")
    )
    assertEquals(ResearchOverview.blocker(g.copy(openApprovals = 0), None, ms), None)

  test("the weekly allowance: each plan that reports this week's limit"):
    val w = PlanQuota.WindowStatus("claude", "seven_day", 39, 61, 10, usable = true, stale = false, None, "", 0, "")
    val pool = PlanQuota.PoolStatus(
      "claude",
      "Claude plan",
      "claude",
      Nil,
      "",
      "",
      held = false,
      usable = true,
      None,
      None,
      1,
      List(w)
    )
    assertEquals(
      ResearchOverview.weekly(PlanQuota.QuotaInfo(List(pool, pool.copy(windows = Nil)), Nil, Nil, "")),
      List("Claude plan: 61% of this week's limit left")
    )
