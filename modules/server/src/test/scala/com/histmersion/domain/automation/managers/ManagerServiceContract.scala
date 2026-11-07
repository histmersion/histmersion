package com.histmersion.domain.automation.managers

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Agents.JobKind
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}

import java.time.Instant
import com.histmersion.domain.automation.Goal
import com.histmersion.util.jobs.{Job, RunRow}

/** The contract the managers keep: made once, their settings; items added once and brought back, held and released (for
  * a person, for the focus), settled and dispatched; events; the reads over every manager at once.
  */
abstract class ManagerServiceContract extends EngineStoreSuite:
  private object Feeds:
    val Ingested = ManagerEvent.Kind(Feed.Ingested)
    val Evaluated = ManagerEvent.Kind(Feed.Evaluated)
    val Started = ManagerEvent.Kind(Feed.Started)

  private def m(s: EngineStores) = s.managers
  private val t = Instant.parse("2026-11-03T06:00:00Z")
  private def goal(s: EngineStores, slug: Goal.Slug) = assert(
    s.goals.put(slug, com.histmersion.api.automation.Goal.Put(com.histmersion.api.automation.Goal.Title(slug.value))).isRight
  )

  stest("a manager is made once; its state, settings and cron; light, with whether it has a plan-goal item") { s =>
    goal(s, Goal.Slug("zz-g"))
    assert(
      m(s).create(
        com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-g"),
        Kind.Seed,
        Some(com.histmersion.domain.automation.Goal.Slug("zz-g")),
        State.Active,
        ManagerConfig(maxRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRuns(2))),
        Some(com.histmersion.api.automation.Managers.ManagerInfo.Cron("@daily")),
        Some(t)
      )
    )
    assert(!m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-g"), Kind.Seed, Some(com.histmersion.domain.automation.Goal.Slug("zz-g")), State.Paused, ManagerConfig()), "the id is taken")
    assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), Kind.Review, None, State.Paused, ManagerConfig()))
    val got = m(s).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-g")).get
    assertEquals(
      (got.kind, got.goal, got.state, got.pauseRuns, got.config.maxRuns, got.cron, got.nextTickAt),
      ("seed", Some("zz-g"), "active", false, Some(2), Some("@daily"), Some("2026-11-03T06:00:00Z"))
    )
    assertEquals((got.counts, got.inFlight, got.lastEvent, got.spentTodayUsd), (ItemCounts(), 0, None, 0.0))
    assertEquals(m(s).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("nope")), None)
    assert(m(s).setState(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), State.Active, pauseRuns = com.histmersion.api.automation.Managers.ManagerInfo.PauseRuns(true)))
    assert(!m(s).setState(com.histmersion.api.automation.Managers.ManagerInfo.Id("nope"), State.Active, pauseRuns = com.histmersion.api.automation.Managers.ManagerInfo.PauseRuns(false)))
    assertEquals(m(s).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("review")).map(x => (x.state, x.pauseRuns)), Some(("active", true)))
    assert(m(s).setConfig(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), ManagerConfig(lowWater = Some(com.histmersion.api.automation.Managers.ManagerConfig.LowWater(3))), None, None))
    assertEquals(m(s).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("review")).map(x => (x.config.lowWater, x.cron)), Some((Some(3), None)))
    assertEquals(m(s).light().map(l => (l.id, l.planItem)), List(("review", false), ("seed:zz-g", false)))
    val _ = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-g"), ManagerItem.Kind(PlanGoalItem), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-g"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(1), reopen = false)
    assertEquals(m(s).light().map(l => (l.id, l.planItem)), List(("review", false), ("seed:zz-g", true)))
  }

  stest("an item is added once and brought back; held, released, settled, dispatched; the open ones") { s =>
    assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), Kind.Review, None, State.Active, ManagerConfig()))
    val (a, made) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerItem.Kind("article-review"), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-a"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(1), reopen = false)
    assert(made)
    assertEquals(m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerItem.Kind("article-review"), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-a"), ItemOrigin.Person, com.histmersion.api.automation.Managers.ManagerItem.Priority(5), reopen = false), (a, false))
    assertEquals(m(s).item(a).map(i => (i.priority, i.origin)), Some((1, "selection")), "not reopened")
    assertEquals(m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerItem.Kind("article-review"), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-a"), ItemOrigin.Person, com.histmersion.api.automation.Managers.ManagerItem.Priority(5), reopen = true), (a, false))
    assertEquals(m(s).item(a).map(i => (i.priority, i.origin, i.status)), Some((5, "person", "pending")))
    val (b, _) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerItem.Kind("article-review"), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-b"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)
    assertEquals(m(s).itemFor(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerItem.Kind("article-review"), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-b")).map(_.id), Some(b))
    assert(m(s).hold(a, Hold(reason = com.histmersion.api.automation.Managers.Hold.Reason("a person"))))
    assertEquals(m(s).forPerson(), 1)
    assert(m(s).release(a))
    assert(!m(s).release(a), "not held any more")
    assert(m(s).settle(b, ItemStatus.Stopped, com.histmersion.api.automation.Managers.ManagerItem.Outcome("skipped"), List(ItemStatus.Pending, ItemStatus.Held)))
    assert(!m(s).settle(b, ItemStatus.Done, com.histmersion.api.automation.Managers.ManagerItem.Outcome("x"), List(ItemStatus.Pending)), "not pending")
    assertEquals(m(s).item(b).map(i => (i.status, i.outcome)), Some(("stopped", Some("skipped"))))
    assertEquals(m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerItem.Kind("article-review"), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-b"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = true), (b, false))
    assertEquals(m(s).item(b).map(i => (i.status, i.outcome)), Some(("pending", None)), "a settled one reopened")
    val run = s.run(RunRow.Target("zz-a"), manager = Some(RunRow.ManagerId("review")), item = Some(RunRow.ManagerItem(a.value)))
    assert(m(s).dispatch(a, run, ItemState(before = Some(com.histmersion.api.automation.Managers.ItemState.Before(2)))))
    assert(!m(s).dispatch(a, run, ItemState()), "not pending")
    assertEquals(
      m(s).item(a).map(i => (i.status, i.runId, i.attempts, i.state.before)),
      Some(("active", Some(run), 1, Some(2)))
    )
    assertEquals(m(s).openFor(com.histmersion.api.automation.Managers.ManagerItem.Target("zz-a")).map(_.id), List(a))
    assertEquals(m(s).openKeys(com.histmersion.api.automation.Managers.ManagerInfo.Id("review")).toSet, Set(("article-review", "zz-a"), ("article-review", "zz-b")))
    assertEquals(m(s).openOf(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), List(com.histmersion.api.automation.Managers.ManagerItem.Kind("article-review")), List(com.histmersion.api.automation.ItemStatus.Wire("active"))), 1)
    assertEquals(m(s).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("review")).map(_.counts), Some(ItemCounts(pending = com.histmersion.api.automation.Managers.ItemCounts.Pending(1), active = com.histmersion.api.automation.Managers.ItemCounts.Active(1))))
    assertEquals(m(s).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("review")).map(_.inFlight), Some(1))
    val (c, _) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerItem.Kind("article-review"), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-c"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(9), reopen = false)
    assertEquals(m(s).pending(com.histmersion.api.automation.Managers.ManagerInfo.Id("review")).map(_.id), List(c, b), "the most urgent first")
    assertEquals(m(s).items(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.PageSize(10)).map(_.id), List(a, c, b), "active first, then pending by priority")
    assertEquals(m(s).items(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.PageSize(1)).map(_.id), List(a))
    assertEquals(m(s).items(com.histmersion.api.automation.Managers.ManagerInfo.Id("enrich"), com.histmersion.PageSize(10)), Nil)
    assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("enrich"), Kind.Enrich, None, State.Paused, ManagerConfig()))
    assertEquals(m(s).list().map(_.id), List("review", "enrich"), "the oldest first")
  }

  stest("the ingest backlog: an active watcher's pending ones by urgency, every open one, one by its DOI; retried") {
    s =>
      assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-a"), Kind.Watch, None, State.Active, ManagerConfig()))
      assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-b"), Kind.Watch, None, State.Paused, ManagerConfig()))
      val (a1, _) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-a"), ManagerItem.Kind(OaIngest), com.histmersion.api.automation.Managers.ManagerItem.Target("10.5555/zz.1"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)
      val (a2, _) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-a"), ManagerItem.Kind(OaIngest), com.histmersion.api.automation.Managers.ManagerItem.Target("10.5555/zz.2"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(3), reopen = false)
      val (b1, _) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-b"), ManagerItem.Kind(OaIngest), com.histmersion.api.automation.Managers.ManagerItem.Target("10.5555/zz.1"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)
      val (other, _) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-a"), ManagerItem.Kind(AcquisitionSweep), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-a"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(9), reopen = false)
      assertEquals(m(s).backlogPending().map(_.id), List(a2, a1), "an active watcher's, the most urgent first")
      assertEquals(m(s).backlogOpen().map(_.id), List(a1, a2, b1), "every watcher's open ones, the oldest first")
      assertEquals(m(s).backlogOpenFor(com.histmersion.domain.library.Doi("10.5555/zz.1")).map(_.id), Some(a1))
      assertEquals(m(s).backlogOpenFor(com.histmersion.domain.library.Doi("10.5555/zz.9")), None)
      assert(m(s).backlogRetry(a1, com.histmersion.api.automation.Managers.ManagerItem.Failures(2), t, com.histmersion.api.automation.Managers.ManagerItem.Outcome("again later")))
      assertEquals(
        m(s).item(a1).map(i => (i.failures, i.attempts, i.notBefore, i.outcome)),
        Some((2, 1, Some("2026-11-03T06:00:00Z"), Some("again later")))
      )
      assert(m(s).settle(a2, ItemStatus.Done, com.histmersion.api.automation.Managers.ManagerItem.Outcome("ingested"), List(ItemStatus.Pending)))
      assert(!m(s).backlogRetry(a2, com.histmersion.api.automation.Managers.ManagerItem.Failures(1), t, com.histmersion.api.automation.Managers.ManagerItem.Outcome("x")), "not pending")
      assertEquals(m(s).backlogOpen().map(_.id), List(a1, b1))
      assert(m(s).setItemState(b1, ItemState(before = Some(com.histmersion.api.automation.Managers.ItemState.Before(4))), List(ItemStatus.Pending)))
      assert(!m(s).setItemState(a2, ItemState(), List(ItemStatus.Pending)), "not in the statuses named")
      assertEquals(m(s).item(b1).map(_.state.before), Some(Some(4)))
      assertEquals(m(s).item(other).map(_.kind), Some(AcquisitionSweep))
      assertEquals(m(s).sweepAcquireItems(), Nil, "no sweep queued an acquisition item")
  }

  stest("events counted by manager since a moment") { s =>
    assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-a"), Kind.Watch, None, State.Active, ManagerConfig()))
    assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-b"), Kind.Watch, None, State.Active, ManagerConfig()))
    m(s).event(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-a"), None, None, Feeds.Ingested, com.histmersion.api.automation.Managers.ManagerEvent.Text("one"))
    m(s).event(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-a"), None, None, Feeds.Ingested, com.histmersion.api.automation.Managers.ManagerEvent.Text("two"))
    m(s).event(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-b"), None, None, Feeds.Ingested, com.histmersion.api.automation.Managers.ManagerEvent.Text("three"))
    m(s).event(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-b"), None, None, Feeds.Evaluated, com.histmersion.api.automation.Managers.ManagerEvent.Text("not counted"))
    assertEquals(m(s).countEventsBy(Feeds.Ingested, Instant.EPOCH), Map("watch:zz-a" -> 2, "watch:zz-b" -> 1))
    assertEquals(m(s).countEventsBy(Feeds.Ingested, Instant.parse("2999-01-01T00:00:00Z")), Map.empty)
  }

  stest("the focus holds pending items only, and releases only its own holds") { s =>
    assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz"), Kind.Watch, None, State.Active, ManagerConfig()))
    val ids = (1 to 3).toList.map(i =>
      m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz"), ManagerItem.Kind(OaIngest), ManagerItem.Target(s"10.1/$i"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)._1
    )
    assert(m(s).focusHold(ids(0), Hold(reason = com.histmersion.api.automation.Managers.Hold.Reason("focus"), focus = Some(com.histmersion.api.automation.Managers.Hold.Focus("zz")))))
    assert(m(s).focusHold(ids(1), Hold(reason = com.histmersion.api.automation.Managers.Hold.Reason("focus"), focus = Some(com.histmersion.api.automation.Managers.Hold.Focus("zz")))))
    assert(!m(s).focusHold(ids(1), Hold(reason = com.histmersion.api.automation.Managers.Hold.Reason("again"), focus = Some(com.histmersion.api.automation.Managers.Hold.Focus("zz")))), "not pending")
    assert(m(s).hold(ids(2), Hold(reason = com.histmersion.api.automation.Managers.Hold.Reason("a person"))))
    assertEquals(m(s).focusHeldCount(), 2)
    assertEquals(m(s).forPerson(), 1, "the focus's holds wait for nobody")
    assertEquals(m(s).openOf(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz"), List(ManagerItem.Kind(OaIngest)), List(com.histmersion.api.automation.ItemStatus.Wire("held"))), 3)
    assertEquals(m(s).openOf(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz"), List(ManagerItem.Kind(OaIngest)), List(com.histmersion.api.automation.ItemStatus.Wire("held")), focusHeld = false), 1)
    assertEquals(m(s).backlogCounts(), Map("watch:zz" -> (0, 3)))
    assertEquals(m(s).focusRelease(List(ids(0), ids(2))), 1, "a person's hold is never touched")
    assertEquals(m(s).focusReleaseAll(), 1)
    assertEquals(m(s).focusHeldCount(), 0)
    assertEquals(m(s).backlogCounts(), Map("watch:zz" -> (2, 1)))
  }

  stest("events: the newest of a kind, every manager's since a moment, the pace and the last progress") { s =>
    goal(s, Goal.Slug("zz-g"))
    assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g"), Kind.Watch, Some(com.histmersion.domain.automation.Goal.Slug("zz-g")), State.Active, ManagerConfig()))
    assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), Kind.Review, None, State.Active, ManagerConfig()))
    m(s).event(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g"), None, None, Feeds.Ingested, com.histmersion.api.automation.Managers.ManagerEvent.Text("one"))
    m(s).event(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g"), None, None, Feeds.Ingested, com.histmersion.api.automation.Managers.ManagerEvent.Text("two"))
    m(s).event(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), None, None, Feeds.Ingested, ManagerEvent.Text("x" * 3000))
    assertEquals(m(s).lastEventOf(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g"), Feeds.Ingested).map(_.text), Some("two"))
    assertEquals(m(s).lastEventOf(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g"), Feeds.Started), None)
    assertEquals(m(s).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("review")).flatMap(_.lastEvent).map(_.text.value.length), Some(2000))
    assertEquals(m(s).countEventsAll(Feeds.Ingested, Instant.parse("2000-01-01T00:00:00Z")), 3)
    assertEquals(m(s).countEventsAll(Feeds.Ingested, Instant.parse("2099-01-01T00:00:00Z")), 0)
    val (i, _) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g"), ManagerItem.Kind(AcquisitionSweep), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-g"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)
    val _ = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g"), ManagerItem.Kind(OaIngest), com.histmersion.api.automation.Managers.ManagerItem.Target("10.1/x"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)
    assertEquals(
      m(s).pace(Instant.parse("2000-01-01T00:00:00Z")).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g")).map(p => (p._1, p._2, p._3.isDefined)),
      Some((1, 0, true))
    )
    assert(m(s).settle(i, ItemStatus.Done, com.histmersion.api.automation.Managers.ManagerItem.Outcome("swept 3"), List(ItemStatus.Pending)))
    assertEquals(
      m(s).pace(Instant.parse("2000-01-01T00:00:00Z")).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g")).map(p => (p._1, p._2)),
      Some((0, 1))
    )
    assertEquals(m(s).pace(Instant.parse("2099-01-01T00:00:00Z")).get(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-g")).map(_._2), Some(0))
    assertEquals(m(s).sweepOutcomes(), Map("zz-g" -> "swept 3"))
    assert(m(s).lastProgress().contains(com.histmersion.api.automation.Goal.Slug("zz-g")))
    assertEquals(m(s).lastProgress().keySet, Set("zz-g"))
    assert(s.goals.link(com.histmersion.domain.automation.Goal.Slug("zz-g"), Goal.LinkKind.Source, com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zz2020_s"), None))
    assert(m(s).lastProgress()(com.histmersion.api.automation.Goal.Slug("zz-g")).value.matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ"))
  }

  stest("the runs that hold a place, per manager and per goal; the kinds of the newest runs") { s =>
    goal(s, Goal.Slug("zz-g"))
    assert(m(s).create(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-g"), Kind.Seed, Some(com.histmersion.domain.automation.Goal.Slug("zz-g")), State.Active, ManagerConfig()))
    val (a, _) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-g"), ManagerItem.Kind(ProposeArticles), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-g"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)
    val (b, _) = m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-g"), ManagerItem.Kind(ArticleCompile), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-art"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)
    val r1 = s.run(RunRow.Target("zz-g"), manager = Some(RunRow.ManagerId("seed:zz-g")), item = Some(RunRow.ManagerItem(a.value)), pipeline = RunRow.Pipeline("propose-articles"))
    val r2 = s.run(RunRow.Target("zz-art"), manager = Some(RunRow.ManagerId("seed:zz-g")), item = Some(RunRow.ManagerItem(b.value)), pipeline = RunRow.Pipeline("compile-article"))
    val mine = s.run(RunRow.Target("zz2020_x"))
    assert(m(s).dispatch(a, r1, ItemState()))
    assert(m(s).dispatch(b, r2, ItemState()))
    assert(s.runs.move(Job.Kind.assume(JobKind), r2, com.histmersion.util.jobs.RunRow.Status("queued"), com.histmersion.util.jobs.RunRow.Status("running"), None, None, None))
    assert(s.runs.move(Job.Kind.assume(JobKind), r2, com.histmersion.util.jobs.RunRow.Status("running"), com.histmersion.util.jobs.RunRow.Status("waiting"), None, None, None))
    assertEquals(m(s).running(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-g")), 1)
    assertEquals(
      m(s).occupyingRuns().toSet,
      Set[(String, Option[String])](("propose-articles", Some("zz-g")), ("enrich-source", None))
    )
    assertEquals(m(s).recentKinds(com.histmersion.PageSize(5)), List(ArticleCompile, ProposeArticles))
    assertEquals(m(s).recentKinds(com.histmersion.PageSize(1)), List(ArticleCompile))
    assertEquals(m(s).runningByGoal(), Map("zz-g" -> 1))
    assertEquals(m(s).forPerson(), 1, "an active item whose run waits for an approval")
    assert(
      m(s).hold(
        m(s).upsert(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:zz-g"), ManagerItem.Kind(PlanGoalItem), com.histmersion.api.automation.Managers.ManagerItem.Target("zz-g"), ItemOrigin.Selection, com.histmersion.api.automation.Managers.ManagerItem.Priority(0), reopen = false)._1,
        Hold(reason = com.histmersion.api.automation.Managers.Hold.Reason("p"))
      )
    )
    assertEquals(m(s).personHeldByGoal(), Map("zz-g" -> 1))
    assertEquals(mine.value.nonEmpty, true)
  }
