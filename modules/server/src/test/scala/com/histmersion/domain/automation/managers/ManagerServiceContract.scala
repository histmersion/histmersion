package com.histmersion.domain.automation.managers

import com.histmersion.api.GoalPut
import com.histmersion.api.Agents.JobKind
import com.histmersion.api.Managers.*
import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}
import com.histmersion.values.GoalLinkKind

import java.time.Instant

/** The contract the managers keep: made once, their settings; items added once and brought back, held and released (for
  * a person, for the focus), settled and dispatched; events; the reads over every manager at once.
  */
abstract class ManagerServiceContract extends EngineStoreSuite:
  private def m(s: EngineStores) = s.managers
  private val t = Instant.parse("2026-11-03T06:00:00Z")
  private def goal(s: EngineStores, slug: String) = assert(s.goals.put(slug, GoalPut(slug)).isRight)

  stest("a manager is made once; its state, settings and cron; light, with whether it has a plan-goal item") { s =>
    goal(s, "zz-g")
    assert(
      m(s).create(
        "seed:zz-g",
        Kind.Seed,
        Some("zz-g"),
        State.Active,
        ManagerConfig(maxRuns = Some(2)),
        Some("@daily"),
        Some(t)
      )
    )
    assert(!m(s).create("seed:zz-g", Kind.Seed, Some("zz-g"), State.Paused, ManagerConfig()), "the id is taken")
    assert(m(s).create("review", Kind.Review, None, State.Paused, ManagerConfig()))
    val got = m(s).get("seed:zz-g").get
    assertEquals(
      (got.kind, got.goal, got.state, got.pauseRuns, got.config.maxRuns, got.cron, got.nextTickAt),
      ("seed", Some("zz-g"), "active", false, Some(2), Some("@daily"), Some("2026-11-03T06:00:00Z"))
    )
    assertEquals((got.counts, got.inFlight, got.lastEvent, got.spentTodayUsd), (ItemCounts(), 0, None, 0.0))
    assertEquals(m(s).get("nope"), None)
    assert(m(s).setState("review", State.Active, pauseRuns = true))
    assert(!m(s).setState("nope", State.Active, pauseRuns = false))
    assertEquals(m(s).get("review").map(x => (x.state, x.pauseRuns)), Some(("active", true)))
    assert(m(s).setConfig("review", ManagerConfig(lowWater = Some(3)), None, None))
    assertEquals(m(s).get("review").map(x => (x.config.lowWater, x.cron)), Some((Some(3), None)))
    assertEquals(m(s).light().map(l => (l.id, l.planItem)), List(("review", false), ("seed:zz-g", false)))
    val _ = m(s).upsert("seed:zz-g", PlanGoalItem, "zz-g", ItemOrigin.Selection, 1, reopen = false)
    assertEquals(m(s).light().map(l => (l.id, l.planItem)), List(("review", false), ("seed:zz-g", true)))
  }

  stest("an item is added once and brought back; held, released, settled, dispatched; the open ones") { s =>
    assert(m(s).create("review", Kind.Review, None, State.Active, ManagerConfig()))
    val (a, made) = m(s).upsert("review", "article-review", "zz-a", ItemOrigin.Selection, 1, reopen = false)
    assert(made)
    assertEquals(m(s).upsert("review", "article-review", "zz-a", ItemOrigin.Person, 5, reopen = false), (a, false))
    assertEquals(m(s).item(a).map(i => (i.priority, i.origin)), Some((1, "selection")), "not reopened")
    assertEquals(m(s).upsert("review", "article-review", "zz-a", ItemOrigin.Person, 5, reopen = true), (a, false))
    assertEquals(m(s).item(a).map(i => (i.priority, i.origin, i.status)), Some((5, "person", "pending")))
    val (b, _) = m(s).upsert("review", "article-review", "zz-b", ItemOrigin.Selection, 0, reopen = false)
    assertEquals(m(s).itemFor("review", "article-review", "zz-b").map(_.id), Some(b))
    assert(m(s).hold(a, Hold(reason = "a person")))
    assertEquals(m(s).forPerson(), 1)
    assert(m(s).release(a))
    assert(!m(s).release(a), "not held any more")
    assert(m(s).settle(b, ItemStatus.Stopped, "skipped", List(ItemStatus.Pending, ItemStatus.Held)))
    assert(!m(s).settle(b, ItemStatus.Done, "x", List(ItemStatus.Pending)), "not pending")
    assertEquals(m(s).item(b).map(i => (i.status, i.outcome)), Some(("stopped", Some("skipped"))))
    assertEquals(m(s).upsert("review", "article-review", "zz-b", ItemOrigin.Selection, 0, reopen = true), (b, false))
    assertEquals(m(s).item(b).map(i => (i.status, i.outcome)), Some(("pending", None)), "a settled one reopened")
    val run = s.run("zz-a", manager = Some("review"), item = Some(a))
    assert(m(s).dispatch(a, run, ItemState(before = Some(2))))
    assert(!m(s).dispatch(a, run, ItemState()), "not pending")
    assertEquals(
      m(s).item(a).map(i => (i.status, i.runId, i.attempts, i.state.before)),
      Some(("active", Some(run), 1, Some(2)))
    )
    assertEquals(m(s).openFor("zz-a").map(_.id), List(a))
    assertEquals(m(s).openKeys("review").toSet, Set(("article-review", "zz-a"), ("article-review", "zz-b")))
    assertEquals(m(s).openOf("review", List("article-review"), List("active")), 1)
    assertEquals(m(s).get("review").map(_.counts), Some(ItemCounts(pending = 1, active = 1)))
    assertEquals(m(s).get("review").map(_.inFlight), Some(1))
    val (c, _) = m(s).upsert("review", "article-review", "zz-c", ItemOrigin.Selection, 9, reopen = false)
    assertEquals(m(s).pending("review").map(_.id), List(c, b), "the most urgent first")
    assert(m(s).create("enrich", Kind.Enrich, None, State.Paused, ManagerConfig()))
    assertEquals(m(s).list().map(_.id), List("review", "enrich"), "the oldest first")
  }

  stest("the focus holds pending items only, and releases only its own holds") { s =>
    assert(m(s).create("watch:zz", Kind.Watch, None, State.Active, ManagerConfig()))
    val ids = (1 to 3).toList.map(i =>
      m(s).upsert("watch:zz", OaIngest, s"10.1/$i", ItemOrigin.Selection, 0, reopen = false)._1
    )
    assert(m(s).focusHold(ids(0), Hold(reason = "focus", focus = Some("zz"))))
    assert(m(s).focusHold(ids(1), Hold(reason = "focus", focus = Some("zz"))))
    assert(!m(s).focusHold(ids(1), Hold(reason = "again", focus = Some("zz"))), "not pending")
    assert(m(s).hold(ids(2), Hold(reason = "a person")))
    assertEquals(m(s).focusHeldCount(), 2)
    assertEquals(m(s).forPerson(), 1, "the focus's holds wait for nobody")
    assertEquals(m(s).openOf("watch:zz", List(OaIngest), List("held")), 3)
    assertEquals(m(s).openOf("watch:zz", List(OaIngest), List("held"), focusHeld = false), 1)
    assertEquals(m(s).backlogCounts(), Map("watch:zz" -> (0, 3)))
    assertEquals(m(s).focusRelease(List(ids(0), ids(2))), 1, "a person's hold is never touched")
    assertEquals(m(s).focusReleaseAll(), 1)
    assertEquals(m(s).focusHeldCount(), 0)
    assertEquals(m(s).backlogCounts(), Map("watch:zz" -> (2, 1)))
  }

  stest("events: the newest of a kind, every manager's since a moment, the pace and the last progress") { s =>
    goal(s, "zz-g")
    assert(m(s).create("watch:zz-g", Kind.Watch, Some("zz-g"), State.Active, ManagerConfig()))
    assert(m(s).create("review", Kind.Review, None, State.Active, ManagerConfig()))
    m(s).event("watch:zz-g", None, None, Feed.Ingested, "one")
    m(s).event("watch:zz-g", None, None, Feed.Ingested, "two")
    m(s).event("review", None, None, Feed.Ingested, "x" * 3000)
    assertEquals(m(s).lastEventOf("watch:zz-g", Feed.Ingested).map(_.text), Some("two"))
    assertEquals(m(s).lastEventOf("watch:zz-g", Feed.Started), None)
    assertEquals(m(s).get("review").flatMap(_.lastEvent).map(_.text.length), Some(2000))
    assertEquals(m(s).countEventsAll(Feed.Ingested, Instant.parse("2000-01-01T00:00:00Z")), 3)
    assertEquals(m(s).countEventsAll(Feed.Ingested, Instant.parse("2099-01-01T00:00:00Z")), 0)
    val (i, _) = m(s).upsert("watch:zz-g", AcquisitionSweep, "zz-g", ItemOrigin.Selection, 0, reopen = false)
    val _ = m(s).upsert("watch:zz-g", OaIngest, "10.1/x", ItemOrigin.Selection, 0, reopen = false)
    assertEquals(
      m(s).pace(Instant.parse("2000-01-01T00:00:00Z")).get("watch:zz-g").map(p => (p._1, p._2, p._3.isDefined)),
      Some((1, 0, true))
    )
    assert(m(s).settle(i, ItemStatus.Done, "swept 3", List(ItemStatus.Pending)))
    assertEquals(
      m(s).pace(Instant.parse("2000-01-01T00:00:00Z")).get("watch:zz-g").map(p => (p._1, p._2)),
      Some((0, 1))
    )
    assertEquals(m(s).pace(Instant.parse("2099-01-01T00:00:00Z")).get("watch:zz-g").map(_._2), Some(0))
    assertEquals(m(s).sweepOutcomes(), Map("zz-g" -> "swept 3"))
    assert(m(s).lastProgress().contains("zz-g"))
    assertEquals(m(s).lastProgress().keySet, Set("zz-g"))
    assert(s.goals.link("zz-g", GoalLinkKind.Source, "zz2020_s", None))
    assert(m(s).lastProgress()("zz-g").matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ"))
  }

  stest("the runs that hold a place, per manager and per goal; the kinds of the newest runs") { s =>
    goal(s, "zz-g")
    assert(m(s).create("seed:zz-g", Kind.Seed, Some("zz-g"), State.Active, ManagerConfig()))
    val (a, _) = m(s).upsert("seed:zz-g", ProposeArticles, "zz-g", ItemOrigin.Selection, 0, reopen = false)
    val (b, _) = m(s).upsert("seed:zz-g", ArticleCompile, "zz-art", ItemOrigin.Selection, 0, reopen = false)
    val r1 = s.run("zz-g", manager = Some("seed:zz-g"), item = Some(a), pipeline = "propose-articles")
    val r2 = s.run("zz-art", manager = Some("seed:zz-g"), item = Some(b), pipeline = "compile-article")
    val mine = s.run("zz2020_x")
    assert(m(s).dispatch(a, r1, ItemState()))
    assert(m(s).dispatch(b, r2, ItemState()))
    assert(s.runs.move(JobKind, r2, "queued", "running", None, None, None))
    assert(s.runs.move(JobKind, r2, "running", "waiting", None, None, None))
    assertEquals(m(s).running("seed:zz-g"), 1)
    assertEquals(
      m(s).occupyingRuns().toSet,
      Set[(String, Option[String])](("propose-articles", Some("zz-g")), ("enrich-source", None))
    )
    assertEquals(m(s).recentKinds(5), List(ArticleCompile, ProposeArticles))
    assertEquals(m(s).recentKinds(1), List(ArticleCompile))
    assertEquals(m(s).runningByGoal(), Map("zz-g" -> 1))
    assertEquals(m(s).forPerson(), 1, "an active item whose run waits for an approval")
    assert(
      m(s).hold(
        m(s).upsert("seed:zz-g", PlanGoalItem, "zz-g", ItemOrigin.Selection, 0, reopen = false)._1,
        Hold(reason = "p")
      )
    )
    assertEquals(m(s).personHeldByGoal(), Map("zz-g" -> 1))
    assertEquals(mine.nonEmpty, true)
  }
