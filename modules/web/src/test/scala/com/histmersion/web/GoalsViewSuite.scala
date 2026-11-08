package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Agents.ApprovalInfo
import com.histmersion.api.automation.Goal.Plans.*

/** The pure parts of the goals windows: the tree, the open plan of a goal, the schedule editor's defaults, and a link
  * to add.
  */
class GoalsViewSuite extends munit.FunSuite:

  private def g(slug: Goal.Slug, parent: Option[Goal.Slug] = None) =
    Goal(slug, Goal.Title(slug.value.capitalize), com.histmersion.api.automation.Goal.StatusValue("planned"), parentSlug = parent)

  test("goals as a tree: roots first, sub-goals under them by slug; an orphan or a cycle cannot hide or loop"):
    val goals = List(
      g(Goal.Slug("b"), Some(Goal.Slug("a"))),
      g(Goal.Slug("a")),
      g(Goal.Slug("c"), Some(Goal.Slug("a"))),
      g(Goal.Slug("d"), Some(Goal.Slug("b"))),
      g(Goal.Slug("z"), Some(Goal.Slug("missing"))),
      g(Goal.Slug("x"), Some(Goal.Slug("y"))),
      g(Goal.Slug("y"), Some(Goal.Slug("x")))
    )
    assertEquals(
      GoalsView.tree(goals).map((x, d) => (x.slug.value, d.value)),
      List(("a", 0), ("b", 1), ("d", 2), ("c", 1), ("z", 0))
    )

  test("the open plan of a goal"):
    val open = ApprovalInfo(com.histmersion.api.automation.Agents.ApprovalInfo.Id(1), Some(com.histmersion.api.automation.Agents.RunInfo.Id("r")), com.histmersion.api.automation.Agents.ApprovalInfo.Kind("goal-plan"), com.histmersion.api.library.Citekey(""), subject = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Subject("a")))
    val done = open.modify(_.id).setTo(ApprovalInfo.Id(2)).modify(_.state).setTo(ApprovalInfo.State("approved"))
    val other = open.modify(_.id).setTo(ApprovalInfo.Id(3)).modify(_.subject).setTo(Some(ApprovalInfo.Subject("b")))
    val edge = ApprovalInfo(com.histmersion.api.automation.Agents.ApprovalInfo.Id(4), None, com.histmersion.api.automation.Agents.ApprovalInfo.Kind("edge"), com.histmersion.api.library.Citekey("a2020_b"))
    assertEquals(GoalsView.pendingPlan(List(edge, done, other, open), com.histmersion.api.automation.Goal.Slug("a")).map(_.id.value), Some(1L))
    assertEquals(GoalsView.pendingPlan(List(done, edge), com.histmersion.api.automation.Goal.Slug("a")), None)

  test("schedule defaults and links to add"):
    assertEquals(GoalsView.defaultCron(ScheduleKind.Currency).value, "@weekly")
    assertEquals(GoalsView.defaultCron(ScheduleKind.Acquisition).value, "@monthly")
    assertEquals(
      GoalsView.linkToAdd(LinkKind.Query, com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref("  tin trade "), com.histmersion.api.automation.Goal.Plans.LinkAdd.Note(" ")),
      Some(LinkAdd(LinkKind.Query, com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref("tin trade"), None))
    )
    assertEquals(GoalsView.linkToAdd(LinkKind.Source, com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref("  "), com.histmersion.api.automation.Goal.Plans.LinkAdd.Note("why")), None)
    val ls = List(GoalLink(LinkKind.Source, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("a")), GoalLink(LinkKind.Query, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("q")), GoalLink(LinkKind.Source, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("b")))
    assertEquals(GoalsView.linksOf(ls, LinkKind.Source).map(_.ref.value), List("a", "b"))

  test("every goal stage has a meaning, in the order a goal moves through them"):
    assertEquals(GoalsView.stages.map(_._1.value), com.histmersion.api.automation.Goal.Status.all.map(_.value))
    assert(GoalsView.stages.forall(_._2.value.nonEmpty))
    assertEquals(GoalsView.meaning(com.histmersion.api.automation.Goal.Stage("nonsense")), None)
