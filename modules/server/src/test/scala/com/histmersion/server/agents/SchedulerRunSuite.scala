package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.GoalPlans.*
import com.histmersion.db.{AgentRepo, Db, GoalLinkRepo, ManagerRepo, Migrate}
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.*

import java.time.Instant

/** The goal sweeps and the goal schedules, against a `*_test` database: a currency check (assess and
  * followups over a goal's sources; a disagreeing paper in the corpus becomes ONE edge proposal, one
  * outside it ONE acquisition item, however often it runs), an acquisition sweep (never queues what the
  * corpus or the queue has; the ranking model may only pick listed candidates), and — since phase 3 —
  * the schedules as the goal's watcher (set through the goal: a paused watcher; the `goal_schedules`
  * rows migrated into it once, logged; a scheduled run that pauses still leaves a backlog item). The
  * watcher's own runs and crons: [[ManagerPhase3Suite]]. Skipped without HISTMERSION_TEST_DB_URL. */
class SchedulerRunSuite extends com.histmersion.db.DbSuite with com.histmersion.db.Bounded:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout         = scala.concurrent.duration.Duration(120, "s")

  private val Goal  = "zzagent-sched"
  private val Ck    = "zzagent2000_old"
  private val Ck2   = "zzagent2024_new"
  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(enabled = true, prices = "scripted=1/2", roles = b.agents.roles.copy(planner = Model.wire)))

  private lazy val ds = testPool(baseCfg.db)

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers, StubUpstream) =
    val deps = Deps(cfg, ds)
    val h    = Handlers(deps)
    val a    = AgentsHandlers(deps, h, (_, _) => Right(c))
    val up   = StubUpstream()
    a.attach(up)
    (a, h, up)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM approvals WHERE citekey LIKE 'zzagent%' OR subject LIKE 'zzagent%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit   = clean()

  private def seed(h: Handlers): Unit =
    assert(h.putSource(Ck, SourcePut(title = "Old tin", year = Some(2000), doi = Some("10.5555/zzagent.old"))).isRight)
    assert(h.putSource(Ck2, SourcePut(title = "New tin", year = Some(2024), doi = Some("10.5555/zzagent.new"))).isRight)
    assert(h.putGoal(Goal, GoalPut("Zz scheduled tin")).isRight)
    assert(GoalLinkRepo.link(ds, Goal, LinkKind.Source, Ck, None))

  private def start(a: AgentsHandlers, req: RunRequest): RunInfo =
    val run = a.startRun(req).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    AgentRepo.get(ds, run.id).get

  private val citing = List(
    FollowupRow(title = Some("New tin"), year = Some(2024), doi = Some("10.5555/zzagent.new"), contexts = List("we refute the old dating"),
      proposedKind = "refutes", citekey = Some(Ck2)),
    FollowupRow(title = Some("Zzagent outside critique"), year = Some(2022), doi = Some("10.5555/zzagent.out"),
      proposedKind = "challenges"),
    FollowupRow(title = Some("Zzagent agreeing"), year = Some(2021), doi = Some("10.5555/zzagent.ok"), proposedKind = "confirms"))

  test("currency-check: assess and followups over the goal's sources; one edge proposal, one acquisition item — never twice"):
    val (a, h, up) = handlers(ScriptedClient(Model)); seed(h)
    up.citing = Map(Ck -> citing)
    val run = start(a, RunRequest("currency-check", Goal))
    assertEquals(run.status, "done", run.error)
    import scala.jdk.CollectionConverters.*
    assertEquals(up.calls.asScala.toList, List(s"assess $Ck", s"followups $Ck"))
    val open = a.approvalsList(None, None).toOption.get.filter(_.citekey == Ck2)
    assertEquals(open.map(x => (x.kind, x.edgeKind, x.target)), List(("edge", Some("refutes"), Some(Ck))))
    assert(open.head.evidence.contains("we refute the old dating"), open.head.evidence)
    assertEquals(h.neighbors(Ck2, List("refutes")), Nil, "nothing applied before approval")
    def queued = h.ops.queueList("acquire", all = true).toOption.get.items.filter(_.text.contains("zzagent.out"))
    assertEquals(queued.size, 1)
    assert(GoalLinkRepo.links(ds, Goal).exists(l => l.kind == LinkKind.QueueItem && l.ref == queued.head.id.toString))
    assert(run.progress.exists(_.contains("1 edge(s) proposed, 1 paper(s) queued")), run.progress)

    // again (as a schedule would): the open proposal and the queued paper are not doubled
    val again = start(a, RunRequest("currency-check", Goal))
    assertEquals(again.status, "done", again.error)
    assertEquals(a.approvalsList(Some("all"), None).toOption.get.count(_.citekey == Ck2), 1)
    assertEquals(queued.size, 1)
    // a rejected proposal is not asked again either
    assert(a.decide(open.head.id, "reject", DecisionRequest()).isRight)
    assertEquals(start(a, RunRequest("currency-check", Goal)).status, "done")
    assertEquals(a.approvalsList(Some("all"), None).toOption.get.count(_.citekey == Ck2), 1)

  private def work(doi: String, title: String, inDb: Boolean = false) =
    WorkRef(doi = Some(doi), title = Some(title), year = Some(2023), authors = List("Zz"), inDb = inDb)

  test("acquisition-sweep: the goal's queries searched, corpus and queue left out, the ranking model's picks queued once"):
    val c = ScriptedClient(Model)
    c.scripts ++= List(Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"},{"doi":"10.9999/invented","why":"?"}]}""")),
      Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"}]}""")))
    val (a, h, up) = handlers(c); seed(h)
    assert(a.addGoalLink(Goal, LinkAdd(LinkKind.Query, "zzagent tin trade")).isRight)
    assert(h.ops.queueAdd("acquire", Queue.QueueAdd("Zzagent queued. https://doi.org/10.5555/zzagent.q")).isRight)
    up.found = Map("zzagent tin trade" -> List(work("10.5555/zzagent.a", "A"), work("10.5555/zzagent.b", "B"),
      work("10.5555/zzagent.in", "In", inDb = true), work("10.5555/zzagent.q", "Queued")))
    val run = start(a, RunRequest("acquisition-sweep", Goal))
    assertEquals(run.status, "done", run.error)
    // the model saw only the two new candidates, and its invented pick was sent back
    val offered = c.steps.head.head.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }.get
    assert(offered.contains("10.5555/zzagent.a") && offered.contains("10.5555/zzagent.b") && !offered.contains("zzagent.in") &&
      !offered.contains("zzagent.q"), offered)
    assertEquals(c.steps.size, 2, "the invented DOI was refused and the ranking asked again")
    val items = h.ops.queueList("acquire", all = true).toOption.get.items.filter(_.text.contains("zzagent.a"))
    assertEquals(items.size, 1)
    assert(items.head.text.contains("for goal zzagent-sched: core study"), items.head.text)
    assert(run.progress.exists(_.contains("2 new candidate(s), 1 picked, 1 queued")), run.progress)

    // the next sweep finds nothing new: the picked work is now queued, the other still on offer
    c.scripts += Seq(text("""{"picks":[]}"""))
    val again = start(a, RunRequest("acquisition-sweep", Goal))
    assertEquals(again.status, "done", again.error)
    assertEquals(h.ops.queueList("acquire", all = true).toOption.get.items.count(_.text.contains("zzagent.a")), 1)

  // ── goal schedules are watchers (phase 3) ────────────────────────────────────────────────────────

  private val Now = Instant.parse("2026-10-04T12:00:00Z")

  private def cleanManagers(): Unit =
    connect(ds):
      sql"DELETE FROM agent_managers WHERE id LIKE 'watch:zzagent%'".update.run()
      sql"DELETE FROM goal_schedules WHERE goal_slug LIKE 'zzagent%'".update.run()

  test("schedules: a cron is checked, too frequent is refused; setting one makes a PAUSED watcher of the goal with that sweep"):
    cleanManagers()
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(h)
    assert(a.setSchedule(Goal, "currency", ScheduleSet("not cron"), Now).left.exists(_.message.contains("five fields")))
    assert(a.setSchedule(Goal, "currency", ScheduleSet("*/5 * * * *"), Now).left.exists(_.message.contains("min-interval")))
    assert(a.setSchedule(Goal, "weekly", ScheduleSet("@daily"), Now).isLeft)
    val s = a.setSchedule(Goal, "currency", ScheduleSet("@daily", budgetUsd = Some(0.5)), Now).toOption.get
    assertEquals((s.kind, s.cron, s.enabled, s.budgetUsd, s.nextRunAt), (ScheduleKind.Currency, "@daily", true, Some(0.5),
      Some("2026-10-05T00:00:00Z")))
    assert(s.lastNote.exists(_.contains("paused")), s.lastNote)
    val w = a.managing.get(Managers.watchId(Goal)).toOption.get.manager
    assertEquals((w.kind, w.state, w.cron, w.config.sweeps, w.config.budgetUsd), ("watch", "paused", Some("@daily"),
      Some(List("currency")), Some(0.5)))
    // the acquisition sweep joins it; the cron is the watcher's, shared
    val acq = a.setSchedule(Goal, "acquisition", ScheduleSet("@weekly"), Now).toOption.get
    assertEquals((acq.kind, acq.cron), (ScheduleKind.Acquisition, "@weekly"))
    assertEquals(a.goalView(Goal).toOption.get.schedules.map(s => (s.kind, s.cron)),
      List((ScheduleKind.Acquisition, "@weekly"), (ScheduleKind.Currency, "@weekly")))
    val off = a.setSchedule(Goal, "currency", ScheduleSet("@weekly", enabled = false), Now).toOption.get
    assertEquals(off.enabled, false)
    assertEquals(a.goalView(Goal).toOption.get.schedules.map(_.kind), List(ScheduleKind.Acquisition))
    assertEquals(a.removeSchedule(Goal, "acquisition").map(_.removed), Right(true))
    assertEquals(a.removeSchedule(Goal, "acquisition").map(_.removed), Right(false))
    assertEquals(a.goalView(Goal).toOption.get.schedules, Nil)
    assert(a.managing.get(Managers.watchId(Goal)).isRight, "the watcher stays")
    cleanManagers()

  test("migration: goal_schedules rows become the goal's paused watcher (cron, sweeps, budget, next firing), logged, once"):
    cleanManagers()
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(h)
    GoalLinkRepo.setSchedule(ds, Goal, ScheduleKind.Currency, "@daily", true, Some(BigDecimal("0.1")),
      Some(Instant.parse("2026-10-05T00:00:00Z")))
    GoalLinkRepo.setSchedule(ds, Goal, ScheduleKind.Acquisition, "@monthly", true, Some(BigDecimal("0.25")),
      Some(Instant.parse("2026-11-01T00:00:00Z")))
    // before the move, the goal shows the rows as they are
    assertEquals(a.goalView(Goal).toOption.get.schedules.size, 2)
    val out = a.managers.migrateSchedules(Now)
    assertEquals(out, List(s"${Managers.watchId(Goal)}: migrated"))
    assertEquals(GoalLinkRepo.schedules(ds, Some(Goal)), Nil, "the rows are gone")
    val w = a.managing.get(Managers.watchId(Goal)).toOption.get.manager
    assertEquals((w.state, w.cron, w.nextTickAt, w.config.sweeps, w.config.budgetUsd),
      ("paused", Some("@daily"), Some("2026-10-05T00:00:00Z"), Some(List("acquisition", "currency")), Some(0.25)))
    val ev = ManagerRepo.events(ds, w.id, None, 10).filter(_.kind == Managers.Feed.Migrated)
    assertEquals(ev.size, 1)
    assert(ev.head.text.contains("created paused") && ev.head.text.contains("`@daily` (fires most often)"), ev.head.text)
    val log = h.ops.queueList("log", all = true).toOption.get.items.map(_.text).filter(_.contains(Goal))
    assert(log.exists(_.contains("2 goal schedules moved into this watcher")), log)
    assertEquals(a.managers.migrateSchedules(Now), Nil, "idempotent")
    // a schedule brought in later (a bundle import) joins the watcher, which keeps its own cron
    GoalLinkRepo.setSchedule(ds, Goal, ScheduleKind.Currency, "@monthly", false, None, None)
    assertEquals(a.managers.migrateSchedules(Now).size, 1)
    val w2 = a.managing.get(Managers.watchId(Goal)).toOption.get.manager
    assertEquals((w2.cron, w2.config.sweeps), (Some("@daily"), Some(List("acquisition", "currency"))))
    assert(ManagerRepo.events(ds, w.id, None, 10).exists(e => e.kind == Managers.Feed.Migrated && e.text.contains("was off")))
    // the tick migrates too (with agents on)
    GoalLinkRepo.setSchedule(ds, Goal, ScheduleKind.Acquisition, "@weekly", true, None, None)
    assert(a.managers.tick(Now).contains(s"${Managers.watchId(Goal)}: migrated"))
    cleanManagers()

  test("a run a schedule started (origin scheduler) that pauses leaves a backlog item; a person's does not"):
    val c = ScriptedClient(Model); c.fail = true
    val (a, h, up) = handlers(c); seed(h)
    assert(a.addGoalLink(Goal, LinkAdd(LinkKind.Query, "zzagent q")).isRight)
    up.found = Map("zzagent q" -> List(work("10.5555/zzagent.z", "Z")))
    val id = a.startRunAs(RunRequest("acquisition-sweep", Goal), Origin.Scheduler, None).fold(e => fail(e.message), _.id)
    a.engine.execute(id)
    assertEquals(AgentRepo.get(ds, id).get.status, "paused")
    def tasks = h.ops.queueList("tasks", all = false).toOption.get.items.map(_.text).filter(_.contains(Goal))
    assert(tasks.exists(t => t.contains("scheduled acquisition-sweep") && t.contains("paused (provider)")), tasks)
    val mine = start(a, RunRequest("acquisition-sweep", Goal))
    assertEquals(mine.status, "paused")
    assertEquals(tasks.size, 1, "a person's run is watched; it adds no backlog item")

  // ── #992: dry runs ───────────────────────────────────────────────────────────────────────────────

  test("currency-check dry run: assess without applying; the edge proposal, the acquisition item and the log are only recorded"):
    val (a, h, up) = handlers(ScriptedClient(Model)); seed(h)
    up.citing = Map(Ck -> citing)
    val before = CorpusSnapshot.of(ds)
    val run = start(a, RunRequest("currency-check", Goal, dryRun = Some(true)))
    assertEquals(run.status, "done", run.error)
    assertEquals(CorpusSnapshot.of(ds), before)
    val steps = AgentRepo.steps(ds, run.id, 100000)
    def would(n: String) = steps.find(_.name == n).toList.flatMap(_.wouldWrite.map(_.what))
    assertEquals(would("assess"), List(s"assess [[$Ck]] applied: verdict current"))
    val followed = would("followups")
    assertEquals(followed.size, 2, followed)
    assert(followed.head.startsWith("edge proposal"), followed)
    assertEquals(followed(1), s"acquire item for 10.5555/zzagent.out, linked to goal $Goal")
    assertEquals(would("close"), List("log line"))
    assert(run.progress.exists(_.contains("1 edge(s) proposed, 1 paper(s) queued")), run.progress)

  test("acquisition-sweep dry run: the ranking runs; the picks it would queue are recorded, none is queued"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"}]}"""))
    val (a, h, up) = handlers(c); seed(h)
    assert(a.addGoalLink(Goal, LinkAdd(LinkKind.Query, "zzagent tin trade")).isRight)
    up.found = Map("zzagent tin trade" -> List(work("10.5555/zzagent.a", "A"), work("10.5555/zzagent.b", "B")))
    val before = CorpusSnapshot.of(ds)
    val run = start(a, RunRequest("acquisition-sweep", Goal, dryRun = Some(true)))
    assertEquals(run.status, "done", run.error)
    assertEquals(CorpusSnapshot.of(ds), before)
    val queue = AgentRepo.steps(ds, run.id, 100000).find(_.name == "queue").get.wouldWrite
    assertEquals(queue.map(_.what), List(s"acquire item for 10.5555/zzagent.a, linked to goal $Goal"))
    assert(queue.head.payload.contains("core study"), queue.head.payload)
