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
    assertEquals(ManagersView.badge(ManagerInfo("seed:g", "seed", statusNote = Some("focus: its goal waits for a slot — idle"))), "queued")
    assertEquals(ManagersView.badge(ManagerInfo("review", "review", statusNote = Some("3 pending"))), "active")

  test("item actions by status"):
    assertEquals(ManagersView.itemActions("pending"), List("hold", "priority", "skip"))
    assertEquals(ManagersView.itemActions("held"), List("release", "priority", "skip"))
    assertEquals(ManagersView.itemActions("active"), List("priority", "skip"))
    assertEquals(ManagersView.itemActions("stopped"), List("reopen"))
    assertEquals(ManagersView.itemActions("nonsense"), Nil)

  test("an item the focus holds is not released by hand; a person's hold is"):
    val focus = ManagerItem(1, "seed:g", ProposeArticles, "g", "held", hold = Some(Hold(reason = "focus: waits", focus = Some("g"))))
    assertEquals(ManagersView.itemActionsFor(focus), List("priority", "skip"))
    assertEquals(ManagersView.itemActionsFor(focus.copy(hold = Some(Hold(reason = "held by a person")))), List("release", "priority", "skip"))

  test("the focus: its heading, an active goal's pace, the goals that cannot use a slot by why"):
    import com.histmersion.core.Autopilot.*
    assertEquals(ManagersView.focusHead(FocusInfo(on = true, max = 8, held = 3)), "Focus: at most 8 active goals · 3 items held")
    assertEquals(ManagersView.focusHead(FocusInfo(on = false, max = 0, maxFrom = "person")), "Focus: not in force (cap set by a person)")
    assertEquals(ManagersView.focusPace(FocusGoal("g", open = 4, doneLast7 = 7, etaDays = Some(4))), "7 done in 7 days, 4 open (~4 d)")
    assertEquals(ManagersView.waitingSummary(List(FocusWaiting("a", "person"), FocusWaiting("b", "idle"), FocusWaiting("c", "person"))),
      "2 person · 1 idle")

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

  test("New research area, watch: auto-ingest is off unless ticked; its cap a whole number; the note names the watcher"):
    val off = GoalsView.newArea("Zz tin", "", "", "", plan = false, seed = false, watch = true)
    assertEquals(off.map(_.watch), Right(Some(ManagerConfig(autoIngest = Some(false)))))
    val on = GoalsView.newArea("Zz tin", "", "", "", plan = false, seed = false, watch = true, autoIngest = true, perDay = "3")
    assertEquals(on.map(_.watch.flatMap(_.autoIngestPerDay)), Right(Some(3)))
    assert(GoalsView.newArea("Zz tin", "", "", "", false, false, watch = true, autoIngest = true, perDay = "lots").isLeft)
    assertEquals(GoalsView.newArea("Zz tin", "", "", "", false, false).map(_.watch), Right(None))
    val note = GoalsView.createdNote(on.toOption.get, planned = false, seeded = None, watched = Some("watch:zz-tin"))
    assert(note.contains("watcher watch:zz-tin created paused") && note.contains("ingests legal open-access finds"), note)
    val auto = GoalsView.createdNote(on.toOption.get, planned = false, seeded = None, watched = Some("watch:zz-tin"),
      active = Set("watch:zz-tin"))
    assert(auto.contains("watcher watch:zz-tin created active (the autopilot is on)"), auto)

  test("watcher settings: sweeps, auto-ingest and its cap round-trip; a watcher's form leaves out the review settings"):
    val c = ManagersView.config(Map("sweeps" -> "currency, acquisition-sweep", "autoIngest" -> "on", "autoIngestPerDay" -> "2"))
    assertEquals(c.map(x => (x.sweeps, x.autoIngest, x.autoIngestPerDay)), Right((Some(List("currency", "acquisition")), Some(true), Some(2))))
    assert(ManagersView.config(Map("sweeps" -> "weekly")).isLeft)
    assert(ManagersView.config(Map("autoIngest" -> "maybe")).isLeft)
    assertEquals(ManagersView.fields(ManagerConfig(autoIngest = Some(false), sweeps = Some(List("currency"))))("autoIngest"), "off")
    val watch = ManagersView.settingsFor("watch").map(_._1)
    assert(watch.contains("autoIngest") && !watch.contains("rounds"), watch)
    assert(!ManagersView.settingsFor("review").map(_._1).contains("sweeps"))
    val es = List(ManagerEvent(3, "watch:t", kind = "ingested", text = "[[a]] ingested"), ManagerEvent(2, "watch:t", kind = "started"),
      ManagerEvent(1, "watch:t", kind = "swept", text = "acquisition-sweep goal t: …"))
    assertEquals(ManagersView.sweepEvents(es).map(_.id), List(3L, 1L))

  test("autopilot settings: auto-approve and extend-min-new round-trip; extend is the review manager's"):
    val c = ManagersView.config(Map("autoApprove" -> "on", "extendMinNew" -> "3"))
    assertEquals(c.map(x => (x.autoApprove, x.extendMinNew)), Right((Some(true), Some(3))))
    assert(ManagersView.config(Map("autoApprove" -> "sometimes")).isLeft)
    assertEquals(ManagersView.fields(ManagerConfig(autoApprove = Some(false)))("autoApprove"), "off")
    assert(ManagersView.settingsFor("review").map(_._1).contains("extendMinNew"))
    assert(!ManagersView.settingsFor("watch").map(_._1).contains("extendMinNew"))

  test("autopilot status: a goal's line and the stages count"):
    import com.histmersion.core.Autopilot.GoalProgress
    val gs = List(GoalProgress("a", "A", "seeding", sources = 3, articles = 1, openApprovals = 2, doneLast7 = 7, open = 4, etaDays = Some(4)),
      GoalProgress("b", "B", "planned"), GoalProgress("c", "C", "seeding"))
    assertEquals(ManagersView.goalLine(gs.head), "3 sources · 1 article · 2 open approvals · 7 done in 7 days, 4 open (~4 d)")
    assertEquals(ManagersView.stages(gs), "1 planned · 2 seeding")
