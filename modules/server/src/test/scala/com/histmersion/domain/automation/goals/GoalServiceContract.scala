package com.histmersion
package domain.automation.goals

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}
import com.histmersion.api.automation.ScheduleKind

import java.time.Instant
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.Goal as DomainGoal
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.domain.Title
import com.histmersion.api.catalogue.Year

/** The contract the goals keep: put and read back (sub-goals, a refused period), links in their order with what they
  * point at, plans and caps, the schedules not moved yet and their move.
  */
abstract class GoalServiceContract extends EngineStoreSuite:
  private def g(s: EngineStores) = s.goals
  private def bare(s: EngineStores, slug: DomainGoal.Slug) = g(s).get(slug).map(_.modify(_.updatedAt).setTo(None))

  stest("a goal is put, replaced and read back; its sub-goals by slug; a period ending before it starts is refused") {
    s =>
      assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-b"), Goal.Put(Goal.Title("B"), periodStart = Some(Year(-1200)))).isRight)
      assert(
        g(s)
          .put(
            com.histmersion.domain.automation.Goal.Slug("zz-a"),
            Goal
              .Put(Goal.Title("A"), Goal.StatusValue("active"), Some(Goal.Notes("n")), List(Goal.Tags("t")), List(Goal.Regions("r")), Some(Year(-3000)), Some(Year(-2000)))
          )
          .isRight
      )
      assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-a2"), Goal.Put(Goal.Title("A2"), parentSlug = Some(Goal.Slug("zz-a")))).isRight)
      assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-a1"), Goal.Put(Goal.Title("A1"), parentSlug = Some(Goal.Slug("zz-a")))).isRight)
      assertEquals(
        bare(s, DomainGoal.Slug("zz-a")).map(x => (x.title, x.status, x.notes, x.tags, x.regions, x.periodStart, x.periodEnd)),
        Some(("A", "active", Some("n"), List("t"), List("r"), Some(-3000L), Some(-2000L)))
      )
      assertEquals(
        bare(s, DomainGoal.Slug("zz-b")).map(x => (x.status, x.periodStart, x.periodEnd)),
        Some(("planned", Some(-1200L), None))
      )
      assert(g(s).get(com.histmersion.domain.automation.Goal.Slug("zz-a")).flatMap(_.updatedAt).isDefined)
      assertEquals(g(s).all().map(_.slug), List("zz-a", "zz-a1", "zz-a2", "zz-b"))
      assertEquals(g(s).children(com.histmersion.domain.automation.Goal.Slug("zz-a")).map(_.slug), List("zz-a1", "zz-a2"))
      assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-b"), Goal.Put(Goal.Title("B again"))).isRight)
      assertEquals(bare(s, DomainGoal.Slug("zz-b")).map(x => (x.title, x.periodStart)), Some(("B again", None)))
      val bad =
        g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-c"), Goal.Put(Goal.Title("C"), periodStart = Some(Year(100)), periodEnd = Some(Year(-100))))
      assert(bad.left.exists(_.message.value.contains("goal 'zz-c'")), bad)
      assertEquals(g(s).get(com.histmersion.domain.automation.Goal.Slug("zz-c")), None)
  }

  stest(
    "links: only to a goal that exists, sources then articles then queries, a note kept unless a new one is given"
  ) { s =>
    assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-g"), Goal.Put(Goal.Title("G"))).isRight)
    s.source(Citekey("zz2020_src"), Title("A source"))
    assert(!g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-none"), com.histmersion.domain.automation.Goal.LinkKind.Source, Goal.Plans.GoalLink.Ref("zz2020_src"), None), "no such goal")
    assert(g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.Query, Goal.Plans.GoalLink.Ref("tin trade"), None))
    assert(g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.Article, Goal.Plans.GoalLink.Ref("zz-art"), Some(Goal.Plans.GoalLink.Note("why"))))
    assert(g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.Source, Goal.Plans.GoalLink.Ref("zz2020_src"), Some(Goal.Plans.GoalLink.Note("first"))))
    assert(g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.Source, Goal.Plans.GoalLink.Ref("zz2020_src"), None))
    val ls = g(s).links(com.histmersion.domain.automation.Goal.Slug("zz-g"))
    assertEquals(
      ls.map(l => (l.kind, l.ref, l.note, l.title)),
      List(
        (Goal.LinkKind.Source, "zz2020_src", Some("first"), Some("A source")),
        (Goal.LinkKind.Article, "zz-art", Some("why"), None),
        (Goal.LinkKind.Query, "tin trade", None, None)
      )
    )
    assert(ls.forall(_.createdAt.value.matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ")), ls)
    assert(g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.Source, Goal.Plans.GoalLink.Ref("zz2020_src"), Some(Goal.Plans.GoalLink.Note("second"))))
    assertEquals(g(s).links(com.histmersion.domain.automation.Goal.Slug("zz-g")).head.note, Some("second"))
    assertEquals(g(s).linkCounts(), Map(("zz-g", "source") -> 1, ("zz-g", "query") -> 1))
    s.article(Slug("zz-art"), Title("An article"))
    assertEquals(g(s).linkCounts().get((com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.Wire("article"))), Some(1))
    assertEquals(g(s).links(com.histmersion.domain.automation.Goal.Slug("zz-g"))(1).title, Some("An article"))
    assert(g(s).unlink(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.Query, Goal.Plans.GoalLink.Ref("tin trade")))
    assert(!g(s).unlink(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.Query, Goal.Plans.GoalLink.Ref("tin trade")))
    assertEquals(g(s).links(com.histmersion.domain.automation.Goal.Slug("zz-g")).map(_.kind), List(Goal.LinkKind.Source, Goal.LinkKind.Article))
  }

  stest("the refs of one kind of a goal's links, oldest first; another kind's and another goal's left out") { s =>
    assert(s.goals.put(com.histmersion.domain.automation.Goal.Slug("zz-g"), Goal.Put(Goal.Title("A goal"))).isRight)
    assert(s.goals.put(com.histmersion.domain.automation.Goal.Slug("zz-h"), Goal.Put(Goal.Title("Another"))).isRight)
    assert(s.goals.link(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.SearchQuery, Goal.Plans.GoalLink.Ref("storm gods"), None))
    assert(s.goals.link(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.SearchQuery, Goal.Plans.GoalLink.Ref("anatolia"), None))
    assert(s.goals.link(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.QueueItem, Goal.Plans.GoalLink.Ref("7"), Some(Goal.Plans.GoalLink.Note("10.5555/zz"))))
    assert(s.goals.link(com.histmersion.domain.automation.Goal.Slug("zz-h"), com.histmersion.domain.automation.Goal.LinkKind.SearchQuery, Goal.Plans.GoalLink.Ref("elsewhere"), None))
    assertEquals(s.goals.refs(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.SearchQuery), List("storm gods", "anatolia"))
    assertEquals(s.goals.refs(com.histmersion.domain.automation.Goal.Slug("zz-g"), com.histmersion.domain.automation.Goal.LinkKind.QueueItem), List("7"))
    assertEquals(s.goals.refs(com.histmersion.domain.automation.Goal.Slug("zz-none"), com.histmersion.domain.automation.Goal.LinkKind.SearchQuery), Nil)
  }

  stest("an approved plan and a run cap; the planned goals") { s =>
    assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-p"), Goal.Put(Goal.Title("P"))).isRight)
    assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-q"), Goal.Put(Goal.Title("Q"))).isRight)
    assertEquals(g(s).planAndBudget(com.histmersion.domain.automation.Goal.Slug("zz-none")), None)
    assertEquals(g(s).planAndBudget(com.histmersion.domain.automation.Goal.Slug("zz-p")), Some((None, None)))
    g(s).setPlan(com.histmersion.domain.automation.Goal.Slug("zz-p"), BGoalRow.Plan("""{"goal":"zz-p"}"""))
    g(s).setBudget(com.histmersion.domain.automation.Goal.Slug("zz-p"), Some(BigDecimal("2.50")))
    val Some((plan, usd)) = g(s).planAndBudget(com.histmersion.domain.automation.Goal.Slug("zz-p")): @unchecked
    assertEquals(
      plan.map(p => io.circe.parser.parse(p.value).toOption),
      Some(io.circe.parser.parse("""{"goal":"zz-p"}""").toOption)
    )
    assertEquals(usd.map(_.toDouble), Some(2.5))
    assertEquals(g(s).planned(), Set("zz-p"))
    g(s).setBudget(com.histmersion.domain.automation.Goal.Slug("zz-p"), None)
    assertEquals(g(s).planAndBudget(com.histmersion.domain.automation.Goal.Slug("zz-p")).flatMap(_._2), None)
  }

  stest("schedules not moved yet: one per goal and kind, the next due first; removed, or taken out to move") { s =>
    List(DomainGoal.Slug("zz-s1"), DomainGoal.Slug("zz-s2")).foreach(x => assert(g(s).put(x, Goal.Put(Goal.Title(x.value))).isRight))
    val t = Instant.parse("2026-11-02T03:00:00Z")
    g(s).setSchedule(com.histmersion.domain.automation.Goal.Slug("zz-s2"), com.histmersion.domain.automation.ScheduleKind.Currency, Goal.Plans.GoalSchedule.Cron("@weekly"), enabled = Goal.Plans.GoalSchedule.Enabled(true), None, None)
    g(s).setSchedule(com.histmersion.domain.automation.Goal.Slug("zz-s1"), com.histmersion.domain.automation.ScheduleKind.Currency, Goal.Plans.GoalSchedule.Cron("@daily"), enabled = Goal.Plans.GoalSchedule.Enabled(true), Some(BigDecimal(1)), Some(t))
    g(s).setSchedule(com.histmersion.domain.automation.Goal.Slug("zz-s1"), com.histmersion.domain.automation.ScheduleKind.Acquisition, Goal.Plans.GoalSchedule.Cron("@daily"), enabled = Goal.Plans.GoalSchedule.Enabled(false), None, Some(t))
    g(s).setSchedule(com.histmersion.domain.automation.Goal.Slug("zz-s1"), com.histmersion.domain.automation.ScheduleKind.Currency, Goal.Plans.GoalSchedule.Cron("@monthly"), enabled = Goal.Plans.GoalSchedule.Enabled(true), Some(BigDecimal(1)), Some(t))
    assertEquals(
      g(s).schedules(None).map(x => (x.goal, x.kind, x.cron, x.enabled, x.budgetUsd, x.nextRunAt)),
      List(
        ("zz-s1", ScheduleKind.Acquisition, "@daily", false, None, Some("2026-11-02T03:00:00Z")),
        ("zz-s1", ScheduleKind.Currency, "@monthly", true, Some(1.0), Some("2026-11-02T03:00:00Z")),
        ("zz-s2", ScheduleKind.Currency, "@weekly", true, None, None)
      )
    )
    assertEquals(g(s).schedules(Some(com.histmersion.domain.automation.Goal.Slug("zz-s2"))).map(_.kind), List(ScheduleKind.Currency))
    assertEquals(g(s).scheduleGoals(), List("zz-s1", "zz-s2"))
    assert(g(s).removeSchedule(com.histmersion.domain.automation.Goal.Slug("zz-s2"), com.histmersion.domain.automation.ScheduleKind.Currency))
    assert(!g(s).removeSchedule(com.histmersion.domain.automation.Goal.Slug("zz-s2"), com.histmersion.domain.automation.ScheduleKind.Currency))
    val taken = s.within(g(s).takeSchedulesIn(com.histmersion.domain.automation.Goal.Slug("zz-s1")))
    assertEquals(taken.map(_.kind).toSet, Set(ScheduleKind.Acquisition, ScheduleKind.Currency))
    assertEquals(g(s).schedules(None), Nil)
    assertEquals(g(s).scheduleGoals(), Nil)
  }

  stest("a page of goals by slug, of one status, with the total and the next offset") { s =>
    List(
      DomainGoal.Slug("zz-c") -> Goal.StatusValue("active"),
      DomainGoal.Slug("zz-a") -> Goal.StatusValue("planned"),
      DomainGoal.Slug("zz-b") -> Goal.StatusValue("active")
    ).foreach((slug, st) => assert(g(s).put(slug, Goal.Put(Goal.Title(slug.value.toUpperCase), st)).isRight))
    val first = g(s).page(None, PageSize(2), Offset(0))
    assertEquals(
      ((first.total.value: Int), first.goals.map(_.slug), (first.offset.value: Int), first.next.map(n => (n.value: Int))),
      (3, List("zz-a", "zz-b"), 0, Some(2))
    )
    assertEquals(g(s).page(None, PageSize(2), Offset(2)).goals.map(_.slug), List("zz-c"))
    assertEquals(g(s).page(Some(com.histmersion.domain.automation.Goal.Status.Wire(" ACTIVE ")), PageSize(10), Offset(0)).goals.map(_.slug), List("zz-b", "zz-c"))
  }

  stest("a patch changes what it names, keeps a stored bound given the other alone, clears a cap of 0") { s =>
    assert(
      g(s)
        .put(com.histmersion.domain.automation.Goal.Slug("zz-p"), Goal.Put(Goal.Title("P"), periodStart = Some(Year(-2000)), periodEnd = Some(Year(-1000))))
        .isRight
    )
    assert(
      g(s)
        .patch(
          com.histmersion.domain.automation.Goal.Slug("zz-p"),
          Goal.Patch(title = Some(Goal.Title("P2")), tags = Some(List(Goal.Tags("t"))), periodEnd = Some(Year(-1500)))
        )
        .isRight
    )
    assertEquals(
      bare(s, DomainGoal.Slug("zz-p")).map(x => (x.title, x.tags, x.periodStart, x.periodEnd)),
      Some(("P2", List("t"), Some(-2000L), Some(-1500L)))
    )
    assert(g(s).patch(com.histmersion.domain.automation.Goal.Slug("zz-p"), Goal.Patch(budgetUsd = Some(Goal.Patch.BudgetUsd(3.5)))).isRight)
    assertEquals(g(s).planAndBudget(com.histmersion.domain.automation.Goal.Slug("zz-p")).flatMap(_._2), Some(BigDecimal(3.5)))
    assert(g(s).patch(com.histmersion.domain.automation.Goal.Slug("zz-p"), Goal.Patch(budgetUsd = Some(Goal.Patch.BudgetUsd(0)))).isRight)
    assertEquals(g(s).planAndBudget(com.histmersion.domain.automation.Goal.Slug("zz-p")).flatMap(_._2), None)
    assert(
      g(s)
        .patch(com.histmersion.domain.automation.Goal.Slug("zz-none"), Goal.Patch(title = Some(Goal.Title("x"))))
        .left
        .exists(_.message.value.contains("zz-none"))
    )
  }

  stest("a goal is deleted once; a rename moves its slug, keeps the old one, refuses a taken slug") { s =>
    assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-old"), Goal.Put(Goal.Title("Old"))).isRight)
    assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-other"), Goal.Put(Goal.Title("Other"))).isRight)
    assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-kid"), Goal.Put(Goal.Title("Kid"), parentSlug = Some(Goal.Slug("zz-old")))).isRight)
    assert(g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-old"), com.histmersion.domain.automation.Goal.LinkKind.Query, Goal.Plans.GoalLink.Ref("tin"), None))
    assert(g(s).rename(com.histmersion.domain.automation.Goal.Slug("zz-old"), com.histmersion.domain.automation.Goal.Slug("zz-other")).isLeft)
    assert(g(s).rename(com.histmersion.domain.automation.Goal.Slug("zz-none"), com.histmersion.domain.automation.Goal.Slug("zz-x")).isLeft)
    assertEquals(g(s).rename(com.histmersion.domain.automation.Goal.Slug.assume(" zz-old "), com.histmersion.domain.automation.Goal.Slug.assume("zz-new ")), Right(()))
    assertEquals(g(s).get(com.histmersion.domain.automation.Goal.Slug("zz-old")), None)
    assertEquals(bare(s, DomainGoal.Slug("zz-new")).map(_.title), Some("Old"))
    assertEquals(bare(s, DomainGoal.Slug("zz-kid")).flatMap(_.parentSlug), Some("zz-new"))
    assertEquals(g(s).refs(com.histmersion.domain.automation.Goal.Slug("zz-new"), com.histmersion.domain.automation.Goal.LinkKind.Query), List("tin"))
    assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-third"), Goal.Put(Goal.Title("Third"))).isRight)
    assert(g(s).rename(com.histmersion.domain.automation.Goal.Slug("zz-third"), com.histmersion.domain.automation.Goal.Slug("zz-old")).left.exists(_.message.value.contains("old slug of 'zz-new'")))
    assert(g(s).delete(com.histmersion.domain.automation.Goal.Slug("zz-other")))
    assert(!g(s).delete(com.histmersion.domain.automation.Goal.Slug("zz-other")))
  }

  stest("the goals linking work-list items, an item's goals by title") { s =>
    assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-g1"), Goal.Put(Goal.Title("Beta"))).isRight)
    assert(g(s).put(com.histmersion.domain.automation.Goal.Slug("zz-g2"), Goal.Put(Goal.Title("Alpha"))).isRight)
    assert(g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-g1"), com.histmersion.domain.automation.Goal.LinkKind.QueueItem, Goal.Plans.GoalLink.Ref("7"), None))
    assert(g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-g2"), com.histmersion.domain.automation.Goal.LinkKind.QueueItem, Goal.Plans.GoalLink.Ref("7"), None))
    assert(g(s).link(com.histmersion.domain.automation.Goal.Slug("zz-g2"), com.histmersion.domain.automation.Goal.LinkKind.QueueItem, Goal.Plans.GoalLink.Ref("8"), None))
    assertEquals(g(s).goalsOfItems(List(GoalLinkRepo.ItemId(7))), List((7, "zz-g2", "Alpha"), (7, "zz-g1", "Beta")))
    assertEquals(g(s).goalsOfItems(Nil), Nil)
  }
