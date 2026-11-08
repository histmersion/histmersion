package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.ManagersMarkdown

/** The managers window's pure parts: the badge, the item actions per status, the settings form, the hint box, the pager
  * and the window keys.
  */
class ManagersViewSuite extends munit.FunSuite:

  test("badge: the person's state, else what the supervisor derived"):
    assertEquals(
      ManagersView.badge(ManagerInfo(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerInfo.Kind("review"), state = com.histmersion.api.automation.Managers.ManagerInfo.State("paused"), statusNote = Some(ManagerInfo.StatusNote("paused")))),
      "paused"
    )
    assertEquals(
      ManagersView.badge(
        ManagerInfo(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerInfo.Kind("review"), statusNote = Some(com.histmersion.api.automation.Managers.ManagerInfo.StatusNote("starved: skeptic chain below its plan floor")))
      ),
      "starved"
    )
    assertEquals(
      ManagersView.badge(
        ManagerInfo(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerInfo.Kind("review"), statusNote = Some(com.histmersion.api.automation.Managers.ManagerInfo.StatusNote("at capacity (2 runs in flight, 3 pending)")))
      ),
      "at capacity"
    )
    assertEquals(ManagersView.badge(ManagerInfo(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerInfo.Kind("review"), statusNote = Some(com.histmersion.api.automation.Managers.ManagerInfo.StatusNote("idle")))), "idle")
    assertEquals(
      ManagersView.badge(ManagerInfo(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:g"), com.histmersion.api.automation.Managers.ManagerInfo.Kind("seed"), statusNote = Some(com.histmersion.api.automation.Managers.ManagerInfo.StatusNote("focus: its goal waits for a slot — idle")))),
      "queued"
    )
    assertEquals(ManagersView.badge(ManagerInfo(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), com.histmersion.api.automation.Managers.ManagerInfo.Kind("review"), statusNote = Some(com.histmersion.api.automation.Managers.ManagerInfo.StatusNote("3 pending")))), "active")

  test("item actions by status"):
    assertEquals(ManagersView.itemActions(com.histmersion.api.automation.ItemStatus.Wire("pending")), List("hold", "priority", "skip"))
    assertEquals(ManagersView.itemActions(com.histmersion.api.automation.ItemStatus.Wire("held")), List("release", "priority", "skip"))
    assertEquals(ManagersView.itemActions(com.histmersion.api.automation.ItemStatus.Wire("active")), List("priority", "skip"))
    assertEquals(ManagersView.itemActions(com.histmersion.api.automation.ItemStatus.Wire("stopped")), List("reopen"))
    assertEquals(ManagersView.itemActions(com.histmersion.api.automation.ItemStatus.Wire("nonsense")), Nil)

  test("an item the focus holds is not released by hand; a person's hold is"):
    val focus = ManagerItem(
      com.histmersion.api.automation.Managers.ManagerItem.Id(1),
      com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:g"),
      ManagerItem.Kind(ProposeArticles),
      com.histmersion.api.automation.Managers.ManagerItem.Target("g"),
      com.histmersion.api.automation.Managers.ManagerItem.Status("held"),
      hold = Some(Hold(reason = com.histmersion.api.automation.Managers.Hold.Reason("focus: waits"), focus = Some(com.histmersion.api.automation.Managers.Hold.Focus("g"))))
    )
    assertEquals(ManagersView.itemActionsFor(focus), List("priority", "skip"))
    assertEquals(
      ManagersView.itemActionsFor(focus.modify(_.hold).setTo(Some(Hold(reason = Hold.Reason("held by a person"))))),
      List("release", "priority", "skip")
    )

  test("the focus: its heading, an active goal's pace, the goals that cannot use a slot by why"):
    import com.histmersion.api.automation.Autopilot.*
    assertEquals(
      ManagersView.focusHead(FocusInfo(on = com.histmersion.api.automation.Autopilot.FocusInfo.On(true), max = com.histmersion.api.automation.Autopilot.FocusInfo.Max(8), held = com.histmersion.api.automation.Autopilot.FocusInfo.Held(3))),
      "Focus: at most 8 active goals · 3 items held"
    )
    assertEquals(
      ManagersView.focusHead(FocusInfo(on = com.histmersion.api.automation.Autopilot.FocusInfo.On(false), max = com.histmersion.api.automation.Autopilot.FocusInfo.Max(0), maxFrom = com.histmersion.api.automation.Autopilot.FocusInfo.MaxFrom("person"))),
      "Focus: not in force (cap set by a person)"
    )
    assertEquals(
      ManagersView.focusPace(FocusGoal(com.histmersion.api.automation.Autopilot.FocusGoal.Slug("g"), open = com.histmersion.api.automation.Autopilot.FocusGoal.Open(4), doneLast7 = com.histmersion.api.automation.Autopilot.FocusGoal.DoneLast7(7), etaDays = Some(com.histmersion.api.automation.Autopilot.FocusGoal.EtaDays(4)))),
      "7 done in 7 days, 4 open (~4 d)"
    )
    assertEquals(
      ManagersView.waitingSummary(
        List(FocusWaiting(com.histmersion.api.automation.Autopilot.FocusWaiting.Slug("a"), com.histmersion.api.automation.Autopilot.FocusWaiting.Code("person")), FocusWaiting(com.histmersion.api.automation.Autopilot.FocusWaiting.Slug("b"), com.histmersion.api.automation.Autopilot.FocusWaiting.Code("idle")), FocusWaiting(com.histmersion.api.automation.Autopilot.FocusWaiting.Slug("c"), com.histmersion.api.automation.Autopilot.FocusWaiting.Code("person")))
      ),
      "2 person · 1 idle"
    )

  test("settings form: blank is the default; it reads back what it shows; a bad number is refused"):
    val c = ManagerConfig(
      maxRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRuns(3)),
      includeTags = Some(List(com.histmersion.api.automation.Managers.ManagerConfig.IncludeTags("a"), com.histmersion.api.automation.Managers.ManagerConfig.IncludeTags("b"))),
      models = Some(List(com.histmersion.api.automation.Agents.ModelOverride(com.histmersion.api.automation.AgentRole.Wire("fixer"), com.histmersion.api.automation.Agents.ModelOverride.Model("claude-code:opus"))))
    )
    assertEquals(ManagersView.config(ManagersView.fields(c)), Right(c))
    assertEquals(ManagersView.config(Map.empty), Right(ManagerConfig()))
    assert(ManagersView.config(Map(ManagersView.ConfigKey("rounds") -> ManagersView.ConfigText("x"))).isLeft)
    assert(ManagersView.config(Map(ManagersView.ConfigKey("models") -> ManagersView.ConfigText("fixer"))).isLeft)
    assert(ManagersView.config(Map(ManagersView.ConfigKey("budgetUsd") -> ManagersView.ConfigText("-1"))).isLeft)

  test("hint box: text alone, scoped, with an action; a hold needs a date or a source"):
    assertEquals(
      ManagersView.hint(com.histmersion.api.automation.Managers.HintAdd.Text("prefer 2019"), com.histmersion.api.automation.Managers.HintAdd.Item(""), once = com.histmersion.api.automation.Managers.HintAdd.Once(false), com.histmersion.api.automation.Managers.HintEffect.Action("none"), com.histmersion.api.automation.Managers.HintEffect.Targets(""), com.histmersion.api.automation.Managers.HintEffect.Tag(""), com.histmersion.api.automation.Managers.HintEffect.Until(""), com.histmersion.api.automation.Managers.HintEffect.UntilSource("")),
      Right(HintAdd(com.histmersion.api.automation.Managers.HintAdd.Text("prefer 2019"), None, Some(com.histmersion.api.automation.Managers.HintAdd.Once(false))))
    )
    assertEquals(ManagersView.hint(com.histmersion.api.automation.Managers.HintAdd.Text("x"), com.histmersion.api.automation.Managers.HintAdd.Item("tin"), once = com.histmersion.api.automation.Managers.HintAdd.Once(true), com.histmersion.api.automation.Managers.HintEffect.Action("none"), com.histmersion.api.automation.Managers.HintEffect.Targets(""), com.histmersion.api.automation.Managers.HintEffect.Tag(""), com.histmersion.api.automation.Managers.HintEffect.Until(""), com.histmersion.api.automation.Managers.HintEffect.UntilSource("")).map(_.item), Right(Some("tin")))
    val p = ManagersView.hint(com.histmersion.api.automation.Managers.HintAdd.Text("first"), com.histmersion.api.automation.Managers.HintAdd.Item(""), once = com.histmersion.api.automation.Managers.HintAdd.Once(false), com.histmersion.api.automation.Managers.HintEffect.Action("prioritise"), com.histmersion.api.automation.Managers.HintEffect.Targets("a, b"), com.histmersion.api.automation.Managers.HintEffect.Tag(""), com.histmersion.api.automation.Managers.HintEffect.Until(""), com.histmersion.api.automation.Managers.HintEffect.UntilSource(""))
    assertEquals(p.map(_.effect.map(e => (e.action, e.targets))), Right(Some(("prioritise", List("a", "b")))))
    assert(ManagersView.hint(com.histmersion.api.automation.Managers.HintAdd.Text("wait"), com.histmersion.api.automation.Managers.HintAdd.Item(""), once = com.histmersion.api.automation.Managers.HintAdd.Once(false), com.histmersion.api.automation.Managers.HintEffect.Action("hold"), com.histmersion.api.automation.Managers.HintEffect.Targets("a"), com.histmersion.api.automation.Managers.HintEffect.Tag(""), com.histmersion.api.automation.Managers.HintEffect.Until(""), com.histmersion.api.automation.Managers.HintEffect.UntilSource("")).isLeft)
    assertEquals(
      ManagersView.hint(com.histmersion.api.automation.Managers.HintAdd.Text("wait"), com.histmersion.api.automation.Managers.HintAdd.Item(""), once = com.histmersion.api.automation.Managers.HintAdd.Once(false), com.histmersion.api.automation.Managers.HintEffect.Action("hold"), com.histmersion.api.automation.Managers.HintEffect.Targets("a"), com.histmersion.api.automation.Managers.HintEffect.Tag(""), com.histmersion.api.automation.Managers.HintEffect.Until("2026-11-01"), com.histmersion.api.automation.Managers.HintEffect.UntilSource("")).map(_.effect.flatMap(_.until)),
      Right(Some("2026-11-01"))
    )
    assert(ManagersView.hint(com.histmersion.api.automation.Managers.HintAdd.Text("skip"), com.histmersion.api.automation.Managers.HintAdd.Item(""), once = com.histmersion.api.automation.Managers.HintAdd.Once(false), com.histmersion.api.automation.Managers.HintEffect.Action("skip"), com.histmersion.api.automation.Managers.HintEffect.Targets(""), com.histmersion.api.automation.Managers.HintEffect.Tag(""), com.histmersion.api.automation.Managers.HintEffect.Until(""), com.histmersion.api.automation.Managers.HintEffect.UntilSource("")).isLeft)
    assert(ManagersView.hint(com.histmersion.api.automation.Managers.HintAdd.Text(" "), com.histmersion.api.automation.Managers.HintAdd.Item(""), once = com.histmersion.api.automation.Managers.HintAdd.Once(false), com.histmersion.api.automation.Managers.HintEffect.Action("none"), com.histmersion.api.automation.Managers.HintEffect.Targets(""), com.histmersion.api.automation.Managers.HintEffect.Tag(""), com.histmersion.api.automation.Managers.HintEffect.Until(""), com.histmersion.api.automation.Managers.HintEffect.UntilSource("")).isLeft)
    assert(ManagerPolicyText.isDate("2026-11-01") && !ManagerPolicyText.isDate("10.1000/x"))

  test("pager: the range line and the offsets"):
    assertEquals(Pager.range(com.histmersion.api.Offset(0), com.histmersion.api.Paging.Shown(25), com.histmersion.api.Paging.Page.Total(112)), "1–25 of 112")
    assertEquals(Pager.range(com.histmersion.api.Offset(100), com.histmersion.api.Paging.Shown(12), com.histmersion.api.Paging.Page.Total(112)), "101–112 of 112")
    assertEquals(Pager.range(com.histmersion.api.Offset(0), com.histmersion.api.Paging.Shown(0), com.histmersion.api.Paging.Page.Total(0)), "none")
    assertEquals(Pager.previous(com.histmersion.api.Offset(10), com.histmersion.api.Paging.Limit(25)), 0)
    assertEquals(Pager.next(com.histmersion.api.Offset(75), com.histmersion.api.Paging.Limit(25), com.histmersion.api.Paging.Page.Total(112)), Some(100))
    assertEquals(Pager.next(com.histmersion.api.Offset(100), com.histmersion.api.Paging.Limit(25), com.histmersion.api.Paging.Page.Total(112)), None)

  test("window keys: managers and one manager round-trip through the hash"):
    assertEquals(WindowKey.parse(WindowKey.Managers.wire), Some(WindowKey.Managers))
    assertEquals(WindowKey.parse(WindowKey.Manager(com.histmersion.web.WindowKey.Manager.Id("review")).wire), Some(WindowKey.Manager(com.histmersion.web.WindowKey.Manager.Id("review"))))
    assertEquals(WindowKey.Manager(com.histmersion.web.WindowKey.Manager.Id("review")).initialTitle, "Review manager")

  test("phase 2 settings: stale days, infer filter, seed checks round-trip through the form"):
    val f = ManagersView.fields(
      ManagerConfig(staleAfterDays = Some(com.histmersion.api.automation.Managers.ManagerConfig.StaleAfterDays(30)), inferFilter = Some(com.histmersion.api.automation.Managers.ManagerConfig.InferFilter(false)), minSources = Some(com.histmersion.api.automation.Managers.ManagerConfig.MinSources(4)), maxArticles = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxArticles(3)))
    )
    assertEquals((f(com.histmersion.web.ManagersView.ConfigKey("staleAfterDays")), f(com.histmersion.web.ManagersView.ConfigKey("inferFilter")), f(com.histmersion.web.ManagersView.ConfigKey("minSources")), f(com.histmersion.web.ManagersView.ConfigKey("maxArticles"))), ("30", "off", "4", "3"))
    assertEquals(
      ManagersView.config(f).map(c => (c.staleAfterDays, c.inferFilter, c.minSources, c.maxArticles)),
      Right((Some(30), Some(false), Some(4), Some(3)))
    )
    assert(ManagersView.config(Map(ManagersView.ConfigKey("inferFilter") -> ManagersView.ConfigText("maybe"))).isLeft)
    assert(ManagersView.config(Map(ManagersView.ConfigKey("minSources") -> ManagersView.ConfigText("x"))).isLeft)

  test("daily cap: a number of USD (0 = none) round-trips through the form; the day's spend reads with it"):
    assertEquals(ManagersView.config(Map(ManagersView.ConfigKey("dailyUsd") -> ManagersView.ConfigText("2.5"))).map(_.dailyUsd), Right(Some(2.5)))
    assertEquals(ManagersView.fields(ManagerConfig(dailyUsd = Some(ManagerConfig.DailyUsd(2.5))))(ManagersView.ConfigKey("dailyUsd")), "2.5")
    assert(ManagersView.config(Map(ManagersView.ConfigKey("dailyUsd") -> ManagersView.ConfigText("-1"))).isLeft)
    val m = ManagerInfo(
      com.histmersion.api.automation.Managers.ManagerInfo.Id("review"),
      com.histmersion.api.automation.Managers.ManagerInfo.Kind("review"),
      effective = ManagerConfig(dailyUsd = Some(ManagerConfig.DailyUsd(2.0))),
      spentTodayUsd = ManagerInfo.SpentTodayUsd(0.5),
      tokensToday = ManagerInfo.TokensToday(12345L)
    )
    assertEquals(ManagersMarkdown.today(m), "today 0.50 of 2.00 USD, 12.3k tokens")
    assertEquals(ManagersMarkdown.today(ManagerInfo(com.histmersion.api.automation.Managers.ManagerInfo.Id("enrich"), com.histmersion.api.automation.Managers.ManagerInfo.Kind("enrich"), spentTodayUsd = ManagerInfo.SpentTodayUsd(0.012))), "today 0.0120 USD")

  test("the page buttons' line: one item, several, nothing to enrich"):
    val i = ManagerItem(com.histmersion.api.automation.Managers.ManagerItem.Id(7), com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), ManagerItem.Kind(ArticleReview), com.histmersion.api.automation.Managers.ManagerItem.Target("tin"), com.histmersion.api.automation.Managers.ManagerItem.Status("pending"))
    assertEquals(
      ManagersView.enqueuedNote(EnqueueKind.Review, EnqueueResult(com.histmersion.api.automation.Managers.ManagerInfo.Id("review"), List(i))),
      "queued for review (item #7, pending)"
    )
    assertEquals(
      ManagersView
        .enqueuedNote(EnqueueKind.Enrich, EnqueueResult(com.histmersion.api.automation.Managers.ManagerInfo.Id("enrich"), List(i, i.modify(_.id).setTo(ManagerItem.Id(8))), List(com.histmersion.api.automation.Managers.EnqueueResult.Skipped("[[x]]: notes")))),
      "2 items queued for enrichment; 1 source enriched already"
    )
    assertEquals(
      ManagersView.enqueuedNote(EnqueueKind.Enrich, EnqueueResult(com.histmersion.api.automation.Managers.ManagerInfo.Id("enrich"), Nil, List(com.histmersion.api.automation.Managers.EnqueueResult.Skipped("a"), com.histmersion.api.automation.Managers.EnqueueResult.Skipped("b")))),
      "nothing to enrich; 2 sources enriched already"
    )

  test("New research area: the slug comes from the title; a title is needed; tags split; the note says what was made"):
    val a =
      GoalsView.newArea(com.histmersion.api.automation.Goal.Title("Bronze-Age Tin: where from?"), com.histmersion.api.automation.Goal.Slug(""), com.histmersion.api.automation.Goal.Notes(" origin "), com.histmersion.web.GoalsView.TagsText("tin, trade, tin"), plan = com.histmersion.web.GoalsView.NewAreaPlan(true), seed = com.histmersion.web.GoalsView.NewAreaSeed(false))
    assertEquals(
      a.map(x => (x.slug, x.put.title, x.put.notes, x.put.tags, x.plan, x.seed)),
      Right(
        ("bronze-age-tin-where-from", "Bronze-Age Tin: where from?", Some("origin"), List("tin", "trade"), true, false)
      )
    )
    assert(GoalsView.newArea(com.histmersion.api.automation.Goal.Title(" "), com.histmersion.api.automation.Goal.Slug(""), com.histmersion.api.automation.Goal.Notes(""), com.histmersion.web.GoalsView.TagsText(""), com.histmersion.web.GoalsView.NewAreaPlan(true), com.histmersion.web.GoalsView.NewAreaSeed(true)).isLeft)
    // the slug is the server's to judge (it answers why it refuses one)
    assertEquals(GoalsView.newArea(com.histmersion.api.automation.Goal.Title("x"), com.histmersion.api.automation.Goal.Slug("Not A Slug"), com.histmersion.api.automation.Goal.Notes(""), com.histmersion.web.GoalsView.TagsText(""), com.histmersion.web.GoalsView.NewAreaPlan(true), com.histmersion.web.GoalsView.NewAreaSeed(true)).map(_.slug), Right("Not A Slug"))
    assertEquals(GoalsView.newArea(com.histmersion.api.automation.Goal.Title("x"), com.histmersion.api.automation.Goal.Slug("my-area"), com.histmersion.api.automation.Goal.Notes(""), com.histmersion.web.GoalsView.TagsText(""), com.histmersion.web.GoalsView.NewAreaPlan(false), com.histmersion.web.GoalsView.NewAreaSeed(true)).map(_.slug), Right("my-area"))
    val note = GoalsView.createdNote(a.toOption.get, planned = com.histmersion.web.GoalsView.Planned(true), seeded = Some(com.histmersion.api.automation.Managers.ManagerInfo.Id("seed:bronze-age-tin-where-from")))
    assert(note.value.contains("planner is running") && note.value.contains("created paused"), note.value)

  test("hint box: suggest lets the model propose the action (none chosen here); both at once is refused"):
    assertEquals(
      ManagersView.hint(com.histmersion.api.automation.Managers.HintAdd.Text("leave tin alone until December"), com.histmersion.api.automation.Managers.HintAdd.Item(""), com.histmersion.api.automation.Managers.HintAdd.Once(false), com.histmersion.api.automation.Managers.HintEffect.Action("none"), com.histmersion.api.automation.Managers.HintEffect.Targets(""), com.histmersion.api.automation.Managers.HintEffect.Tag(""), com.histmersion.api.automation.Managers.HintEffect.Until(""), com.histmersion.api.automation.Managers.HintEffect.UntilSource(""), suggest = com.histmersion.api.automation.Managers.HintAdd.Suggest(true)),
      Right(HintAdd(com.histmersion.api.automation.Managers.HintAdd.Text("leave tin alone until December"), None, Some(com.histmersion.api.automation.Managers.HintAdd.Once(false)), suggest = Some(com.histmersion.api.automation.Managers.HintAdd.Suggest(true))))
    )
    assert(ManagersView.hint(com.histmersion.api.automation.Managers.HintAdd.Text("x"), com.histmersion.api.automation.Managers.HintAdd.Item(""), com.histmersion.api.automation.Managers.HintAdd.Once(false), com.histmersion.api.automation.Managers.HintEffect.Action("skip"), com.histmersion.api.automation.Managers.HintEffect.Targets("tin"), com.histmersion.api.automation.Managers.HintEffect.Tag(""), com.histmersion.api.automation.Managers.HintEffect.Until(""), com.histmersion.api.automation.Managers.HintEffect.UntilSource(""), suggest = com.histmersion.api.automation.Managers.HintAdd.Suggest(true)).isLeft)

  test(
    "New research area, watch: auto-ingest is off unless ticked; its cap a whole number; the note names the watcher"
  ):
    val off = GoalsView.newArea(com.histmersion.api.automation.Goal.Title("Zz tin"), com.histmersion.api.automation.Goal.Slug(""), com.histmersion.api.automation.Goal.Notes(""), com.histmersion.web.GoalsView.TagsText(""), plan = com.histmersion.web.GoalsView.NewAreaPlan(false), seed = com.histmersion.web.GoalsView.NewAreaSeed(false), watch = com.histmersion.web.GoalsView.Watch(true))
    assertEquals(off.map(_.watch), Right(Some(ManagerConfig(autoIngest = Some(com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(false))))))
    val on =
      GoalsView.newArea(com.histmersion.api.automation.Goal.Title("Zz tin"), com.histmersion.api.automation.Goal.Slug(""), com.histmersion.api.automation.Goal.Notes(""), com.histmersion.web.GoalsView.TagsText(""), plan = com.histmersion.web.GoalsView.NewAreaPlan(false), seed = com.histmersion.web.GoalsView.NewAreaSeed(false), watch = com.histmersion.web.GoalsView.Watch(true), autoIngest = com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(true), perDay = com.histmersion.web.GoalsView.PerDay("3"))
    assertEquals(on.map(_.watch.flatMap(_.autoIngestPerDay)), Right(Some(3)))
    assert(
      GoalsView.newArea(com.histmersion.api.automation.Goal.Title("Zz tin"), com.histmersion.api.automation.Goal.Slug(""), com.histmersion.api.automation.Goal.Notes(""), com.histmersion.web.GoalsView.TagsText(""), com.histmersion.web.GoalsView.NewAreaPlan(false), com.histmersion.web.GoalsView.NewAreaSeed(false), watch = com.histmersion.web.GoalsView.Watch(true), autoIngest = com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(true), perDay = com.histmersion.web.GoalsView.PerDay("lots")).isLeft
    )
    assertEquals(GoalsView.newArea(com.histmersion.api.automation.Goal.Title("Zz tin"), com.histmersion.api.automation.Goal.Slug(""), com.histmersion.api.automation.Goal.Notes(""), com.histmersion.web.GoalsView.TagsText(""), com.histmersion.web.GoalsView.NewAreaPlan(false), com.histmersion.web.GoalsView.NewAreaSeed(false)).map(_.watch), Right(None))
    val note = GoalsView.createdNote(on.toOption.get, planned = com.histmersion.web.GoalsView.Planned(false), seeded = None, watched = Some(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-tin")))
    assert(
      note.value.contains("watcher watch:zz-tin created paused") && note.value.contains("ingests legal open-access finds"),
      note.value
    )
    val auto = GoalsView.createdNote(
      on.toOption.get,
      planned = com.histmersion.web.GoalsView.Planned(false),
      seeded = None,
      watched = Some(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-tin")),
      active = Set(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:zz-tin"))
    )
    assert(auto.value.contains("watcher watch:zz-tin created active (the autopilot is on)"), auto.value)

  test("watcher settings: sweeps, auto-ingest and its cap round-trip; a watcher's form leaves out the review settings"):
    val c = ManagersView.config(
      Map(ManagersView.ConfigKey("sweeps") -> ManagersView.ConfigText("currency, acquisition-sweep"), ManagersView.ConfigKey("autoIngest") -> ManagersView.ConfigText("on"), ManagersView.ConfigKey("autoIngestPerDay") -> ManagersView.ConfigText("2"))
    )
    assertEquals(
      c.map(x => (x.sweeps, x.autoIngest, x.autoIngestPerDay)),
      Right((Some(List("currency", "acquisition")), Some(true), Some(2)))
    )
    assert(ManagersView.config(Map(ManagersView.ConfigKey("sweeps") -> ManagersView.ConfigText("weekly"))).isLeft)
    assert(ManagersView.config(Map(ManagersView.ConfigKey("autoIngest") -> ManagersView.ConfigText("maybe"))).isLeft)
    assertEquals(
      ManagersView.fields(ManagerConfig(autoIngest = Some(com.histmersion.api.automation.Managers.ManagerConfig.AutoIngest(false)), sweeps = Some(List(com.histmersion.api.automation.Managers.Sweep.Wire("currency")))))(com.histmersion.web.ManagersView.ConfigKey("autoIngest")),
      "off"
    )
    val watch = ManagersView.settingsFor(com.histmersion.api.automation.Manager.Kind.Wire("watch")).map(_._1)
    assert(watch.contains(ManagersView.ConfigKey("autoIngest")) && !watch.contains(ManagersView.ConfigKey("rounds")), watch)
    assert(!ManagersView.settingsFor(com.histmersion.api.automation.Manager.Kind.Wire("review")).map(_._1).contains(ManagersView.ConfigKey("sweeps")))
    val es = List(
      ManagerEvent(com.histmersion.api.automation.Managers.ManagerEvent.Id(3), com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:t"), kind = com.histmersion.api.automation.Managers.ManagerEvent.Kind("ingested"), text = com.histmersion.api.automation.Managers.ManagerEvent.Text("[[a]] ingested")),
      ManagerEvent(com.histmersion.api.automation.Managers.ManagerEvent.Id(2), com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:t"), kind = com.histmersion.api.automation.Managers.ManagerEvent.Kind("started")),
      ManagerEvent(com.histmersion.api.automation.Managers.ManagerEvent.Id(1), com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:t"), kind = com.histmersion.api.automation.Managers.ManagerEvent.Kind("swept"), text = com.histmersion.api.automation.Managers.ManagerEvent.Text("acquisition-sweep goal t: …"))
    )
    assertEquals(ManagersView.sweepEvents(es).map(_.id), List(3L, 1L))

  test("autopilot settings: auto-approve and extend-min-new round-trip; extend is the review manager's"):
    val c = ManagersView.config(Map(ManagersView.ConfigKey("autoApprove") -> ManagersView.ConfigText("on"), ManagersView.ConfigKey("extendMinNew") -> ManagersView.ConfigText("3")))
    assertEquals(c.map(x => (x.autoApprove, x.extendMinNew)), Right((Some(true), Some(3))))
    assert(ManagersView.config(Map(ManagersView.ConfigKey("autoApprove") -> ManagersView.ConfigText("sometimes"))).isLeft)
    assertEquals(ManagersView.fields(ManagerConfig(autoApprove = Some(com.histmersion.api.automation.Managers.ManagerConfig.AutoApprove(false))))(com.histmersion.web.ManagersView.ConfigKey("autoApprove")), "off")
    assert(ManagersView.settingsFor(com.histmersion.api.automation.Manager.Kind.Wire("review")).map(_._1).contains(ManagersView.ConfigKey("extendMinNew")))
    assert(!ManagersView.settingsFor(com.histmersion.api.automation.Manager.Kind.Wire("watch")).map(_._1).contains(ManagersView.ConfigKey("extendMinNew")))

  test("autopilot status: a goal's line and the stages count"):
    import com.histmersion.api.automation.Autopilot.GoalProgress
    val gs = List(
      GoalProgress(
        com.histmersion.api.automation.Autopilot.GoalProgress.Slug("a"),
        com.histmersion.api.automation.Autopilot.GoalProgress.Title("A"),
        com.histmersion.api.automation.Autopilot.GoalProgress.Status("seeding"),
        sources = com.histmersion.api.automation.Autopilot.GoalProgress.Sources(3),
        articles = com.histmersion.api.automation.Autopilot.GoalProgress.Articles(1),
        openApprovals = com.histmersion.api.automation.Autopilot.GoalProgress.OpenApprovals(2),
        doneLast7 = GoalProgress.DoneLast7(7),
        open = GoalProgress.Open(4),
        etaDays = Some(GoalProgress.EtaDays(4))
      ),
      GoalProgress(com.histmersion.api.automation.Autopilot.GoalProgress.Slug("b"), com.histmersion.api.automation.Autopilot.GoalProgress.Title("B"), com.histmersion.api.automation.Autopilot.GoalProgress.Status("planned")),
      GoalProgress(com.histmersion.api.automation.Autopilot.GoalProgress.Slug("c"), com.histmersion.api.automation.Autopilot.GoalProgress.Title("C"), com.histmersion.api.automation.Autopilot.GoalProgress.Status("seeding"))
    )
    assertEquals(
      ManagersView.goalLine(gs.head),
      "3 sources · 1 article · 2 open approvals · 7 done in 7 days, 4 open (~4 d)"
    )
    assertEquals(ManagersView.stages(gs), "1 planned · 2 seeding")

/** The scheduler panel's pure parts: the queue's filters and facets, and its heading. */
class SchedulerPanelSuite extends munit.FunSuite:
  import com.histmersion.api.automation.AgentScheduler.*
  import com.histmersion.api.automation.Goal
  import com.histmersion.api.jobs.JobKinds
  import com.histmersion.api.library.Facet

  private def w(
      id: Waiting.Item,
      t: Waiting.JobType,
      code: Waiting.Code,
      target: Waiting.Target = Waiting.Target(""),
      goal: Option[Goal.Slug] = None
  ) =
    Waiting(id, Waiting.Manager("m"), Waiting.Kind("k"), t, if target.value.isEmpty then Waiting.Target(s"t${id.value}") else target, goal, code = code)
  private val ws =
    List(
      w(Waiting.Item(1), JobKinds.Wire("review"), Waiting.Code("capacity"), goal = Some(Goal.Slug("tin"))),
      w(Waiting.Item(2), JobKinds.Wire("sweep"), Waiting.Code("pool")),
      w(Waiting.Item(3), JobKinds.Wire("review"), Waiting.Code("blocked"), Waiting.Target("copper"))
    )

  test("the queue's filters: any of the types, any of the reasons, the text in the target, goal or manager"):
    assertEquals(ManagersView.visibleWaiting(ws, Nil, Nil, None).map(_.item), List(1L, 2L, 3L))
    assertEquals(ManagersView.visibleWaiting(ws, List(com.histmersion.api.jobs.JobKinds.Wire("review")), Nil, None).map(_.item), List(1L, 3L))
    assertEquals(ManagersView.visibleWaiting(ws, List(com.histmersion.api.jobs.JobKinds.Wire("review")), List(com.histmersion.api.automation.AgentScheduler.Waiting.Code("blocked")), None).map(_.item), List(3L))
    assertEquals(ManagersView.visibleWaiting(ws, Nil, Nil, Some(com.histmersion.api.automation.Activity.Subject(" TIN "))).map(_.item), List(1L))

  test("facets count the queue, the most first; the heading says the strategy and the room"):
    assertEquals(ManagersView.facet(ws, w => Facet.Value(w.jobType.value)), List("review" -> 2, "sweep" -> 1))
    val s = SchedulerStatus(
      com.histmersion.api.automation.AgentScheduler.SchedulerStatus.Strategy("goal-first"),
      com.histmersion.api.automation.AgentScheduler.SchedulerStatus.StrategyFrom("person"),
      maxRuns = com.histmersion.api.automation.AgentScheduler.SchedulerStatus.MaxRuns(4),
      occupied = com.histmersion.api.automation.AgentScheduler.SchedulerStatus.Occupied(3),
      pools = List(PoolUse(com.histmersion.api.automation.AgentScheduler.PoolUse.Pool("chatgpt-plan"), com.histmersion.api.automation.AgentScheduler.PoolUse.Used(1), com.histmersion.api.automation.AgentScheduler.PoolUse.Cap(2))),
      forPerson = SchedulerStatus.ForPerson(2)
    )
    assertEquals(
      ManagersView.schedulerHead(s),
      "Scheduler: One goal at a time (set by a person) · 3 of 4 runs at once · ChatGPT plan 1/2 · 2 items wait for a person (no place held)"
    )

/** The approvals window's digest line. */
class ApprovalDigestLineSuite extends munit.FunSuite:
  import com.histmersion.api.automation.ApprovalDigest.KindGroup
  test("a kind: how many, the oldest, who decides"):
    assertEquals(
      ApprovalsView.digestLine(KindGroup(com.histmersion.api.automation.ApprovalDigest.KindGroup.Kind("goal-plan"), com.histmersion.api.automation.ApprovalDigest.KindGroup.Count(64), Some(com.histmersion.api.automation.ApprovalDigest.KindGroup.Oldest("2026-10-21")))),
      "Plan a goal 64 (oldest 2026-10-21) · a person decides"
    )
    assertEquals(
      ApprovalsView.digestLine(KindGroup(com.histmersion.api.automation.ApprovalDigest.KindGroup.Kind("goal-plan"), com.histmersion.api.automation.ApprovalDigest.KindGroup.Count(64), Some(com.histmersion.api.automation.ApprovalDigest.KindGroup.Oldest("2026-10-21")), com.histmersion.api.automation.ApprovalDigest.KindGroup.DecidedBy("waits"))),
      "Plan a goal 64 (oldest 2026-10-21) · waiting: nobody needs to decide them now"
    )
    assertEquals(
      ApprovalsView.digestLine(KindGroup(com.histmersion.api.automation.ApprovalDigest.KindGroup.Kind("article-publish"), com.histmersion.api.automation.ApprovalDigest.KindGroup.Count(2), None, com.histmersion.api.automation.ApprovalDigest.KindGroup.DecidedBy("checked"))),
      "Make an article readable 2 · the autopilot's checks decide them within the day's cap"
    )

/** The autopilot section's drain line. */
class DrainLineSuite extends munit.FunSuite:
  import com.histmersion.api.automation.Autopilot.DrainInfo
  test("draining with runs in flight, then drained"):
    assertEquals(
      ManagersView.drainLine(DrainInfo(com.histmersion.api.automation.Autopilot.DrainInfo.Since("2026-10-24T10:00:00Z"), com.histmersion.api.automation.Autopilot.DrainInfo.By("cli"), inFlight = com.histmersion.api.automation.Autopilot.DrainInfo.InFlight(2))),
      "Finishing current work, then pausing (since 2026-10-24T10:00:00Z, by cli): nothing new starts; 2 runs in flight still finishing"
    )
    assert(ManagersView.drainLine(DrainInfo(com.histmersion.api.automation.Autopilot.DrainInfo.Since("t"), drained = com.histmersion.api.automation.Autopilot.DrainInfo.Drained(true), drainedAt = Some(com.histmersion.api.automation.Autopilot.DrainInfo.DrainedAt("t2")))).value.startsWith("Paused since t2"))
