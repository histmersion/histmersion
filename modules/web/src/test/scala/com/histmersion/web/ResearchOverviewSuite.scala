package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.{Autopilot, Managers, PlanQuota}
import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.api.automation.Agents
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Goal

/** The overview's pure parts: what to read before a decision, the oldest first, a goal's progress and what blocks it,
  * the weekly allowance.
  */
class ResearchOverviewSuite extends munit.FunSuite:

  private def approval(
      id: ApprovalInfo.Id,
      kind: ApprovalInfo.Kind,
      at: ApprovalInfo.CreatedAt,
      subject: Option[ApprovalInfo.Subject] = None,
      target: Option[ApprovalInfo.Target] = None
  ) =
    ApprovalInfo(
      id,
      Some(Agents.RunInfo.Id("r1")),
      kind,
      Citekey("drake2012"),
      createdAt = at,
      subject = subject,
      target = target
    )

  test("a decision names what to read first, and the evidence comes before its accept button"):
    assertEquals(
      ResearchOverview.evidence(
        approval(
          ApprovalInfo.Id(1),
          ApprovalInfo.Kind("edge"),
          ApprovalInfo.CreatedAt("t"),
          target = Some(ApprovalInfo.Target("kaniewski2013"))
        )
      ),
      List("See the evidence" -> "/source/drake2012", "Compare the two" -> "/source/kaniewski2013")
    )
    assertEquals(
      ResearchOverview.evidence(
        approval(
          ApprovalInfo.Id(2),
          ApprovalInfo.Kind("article-publish"),
          ApprovalInfo.CreatedAt("t"),
          subject = Some(ApprovalInfo.Subject("sea-peoples"))
        )
      ),
      List("Read it first" -> "/article/sea-peoples")
    )
    assertEquals(
      ResearchOverview.evidence(
        approval(
          ApprovalInfo.Id(3),
          ApprovalInfo.Kind("goal-plan"),
          ApprovalInfo.CreatedAt("t"),
          subject = Some(ApprovalInfo.Subject("collapse"))
        )
      ),
      List("Read the plan first" -> "/research/goals/collapse")
    )
    assertEquals(
      ResearchOverview
        .evidence(approval(ApprovalInfo.Id(4), ApprovalInfo.Kind("status-change"), ApprovalInfo.CreatedAt("t")))
        .map(_._1),
      List("See the evidence")
    )
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
        .forall(k =>
          ResearchOverview
            .evidence(
              approval(
                ApprovalInfo.Id(5),
                ApprovalInfo.Kind(k),
                ApprovalInfo.CreatedAt("t"),
                subject = Some(ApprovalInfo.Subject("x"))
              )
            )
            .nonEmpty
        ),
      "every kind has something to read"
    )

  test("the oldest decision first; decided ones are not listed"):
    val as = List(
      approval(ApprovalInfo.Id(3), ApprovalInfo.Kind("edge"), ApprovalInfo.CreatedAt("2026-10-30T10:00:00Z")),
      approval(ApprovalInfo.Id(1), ApprovalInfo.Kind("edge"), ApprovalInfo.CreatedAt("2026-10-29T10:00:00Z")),
      approval(ApprovalInfo.Id(2), ApprovalInfo.Kind("edge"), ApprovalInfo.CreatedAt("2026-10-29T10:00:00Z"))
        .modify(_.state)
        .setTo(ApprovalInfo.State("approved"))
    )
    assertEquals(ResearchOverview.oldestFirst(as).map(_.id), List(1L, 3L))
    assertEquals(
      ResearchOverview.waited(ResearchOverview.EpochMillis(0), ResearchOverview.EpochMillis(5 * 3600000.0)),
      "5 hours"
    )
    assertEquals(
      ResearchOverview.waited(ResearchOverview.EpochMillis(0), ResearchOverview.EpochMillis(1800000.0)),
      "less than an hour"
    )
    assertEquals(
      ResearchOverview.waited(ResearchOverview.EpochMillis(0), ResearchOverview.EpochMillis(72 * 3600000.0)),
      "3 days"
    )

  private def manager(
      id: Managers.ManagerInfo.Id,
      goal: Goal.Slug,
      done: Managers.ItemCounts.Done,
      pending: Managers.ItemCounts.Pending,
      note: Option[Managers.ManagerInfo.StatusNote] = None
  ) =
    Managers.ManagerInfo(
      id,
      Managers.ManagerInfo.Kind("seed"),
      goal = Some(goal),
      counts = Managers.ItemCounts(pending = pending, done = done),
      statusNote = note
    )

  test("a goal's progress is its managers' steps done of all; what blocks it is said in words"):
    import com.histmersion.api.automation.Goal
    import Managers.{ItemCounts, ManagerInfo}
    val ms = List(
      manager(ManagerInfo.Id("seed:a"), Goal.Slug("a"), ItemCounts.Done(8), ItemCounts.Pending(3)),
      manager(ManagerInfo.Id("watch:a"), Goal.Slug("a"), ItemCounts.Done(0), ItemCounts.Pending(1)),
      manager(ManagerInfo.Id("seed:b"), Goal.Slug("b"), ItemCounts.Done(1), ItemCounts.Pending(0))
    )
    assertEquals(
      ResearchOverview.progressOf(Goal.Slug("a"), ms),
      ResearchOverview.Progress(ResearchOverview.Progress.Done(8), ResearchOverview.Progress.Total(12))
    )
    assertEquals(ResearchOverview.progressOf(Goal.Slug("a"), ms).pct, 66)
    assertEquals(
      ResearchOverview.progressOf(Goal.Slug("none"), ms),
      ResearchOverview.Progress(ResearchOverview.Progress.Done(0), ResearchOverview.Progress.Total(0))
    )
    val g = Autopilot.GoalProgress(
      Autopilot.GoalProgress.Slug("a"),
      Autopilot.GoalProgress.Title("A"),
      Autopilot.GoalProgress.Status("seeding"),
      openApprovals = Autopilot.GoalProgress.OpenApprovals(2)
    )
    val focus = Autopilot.FocusInfo(
      on = Autopilot.FocusInfo.On(true),
      waiting = List(
        Autopilot.FocusWaiting(
          Autopilot.FocusWaiting.Slug("a"),
          Autopilot.FocusWaiting.Code("person"),
          Autopilot.FocusWaiting.Why("an approval only a person decides")
        )
      )
    )
    assertEquals(ResearchOverview.blocker(g, Some(focus), ms), Some("an approval only a person decides"))
    assertEquals(ResearchOverview.blocker(g, None, ms), Some("waits for 2 of your decisions"))
    assertEquals(
      ResearchOverview
        .blocker(
          g.modify(_.openApprovals).setTo(Autopilot.GoalProgress.OpenApprovals(0)),
          None,
          List(
            manager(
              ManagerInfo.Id("seed:a"),
              Goal.Slug("a"),
              ItemCounts.Done(0),
              ItemCounts.Pending(1),
              Some(ManagerInfo.StatusNote("starved: no model"))
            )
          )
        ),
      Some("starved: no model")
    )
    assertEquals(
      ResearchOverview.blocker(g.modify(_.openApprovals).setTo(Autopilot.GoalProgress.OpenApprovals(0)), None, ms),
      None
    )

  test("the weekly allowance: each plan that reports this week's limit"):
    val w = PlanQuota.WindowStatus(
      PlanQuota.WindowStatus.Limit("claude"),
      PlanQuota.WindowStatus.Kind("seven_day"),
      PlanQuota.WindowStatus.UsedPct(39),
      PlanQuota.WindowStatus.LeftPct(61),
      PlanQuota.WindowStatus.Floor(10),
      usable = PlanQuota.WindowStatus.Usable(true),
      stale = PlanQuota.WindowStatus.Stale(false),
      None,
      PlanQuota.WindowStatus.ObservedAt(""),
      PlanQuota.WindowStatus.AgeSeconds(0),
      PlanQuota.WindowStatus.Source("")
    )
    val pool = PlanQuota.PoolStatus(
      PlanQuota.PoolStatus.Pool("claude"),
      PlanQuota.PoolStatus.Title("Claude plan"),
      PlanQuota.PoolStatus.Kind("claude"),
      Nil,
      PlanQuota.PoolStatus.Floors(""),
      PlanQuota.PoolStatus.FloorsSource(""),
      held = PlanQuota.PoolStatus.Held(false),
      usable = PlanQuota.PoolStatus.Usable(true),
      None,
      None,
      PlanQuota.PoolStatus.MaxCallsInFlight(1),
      List(w)
    )
    assertEquals(
      ResearchOverview.weekly(
        PlanQuota.QuotaInfo(List(pool, pool.modify(_.windows).setTo(Nil)), Nil, Nil, PlanQuota.QuotaInfo.Now(""))
      ),
      List("Claude plan: 61% of this week's limit left")
    )
