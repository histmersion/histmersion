package com.histmersion.domain.automation.goals

import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}
import com.histmersion.domain.automation.ScheduleKind

import java.time.Instant
import com.histmersion.domain.automation.Goal

/** The contract the goals keep: put and read back (sub-goals, a refused period), links in their order with what they
  * point at, plans and caps, the schedules not moved yet and their move.
  */
abstract class GoalServiceContract extends EngineStoreSuite:
  private def g(s: EngineStores) = s.goals
  private def bare(s: EngineStores, slug: String) = g(s).get(slug).map(_.copy(updatedAt = None))

  stest("a goal is put, replaced and read back; its sub-goals by slug; a period ending before it starts is refused") {
    s =>
      assert(g(s).put("zz-b", com.histmersion.api.automation.Goal.Put("B", periodStart = Some(-1200))).isRight)
      assert(
        g(s)
          .put(
            "zz-a",
            com.histmersion.api.automation.Goal
              .Put("A", "active", Some("n"), List("t"), List("r"), Some(-3000), Some(-2000))
          )
          .isRight
      )
      assert(g(s).put("zz-a2", com.histmersion.api.automation.Goal.Put("A2", parentSlug = Some("zz-a"))).isRight)
      assert(g(s).put("zz-a1", com.histmersion.api.automation.Goal.Put("A1", parentSlug = Some("zz-a"))).isRight)
      assertEquals(
        bare(s, "zz-a").map(x => (x.title, x.status, x.notes, x.tags, x.regions, x.periodStart, x.periodEnd)),
        Some(("A", "active", Some("n"), List("t"), List("r"), Some(-3000L), Some(-2000L)))
      )
      assertEquals(
        bare(s, "zz-b").map(x => (x.status, x.periodStart, x.periodEnd)),
        Some(("planned", Some(-1200L), None))
      )
      assert(g(s).get("zz-a").flatMap(_.updatedAt).isDefined)
      assertEquals(g(s).all().map(_.slug), List("zz-a", "zz-a1", "zz-a2", "zz-b"))
      assertEquals(g(s).children("zz-a").map(_.slug), List("zz-a1", "zz-a2"))
      assert(g(s).put("zz-b", com.histmersion.api.automation.Goal.Put("B again")).isRight)
      assertEquals(bare(s, "zz-b").map(x => (x.title, x.periodStart)), Some(("B again", None)))
      val bad =
        g(s).put("zz-c", com.histmersion.api.automation.Goal.Put("C", periodStart = Some(100), periodEnd = Some(-100)))
      assert(bad.left.exists(_.message.contains("goal 'zz-c'")), bad)
      assertEquals(g(s).get("zz-c"), None)
  }

  stest(
    "links: only to a goal that exists, sources then articles then queries, a note kept unless a new one is given"
  ) { s =>
    assert(g(s).put("zz-g", com.histmersion.api.automation.Goal.Put("G")).isRight)
    s.source("zz2020_src", "A source")
    assert(!g(s).link("zz-none", Goal.LinkKind.Source, "zz2020_src", None), "no such goal")
    assert(g(s).link("zz-g", Goal.LinkKind.Query, "tin trade", None))
    assert(g(s).link("zz-g", Goal.LinkKind.Article, "zz-art", Some("why")))
    assert(g(s).link("zz-g", Goal.LinkKind.Source, "zz2020_src", Some("first")))
    assert(g(s).link("zz-g", Goal.LinkKind.Source, "zz2020_src", None))
    val ls = g(s).links("zz-g")
    assertEquals(
      ls.map(l => (l.kind, l.ref, l.note, l.title)),
      List(
        (Goal.LinkKind.Source, "zz2020_src", Some("first"), Some("A source")),
        (Goal.LinkKind.Article, "zz-art", Some("why"), None),
        (Goal.LinkKind.Query, "tin trade", None, None)
      )
    )
    assert(ls.forall(_.createdAt.matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ")), ls)
    assert(g(s).link("zz-g", Goal.LinkKind.Source, "zz2020_src", Some("second")))
    assertEquals(g(s).links("zz-g").head.note, Some("second"))
    assertEquals(g(s).linkCounts(), Map(("zz-g", "source") -> 1, ("zz-g", "query") -> 1))
    s.article("zz-art", "An article")
    assertEquals(g(s).linkCounts().get(("zz-g", "article")), Some(1))
    assertEquals(g(s).links("zz-g")(1).title, Some("An article"))
    assert(g(s).unlink("zz-g", Goal.LinkKind.Query, "tin trade"))
    assert(!g(s).unlink("zz-g", Goal.LinkKind.Query, "tin trade"))
    assertEquals(g(s).links("zz-g").map(_.kind), List(Goal.LinkKind.Source, Goal.LinkKind.Article))
  }

  stest("the refs of one kind of a goal's links, oldest first; another kind's and another goal's left out") { s =>
    assert(s.goals.put("zz-g", com.histmersion.api.automation.Goal.Put("A goal")).isRight)
    assert(s.goals.put("zz-h", com.histmersion.api.automation.Goal.Put("Another")).isRight)
    assert(s.goals.link("zz-g", Goal.LinkKind.SearchQuery, "storm gods", None))
    assert(s.goals.link("zz-g", Goal.LinkKind.SearchQuery, "anatolia", None))
    assert(s.goals.link("zz-g", Goal.LinkKind.QueueItem, "7", Some("10.5555/zz")))
    assert(s.goals.link("zz-h", Goal.LinkKind.SearchQuery, "elsewhere", None))
    assertEquals(s.goals.refs("zz-g", Goal.LinkKind.SearchQuery), List("storm gods", "anatolia"))
    assertEquals(s.goals.refs("zz-g", Goal.LinkKind.QueueItem), List("7"))
    assertEquals(s.goals.refs("zz-none", Goal.LinkKind.SearchQuery), Nil)
  }

  stest("an approved plan and a run cap; the planned goals") { s =>
    assert(g(s).put("zz-p", com.histmersion.api.automation.Goal.Put("P")).isRight)
    assert(g(s).put("zz-q", com.histmersion.api.automation.Goal.Put("Q")).isRight)
    assertEquals(g(s).planAndBudget("zz-none"), None)
    assertEquals(g(s).planAndBudget("zz-p"), Some((None, None)))
    g(s).setPlan("zz-p", """{"goal":"zz-p"}""")
    g(s).setBudget("zz-p", Some(BigDecimal("2.50")))
    val Some((plan, usd)) = g(s).planAndBudget("zz-p"): @unchecked
    assertEquals(
      plan.map(io.circe.parser.parse(_).toOption),
      Some(io.circe.parser.parse("""{"goal":"zz-p"}""").toOption)
    )
    assertEquals(usd.map(_.toDouble), Some(2.5))
    assertEquals(g(s).planned(), Set("zz-p"))
    g(s).setBudget("zz-p", None)
    assertEquals(g(s).planAndBudget("zz-p").flatMap(_._2), None)
  }

  stest("schedules not moved yet: one per goal and kind, the next due first; removed, or taken out to move") { s =>
    List("zz-s1", "zz-s2").foreach(x => assert(g(s).put(x, com.histmersion.api.automation.Goal.Put(x)).isRight))
    val t = Instant.parse("2026-11-02T03:00:00Z")
    g(s).setSchedule("zz-s2", ScheduleKind.Currency, "@weekly", enabled = true, None, None)
    g(s).setSchedule("zz-s1", ScheduleKind.Currency, "@daily", enabled = true, Some(BigDecimal(1)), Some(t))
    g(s).setSchedule("zz-s1", ScheduleKind.Acquisition, "@daily", enabled = false, None, Some(t))
    g(s).setSchedule("zz-s1", ScheduleKind.Currency, "@monthly", enabled = true, Some(BigDecimal(1)), Some(t))
    assertEquals(
      g(s).schedules(None).map(x => (x.goal, x.kind, x.cron, x.enabled, x.budgetUsd, x.nextRunAt)),
      List(
        ("zz-s1", ScheduleKind.Acquisition, "@daily", false, None, Some("2026-11-02T03:00:00Z")),
        ("zz-s1", ScheduleKind.Currency, "@monthly", true, Some(1.0), Some("2026-11-02T03:00:00Z")),
        ("zz-s2", ScheduleKind.Currency, "@weekly", true, None, None)
      )
    )
    assertEquals(g(s).schedules(Some("zz-s2")).map(_.kind), List(ScheduleKind.Currency))
    assertEquals(g(s).scheduleGoals(), List("zz-s1", "zz-s2"))
    assert(g(s).removeSchedule("zz-s2", ScheduleKind.Currency))
    assert(!g(s).removeSchedule("zz-s2", ScheduleKind.Currency))
    val taken = s.within(g(s).takeSchedulesIn("zz-s1"))
    assertEquals(taken.map(_.kind).toSet, Set(ScheduleKind.Acquisition, ScheduleKind.Currency))
    assertEquals(g(s).schedules(None), Nil)
    assertEquals(g(s).scheduleGoals(), Nil)
  }

  stest("a page of goals by slug, of one status, with the total and the next offset") { s =>
    List("zz-c" -> "active", "zz-a" -> "planned", "zz-b" -> "active").foreach((slug, st) =>
      assert(g(s).put(slug, com.histmersion.api.automation.Goal.Put(slug.toUpperCase, st)).isRight)
    )
    val first = g(s).page(None, 2, 0)
    assertEquals(
      (first.total, first.goals.map(_.slug), first.offset, first.next),
      (3, List("zz-a", "zz-b"), 0, Some(2))
    )
    assertEquals(g(s).page(None, 2, 2).goals.map(_.slug), List("zz-c"))
    assertEquals(g(s).page(Some(" ACTIVE "), 10, 0).goals.map(_.slug), List("zz-b", "zz-c"))
  }

  stest("a patch changes what it names, keeps a stored bound given the other alone, clears a cap of 0") { s =>
    assert(
      g(s)
        .put("zz-p", com.histmersion.api.automation.Goal.Put("P", periodStart = Some(-2000), periodEnd = Some(-1000)))
        .isRight
    )
    assert(
      g(s)
        .patch(
          "zz-p",
          com.histmersion.api.automation.Goal.Patch(title = Some("P2"), tags = Some(List("t")), periodEnd = Some(-1500))
        )
        .isRight
    )
    assertEquals(
      bare(s, "zz-p").map(x => (x.title, x.tags, x.periodStart, x.periodEnd)),
      Some(("P2", List("t"), Some(-2000L), Some(-1500L)))
    )
    assert(g(s).patch("zz-p", com.histmersion.api.automation.Goal.Patch(budgetUsd = Some(3.5))).isRight)
    assertEquals(g(s).planAndBudget("zz-p").flatMap(_._2), Some(BigDecimal(3.5)))
    assert(g(s).patch("zz-p", com.histmersion.api.automation.Goal.Patch(budgetUsd = Some(0))).isRight)
    assertEquals(g(s).planAndBudget("zz-p").flatMap(_._2), None)
    assert(
      g(s)
        .patch("zz-none", com.histmersion.api.automation.Goal.Patch(title = Some("x")))
        .left
        .exists(_.message.contains("zz-none"))
    )
  }

  stest("a goal is deleted once; a rename moves its slug, keeps the old one, refuses a taken slug") { s =>
    assert(g(s).put("zz-old", com.histmersion.api.automation.Goal.Put("Old")).isRight)
    assert(g(s).put("zz-other", com.histmersion.api.automation.Goal.Put("Other")).isRight)
    assert(g(s).put("zz-kid", com.histmersion.api.automation.Goal.Put("Kid", parentSlug = Some("zz-old"))).isRight)
    assert(g(s).link("zz-old", Goal.LinkKind.Query, "tin", None))
    assert(g(s).rename("zz-old", "zz-other").isLeft)
    assert(g(s).rename("zz-none", "zz-x").isLeft)
    assertEquals(g(s).rename(" zz-old ", "zz-new "), Right(()))
    assertEquals(g(s).get("zz-old"), None)
    assertEquals(bare(s, "zz-new").map(_.title), Some("Old"))
    assertEquals(bare(s, "zz-kid").flatMap(_.parentSlug), Some("zz-new"))
    assertEquals(g(s).refs("zz-new", Goal.LinkKind.Query), List("tin"))
    assert(g(s).put("zz-third", com.histmersion.api.automation.Goal.Put("Third")).isRight)
    assert(g(s).rename("zz-third", "zz-old").left.exists(_.message.contains("old slug of 'zz-new'")))
    assert(g(s).delete("zz-other"))
    assert(!g(s).delete("zz-other"))
  }

  stest("the goals linking work-list items, an item's goals by title") { s =>
    assert(g(s).put("zz-g1", com.histmersion.api.automation.Goal.Put("Beta")).isRight)
    assert(g(s).put("zz-g2", com.histmersion.api.automation.Goal.Put("Alpha")).isRight)
    assert(g(s).link("zz-g1", Goal.LinkKind.QueueItem, "7", None))
    assert(g(s).link("zz-g2", Goal.LinkKind.QueueItem, "7", None))
    assert(g(s).link("zz-g2", Goal.LinkKind.QueueItem, "8", None))
    assertEquals(g(s).goalsOfItems(List(7)), List((7, "zz-g2", "Alpha"), (7, "zz-g1", "Beta")))
    assertEquals(g(s).goalsOfItems(Nil), Nil)
  }
