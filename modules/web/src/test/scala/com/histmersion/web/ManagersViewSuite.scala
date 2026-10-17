package com.histmersion.web

import com.histmersion.core.Managers.*
import com.histmersion.core.ManagersMarkdown

/** The managers window's pure parts: the badge, the item actions per status, the settings form, the
  * hint box, the pager and the window keys. */
class ManagersViewSuite extends munit.FunSuite:

  test("badge: the person's state, else what the supervisor derived"):
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", state = "paused", statusNote = Some("paused"))), "paused")
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", statusNote = Some("starved: skeptic chain below its plan floor"))), "starved")
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", statusNote = Some("at capacity (2 runs in flight, 3 pending)"))), "at capacity")
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", statusNote = Some("idle"))), "idle")
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", statusNote = Some("3 pending"))), "active")

  test("item actions by status"):
    assertEquals(ManagersView.itemActions("pending"), List("hold", "priority", "skip"))
    assertEquals(ManagersView.itemActions("held"), List("release", "priority", "skip"))
    assertEquals(ManagersView.itemActions("active"), List("priority", "skip"))
    assertEquals(ManagersView.itemActions("stopped"), List("reopen"))
    assertEquals(ManagersView.itemActions("nonsense"), Nil)

  test("settings form: blank is the default; it reads back what it shows; a bad number is refused"):
    val c = ManagerConfig(maxRuns = Some(3), includeTags = Some(List("a", "b")),
      models = Some(List(com.histmersion.core.Agents.ModelOverride("fixer", "claude-code:opus"))))
    assertEquals(ManagersView.config(ManagersView.fields(c)), Right(c))
    assertEquals(ManagersView.config(Map.empty), Right(ManagerConfig()))
    assert(ManagersView.config(Map("rounds" -> "x")).isLeft)
    assert(ManagersView.config(Map("models" -> "fixer")).isLeft)
    assert(ManagersView.config(Map("budgetUsd" -> "-1")).isLeft)

  test("hint box: text alone, scoped, with an action; a hold needs a date or a source"):
    assertEquals(ManagersView.hint("prefer 2019", "", once = false, "none", "", "", "", ""), Right(HintAdd("prefer 2019", None, Some(false))))
    assertEquals(ManagersView.hint("x", "tin", once = true, "none", "", "", "", "").map(_.item), Right(Some("tin")))
    val p = ManagersView.hint("first", "", once = false, "prioritise", "a, b", "", "", "")
    assertEquals(p.map(_.effect.map(e => (e.action, e.targets))), Right(Some(("prioritise", List("a", "b")))))
    assert(ManagersView.hint("wait", "", once = false, "hold", "a", "", "", "").isLeft)
    assertEquals(ManagersView.hint("wait", "", once = false, "hold", "a", "", "2026-11-01", "").map(_.effect.flatMap(_.until)), Right(Some("2026-11-01")))
    assert(ManagersView.hint("skip", "", once = false, "skip", "", "", "", "").isLeft)
    assert(ManagersView.hint(" ", "", once = false, "none", "", "", "", "").isLeft)
    assert(ManagerPolicyText.isDate("2026-11-01") && !ManagerPolicyText.isDate("10.1000/x"))

  test("pager: the range line and the offsets"):
    assertEquals(Pager.range(0, 25, 112), "1–25 of 112")
    assertEquals(Pager.range(100, 12, 112), "101–112 of 112")
    assertEquals(Pager.range(0, 0, 0), "none")
    assertEquals(Pager.previous(10, 25), 0)
    assertEquals(Pager.next(75, 25, 112), Some(100))
    assertEquals(Pager.next(100, 25, 112), None)

  test("window keys: managers and one manager round-trip through the hash"):
    assertEquals(WindowKey.parse(WindowKey.Managers.wire), Some(WindowKey.Managers))
    assertEquals(WindowKey.parse(WindowKey.Manager("review").wire), Some(WindowKey.Manager("review")))
    assertEquals(WindowKey.Manager("review").initialTitle, "manager · review")

  test("phase 2 settings: stale days, infer filter, seed checks round-trip through the form"):
    val f = ManagersView.fields(ManagerConfig(staleAfterDays = Some(30), inferFilter = Some(false), minSources = Some(4), maxArticles = Some(3)))
    assertEquals((f("staleAfterDays"), f("inferFilter"), f("minSources"), f("maxArticles")), ("30", "off", "4", "3"))
    assertEquals(ManagersView.config(f).map(c => (c.staleAfterDays, c.inferFilter, c.minSources, c.maxArticles)),
      Right((Some(30), Some(false), Some(4), Some(3))))
    assert(ManagersView.config(Map("inferFilter" -> "maybe")).isLeft)
    assert(ManagersView.config(Map("minSources" -> "x")).isLeft)

  test("daily cap: a number of USD (0 = none) round-trips through the form; the day's spend reads with it"):
    assertEquals(ManagersView.config(Map("dailyUsd" -> "2.5")).map(_.dailyUsd), Right(Some(2.5)))
    assertEquals(ManagersView.fields(ManagerConfig(dailyUsd = Some(2.5)))("dailyUsd"), "2.5")
    assert(ManagersView.config(Map("dailyUsd" -> "-1")).isLeft)
    val m = ManagerInfo("review", "review", effective = ManagerConfig(dailyUsd = Some(2.0)), spentTodayUsd = 0.5, tokensToday = 12345)
    assertEquals(ManagersMarkdown.today(m), "today 0.50 of 2.00 USD, 12.3k tokens")
    assertEquals(ManagersMarkdown.today(ManagerInfo("enrich", "enrich", spentTodayUsd = 0.012)), "today 0.0120 USD")

  test("the page buttons' line: one item, several, nothing to enrich"):
    val i = ManagerItem(7, "review", ArticleReview, "tin", "pending")
    assertEquals(ManagersView.enqueuedNote(EnqueueKind.Review, EnqueueResult("review", List(i))), "queued for review (item #7, pending)")
    assertEquals(ManagersView.enqueuedNote(EnqueueKind.Enrich, EnqueueResult("enrich", List(i, i.copy(id = 8)), List("[[x]]: notes"))),
      "2 items queued for enrichment; 1 source enriched already")
    assertEquals(ManagersView.enqueuedNote(EnqueueKind.Enrich, EnqueueResult("enrich", Nil, List("a", "b"))),
      "nothing to enrich; 2 sources enriched already")

  test("New research area: the slug comes from the title; a title is needed; tags split; the note says what was made"):
    val a = GoalsView.newArea("Bronze-Age Tin: where from?", "", " origin ", "tin, trade, tin", plan = true, seed = false)
    assertEquals(a.map(x => (x.slug, x.put.title, x.put.notes, x.put.tags, x.plan, x.seed)),
      Right(("bronze-age-tin-where-from", "Bronze-Age Tin: where from?", Some("origin"), List("tin", "trade"), true, false)))
    assert(GoalsView.newArea(" ", "", "", "", true, true).isLeft)
    assert(GoalsView.newArea("x", "Not A Slug", "", "", true, true).isLeft)
    assertEquals(GoalsView.newArea("x", "my-area", "", "", false, true).map(_.slug), Right("my-area"))
    val note = GoalsView.createdNote(a.toOption.get, planned = true, seeded = Some("seed:bronze-age-tin-where-from"))
    assert(note.contains("planner is running") && note.contains("created paused"), note)

  test("hint box: suggest lets the model propose the action (none chosen here); both at once is refused"):
    assertEquals(ManagersView.hint("leave tin alone until December", "", false, "none", "", "", "", "", suggest = true),
      Right(HintAdd("leave tin alone until December", None, Some(false), suggest = Some(true))))
    assert(ManagersView.hint("x", "", false, "skip", "tin", "", "", "", suggest = true).isLeft)
