package com.histmersion
package domain.automation.goals

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}

import java.time.Instant
import com.histmersion.domain.automation.Goal

/** The contract the focus's reads and records keep: every goal with what the focus recorded; the goals of linked
  * sources and articles; each goal's last stop and its sources.
  */
abstract class FocusServiceContract extends EngineStoreSuite:
  private def f(s: EngineStores) = s.focus
  private val t = Instant.parse("2026-11-03T06:00:00Z")

  stest(
    "every goal with what the focus recorded: joined, left, a person's pin and priority (only of a goal that exists)"
  ) { s =>
    List(Goal.Slug("zz-a"), Goal.Slug("zz-b")).foreach(g =>
      assert(
        s.goals
          .put(g, com.histmersion.api.automation.Goal.Put(com.histmersion.api.automation.Goal.Title(g.value), parentSlug = Option.when(g == Goal.Slug("zz-b"))(com.histmersion.api.automation.Goal.Slug("zz-a"))))
          .isRight
      )
    )
    assertEquals(
      f(s).goals().map(g => (g.slug, g.active, g.pinned, g.priority, g.parent)),
      List(("zz-a", false, false, 0, None), ("zz-b", false, false, 0, Some("zz-a")))
    )
    f(s).joined(Goal.Slug("zz-a"), FocusRepo.JoinWhy("pinned"), t)
    assert(f(s).pin(Goal.Slug("zz-a"), pinned = FocusRepo.GoalRow.Pinned(true)))
    assert(f(s).priority(Goal.Slug("zz-b"), FocusRepo.GoalRow.Priority(5)))
    assert(!f(s).pin(Goal.Slug("zz-none"), pinned = FocusRepo.GoalRow.Pinned(true)))
    assert(!f(s).priority(Goal.Slug("zz-none"), FocusRepo.GoalRow.Priority(1)))
    val Some(a) = f(s).goals().find(_.slug.value == "zz-a"): @unchecked
    assertEquals((a.active, a.pinned, a.since, a.why), (true, true, Some("2026-11-03T06:00:00Z"), Some("pinned")))
    f(s).left(Goal.Slug("zz-a"), FocusRepo.GoalRow.LeftWhy("done"), t.plusSeconds(60))
    val Some(a2) = f(s).goals().find(_.slug.value == "zz-a"): @unchecked
    assertEquals(
      (a2.active, a2.leftAt, a2.leftWhy, a2.pinned),
      (false, Some("2026-11-03T06:01:00Z"), Some("done"), true)
    )
    assertEquals(f(s).goals().find(_.slug.value == "zz-b").map(_.priority), Some(5))
    assert(f(s).goals().forall(_.createdAt.value.nonEmpty))
  }

  stest("the goals linked to sources and articles; each goal's sources; its last stop") { s =>
    List(Goal.Slug("zz-a"), Goal.Slug("zz-b")).foreach(g => assert(s.goals.put(g, com.histmersion.api.automation.Goal.Put(com.histmersion.api.automation.Goal.Title(g.value))).isRight))
    assert(s.goals.link(Goal.Slug("zz-b"), Goal.LinkKind.Source, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz2020_s"), None))
    assert(s.goals.link(Goal.Slug("zz-a"), Goal.LinkKind.Source, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz2020_s"), None))
    assert(s.goals.link(Goal.Slug("zz-a"), Goal.LinkKind.Article, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz-art"), None))
    assertEquals(f(s).goalsOf(Goal.LinkKind.Wire("source"), List(com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz2020_s"), com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz2020_none"))), Map("zz2020_s" -> List("zz-a", "zz-b")))
    assertEquals(f(s).goalsOf(Goal.LinkKind.Wire("article"), List(com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz-art"))), Map("zz-art" -> List("zz-a")))
    assertEquals(f(s).goalsOf(Goal.LinkKind.Wire("source"), Nil), Map.empty)
    assertEquals(f(s).sourceCounts(), Map("zz-a" -> 1, "zz-b" -> 1))
    assert(s.managers.create(ManagerInfo.Id("seed:zz-a"), Kind.Seed, Some(Goal.Slug("zz-a")), State.Active, ManagerConfig()))
    val (i1, _) = s.managers.upsert(ManagerInfo.Id("seed:zz-a"), ManagerItem.Kind(ProposeArticles),ManagerItem.Target("zz-a"), ItemOrigin.Selection, ManagerItem.Priority(0), reopen = false)
    assert(s.managers.settle(i1, ItemStatus.Failed, ManagerItem.Outcome("the model gave up"), List(ItemStatus.Pending)))
    assertEquals(f(s).lastStops(), Map("zz-a" -> "the model gave up"))
    val (i2, _) = s.managers.upsert(ManagerInfo.Id("seed:zz-a"), ManagerItem.Kind(PlanGoalItem),ManagerItem.Target("zz-a"), ItemOrigin.Selection, ManagerItem.Priority(0), reopen = false)
    assert(s.managers.settle(i2, ItemStatus.Done, ManagerItem.Outcome("planned"), List(ItemStatus.Pending)))
    assertEquals(f(s).lastStops(), Map.empty, "its newest settled item is done")
  }
