package com.histmersion
package web

import com.histmersion.api.automation.managers.ManagerInfo
import com.histmersion.api.automation.managers.ManagerItem
import com.histmersion.api.automation.autopilot.FocusInfo
import com.histmersion.api.automation.autopilot.FocusGoal
import com.histmersion.api.automation.autopilot.FocusWaiting
import com.histmersion.api.automation.managers.ManagerConfig
import com.histmersion.api.automation.agents.ModelOverride
import com.histmersion.api.automation.managers.HintAdd
import com.histmersion.api.automation.managers.HintEffect
import com.histmersion.api.automation.managers.EnqueueKind
import com.histmersion.api.automation.managers.EnqueueResult
import com.histmersion.api.automation.managers.Sweep
import com.histmersion.api.automation.managers.ManagerEvent
import com.histmersion.api.automation.autopilot.GoalProgress
import com.histmersion.api.automation.agentscheduler.Waiting
import com.histmersion.api.automation.agentscheduler.SchedulerStatus
import com.histmersion.api.automation.agentscheduler.StrategyLine
import com.histmersion.api.automation.agentscheduler.PoolUse
import com.histmersion.api.automation.approvaldigest.KindGroup
import com.histmersion.api.automation.autopilot.DrainInfo

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.automation.Manager
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.AgentScheduler
import com.histmersion.api.automation.AgentRole
import com.histmersion.api.Offset
import com.histmersion.api.Paging
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Manager
import com.histmersion.api.automation.Activity
import com.histmersion.api.WireEnumValues
import com.histmersion.web.ui.Choices
import com.histmersion.lint.{tolerated, LintRule}

/** The managers window's pure parts: the badge, the item actions per status, the settings form, the hint box, the pager
  * and the window keys.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ManagersViewSuite extends munit.FunSuite:

  test("badge: the person's state, else what the supervisor derived"):
    assertEquals(
      ManagersView.badge(
        ManagerInfo(
          ManagerInfo.Id("review"),
          ManagerInfo.Kind("review"),
          state = ManagerInfo.State("paused"),
          statusNote = Some(Manager.Status.parse("paused"))
        )
      ),
      "paused"
    )
    assertEquals(
      ManagersView.badge(
        ManagerInfo(
          ManagerInfo.Id("review"),
          ManagerInfo.Kind("review"),
          statusNote = Some(Manager.Status.parse("starved: skeptic chain below its plan floor"))
        )
      ),
      "starved"
    )
    assertEquals(
      ManagersView.badge(
        ManagerInfo(
          ManagerInfo.Id("review"),
          ManagerInfo.Kind("review"),
          statusNote = Some(Manager.Status.parse("at capacity (2 runs in flight, 3 pending)"))
        )
      ),
      "at capacity"
    )
    assertEquals(
      ManagersView.badge(
        ManagerInfo(
          ManagerInfo.Id("review"),
          ManagerInfo.Kind("review"),
          statusNote = Some(Manager.Status.parse("idle"))
        )
      ),
      "idle"
    )
    assertEquals(
      ManagersView.badge(
        ManagerInfo(
          ManagerInfo.Id("seed:g"),
          ManagerInfo.Kind("seed"),
          statusNote = Some(Manager.Status.parse("focus: its goal waits for a slot — idle"))
        )
      ),
      "queued"
    )
    assertEquals(
      ManagersView.badge(
        ManagerInfo(
          ManagerInfo.Id("review"),
          ManagerInfo.Kind("review"),
          statusNote = Some(Manager.Status.parse("3 pending"))
        )
      ),
      "active"
    )

  test("item actions by status"):
    assertEquals(
      ManagersView.itemActions(api.automation.ItemStatus.Wire("pending")),
      List("hold", "priority", "skip")
    )
    assertEquals(
      ManagersView.itemActions(api.automation.ItemStatus.Wire("held")),
      List("release", "priority", "skip")
    )
    assertEquals(
      ManagersView.itemActions(api.automation.ItemStatus.Wire("active")),
      List("priority", "skip")
    )
    assertEquals(ManagersView.itemActions(api.automation.ItemStatus.Wire("stopped")), List("reopen"))
    assertEquals(ManagersView.itemActions(api.automation.ItemStatus.Wire("nonsense")), Nil)

  test("an item the focus holds is not released by hand; a person's hold is"):
    val focus = ManagerItem(
      ManagerItem.Id(1),
      ManagerInfo.Id("seed:g"),
      ManagerItem.Kind(ProposeArticles),
      ManagerItem.Target("g"),
      ManagerItem.Status("held"),
      hold = Some(
        api.automation.managers
          .Hold(reason = Managers.Rationale("focus: waits"), focus = Some(api.automation.managers.Hold.Focus("g")))
      )
    )
    assertEquals(ManagersView.itemActionsFor(focus), List("priority", "skip"))
    assertEquals(
      ManagersView.itemActionsFor(
        focus.modify(_.hold).setTo(Some(api.automation.managers.Hold(reason = Managers.Rationale("held by a person"))))
      ),
      List("release", "priority", "skip")
    )

  test("the focus: its heading, an active goal's pace, the goals that cannot use a slot by why"):
    import com.histmersion.api.automation.Autopilot.*
    assertEquals(
      ManagersView.focusHead(FocusInfo(on = FocusInfo.On.On, max = FocusInfo.Max(8), held = FocusInfo.Held(3))),
      "Focus: at most 8 active goals · 3 items held"
    )
    assertEquals(
      ManagersView.focusHead(
        FocusInfo(on = FocusInfo.On.Off, max = FocusInfo.Max(0), maxFrom = FocusInfo.MaxFrom("person"))
      ),
      "Focus: not in force (cap set by a person)"
    )
    assertEquals(
      ManagersView.focusPace(
        FocusGoal(
          FocusGoal.Slug("g"),
          open = FocusGoal.Open(4),
          doneLast7 = FocusGoal.DoneLast7(7),
          etaDays = Some(FocusGoal.EtaDays(4))
        )
      ),
      "7 done in 7 days, 4 open (~4 d)"
    )
    assertEquals(
      ManagersView.waitingSummary(
        List(
          FocusWaiting(FocusWaiting.Slug("a"), FocusWaiting.Code("person")),
          FocusWaiting(FocusWaiting.Slug("b"), FocusWaiting.Code("idle")),
          FocusWaiting(FocusWaiting.Slug("c"), FocusWaiting.Code("person"))
        )
      ),
      "2 person · 1 idle"
    )

  test("settings form: blank is the default; it reads back what it shows; a bad number is refused"):
    val c = ManagerConfig(
      maxRuns = Some(ManagerConfig.MaxRuns(3)),
      includeTags = Some(List(ManagerConfig.IncludeTags("a"), ManagerConfig.IncludeTags("b"))),
      models = Some(List(ModelOverride(AgentRole.Wire("fixer"), ModelOverride.Model("claude-code:opus"))))
    )
    assertEquals(ManagersView.config(ManagersView.fields(c)), Right(c))
    assertEquals(ManagersView.config(Map.empty), Right(ManagerConfig()))
    assert(ManagersView.config(Map(ManagersView.ConfigKey("rounds") -> ManagersView.ConfigText("x"))).isLeft)
    assert(ManagersView.config(Map(ManagersView.ConfigKey("models") -> ManagersView.ConfigText("fixer"))).isLeft)
    assert(ManagersView.config(Map(ManagersView.ConfigKey("budgetUsd") -> ManagersView.ConfigText("-1"))).isLeft)

  test("hint box: text alone, scoped, with an action; a hold needs a date or a source"):
    assertEquals(
      ManagersView.hint(
        HintAdd.Text("prefer 2019"),
        HintAdd.Item(""),
        once = HintAdd.Once.Standing,
        HintEffect.Action("none"),
        HintEffect.Targets(""),
        HintEffect.Tag(""),
        HintEffect.Until(""),
        HintEffect.UntilSource("")
      ),
      Right(HintAdd(HintAdd.Text("prefer 2019"), None, Some(HintAdd.Once.Standing)))
    )
    assertEquals(
      ManagersView
        .hint(
          HintAdd.Text("x"),
          HintAdd.Item("tin"),
          once = HintAdd.Once.Once,
          HintEffect.Action("none"),
          HintEffect.Targets(""),
          HintEffect.Tag(""),
          HintEffect.Until(""),
          HintEffect.UntilSource("")
        )
        .map(_.item),
      Right(Some("tin"))
    )
    val p = ManagersView.hint(
      HintAdd.Text("first"),
      HintAdd.Item(""),
      once = HintAdd.Once.Standing,
      HintEffect.Action("prioritise"),
      HintEffect.Targets("a, b"),
      HintEffect.Tag(""),
      HintEffect.Until(""),
      HintEffect.UntilSource("")
    )
    assertEquals(p.map(_.effect.map(e => (e.action, e.targets))), Right(Some(("prioritise", List("a", "b")))))
    assert(
      ManagersView
        .hint(
          HintAdd.Text("wait"),
          HintAdd.Item(""),
          once = HintAdd.Once.Standing,
          HintEffect.Action("hold"),
          HintEffect.Targets("a"),
          HintEffect.Tag(""),
          HintEffect.Until(""),
          HintEffect.UntilSource("")
        )
        .isLeft
    )
    assertEquals(
      ManagersView
        .hint(
          HintAdd.Text("wait"),
          HintAdd.Item(""),
          once = HintAdd.Once.Standing,
          HintEffect.Action("hold"),
          HintEffect.Targets("a"),
          HintEffect.Tag(""),
          HintEffect.Until("2026-11-01"),
          HintEffect.UntilSource("")
        )
        .map(_.effect.flatMap(_.until)),
      Right(Some("2026-11-01"))
    )
    assert(
      ManagersView
        .hint(
          HintAdd.Text("skip"),
          HintAdd.Item(""),
          once = HintAdd.Once.Standing,
          HintEffect.Action("skip"),
          HintEffect.Targets(""),
          HintEffect.Tag(""),
          HintEffect.Until(""),
          HintEffect.UntilSource("")
        )
        .isLeft
    )
    assert(
      ManagersView
        .hint(
          HintAdd.Text(" "),
          HintAdd.Item(""),
          once = HintAdd.Once.Standing,
          HintEffect.Action("none"),
          HintEffect.Targets(""),
          HintEffect.Tag(""),
          HintEffect.Until(""),
          HintEffect.UntilSource("")
        )
        .isLeft
    )
    assert(ManagerPolicyText.isDate("2026-11-01") && !ManagerPolicyText.isDate("10.1000/x"))

  test("pager: the range line and the offsets"):
    assertEquals(Pager.range(Offset(0), Paging.Shown(25), Paging.Page.Total(112)), "1–25 of 112")
    assertEquals(Pager.range(Offset(100), Paging.Shown(12), Paging.Page.Total(112)), "101–112 of 112")
    assertEquals(Pager.range(Offset(0), Paging.Shown(0), Paging.Page.Total(0)), "none")
    assertEquals(Pager.previous(Offset(10), Paging.Limit(25)), 0)
    assertEquals(Pager.next(Offset(75), Paging.Limit(25), Paging.Page.Total(112)), Some(100))
    assertEquals(Pager.next(Offset(100), Paging.Limit(25), Paging.Page.Total(112)), None)

  test("window keys: managers and one manager round-trip through the hash"):
    assertEquals(WindowKey.parse(WindowKey.Managers.wire), Some(WindowKey.Managers))
    assertEquals(
      WindowKey.parse(WindowKey.Manager(WindowKey.Manager.Id("review")).wire),
      Some(WindowKey.Manager(WindowKey.Manager.Id("review")))
    )
    assertEquals(WindowKey.Manager(WindowKey.Manager.Id("review")).initialTitle, "Review manager")

  test("phase 2 settings: stale days, infer filter, seed checks round-trip through the form"):
    val f = ManagersView.fields(
      ManagerConfig(
        staleAfterDays = Some(ManagerConfig.StaleAfterDays(30)),
        inferFilter = Some(ManagerConfig.InferFilter.Explicit),
        minSources = Some(ManagerConfig.MinSources(4)),
        maxArticles = Some(ManagerConfig.MaxArticles(3))
      )
    )
    assertEquals(
      (
        f(ManagersView.ConfigKey("staleAfterDays")),
        f(ManagersView.ConfigKey("inferFilter")),
        f(ManagersView.ConfigKey("minSources")),
        f(ManagersView.ConfigKey("maxArticles"))
      ),
      ("30", "off", "4", "3")
    )
    assertEquals(
      ManagersView
        .config(f)
        .map(c => (c.staleAfterDays, c.inferFilter.map(_.inferFilter), c.minSources, c.maxArticles)),
      Right((Some(30), Some(false), Some(4), Some(3)))
    )
    assert(ManagersView.config(Map(ManagersView.ConfigKey("inferFilter") -> ManagersView.ConfigText("maybe"))).isLeft)
    assert(ManagersView.config(Map(ManagersView.ConfigKey("minSources") -> ManagersView.ConfigText("x"))).isLeft)

  test("daily cap: a number of USD (0 = none) round-trips through the form; the day's spend reads with it"):
    assertEquals(
      ManagersView.config(Map(ManagersView.ConfigKey("dailyUsd") -> ManagersView.ConfigText("2.5"))).map(_.dailyUsd),
      Right(Some(2.5))
    )
    assertEquals(
      ManagersView
        .fields(ManagerConfig(dailyUsd = Some(ManagerConfig.DailyUsd(2.5))))(ManagersView.ConfigKey("dailyUsd")),
      "2.5"
    )
    assert(ManagersView.config(Map(ManagersView.ConfigKey("dailyUsd") -> ManagersView.ConfigText("-1"))).isLeft)
    val m = ManagerInfo(
      ManagerInfo.Id("review"),
      ManagerInfo.Kind("review"),
      effective = ManagerConfig(dailyUsd = Some(ManagerConfig.DailyUsd(2.0))),
      spentTodayUsd = ManagerInfo.SpentTodayUsd(0.5),
      tokensToday = ManagerInfo.TokensToday(12345L)
    )
    assertEquals(ManagersMarkdown.today(m), "today 0.50 of 2.00 USD, 12.3k tokens")
    assertEquals(
      ManagersMarkdown.today(
        ManagerInfo(
          ManagerInfo.Id("enrich"),
          ManagerInfo.Kind("enrich"),
          spentTodayUsd = ManagerInfo.SpentTodayUsd(0.012)
        )
      ),
      "today 0.0120 USD"
    )

  test("the page buttons' line: one item, several, nothing to enrich"):
    val i = ManagerItem(
      ManagerItem.Id(7),
      ManagerInfo.Id("review"),
      ManagerItem.Kind(ArticleReview),
      ManagerItem.Target("tin"),
      ManagerItem.Status("pending")
    )
    assertEquals(
      ManagersView.enqueuedNote(EnqueueKind.Review, EnqueueResult(ManagerInfo.Id("review"), List(i))),
      "queued for review (item #7, pending)"
    )
    assertEquals(
      ManagersView
        .enqueuedNote(
          EnqueueKind.Enrich,
          EnqueueResult(
            ManagerInfo.Id("enrich"),
            List(i, i.modify(_.id).setTo(ManagerItem.Id(8))),
            List(EnqueueResult.Skipped("[[x]]: notes"))
          )
        ),
      "2 items queued for enrichment; 1 source enriched already"
    )
    assertEquals(
      ManagersView.enqueuedNote(
        EnqueueKind.Enrich,
        EnqueueResult(
          ManagerInfo.Id("enrich"),
          Nil,
          List(EnqueueResult.Skipped("a"), EnqueueResult.Skipped("b"))
        )
      ),
      "nothing to enrich; 2 sources enriched already"
    )

  test("New research area: the slug comes from the title; a title is needed; tags split; the note says what was made"):
    val a =
      GoalsView.newArea(
        Goal.Title("Bronze-Age Tin: where from?"),
        Goal.Slug(""),
        Goal.Notes(" origin "),
        GoalsView.TagsText("tin, trade, tin"),
        plan = GoalsView.NewAreaPlan.Plan,
        seed = GoalsView.NewAreaSeed.NoSeed
      )
    assertEquals(
      a.map(x => (x.slug, x.put.title, x.put.notes, x.put.tags, x.plan.plan, x.seed.seed)),
      Right(
        ("bronze-age-tin-where-from", "Bronze-Age Tin: where from?", Some("origin"), List("tin", "trade"), true, false)
      )
    )
    assert(
      GoalsView
        .newArea(
          Goal.Title(" "),
          Goal.Slug(""),
          Goal.Notes(""),
          GoalsView.TagsText(""),
          GoalsView.NewAreaPlan.Plan,
          GoalsView.NewAreaSeed.Seed
        )
        .isLeft
    )
    // the slug is the server's to judge (it answers why it refuses one)
    assertEquals(
      GoalsView
        .newArea(
          Goal.Title("x"),
          Goal.Slug("Not A Slug"),
          Goal.Notes(""),
          GoalsView.TagsText(""),
          GoalsView.NewAreaPlan.Plan,
          GoalsView.NewAreaSeed.Seed
        )
        .map(_.slug),
      Right("Not A Slug")
    )
    assertEquals(
      GoalsView
        .newArea(
          Goal.Title("x"),
          Goal.Slug("my-area"),
          Goal.Notes(""),
          GoalsView.TagsText(""),
          GoalsView.NewAreaPlan.NoPlan,
          GoalsView.NewAreaSeed.Seed
        )
        .map(_.slug),
      Right("my-area")
    )
    val note = GoalsView.createdNote(
      a.toOption.get,
      planned = GoalsView.Planned.Planned,
      seeded = Some(ManagerInfo.Id("seed:bronze-age-tin-where-from"))
    )
    assert(note.value.contains("planner is running") && note.value.contains("created paused"), note.value)

  test("New research area: the form is ONE request to the server; the note says what the server made"):
    val a = GoalsView
      .newArea(
        Goal.Title("Tin"),
        Goal.Slug(""),
        Goal.Notes("where from"),
        GoalsView.TagsText("tin"),
        GoalsView.NewAreaPlan.Plan,
        GoalsView.NewAreaSeed.Seed,
        GoalsView.Watch.Watched
      )
      .toOption
      .get
    val req = a.create
    assertEquals(
      (req.slug, req.goal.title, req.goal.notes, req.plan.plan, req.seed.seed, req.watch.isDefined),
      ("tin", "Tin", Some("where from"), true, true, true)
    )
    val goal = Goal(Goal.Slug("tin"), Goal.Title("Tin"), Goal.StatusValue("planned"))
    // what the server made decides the note, not what was asked: here only the goal
    assertEquals(GoalsView.madeNote(a, Goal.AreaCreated(goal)).value, "research area tin created")

  test("hint box: suggest lets the model propose the action (none chosen here); both at once is refused"):
    assertEquals(
      ManagersView.hint(
        HintAdd.Text("leave tin alone until December"),
        HintAdd.Item(""),
        HintAdd.Once.Standing,
        HintEffect.Action("none"),
        HintEffect.Targets(""),
        HintEffect.Tag(""),
        HintEffect.Until(""),
        HintEffect.UntilSource(""),
        suggest = HintAdd.Suggest.Suggest
      ),
      Right(
        HintAdd(
          HintAdd.Text("leave tin alone until December"),
          None,
          Some(HintAdd.Once.Standing),
          suggest = Some(HintAdd.Suggest.Suggest)
        )
      )
    )
    assert(
      ManagersView
        .hint(
          HintAdd.Text("x"),
          HintAdd.Item(""),
          HintAdd.Once.Standing,
          HintEffect.Action("skip"),
          HintEffect.Targets("tin"),
          HintEffect.Tag(""),
          HintEffect.Until(""),
          HintEffect.UntilSource(""),
          suggest = HintAdd.Suggest.Suggest
        )
        .isLeft
    )

  test(
    "New research area, watch: auto-ingest is off unless ticked; its cap a whole number; the note names the watcher"
  ):
    val off = GoalsView.newArea(
      Goal.Title("Zz tin"),
      Goal.Slug(""),
      Goal.Notes(""),
      GoalsView.TagsText(""),
      plan = GoalsView.NewAreaPlan.NoPlan,
      seed = GoalsView.NewAreaSeed.NoSeed,
      watch = GoalsView.Watch.Watched
    )
    assertEquals(
      off.map(_.watch),
      Right(Some(ManagerConfig(autoIngest = Some(ManagerConfig.AutoIngest.Manual))))
    )
    val on =
      GoalsView.newArea(
        Goal.Title("Zz tin"),
        Goal.Slug(""),
        Goal.Notes(""),
        GoalsView.TagsText(""),
        plan = GoalsView.NewAreaPlan.NoPlan,
        seed = GoalsView.NewAreaSeed.NoSeed,
        watch = GoalsView.Watch.Watched,
        autoIngest = ManagerConfig.AutoIngest.Auto,
        perDay = GoalsView.PerDay("3")
      )
    assertEquals(on.map(_.watch.flatMap(_.autoIngestPerDay)), Right(Some(3)))
    assert(
      GoalsView
        .newArea(
          Goal.Title("Zz tin"),
          Goal.Slug(""),
          Goal.Notes(""),
          GoalsView.TagsText(""),
          GoalsView.NewAreaPlan.NoPlan,
          GoalsView.NewAreaSeed.NoSeed,
          watch = GoalsView.Watch.Watched,
          autoIngest = ManagerConfig.AutoIngest.Auto,
          perDay = GoalsView.PerDay("lots")
        )
        .isLeft
    )
    assertEquals(
      GoalsView
        .newArea(
          Goal.Title("Zz tin"),
          Goal.Slug(""),
          Goal.Notes(""),
          GoalsView.TagsText(""),
          GoalsView.NewAreaPlan.NoPlan,
          GoalsView.NewAreaSeed.NoSeed
        )
        .map(_.watch),
      Right(None)
    )
    val note = GoalsView.createdNote(
      on.toOption.get,
      planned = GoalsView.Planned.Unplanned,
      seeded = None,
      watched = Some(ManagerInfo.Id("watch:zz-tin"))
    )
    assert(
      note.value.contains("watcher watch:zz-tin created paused") && note.value.contains(
        "ingests legal open-access finds"
      ),
      note.value
    )
    val auto = GoalsView.createdNote(
      on.toOption.get,
      planned = GoalsView.Planned.Unplanned,
      seeded = None,
      watched = Some(ManagerInfo.Id("watch:zz-tin")),
      active = Set(ManagerInfo.Id("watch:zz-tin"))
    )
    assert(auto.value.contains("watcher watch:zz-tin created active (the autopilot is on)"), auto.value)

  test("watcher settings: sweeps, auto-ingest and its cap round-trip; a watcher's form leaves out the review settings"):
    val c = ManagersView.config(
      Map(
        ManagersView.ConfigKey("sweeps") -> ManagersView.ConfigText("currency, acquisition-sweep"),
        ManagersView.ConfigKey("autoIngest") -> ManagersView.ConfigText("on"),
        ManagersView.ConfigKey("autoIngestPerDay") -> ManagersView.ConfigText("2")
      )
    )
    assertEquals(
      c.map(x => (x.sweeps, x.autoIngest.map(_.autoIngest), x.autoIngestPerDay)),
      Right((Some(List("currency", "acquisition")), Some(true), Some(2)))
    )
    assert(ManagersView.config(Map(ManagersView.ConfigKey("sweeps") -> ManagersView.ConfigText("weekly"))).isLeft)
    assert(ManagersView.config(Map(ManagersView.ConfigKey("autoIngest") -> ManagersView.ConfigText("maybe"))).isLeft)
    assertEquals(
      ManagersView.fields(
        ManagerConfig(
          autoIngest = Some(ManagerConfig.AutoIngest.Manual),
          sweeps = Some(List(Sweep.Wire("currency")))
        )
      )(ManagersView.ConfigKey("autoIngest")),
      "off"
    )
    val watch = ManagersView.settingsFor(Manager.Kind.Wire("watch")).map(_._1)
    assert(
      watch.contains(ManagersView.ConfigKey("autoIngest")) && !watch.contains(ManagersView.ConfigKey("rounds")),
      watch
    )
    assert(!ManagersView.settingsFor(Manager.Kind.Wire("review")).map(_._1).contains(ManagersView.ConfigKey("sweeps")))
    val es = List(
      ManagerEvent(
        ManagerEvent.Id(3),
        ManagerInfo.Id("watch:t"),
        kind = ManagerEvent.Kind("ingested"),
        text = ManagerEvent.Text("[[a]] ingested")
      ),
      ManagerEvent(
        ManagerEvent.Id(2),
        ManagerInfo.Id("watch:t"),
        kind = ManagerEvent.Kind("started")
      ),
      ManagerEvent(
        ManagerEvent.Id(1),
        ManagerInfo.Id("watch:t"),
        kind = ManagerEvent.Kind("swept"),
        text = ManagerEvent.Text("acquisition-sweep goal t: …")
      )
    )
    assertEquals(ManagersView.sweepEvents(es).map(_.id), List(3L, 1L))

  test("autopilot settings: auto-approve and extend-min-new round-trip; extend is the review manager's"):
    val c = ManagersView.config(
      Map(
        ManagersView.ConfigKey("autoApprove") -> ManagersView.ConfigText("on"),
        ManagersView.ConfigKey("extendMinNew") -> ManagersView.ConfigText("3")
      )
    )
    assertEquals(c.map(x => (x.autoApprove.map(_.autoApprove), x.extendMinNew)), Right((Some(true), Some(3))))
    assert(
      ManagersView.config(Map(ManagersView.ConfigKey("autoApprove") -> ManagersView.ConfigText("sometimes"))).isLeft
    )
    assertEquals(
      ManagersView.fields(ManagerConfig(autoApprove = Some(ManagerConfig.AutoApprove.Manual)))(
        ManagersView.ConfigKey("autoApprove")
      ),
      "off"
    )
    assert(
      ManagersView.settingsFor(Manager.Kind.Wire("review")).map(_._1).contains(ManagersView.ConfigKey("extendMinNew"))
    )
    assert(
      !ManagersView.settingsFor(Manager.Kind.Wire("watch")).map(_._1).contains(ManagersView.ConfigKey("extendMinNew"))
    )

  test("autopilot status: a goal's line and the stages count"):
    val gs = List(
      GoalProgress(
        GoalProgress.Slug("a"),
        GoalProgress.Title("A"),
        GoalProgress.Status("seeding"),
        sources = GoalProgress.Sources(3),
        articles = GoalProgress.Articles(1),
        openApprovals = GoalProgress.OpenApprovals(2),
        doneLast7 = GoalProgress.DoneLast7(7),
        open = GoalProgress.Open(4),
        etaDays = Some(GoalProgress.EtaDays(4))
      ),
      GoalProgress(GoalProgress.Slug("b"), GoalProgress.Title("B"), GoalProgress.Status("planned")),
      GoalProgress(GoalProgress.Slug("c"), GoalProgress.Title("C"), GoalProgress.Status("seeding"))
    )
    assertEquals(
      ManagersView.goalLine(gs.head),
      "3 sources · 1 article · 2 open approvals · 7 done in 7 days, 4 open (~4 d)"
    )
    assertEquals(ManagersView.stages(gs), "1 planned · 2 seeding")

/** The scheduler panel's pure parts: the queue's filters and facets, and its heading. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SchedulerPanelSuite extends munit.FunSuite:
  import com.histmersion.api.automation.AgentScheduler.*
  import com.histmersion.api.automation.Goal
  import com.histmersion.api.jobs.JobKinds
  import com.histmersion.api.library.Facet

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def w(
      id: Waiting.Item,
      t: Waiting.JobType,
      code: Waiting.Code,
      target: Waiting.Target = Waiting.Target(""),
      goal: Option[Goal.Slug] = None
  ) =
    Waiting(
      id,
      Waiting.Manager("m"),
      Waiting.Kind("k"),
      t,
      if target.value.isEmpty then Waiting.Target(s"t${id.value}") else target,
      goal,
      code = code
    )
  private val ws =
    List(
      w(Waiting.Item(1), JobKinds.Wire("review"), Waiting.Code("capacity"), goal = Some(Goal.Slug("tin"))),
      w(Waiting.Item(2), JobKinds.Wire("sweep"), Waiting.Code("pool")),
      w(Waiting.Item(3), JobKinds.Wire("review"), Waiting.Code("blocked"), Waiting.Target("copper"))
    )

  test("the queue's filters: any of the types, any of the reasons, the text in the target, goal or manager"):
    assertEquals(ManagersView.visibleWaiting(ws, Nil, Nil, None).map(_.item), List(1L, 2L, 3L))
    assertEquals(ManagersView.visibleWaiting(ws, List(JobKinds.Wire("review")), Nil, None).map(_.item), List(1L, 3L))
    assertEquals(
      ManagersView.visibleWaiting(ws, List(JobKinds.Wire("review")), List(Waiting.Code("blocked")), None).map(_.item),
      List(3L)
    )
    assertEquals(ManagersView.visibleWaiting(ws, Nil, Nil, Some(Activity.Subject(" TIN "))).map(_.item), List(1L))

  test("facets count the queue, the most first; the heading says the strategy and the room"):
    assertEquals(ManagersView.facet(ws, w => Facet.Value(w.jobType.value)), List("review" -> 2, "sweep" -> 1))
    val s = SchedulerStatus(
      StrategyLine.Name("goal-first"),
      SchedulerStatus.StrategyFrom("person"),
      maxRuns = SchedulerStatus.MaxRuns(4),
      occupied = SchedulerStatus.Occupied(3),
      pools = List(PoolUse(PoolUse.Pool("chatgpt-plan"), PoolUse.Used(1), PoolUse.Cap(2))),
      forPerson = SchedulerStatus.ForPerson(2)
    )
    assertEquals(
      ManagersView.schedulerHead(s),
      "Scheduler: One goal at a time (set by a person) · 3 of 4 runs at once · ChatGPT plan 1/2 · 2 items wait for a person (no place held)"
    )

/** The approvals window's digest line. */
class ApprovalDigestLineSuite extends munit.FunSuite:
  test("a kind: how many, the oldest, who decides"):
    assertEquals(
      ApprovalsView.digestLine(
        KindGroup(KindGroup.Kind("goal-plan"), KindGroup.Count(64), Some(KindGroup.Oldest("2026-10-21")))
      ),
      "Plan a goal 64 (oldest 2026-10-21) · a person decides"
    )
    assertEquals(
      ApprovalsView.digestLine(
        KindGroup(
          KindGroup.Kind("goal-plan"),
          KindGroup.Count(64),
          Some(KindGroup.Oldest("2026-10-21")),
          KindGroup.DecidedBy("waits")
        )
      ),
      "Plan a goal 64 (oldest 2026-10-21) · waiting: nobody needs to decide them now"
    )
    assertEquals(
      ApprovalsView.digestLine(
        KindGroup(KindGroup.Kind("article-publish"), KindGroup.Count(2), None, KindGroup.DecidedBy("checked"))
      ),
      "Make an article readable 2 · the autopilot's checks decide them within the day's cap"
    )

/** The autopilot section's drain line. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class DrainLineSuite extends munit.FunSuite:
  test("draining with runs in flight, then drained"):
    assertEquals(
      ManagersView.drainLine(
        DrainInfo(DrainInfo.Since("2026-10-24T10:00:00Z"), DrainInfo.By("cli"), inFlight = DrainInfo.InFlight(2))
      ),
      "Finishing current work, then pausing (since 2026-10-24T10:00:00Z, by cli): nothing new starts; 2 runs in flight still finishing"
    )
    assert(
      ManagersView
        .drainLine(
          DrainInfo(
            DrainInfo.Since("t"),
            drained = DrainInfo.Drained.Drained,
            drainedAt = Some(DrainInfo.DrainedAt("t2"))
          )
        )
        .value
        .startsWith("Paused since t2")
    )

  private val hintActions = Choices.optional(WireEnumValues.Wire.of(HintEffect.Verb.NoAction), HintEffect.Verb.Actions)

  test("the hint box's action select: an option's text reads back as the choice it spells, none or a hint's verb"):
    assertEquals(hintActions.read("none"), Some(None))
    assertEquals(hintActions.read("prioritise"), Some(Some(HintEffect.Verb.Prioritise)))
    assertEquals(hintActions.read("hold"), Some(Some(HintEffect.Verb.Hold)))
    assertEquals(hintActions.read("skip"), Some(Some(HintEffect.Verb.Skip)))

  test("the hint box's action select reads a text no offered choice spells as nothing: config is not a person's hint"):
    assertEquals(hintActions.read("config"), None)
    assertEquals(hintActions.read("Hold"), None)
    assertEquals(hintActions.read(""), None)

  test("the hint box's action select offers none first, then the verbs in order"):
    assertEquals(hintActions.all, Vector(None) ++ HintEffect.Verb.Actions.map(Some(_)))
    assertEquals(Choices.of(HintEffect.Verb.Actions).read("skip"), Some(HintEffect.Verb.Skip))
