package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.GoalPlans.*
import com.histmersion.db.{AgentRepo, Db, GoalLinkRepo, Migrate}
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.*

import java.time.Instant

/** The scheduled pipelines and the scheduler, against a `*_test` database: a currency check (assess
  * and followups over a goal's sources; a disagreeing paper in the corpus becomes ONE edge proposal,
  * one outside it ONE acquisition item, however often it runs), an acquisition sweep (never queues
  * what the corpus or the queue has; the ranking model may only pick listed candidates), and the
  * scheduler's tick (start, skip while the previous run has not settled, skip at the cost cap, defer
  * at max-concurrent-runs, nothing while agents are off; a scheduled run that pauses leaves a backlog
  * item). Skipped without HISTMERSION_TEST_DB_URL. */
class SchedulerRunSuite extends munit.FunSuite with com.histmersion.db.Bounded:
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

  private lazy val ds = Db.dataSource(baseCfg.db)

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

  // ── the scheduler ────────────────────────────────────────────────────────────────────────────────

  private val Now = Instant.parse("2026-10-04T12:00:00Z")

  test("schedules: a cron is checked, too frequent is refused, the next firing is computed"):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(h)
    assert(a.setSchedule(Goal, "currency", ScheduleSet("not cron"), Now).left.exists(_.message.contains("five fields")))
    assert(a.setSchedule(Goal, "currency", ScheduleSet("*/5 * * * *"), Now).left.exists(_.message.contains("min-interval")))
    assert(a.setSchedule(Goal, "weekly", ScheduleSet("@daily"), Now).isLeft)
    val s = a.setSchedule(Goal, "currency", ScheduleSet("@daily", budgetUsd = Some(0.5)), Now).toOption.get
    assertEquals((s.kind, s.cron, s.enabled, s.budgetUsd, s.nextRunAt), (ScheduleKind.Currency, "@daily", true, Some(0.5),
      Some("2026-10-05T00:00:00Z")))
    val off = a.setSchedule(Goal, "currency", ScheduleSet("@daily", enabled = false), Now).toOption.get
    assertEquals(off.nextRunAt, None)
    assertEquals(a.goalView(Goal).toOption.get.schedules.map(_.kind), List(ScheduleKind.Currency))
    assertEquals(a.removeSchedule(Goal, "currency").map(_.removed), Right(true))

  test("tick: a due schedule starts its run (origin scheduler) and moves on; the next tick skips while that run is unsettled"):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(h)
    assert(a.setSchedule(Goal, "currency", ScheduleSet("@daily", budgetUsd = Some(0.25)), Now).isRight)
    assertEquals(a.scheduler.tick(Now), Nil, "not due yet")
    val due = Instant.parse("2026-10-05T00:00:30Z")
    val first = a.scheduler.tick(due)
    assertEquals(first.size, 1)
    assert(first.head._2.startsWith("started run"), first)
    val s = GoalLinkRepo.schedule(ds, Goal, ScheduleKind.Currency).get
    assertEquals(s.nextRunAt, Some("2026-10-06T00:00:00Z"))
    val run = AgentRepo.get(ds, s.lastRun.get).get
    assertEquals((run.pipeline, run.target, run.origin, run.goal, run.status, run.budgetUsd),
      ("currency-check", Goal, Some("scheduler"), Some(Goal), "queued", 0.25))
    // the run has not been executed: the next firing skips it
    val second = a.scheduler.tick(Instant.parse("2026-10-06T00:00:30Z"))
    assert(second.head._2.contains("previous run is still queued"), second)
    assertEquals(GoalLinkRepo.schedule(ds, Goal, ScheduleKind.Currency).get.nextRunAt, Some("2026-10-07T00:00:00Z"))
    val log = h.ops.queueList("log", all = true).toOption.get.items.map(_.text).filter(_.contains(Goal))
    assert(log.exists(_.contains("skipped: its previous run is still queued")), log)

  test("tick: the cost cap skips (checked before the start); a full pool defers; nothing runs with agents off"):
    val capped = baseCfg.copy(agents = baseCfg.agents.copy(budgets = baseCfg.agents.budgets.copy(dailyUsd = 0.0)))
    val (a, h, _) = handlers(ScriptedClient(Model), capped); seed(h)
    assert(a.setSchedule(Goal, "currency", ScheduleSet("@daily"), Now).isRight)
    val due = Instant.parse("2026-10-05T00:00:30Z")
    val skipped = a.scheduler.tick(due)
    assert(skipped.head._2.contains("daily-usd"), skipped)
    assertEquals(AgentRepo.list(ds, None, 50, Some(Goal)), Vector.empty, "no run was started past the cap")

    val one = baseCfg.copy(agents = baseCfg.agents.copy(maxConcurrentRuns = 1))
    val (b, _, _) = handlers(ScriptedClient(Model), one)
    val blocker = b.startRun(RunRequest("currency-check", Goal)).toOption.get // queued, never executed
    assert(b.setSchedule(Goal, "currency", ScheduleSet("@daily"), Now).isRight)
    val deferred = b.scheduler.tick(due)
    assert(deferred.head._2.startsWith("deferred"), deferred)
    assertEquals(GoalLinkRepo.schedule(ds, Goal, ScheduleKind.Currency).get.nextRunAt, Some("2026-10-05T00:00:00Z"),
      "a deferred schedule stays due")
    b.engine.execute(blocker.id)
    assert(b.scheduler.tick(due).head._2.startsWith("started"))

    val off = baseCfg.copy(agents = baseCfg.agents.copy(enabled = false))
    val (c, _, _) = handlers(ScriptedClient(Model), off)
    assertEquals(c.scheduler.tick(Instant.parse("2027-01-01T00:00:00Z")), Nil)
    val noSched = baseCfg.copy(agents = baseCfg.agents.copy(scheduler = baseCfg.agents.scheduler.copy(enabled = false)))
    val (d, _, _) = handlers(ScriptedClient(Model), noSched)
    assertEquals(d.scheduler.tick(Instant.parse("2027-01-01T00:00:00Z")), Nil)

  test("a scheduled run that pauses (its provider is down) leaves a backlog item; a person's does not"):
    val c = ScriptedClient(Model); c.fail = true
    val (a, h, up) = handlers(c); seed(h)
    assert(a.addGoalLink(Goal, LinkAdd(LinkKind.Query, "zzagent q")).isRight)
    up.found = Map("zzagent q" -> List(work("10.5555/zzagent.z", "Z")))
    assert(a.setSchedule(Goal, "acquisition", ScheduleSet("@monthly"), Now).isRight)
    val started = a.scheduler.tick(Instant.parse("2026-11-01T00:00:30Z"))
    val id = GoalLinkRepo.schedule(ds, Goal, ScheduleKind.Acquisition).get.lastRun.get
    assert(started.head._2.startsWith("started"), started)
    a.engine.execute(id)
    assertEquals(AgentRepo.get(ds, id).get.status, "paused")
    def tasks = h.ops.queueList("tasks", all = false).toOption.get.items.map(_.text).filter(_.contains(Goal))
    assert(tasks.exists(t => t.contains("scheduled acquisition-sweep") && t.contains("paused (provider)")), tasks)
    val mine = start(a, RunRequest("acquisition-sweep", Goal))
    assertEquals(mine.status, "paused")
    assertEquals(tasks.size, 1, "a person's run is watched; it adds no backlog item")
