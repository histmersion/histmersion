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

  private def g(slug: String, parent: Option[String] = None) =
    Goal(slug, slug.capitalize, com.histmersion.api.automation.Goal.StatusValue("planned"), parentSlug = parent)

  test("goals as a tree: roots first, sub-goals under them by slug; an orphan or a cycle cannot hide or loop"):
    val goals = List(
      g("b", Some("a")),
      g("a"),
      g("c", Some("a")),
      g("d", Some("b")),
      g("z", Some("missing")),
      g("x", Some("y")),
      g("y", Some("x"))
    )
    assertEquals(
      GoalsView.tree(goals).map((x, d) => (x.slug, d)),
      List(("a", 0), ("b", 1), ("d", 2), ("c", 1), ("z", 0))
    )

  test("the open plan of a goal"):
    val open = ApprovalInfo(com.histmersion.api.automation.Agents.ApprovalInfo.Id(1), Some(com.histmersion.api.automation.Agents.RunInfo.Id("r")), com.histmersion.api.automation.Agents.ApprovalInfo.Kind("goal-plan"), com.histmersion.api.library.Citekey(""), subject = Some(com.histmersion.api.automation.Agents.ApprovalInfo.Subject("a")))
    val done = open.modify(_.id).setTo((2: _root_.scala.Long)).modify(_.state).setTo("approved")
    val other = open.modify(_.id).setTo((3: _root_.scala.Long)).modify(_.subject).setTo(Some("b"))
    val edge = ApprovalInfo(com.histmersion.api.automation.Agents.ApprovalInfo.Id(4), None, com.histmersion.api.automation.Agents.ApprovalInfo.Kind("edge"), com.histmersion.api.library.Citekey("a2020_b"))
    assertEquals(GoalsView.pendingPlan(List(edge, done, other, open), com.histmersion.api.automation.Goal.Slug("a")).map(_.id), Some(1L))
    assertEquals(GoalsView.pendingPlan(List(done, edge), com.histmersion.api.automation.Goal.Slug("a")), None)

  test("schedule defaults and links to add"):
    assertEquals(GoalsView.defaultCron(ScheduleKind.Currency), "@weekly")
    assertEquals(GoalsView.defaultCron(ScheduleKind.Acquisition), "@monthly")
    assertEquals(
      GoalsView.linkToAdd(LinkKind.Query, com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref("  tin trade "), com.histmersion.api.automation.Goal.Plans.LinkAdd.Note(" ")),
      Some(LinkAdd(LinkKind.Query, com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref("tin trade"), None))
    )
    assertEquals(GoalsView.linkToAdd(LinkKind.Source, com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref("  "), com.histmersion.api.automation.Goal.Plans.LinkAdd.Note("why")), None)
    val ls = List(GoalLink(LinkKind.Source, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("a")), GoalLink(LinkKind.Query, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("q")), GoalLink(LinkKind.Source, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("b")))
    assertEquals(GoalsView.linksOf(ls, LinkKind.Source).map(_.ref), List("a", "b"))

  test("every goal stage has a meaning, in the order a goal moves through them"):
    assertEquals(GoalsView.stages.map(_._1), com.histmersion.api.automation.Goal.Status.all)
    assert(GoalsView.stages.forall(_._2.nonEmpty))
    assertEquals(GoalsView.meaning(com.histmersion.api.automation.Goal.Stage("nonsense")), None)
