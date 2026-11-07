package com.histmersion.domain.automation.goals

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
    List("zz-a", "zz-b").foreach(g =>
      assert(
        s.goals
          .put(g, com.histmersion.api.automation.Goal.Put(g, parentSlug = Option.when(g == "zz-b")("zz-a")))
          .isRight
      )
    )
    assertEquals(
      f(s).goals().map(g => (g.slug, g.active, g.pinned, g.priority, g.parent)),
      List(("zz-a", false, false, 0, None), ("zz-b", false, false, 0, Some("zz-a")))
    )
    f(s).joined(com.histmersion.domain.automation.Goal.Slug("zz-a"), com.histmersion.domain.automation.goals.FocusRepo.JoinWhy("pinned"), t)
    assert(f(s).pin(com.histmersion.domain.automation.Goal.Slug("zz-a"), pinned = com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Pinned(true)))
    assert(f(s).priority(com.histmersion.domain.automation.Goal.Slug("zz-b"), com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Priority(5)))
    assert(!f(s).pin(com.histmersion.domain.automation.Goal.Slug("zz-none"), pinned = com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Pinned(true)))
    assert(!f(s).priority(com.histmersion.domain.automation.Goal.Slug("zz-none"), com.histmersion.domain.automation.goals.FocusRepo.GoalRow.Priority(1)))
    val Some(a) = f(s).goals().find(_.slug == "zz-a"): @unchecked
    assertEquals((a.active, a.pinned, a.since, a.why), (true, true, Some("2026-11-03T06:00:00Z"), Some("pinned")))
    f(s).left(com.histmersion.domain.automation.Goal.Slug("zz-a"), com.histmersion.domain.automation.goals.FocusRepo.GoalRow.LeftWhy("done"), t.plusSeconds(60))
    val Some(a2) = f(s).goals().find(_.slug == "zz-a"): @unchecked
    assertEquals(
      (a2.active, a2.leftAt, a2.leftWhy, a2.pinned),
      (false, Some("2026-11-03T06:01:00Z"), Some("done"), true)
    )
    assertEquals(f(s).goals().find(_.slug == "zz-b").map(_.priority), Some(5))
    assert(f(s).goals().forall(_.createdAt.nonEmpty))
  }

  stest("the goals linked to sources and articles; each goal's sources; its last stop") { s =>
    List("zz-a", "zz-b").foreach(g => assert(s.goals.put(g, com.histmersion.api.automation.Goal.Put(g)).isRight))
    assert(s.goals.link(com.histmersion.domain.automation.Goal.Slug("zz-b"), Goal.LinkKind.Source, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz2020_s"), None))
    assert(s.goals.link(com.histmersion.domain.automation.Goal.Slug("zz-a"), Goal.LinkKind.Source, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz2020_s"), None))
    assert(s.goals.link(com.histmersion.domain.automation.Goal.Slug("zz-a"), Goal.LinkKind.Article, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz-art"), None))
    assertEquals(f(s).goalsOf(com.histmersion.domain.automation.Goal.LinkKind.Wire("source"), List(com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz2020_s"), com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz2020_none"))), Map("zz2020_s" -> List("zz-a", "zz-b")))
    assertEquals(f(s).goalsOf(com.histmersion.domain.automation.Goal.LinkKind.Wire("article"), List(com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz-art"))), Map("zz-art" -> List("zz-a")))
    assertEquals(f(s).goalsOf(com.histmersion.domain.automation.Goal.LinkKind.Wire("source"), Nil), Map.empty)
    assertEquals(f(s).sourceCounts(), Map("zz-a" -> 1, "zz-b" -> 1))
    assert(s.managers.create(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-a"), Kind.Seed, Some(com.histmersion.domain.automation.Goal.Slug("zz-a")), State.Active, ManagerConfig()))
    val (i1, _) = s.managers.upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-a"), ProposeArticles, com.histmersion.api.automation.Managers.ManagerItem.Target("zz-a"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)
    assert(s.managers.settle(i1, ItemStatus.Failed, com.histmersion.api.automation.Managers.ManagerItem.Outcome("the model gave up"), List(ItemStatus.Pending)))
    assertEquals(f(s).lastStops(), Map("zz-a" -> "the model gave up"))
    val (i2, _) = s.managers.upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-a"), PlanGoalItem, com.histmersion.api.automation.Managers.ManagerItem.Target("zz-a"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)
    assert(s.managers.settle(i2, ItemStatus.Done, com.histmersion.api.automation.Managers.ManagerItem.Outcome("planned"), List(ItemStatus.Pending)))
    assertEquals(f(s).lastStops(), Map.empty, "its newest settled item is done")
  }
