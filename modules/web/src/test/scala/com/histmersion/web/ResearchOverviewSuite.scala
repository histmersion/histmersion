package com.histmersion
package web

import com.histmersion.api.automation.agents.ApprovalInfo
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.managers.ManagerInfo
import com.histmersion.api.automation.managers.ItemCounts
import com.histmersion.api.automation.autopilot.GoalProgress
import com.histmersion.api.automation.autopilot.FocusInfo
import com.histmersion.api.automation.autopilot.FocusWaiting
import com.histmersion.api.automation.planquota.WindowStatus
import com.histmersion.api.automation.planquota.PoolStatus
import com.histmersion.api.automation.planquota.QuotaInfo

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.automation.Manager
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.{Autopilot, Managers, PlanQuota}
import com.histmersion.api.automation.Agents
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Goal
import com.histmersion.lint.{tolerated, LintRule}

/** The overview's pure parts: what to read before a decision, the oldest first, a goal's progress and what blocks it,
  * the weekly allowance.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
      Some(RunInfo.Id("r1")),
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
      id: ManagerInfo.Id,
      goal: Goal.Slug,
      done: ItemCounts.Done,
      pending: ItemCounts.Pending,
      note: Option[Manager.Status] = None
  ) =
    ManagerInfo(
      id,
      ManagerInfo.Kind("seed"),
      goal = Some(goal),
      counts = ItemCounts(pending = pending, done = done),
      statusNote = note
    )

  test("a goal's progress is its managers' steps done of all; what blocks it is said in words"):
    import com.histmersion.api.automation.Goal
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
    val g = GoalProgress(
      GoalProgress.Slug("a"),
      GoalProgress.Title("A"),
      GoalProgress.Status("seeding"),
      openApprovals = GoalProgress.OpenApprovals(2)
    )
    val focus = FocusInfo(
      on = FocusInfo.On.On,
      waiting = List(
        FocusWaiting(
          FocusWaiting.Slug("a"),
          FocusWaiting.Code("person"),
          Autopilot.Rationale("an approval only a person decides")
        )
      )
    )
    assertEquals(ResearchOverview.blocker(g, Some(focus), ms), Some("an approval only a person decides"))
    assertEquals(ResearchOverview.blocker(g, None, ms), Some("waits for 2 of your decisions"))
    assertEquals(
      ResearchOverview
        .blocker(
          g.modify(_.openApprovals).setTo(GoalProgress.OpenApprovals(0)),
          None,
          List(
            manager(
              ManagerInfo.Id("seed:a"),
              Goal.Slug("a"),
              ItemCounts.Done(0),
              ItemCounts.Pending(1),
              Some(Manager.Status.parse("starved: no model"))
            )
          )
        ),
      Some("starved: no model")
    )
    assertEquals(
      ResearchOverview.blocker(g.modify(_.openApprovals).setTo(GoalProgress.OpenApprovals(0)), None, ms),
      None
    )

  test("the weekly allowance: each plan that reports this week's limit"):
    val w = WindowStatus(
      WindowStatus.Limit("claude"),
      WindowStatus.Kind("seven_day"),
      WindowStatus.UsedPct(39),
      WindowStatus.LeftPct(61),
      WindowStatus.Floor(10),
      usable = WindowStatus.Usable.Usable,
      stale = WindowStatus.Stale.Fresh,
      None,
      WindowStatus.ObservedAt(""),
      WindowStatus.AgeSeconds(0),
      WindowStatus.Source("")
    )
    val pool = PoolStatus(
      PoolStatus.Pool("claude"),
      PoolStatus.Title("Claude plan"),
      PoolStatus.Kind("claude"),
      Nil,
      PoolStatus.Floors(""),
      PoolStatus.FloorsSource(""),
      held = PoolStatus.Held.Released,
      usable = PoolStatus.Usable.Usable,
      None,
      None,
      PoolStatus.MaxCallsInFlight(1),
      List(w)
    )
    assertEquals(
      ResearchOverview.weekly(
        QuotaInfo(List(pool, pool.modify(_.windows).setTo(Nil)), Nil, Nil, QuotaInfo.Now(""))
      ),
      List("Claude plan: 61% of this week's limit left")
    )
