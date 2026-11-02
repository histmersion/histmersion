package com.histmersion.domain.automation.goals

import com.histmersion.api.GoalPut
import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}
import com.histmersion.values.{GoalLinkKind, ScheduleKind}

import java.time.Instant

/** The contract the goals keep: put and read back (sub-goals, a refused period), links in their order with what they
  * point at, plans and caps, the schedules not moved yet and their move.
  */
abstract class GoalServiceContract extends EngineStoreSuite:
  private def g(s: EngineStores) = s.goals
  private def bare(s: EngineStores, slug: String) = g(s).get(slug).map(_.copy(updatedAt = None))

  stest("a goal is put, replaced and read back; its sub-goals by slug; a period ending before it starts is refused") {
    s =>
      assert(g(s).put("zz-b", GoalPut("B", periodStart = Some(-1200))).isRight)
      assert(
        g(s).put("zz-a", GoalPut("A", "active", Some("n"), List("t"), List("r"), Some(-3000), Some(-2000))).isRight
      )
      assert(g(s).put("zz-a2", GoalPut("A2", parentSlug = Some("zz-a"))).isRight)
      assert(g(s).put("zz-a1", GoalPut("A1", parentSlug = Some("zz-a"))).isRight)
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
      assert(g(s).put("zz-b", GoalPut("B again")).isRight)
      assertEquals(bare(s, "zz-b").map(x => (x.title, x.periodStart)), Some(("B again", None)))
      val bad = g(s).put("zz-c", GoalPut("C", periodStart = Some(100), periodEnd = Some(-100)))
      assert(bad.left.exists(_.message.contains("goal 'zz-c'")), bad)
      assertEquals(g(s).get("zz-c"), None)
  }

  stest(
    "links: only to a goal that exists, sources then articles then queries, a note kept unless a new one is given"
  ) { s =>
    assert(g(s).put("zz-g", GoalPut("G")).isRight)
    s.source("zz2020_src", "A source")
    assert(!g(s).link("zz-none", GoalLinkKind.Source, "zz2020_src", None), "no such goal")
    assert(g(s).link("zz-g", GoalLinkKind.Query, "tin trade", None))
    assert(g(s).link("zz-g", GoalLinkKind.Article, "zz-art", Some("why")))
    assert(g(s).link("zz-g", GoalLinkKind.Source, "zz2020_src", Some("first")))
    assert(g(s).link("zz-g", GoalLinkKind.Source, "zz2020_src", None))
    val ls = g(s).links("zz-g")
    assertEquals(
      ls.map(l => (l.kind, l.ref, l.note, l.title)),
      List(
        (GoalLinkKind.Source, "zz2020_src", Some("first"), Some("A source")),
        (GoalLinkKind.Article, "zz-art", Some("why"), None),
        (GoalLinkKind.Query, "tin trade", None, None)
      )
    )
    assert(ls.forall(_.createdAt.matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ")), ls)
    assert(g(s).link("zz-g", GoalLinkKind.Source, "zz2020_src", Some("second")))
    assertEquals(g(s).links("zz-g").head.note, Some("second"))
    assertEquals(g(s).linkCounts(), Map(("zz-g", "source") -> 1, ("zz-g", "query") -> 1))
    s.article("zz-art", "An article")
    assertEquals(g(s).linkCounts().get(("zz-g", "article")), Some(1))
    assertEquals(g(s).links("zz-g")(1).title, Some("An article"))
    assert(g(s).unlink("zz-g", GoalLinkKind.Query, "tin trade"))
    assert(!g(s).unlink("zz-g", GoalLinkKind.Query, "tin trade"))
    assertEquals(g(s).links("zz-g").map(_.kind), List(GoalLinkKind.Source, GoalLinkKind.Article))
  }

  stest("the refs of one kind of a goal's links, oldest first; another kind's and another goal's left out") { s =>
    assert(s.goals.put("zz-g", GoalPut("A goal")).isRight)
    assert(s.goals.put("zz-h", GoalPut("Another")).isRight)
    assert(s.goals.link("zz-g", GoalLinkKind.SearchQuery, "storm gods", None))
    assert(s.goals.link("zz-g", GoalLinkKind.SearchQuery, "anatolia", None))
    assert(s.goals.link("zz-g", GoalLinkKind.QueueItem, "7", Some("10.5555/zz")))
    assert(s.goals.link("zz-h", GoalLinkKind.SearchQuery, "elsewhere", None))
    assertEquals(s.goals.refs("zz-g", GoalLinkKind.SearchQuery), List("storm gods", "anatolia"))
    assertEquals(s.goals.refs("zz-g", GoalLinkKind.QueueItem), List("7"))
    assertEquals(s.goals.refs("zz-none", GoalLinkKind.SearchQuery), Nil)
  }

  stest("an approved plan and a run cap; the planned goals") { s =>
    assert(g(s).put("zz-p", GoalPut("P")).isRight)
    assert(g(s).put("zz-q", GoalPut("Q")).isRight)
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
    List("zz-s1", "zz-s2").foreach(x => assert(g(s).put(x, GoalPut(x)).isRight))
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
