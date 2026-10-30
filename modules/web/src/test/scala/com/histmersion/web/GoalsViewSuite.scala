package com.histmersion.web

import com.histmersion.api.Goal
import com.histmersion.api.Agents.ApprovalInfo
import com.histmersion.api.GoalPlans.*

/** The pure parts of the goals windows: the tree, the open plan of a goal, the schedule editor's defaults, and a link
  * to add.
  */
class GoalsViewSuite extends munit.FunSuite:

  private def g(slug: String, parent: Option[String] = None) =
    Goal(slug, slug.capitalize, "planned", parentSlug = parent)

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
    val open = ApprovalInfo(1, Some("r"), "goal-plan", "", subject = Some("a"))
    val done = open.copy(id = 2, state = "approved")
    val other = open.copy(id = 3, subject = Some("b"))
    val edge = ApprovalInfo(4, None, "edge", "a2020_b")
    assertEquals(GoalsView.pendingPlan(List(edge, done, other, open), "a").map(_.id), Some(1L))
    assertEquals(GoalsView.pendingPlan(List(done, edge), "a"), None)

  test("schedule defaults and links to add"):
    assertEquals(GoalsView.defaultCron(ScheduleKind.Currency), "@weekly")
    assertEquals(GoalsView.defaultCron(ScheduleKind.Acquisition), "@monthly")
    assertEquals(
      GoalsView.linkToAdd(LinkKind.Query, "  tin trade ", " "),
      Some(LinkAdd(LinkKind.Query, "tin trade", None))
    )
    assertEquals(GoalsView.linkToAdd(LinkKind.Source, "  ", "why"), None)
    val ls = List(GoalLink(LinkKind.Source, "a"), GoalLink(LinkKind.Query, "q"), GoalLink(LinkKind.Source, "b"))
    assertEquals(GoalsView.linksOf(ls, LinkKind.Source).map(_.ref), List("a", "b"))

  test("every goal stage has a meaning, in the order a goal moves through them"):
    assertEquals(GoalsView.stages.map(_._1), com.histmersion.values.GoalStatus.all)
    assert(GoalsView.stages.forall(_._2.nonEmpty))
    assertEquals(GoalsView.meaning("nonsense"), None)
