package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.{Autopilot, Managers, PlanQuota}
import com.histmersion.api.automation.Agents.ApprovalInfo

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
    ApprovalInfo(id, Some(com.histmersion.api.automation.Agents.RunInfo.Id("r1")), kind, com.histmersion.api.library.Citekey("drake2012"), createdAt = at, subject = subject, target = target)

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
      approval(2, "edge", "2026-10-29T10:00:00Z").modify(_.state).setTo("approved")
    )
    assertEquals(ResearchOverview.oldestFirst(as).map(_.id), List(1L, 3L))
    assertEquals(ResearchOverview.waited(com.histmersion.web.ResearchOverview.EpochMillis(0), 5 * 3600000.0), "5 hours")
    assertEquals(ResearchOverview.waited(com.histmersion.web.ResearchOverview.EpochMillis(0), 1800000.0), "less than an hour")
    assertEquals(ResearchOverview.waited(com.histmersion.web.ResearchOverview.EpochMillis(0), 72 * 3600000.0), "3 days")

  private def manager(id: String, goal: String, done: Int, pending: Int, note: Option[String] = None) =
    Managers.ManagerInfo(
      id,
      com.histmersion.api.automation.Managers.ManagerInfo.Kind("seed"),
      goal = Some(goal),
      counts = Managers.ItemCounts(pending = pending, done = done),
      statusNote = note
    )

  test("a goal's progress is its managers' steps done of all; what blocks it is said in words"):
    val ms = List(manager("seed:a", "a", 8, 3), manager("watch:a", "a", 0, 1), manager("seed:b", "b", 1, 0))
    assertEquals(ResearchOverview.progressOf(com.histmersion.api.automation.Goal.Slug("a"), ms), ResearchOverview.Progress(com.histmersion.web.ResearchOverview.Progress.Done(8), com.histmersion.web.ResearchOverview.Progress.Total(12)))
    assertEquals(ResearchOverview.progressOf(com.histmersion.api.automation.Goal.Slug("a"), ms).pct, 66)
    assertEquals(ResearchOverview.progressOf(com.histmersion.api.automation.Goal.Slug("none"), ms), ResearchOverview.Progress(com.histmersion.web.ResearchOverview.Progress.Done(0), com.histmersion.web.ResearchOverview.Progress.Total(0)))
    val g = Autopilot.GoalProgress(com.histmersion.api.automation.Autopilot.GoalProgress.Slug("a"), com.histmersion.api.automation.Autopilot.GoalProgress.Title("A"), com.histmersion.api.automation.Autopilot.GoalProgress.Status("seeding"), openApprovals = com.histmersion.api.automation.Autopilot.GoalProgress.OpenApprovals(2))
    val focus = Autopilot.FocusInfo(
      on = com.histmersion.api.automation.Autopilot.FocusInfo.On(true),
      waiting = List(Autopilot.FocusWaiting(com.histmersion.api.automation.Autopilot.FocusWaiting.Slug("a"), com.histmersion.api.automation.Autopilot.FocusWaiting.Code("person"), com.histmersion.api.automation.Autopilot.FocusWaiting.Why("an approval only a person decides")))
    )
    assertEquals(ResearchOverview.blocker(g, Some(focus), ms), Some("an approval only a person decides"))
    assertEquals(ResearchOverview.blocker(g, None, ms), Some("waits for 2 of your decisions"))
    assertEquals(
      ResearchOverview
        .blocker(g.modify(_.openApprovals).setTo(0), None, List(manager("seed:a", "a", 0, 1, Some("starved: no model")))),
      Some("starved: no model")
    )
    assertEquals(ResearchOverview.blocker(g.modify(_.openApprovals).setTo(0), None, ms), None)

  test("the weekly allowance: each plan that reports this week's limit"):
    val w = PlanQuota.WindowStatus(com.histmersion.api.automation.PlanQuota.WindowStatus.Limit("claude"), com.histmersion.api.automation.PlanQuota.WindowStatus.Kind("seven_day"), com.histmersion.api.automation.PlanQuota.WindowStatus.UsedPct(39), com.histmersion.api.automation.PlanQuota.WindowStatus.LeftPct(61), com.histmersion.api.automation.PlanQuota.WindowStatus.Floor(10), usable = com.histmersion.api.automation.PlanQuota.WindowStatus.Usable(true), stale = com.histmersion.api.automation.PlanQuota.WindowStatus.Stale(false), None, com.histmersion.api.automation.PlanQuota.WindowStatus.ObservedAt(""), com.histmersion.api.automation.PlanQuota.WindowStatus.AgeSeconds(0), com.histmersion.api.automation.PlanQuota.WindowStatus.Source(""))
    val pool = PlanQuota.PoolStatus(
      com.histmersion.api.automation.PlanQuota.PoolStatus.Kind("claude"),
      com.histmersion.api.automation.PlanQuota.PoolStatus.Title("Claude plan"),
      com.histmersion.api.automation.PlanQuota.PoolStatus.Kind("claude"),
      Nil,
      com.histmersion.api.automation.PlanQuota.PoolStatus.Floors(""),
      com.histmersion.api.automation.PlanQuota.PoolStatus.FloorsSource(""),
      held = com.histmersion.api.automation.PlanQuota.PoolStatus.Held(false),
      usable = com.histmersion.api.automation.PlanQuota.PoolStatus.Usable(true),
      None,
      None,
      com.histmersion.api.automation.PlanQuota.PoolStatus.MaxCallsInFlight(1),
      List(w)
    )
    assertEquals(
      ResearchOverview.weekly(PlanQuota.QuotaInfo(List(pool, pool.modify(_.windows).setTo(Nil)), Nil, Nil, com.histmersion.api.automation.PlanQuota.QuotaInfo.Now(""))),
      List("Claude plan: 61% of this week's limit left")
    )
