package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.AgentsControllers

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Autopilot.{AutopilotSet, FocusSet}
import com.histmersion.api.automation.Autopilot
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.goals.{FocusRepo, GoalLinkRepo}
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.util.database.Migrate
import com.histmersion.domain.research.workflows.ReviewLedgerFixtures
import com.histmersion.server.{Deps, ServerWiring}
import io.circe.syntax.*

import java.time.Instant
import com.histmersion.db.*
import com.histmersion.api.automation.{
  AgentScheduler,
  AgentSchedulerMarkdown,
  ApprovalDigest,
  Queue,
  Stalls,
  StallsMarkdown
}
import com.histmersion.api.library.PdfInfo
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

/** The central scheduler against a `*_test` database (docs/research-goal-advancement.md "As built"): 20 goals under the
  * autopilot — `goal-first` with 3 runs at once gives every place to the best goal (a pin, then a person's priority,
  * then the oldest), the queue says why each item waits and behind what; switched live to `type-balanced` with more
  * room the starts spread over the job types; the focus holds nothing any more (its pins and priorities are the goals'
  * priorities); the ingest backlog drains for every goal; goal plans are decided by policy whatever the focus. Models
  * are scripted and never called (the runs are dispatched, not executed); the acquisition upstream is a fixture.
  * Skipped without HISTMERSION_TEST_DB_URL.
  */
class SchedulerDbSuite extends com.histmersion.db.DbSuite with RunTranscripts:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val goals = (1 to 20).toList.map(i => f"zzfocus-g$i%02d")
  private def g(i: Int) = goals(i - 1)
  private val Art = "zzfocus_art"

  /** 3 runs at once, goal-first, the crons off (nothing fires by itself), the backlog drained whole. */
  private lazy val cfg =
    val b = AppConfig.load()
    val m = Model.wire
    b.copy(
      db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(
        enabled = true,
        prices = "scripted=1/2",
        maxConcurrentRuns = 3,
        roles = b.agents.roles
          .copy(extractor = m, reviewer = m, coldReader = m, skeptic = m, fixer = m, planner = m, writer = m),
        scheduler = b.agents.scheduler.copy(enabled = false, strategy = "goal-first", minShare = 0),
        autopilot = b.agents.autopilot.copy(backupHours = 0, lintHours = 0, ingestPerDay = 100, maxActiveGoals = 3)
      ),
      libraryAgents = b.libraryAgents.copy(
        seed = b.libraryAgents.seed.copy(minSources = 2),
        managers = b.libraryAgents.managers.copy(watch = b.libraryAgents.managers.watch.copy(drainPerTick = 50))
      )
    )

  private lazy val ds = testPool(cfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def handlers(
      c: AppConfig = cfg,
      digestClock: java.time.Clock = TestRuns.noonClock()
  ): (AgentsControllers, ServerWiring, StubUpstream) =
    val deps = Deps(c, ds)
    val h = ServerWiring(deps)
    val a = AgentsControllers(ServerEngineSite(deps, h, Some(digestClock)), (_, _) => Right(ScriptedClient(Model)))
    val up = StubUpstream()
    a.attach(up)
    (a, h, up)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      // the focus looks at EVERY goal, and the dispatch counts EVERY queued run against max-concurrent-runs:
      // what other suites left (their goals, their runs, open goal plans) must not take the 3 slots or the room
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent'".update.run()
      sql"DELETE FROM approvals WHERE subject LIKE 'zzfocus%' OR kind = 'goal-plan'".update.run()
      sql"DELETE FROM goals".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzfocus%'".update.run()
      sql"DELETE FROM review_ledgers WHERE slug LIKE 'zzfocus%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzfocus%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzfocus%' OR text LIKE 'autopilot%' OR text LIKE 'approval%'".update
        .run()
      sql"""UPDATE agent_autopilot SET enabled = false, paused = '{}', changed_by = '', last_ensure_at = NULL,
                                       max_active_goals = NULL, scheduler_strategy = NULL, last_digest_at = NULL,
                                       drain_since = NULL, drain_by = NULL, drained_at = NULL""".update.run()
      sql"DELETE FROM goal_focus".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def doi(slug: String) = s"10.5555/$slug"
  private def ck(d: String) = "zzfocus" + d.replaceAll("[^a-z0-9]", "")

  /** 20 goals with a query each (no plan needed): g05 has an article to review, g10 enough sources. */
  private def seed(a: AgentsControllers, h: ServerWiring, up: StubUpstream): Unit =
    goals.foreach { s =>
      assert(h.goals.put(s, Goal.Put(s"Zz $s")).isRight)
      assert(a.addGoalLink(s, LinkAdd(LinkKind.Query, s"zzfocus query $s")).isRight)
    }
    List("zzfocus_s1", "zzfocus_s2").foreach { c =>
      assert(
        h.library.sources
          .put(c, Source.Put(title = s"Zz $c", year = Some(2020), topics = List("zzfocus_t"), body = Some("Tin.")))
          .isRight
      )
      assert(transact(ds)(GoalLinkRepo.link(g(10), LinkKind.Source, c, None)))
    }
    assert(
      h.research.articles
        .put(
          Art,
          Article.Put(
            "Zz art",
            "concept",
            "Tin came by sea [[zzfocus_s1]].",
            filter = Some(Article.Filter(topics = List("zzfocus_t")))
          )
        )
        .isRight
    )
    assert(transact(ds)(GoalLinkRepo.link(g(5), LinkKind.Article, Art, None)))
    up.onIngest = d =>
      assert(h.library.sources.put(ck(d), Source.Put(title = s"Ingested $d", year = Some(2023), doi = Some(d))).isRight)
      Right(
        Ingest.Report(
          ck(d),
          Some(d),
          Some(s"Ingested $d"),
          created = true,
          Some(PdfInfo("unpaywall", Some(s"https://example.invalid/$d.pdf"), 2048, None)),
          0,
          None,
          Nil
        )
      )

  private def on(a: AgentsControllers): Unit =
    a.managers.ensureDefaults()
    // the review manager's own selection narrowed to these goals' article (other suites' articles stay out)
    a.managing
      .update(ReviewId, ManagerUpdate(Some(ManagerConfig(includeGoal = Some(g(5))))))
      .fold(e => fail(e.message), identity)
    a.managing.setAutopilot(AutopilotSet(on = true, Some("test"))).fold(e => fail(e.message), identity)

  private def item(mid: String, kind: String, target: String): ManagerItem =
    connect(ds)(ManagerRepo.itemFor(mid, kind, target)).getOrElse(fail(s"no $kind item for $target in $mid"))

  /** The runs the managers dispatched on these goals (and the article), by pipeline. */
  private def runs(): Vector[RunInfo] =
    connect(ds)(AgentRepo.list(None, 1000)).filter(r => r.managerId.isDefined && r.target.startsWith("zzfocus"))

  final private class Clock(var now: Instant = Instant.now()): // a var: the test's own moving time
    def tick(a: AgentsControllers, minutes: Int = 6): List[String] =
      now = now.plusSeconds(minutes.toLong * 60L)
      a.managers.tick(now)

  test(
    "goal-first, 3 runs at once: the pinned goal, then a person's priority, then the oldest; the queue says why; nothing is held"
  ):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    a.focus.set(FocusSet("pin", Some(g(20)), by = Some("test"))).fold(e => fail(e.message), identity)
    a.focus.set(FocusSet("priority", Some(g(15)), Some(5))).fold(e => fail(e.message), identity)
    goals.foreach(s => a.oaBacklog.add(watchId(s), s, OaWork(doi(s), s"Work of $s")))
    val clock = Clock()
    clock.tick(a)

    // every place to the pinned goal: its proposal, its sweep and its currency check (finish-first inside it)
    assertEquals(
      runs().map(r => (r.pipeline, r.target)).toSet,
      Set(("propose-articles", g(20)), ("acquisition-sweep", g(20)), ("currency-check", g(20)))
    )
    // the focus holds nothing: the others are pending, in the scheduler's queue
    assertEquals(connect(ds)(ManagerRepo.focusHeldCount()), 0)
    assertEquals(item(seedId(g(2)), ProposeArticles, g(2)).status, "pending")
    val st = a.scheduler.status(Some(500))
    note("scheduler status", AgentSchedulerMarkdown.status(st))
    assertEquals((st.strategy, st.strategyFrom, st.dry), ("goal-first", "config", false))
    assertEquals((st.maxRuns, st.started.size), (3, 3))
    assert(
      st.started.head.why.contains("gathering share") || st.started.head.why.contains("goal-first"),
      st.started.head.why
    )
    val first = st.waiting.head
    assertEquals((first.goal, first.position, first.code), (Some(g(15)), 1, "capacity"))
    assert(first.why.contains("goal-first") && first.why.contains("no place free"), first.why)
    assert(first.behind.nonEmpty, first)
    // then the oldest goals, by age and slug (they were made in the same second)
    assertEquals(st.waiting.map(_.goal).distinct.take(4), List(Some(g(15)), Some(g(1)), Some(g(2)), Some(g(3))))
    // why, for one item: by id and by target
    val w =
      a.scheduler.why(item(seedId(g(15)), ProposeArticles, g(15)).id.toString).fold(e => fail(e.message), identity)
    assertEquals((w.code, w.position), ("capacity", 1))
    val running = a.scheduler.why(g(20)).fold(e => fail(e.message), identity)
    note("why waiting", AgentSchedulerMarkdown.why(w))
    note("why running", AgentSchedulerMarkdown.why(running))
    note("why unknown", a.scheduler.why("zzfocus-nope").fold(_.message, AgentSchedulerMarkdown.why))
    assertEquals(running.code, AgentScheduler.Code.Running)
    assert(a.scheduler.why("zzfocus-nope").isLeft)
    // the status as Markdown names the strategy and the queue
    val md = AgentSchedulerMarkdown.status(st)
    assert(md.startsWith("# Scheduler: goal-first") && md.contains(s"1. #${first.item}"), md.take(600))
    // the ingest backlog drains for every goal (no AI allowance)
    assertEquals(goals.count(s => item(watchId(s), OaIngest, doi(s)).status == "done"), 20)
    // the manager whose items wait says why in its status line
    assert(
      connect(ds)(ManagerRepo.get(seedId(g(2)))).flatMap(_.statusNote).exists(_.contains("the scheduler: capacity")),
      connect(ds)(ManagerRepo.get(seedId(g(2)))).flatMap(_.statusNote)
    )

    // switched live to type-balanced, with three more places: the starts spread over the job types
    val sw =
      a.scheduler.set(AgentScheduler.SchedulerSet("type-balanced", Some("test"))).fold(e => fail(e.message), identity)
    assertEquals((sw.strategy, sw.strategyFrom), ("type-balanced", "person"))
    assert(QueueRepoLog.has(ds, "agent scheduler: strategy type-balanced"), "a worklog line")
    val (b, _, _) = handlers(cfg.copy(agents = cfg.agents.copy(maxConcurrentRuns = 6)))
    clock.tick(b)
    val next = runs().map(r => (r.pipeline, r.target)).toSet -- Set(
      ("propose-articles", g(20)),
      ("acquisition-sweep", g(20)),
      ("currency-check", g(20))
    )
    assertEquals(next, Set(("review-article", Art), ("propose-articles", g(15)), ("acquisition-sweep", g(15))))
    assertEquals(b.scheduler.status().strategy, "type-balanced")
    note("scheduler status type-balanced", AgentSchedulerMarkdown.status(b.scheduler.status(Some(500))))
    // back to the config's
    assertEquals(b.scheduler.set(AgentScheduler.SchedulerSet("default")).map(_.strategy), Right("goal-first"))
    assert(b.scheduler.set(AgentScheduler.SchedulerSet("fastest")).isLeft)

  test("with agents idle the status is a dry pass: what one would start, nothing started"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    a.autopilot.ensureNow(Instant.now())
    val st = a.scheduler.status()
    note("dry pass", AgentSchedulerMarkdown.status(st))
    assert(st.dry, st)
    assertEquals(st.started.size, 3)
    assertEquals(runs(), Vector.empty, "a dry pass starts nothing")

  test("goal-plan manual: the plan's run waits for a person and holds no place; the next goal's work takes it"):
    val one = cfg.copy(agents = cfg.agents.copy(maxConcurrentRuns = 1))
    val (a, h, up) = handlers(one); seed(a, h, up)
    val planned = "zzfocus-plan"
    assert(h.goals.put(planned, Goal.Put("Zz a goal with no plan")).isRight)
    on(a)
    a.focus.set(FocusSet("pin", Some(planned))).fold(e => fail(e.message), identity)
    val clock = Clock()
    clock.tick(a)
    val planItem = item(seedId(planned), PlanGoalItem, planned)
    assertEquals(planItem.status, "active", "the pinned goal's plan takes the one place")
    assertEquals(runs().size, 1)
    // its run now waits for a person to decide the plan (goal-plan = manual): the place frees
    val run = planItem.runId.get
    connect(ds)(sql"UPDATE jobs SET status = 'waiting' WHERE id = $run".update.run())
    clock.tick(a)
    assertEquals(runs().count(r => r.status == "queued"), 1, "another goal's item started in the freed place")
    assertEquals(a.scheduler.why(planItem.id.toString).map(_.code), Right(AgentScheduler.Code.Person))
    // its proposal waits for the plan without holding anything; the status counts what waits for a person
    val prop =
      a.scheduler.why(item(seedId(planned), ProposeArticles, planned).id.toString).fold(e => fail(e.message), identity)
    assert(prop.code == "blocked" && prop.why.contains("plan comes first"), prop)
    assert(a.scheduler.status().forPerson >= 1)

  test("gathering goals keep their place: a goal still gathering sources starts beside the pinned goal under review"):
    val two = cfg.copy(agents = cfg.agents.copy(maxConcurrentRuns = 2))
    val (a, h, up) = handlers(two); seed(a, h, up)
    on(a)
    a.focus.set(FocusSet("pin", Some(g(5)))).fold(e => fail(e.message), identity)
    Clock().tick(a)
    val started = runs().map(r => (r.pipeline, r.target)).toSet
    assert(started.contains(("review-article", Art)), started)
    assertEquals(started.size, 2)
    val other = (started - (("review-article", Art))).head
    assert(other._2 != g(5) && other._2 != g(10), s"a gathering goal's item, not $other")
    assert(a.scheduler.status().started.exists(_.why.contains("gathering share")), a.scheduler.status().started)

  test("goal plans are decided by code whatever the focus, within the open sub-goals"):
    val auto = cfg.copy(agents = cfg.agents.copy(approvals = cfg.agents.approvals.copy(goalPlan = "auto")))
    val (a, h, up) = handlers(auto); seed(a, h, up)
    on(a)
    Clock().tick(a)
    def plan(goal: String, kids: Int) = TestRuns
      .approvals(ds)
      .openSubject(
        None,
        ApprovalKind.GoalPlan,
        goal,
        Plan(goal, "r", (1 to kids).toList.map(i => PlannedGoal(s"$goal-k$i", s"Zz k$i")), List("q")).asJson.noSpaces
      )
    val now = Instant.now()
    def judged(p: ApprovalInfo) = a.autoApprovals.judge(TestRuns.approvals(ds).approval(p.id).get, None, None, now)
    assert(
      judged(plan(g(7), 0)).isInstanceOf[AutopilotPolicy.Verdict.Approve],
      "not active in any focus: decided all the same"
    )
    assert(judged(plan(g(1), 3)).isInstanceOf[AutopilotPolicy.Verdict.Approve])
    judged(plan(g(1), 6)) match
      case AutopilotPolicy.Verdict.Leave(w) => assert(w.contains("limit on open sub-goals"), w)
      case v                                => fail(v.toString)

  test(
    "goal plans wait while too many planned goals are unfinished: no plan-goal run, none decided by code, a person still may"
  ):
    val auto = cfg.copy(agents =
      cfg.agents.copy(
        approvals = cfg.agents.approvals.copy(goalPlan = "auto"),
        autopilot = cfg.agents.autopilot.copy(maxUnfinishedGoals = 2)
      )
    )
    val (a, h, up) = handlers(auto); seed(a, h, up)
    val unplanned = "zzfocus-plan"
    assert(h.goals.put(unplanned, Goal.Put("Zz a goal with no plan")).isRight)
    // three goals with an approved plan, none reviewed: one more than the limit
    List(g(1), g(2), g(3)).foreach(s =>
      connect(ds)(sql"UPDATE goals SET plan = '{}'::jsonb WHERE slug = $s".update.run())
    )
    on(a)
    a.focus.set(FocusSet("pin", Some(unplanned))).fold(e => fail(e.message), identity)
    Clock().tick(a)
    val planItem = item(seedId(unplanned), PlanGoalItem, unplanned)
    assertEquals(planItem.status, "pending", "no plan-goal run starts")
    assert(!runs().exists(_.pipeline == Pipeline.PlanGoal.wire), runs())
    val why = a.scheduler.why(planItem.id.toString).fold(e => fail(e.message), identity)
    assert(
      why.code == "blocked" && why.why.contains("drop to 2") && why.why.contains("3 goals with an approved plan"),
      why
    )
    // an open goal plan is neither decided by code nor reported to its manager; the digest and the status say why
    val p =
      TestRuns
        .approvals(ds)
        .openSubject(None, ApprovalKind.GoalPlan, g(4), Plan(g(4), "r", Nil, List("q")).asJson.noSpaces)
    val _ = a.autoApprovals.sweep(Instant.now())
    assertEquals(TestRuns.approvals(ds).approval(p.id).map(_.state), Some("open"))
    a.autoApprovals.judge(TestRuns.approvals(ds).approval(p.id).get, None, None, Instant.now()) match
      case AutopilotPolicy.Verdict.Leave(w) => assert(w.contains("limit on unfinished goals"), w)
      case v                                => fail(v.toString)
    val d = a.digest.digest()
    assert(d.goalPlansWait.exists(_.contains("drop to 2")), d)
    assertEquals(d.kinds.find(_.kind == "goal-plan").map(_.decidedBy), Some(ApprovalDigest.Waits))
    assert(a.autopilot.status().goalPlansWait.exists(_.contains("3 goals")))
    // a person decides it all the same
    assertEquals(a.decide(p.id, "approve", DecisionRequest(Some("by hand"))).map(_.state), Right("approved"))
    // one planned goal reviewed: at the limit, goal plans are offered again
    assertEquals(a.focus.unfinishedPlanned(), 3)
    connect(ds)(sql"UPDATE goals SET status = 'reviewed' WHERE slug = ${g(1)}".update.run())
    assertEquals(a.focus.goalPlansWait(), None)
    assertEquals(a.digest.digest().goalPlansWait, None)

  test("the digest: open approvals by kind; one worklog line a UTC day after digest-hour; a kind decided at once"):
    val morning = Instant.parse("2031-03-03T06:59:00Z")
    val (a, h, up) = handlers(digestClock = java.time.Clock.fixed(morning, java.time.ZoneOffset.UTC)); seed(a, h, up)
    def plan(goal: String) =
      TestRuns
        .approvals(ds)
        .openSubject(None, ApprovalKind.GoalPlan, goal, Plan(goal, "r", Nil, List("q")).asJson.noSpaces)
    val ps = List(g(1), g(2), g(3)).map(plan)
    val d = a.digest.digest()
    val plans = d.kinds.find(_.kind == "goal-plan").getOrElse(fail(d.toString))
    assertEquals((plans.count, plans.decidedBy), (3, "person"))
    assertEquals(plans.ids, ps.map(_.id).sorted)
    // once a day, after the hour (7 UTC by default)
    assertEquals(a.digest.tick(morning), Nil, "before the hour")
    assertEquals(a.digest.tick(morning.plusSeconds(120)), List("approval digest written"))
    assertEquals(a.digest.tick(morning.plusSeconds(3600)), Nil, "once a day")
    assertEquals(
      a.digest.tick(morning.plusSeconds(86400 + 120)),
      List("approval digest written"),
      "the next day, after the hour"
    )
    assert(QueueRepoLog.has(ds, "approval digest: "), "the worklog line")
    // a whole kind at once: a dry run lists, then the decision
    assert(a.digest.decideAll(ApprovalDigest.BulkDecide("approve")).isLeft, "never every open approval")
    assert(a.digest.decideAll(ApprovalDigest.BulkDecide("approve", kind = Some("nope"))).isLeft)
    val dry = a.digest
      .decideAll(ApprovalDigest.BulkDecide("reject", kind = Some("goal-plan"), dryRun = true))
      .fold(e => fail(e.message), identity)
    assertEquals((dry.matched, dry.decided.size, dry.dryRun), (3, 3, true))
    assert(ps.forall(p => TestRuns.approvals(ds).approval(p.id).exists(_.state == "open")), "a dry run decides nothing")
    val done = a.digest
      .decideAll(ApprovalDigest.BulkDecide("reject", kind = Some("goal-plan"), subject = Some(g(2)), by = Some("test")))
      .fold(e => fail(e.message), identity)
    assertEquals((done.matched, done.decided, done.failed), (1, List(ps(1).id), Nil))
    assertEquals(
      TestRuns.approvals(ds).approval(ps(1).id).map(x => (x.state, x.note.exists(_.contains("in bulk by test")))),
      Some(("rejected", true))
    )
    assert(QueueRepoLog.has(ds, "approvals rejected in bulk by test"), "a worklog line")

  test("the daily caps scale with the backlog, up to the ceiling"):
    val scaled = cfg.copy(agents =
      cfg.agents.copy(approvals =
        cfg.agents.approvals.copy(
          goalPlan = "auto",
          auto = cfg.agents.approvals.auto.copy(goalPlansPerDay = 1, goalPlansBacklogShare = 0.5, goalPlansCeiling = 3)
        )
      )
    )
    val (a, h, up) = handlers(scaled); seed(a, h, up)
    (1 to 6).foreach(i =>
      TestRuns
        .approvals(ds)
        .openSubject(None, ApprovalKind.GoalPlan, g(i), Plan(g(i), "r", Nil, List("q")).asJson.noSpaces)
    )
    assertEquals(a.autoApprovals.goalPlanCap, 3, "max(1, ceil(6 x 0.5)) = 3, at most 3")
    assert(
      a.managing.autopilot.approvals.exists(r => r.kind == "goal-plan" && r.cap.contains("3 today for a backlog of 6")),
      a.managing.autopilot.approvals
    )
    val (b, _, _) = handlers()
    assertEquals(b.autoApprovals.goalPlanCap, cfg.agents.approvals.auto.goalPlansPerDay, "share 0: the fixed cap")

  test("drain: nothing new starts, the runs in flight finish, drained once none runs; undrain and on lift it"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    val drained = a.drain.set(Autopilot.DrainSet(on = true, Some("test")))
    note("drain on, nothing running", a.autopilotPage(drained))
    assert(drained.drain.exists(_.drained), drained.drain)
    val clock = Clock()
    clock.tick(a)
    assertEquals(runs(), Vector.empty, "nothing starts while draining")
    assert(a.scheduler.status().waiting.forall(_.code == "drain"), a.scheduler.status().waiting.map(_.code).distinct)
    note("drained: scheduler", AgentSchedulerMarkdown.status(a.scheduler.status(Some(500))))
    note("drained: autopilot", a.autopilotPage(a.managing.autopilot))
    assert(a.autopilotPage(a.managing.autopilot).contains("DRAINED since"))
    assert(QueueRepoLog.has(ds, "autopilot: drained"), "a worklog line")
    // lifted: work starts; drained again while runs are in flight: draining, not drained, nothing new
    note("drain off", a.autopilotPage(a.drain.set(Autopilot.DrainSet(on = false, Some("test")))))
    clock.tick(a)
    assertEquals(runs().size, 3)
    a.drain.set(Autopilot.DrainSet(on = true, Some("test")))
    clock.tick(a)
    val d = a.managing.autopilot.drain.getOrElse(fail("draining"))
    assertEquals((d.drained, d.inFlight), (false, 3))
    note("draining, 3 in flight", a.autopilotPage(a.managing.autopilot))
    assert(a.autopilotPage(a.managing.autopilot).contains("DRAINING since"))
    // the runs in flight finish (here: cancelled): drained
    runs().foreach(r => a.engine.cancel(r.id))
    clock.tick(a)
    assert(a.managing.autopilot.drain.exists(_.drained))
    assertEquals(runs().count(r => r.status == "queued"), 0, "still nothing new")
    // turning the autopilot on lifts it
    val st = a.managing.setAutopilot(AutopilotSet(on = true, Some("test"))).fold(e => fail(e.message), identity)
    note("on lifts the drain", a.autopilotPage(st))
    assert(st.changes.exists(_.contains("drain lifted")) && st.drain.isEmpty, st.changes)

  test("stalls: every unfinished goal with no progress, the reason and the action"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    TestRuns
      .approvals(ds)
      .openSubject(None, ApprovalKind.GoalPlan, g(4), Plan(g(4), "r", Nil, List("q")).asJson.noSpaces)
    Clock().tick(a)
    val r = a.scheduler.stalls(Some(0))
    note("stalls", StallsMarkdown.report(r))
    assertEquals(r.goals, 20)
    assertEquals(r.stalled.size, 20, "days 0: every goal counts")
    assertEquals(r.stalled.find(_.slug == g(4)).map(_.code), Some(Stalls.Code.Person))
    assert(r.stalled.exists(_.code == Stalls.Code.Queue), r.byReason)
    assertEquals(a.scheduler.stalls(Some(3)).stalled, Nil, "they all started today")
    a.drain.set(Autopilot.DrainSet(on = true))
    note("stalls while draining", StallsMarkdown.report(a.scheduler.stalls(Some(0))))
    assert(a.scheduler.stalls(Some(0)).stalled.forall(_.code == Stalls.Code.Drain))

  test(
    "seed before the plan: sourceless goals sweep their candidates (no model) and ingest legal open access within the caps"
  ):
    val (a, h, up) = handlers(); seed(a, h, up)
    def cand(id: Long, goal: String, oa: Boolean) =
      Source.Candidate(id, goal, s"10.5555/zzseed.$id", s"Zz seed $id", isOa = oa)
    up.sweepFinds = Map(
      g(1) -> List(cand(1, g(1), oa = true), cand(2, g(1), oa = false)),
      g(2) -> List(cand(3, g(2), oa = true)),
      g(3) -> Nil,
      g(4) -> Nil
    )
    on(a)
    val clock = Clock()
    clock.tick(a)
    import scala.jdk.CollectionConverters.*
    // the two first sourceless goals (g01, g02: oldest, then slug) were seeded; g10 has sources and is not
    def seeded =
      goals.filter(x =>
        connect(ds)(ManagerRepo.events(watchId(x), None, 100)).exists(_.text.startsWith("seed before the plan"))
      )
    assertEquals(seeded, List(g(1), g(2)))
    // their legal open-access finds went through the ingest backlog and were ingested in the same tick
    assertEquals(
      up.calls.asScala.toList.filter(_.startsWith("ingest 10.5555/zzseed")),
      List("ingest 10.5555/zzseed.1", "ingest 10.5555/zzseed.3")
    )
    assert(
      connect(ds)(GoalLinkRepo.links(g(1))).exists(l => l.kind == LinkKind.Source && l.ref == ck("10.5555/zzseed.1")),
      connect(ds)(GoalLinkRepo.links(g(1)))
    )
    assert(QueueRepoLog.has(ds, "seed before the plan: "), "a worklog line")
    // the next tick seeds the next two; a goal seeded today is not seeded again
    clock.tick(a)
    assertEquals(seeded, List(g(1), g(2), g(3), g(4)))
    assertEquals(
      connect(ds)(ManagerRepo.events(watchId(g(1)), None, 100)).count(_.text.startsWith("seed before the plan")),
      1
    )
    // draining: no seeding
    a.drain.set(Autopilot.DrainSet(on = true))
    clock.tick(a)
    assertEquals(seeded.size, 4)

  test("#3763: no proposal while a goal's articles are in progress; once they are done, the next one is proposed"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    val clock = Clock()
    clock.tick(a)
    // g05's article has not passed review: its proposal is kept back
    val prop = item(seedId(g(5)), ProposeArticles, g(5))
    val why = a.scheduler.why(prop.id.toString).fold(e => fail(e.message), identity)
    assert(why.code == "blocked" && why.why.contains("articles in progress come first (1 to pass review)"), why)
    assertEquals(a.autopilot.nextArticles(clock.now), Nil)
    // the article passes its review (a clean ledger, no review item open), and the proposal had settled days ago
    ReviewLedgerFixtures.put(ds, ReviewLedger.Ledger(Art))
    connect(ds)(ManagerRepo.itemFor(ReviewId, ArticleReview, Art))
      .foreach(i =>
        transact(ds)(
          ManagerRepo.settle(
            i.id,
            ItemStatus.Done,
            "converged",
            List(ItemStatus.Pending, ItemStatus.Active, ItemStatus.Held)
          )
        )
      )
    transact(ds)(ManagerRepo.settle(prop.id, ItemStatus.Done, "approval #1 approved", List(ItemStatus.Pending)))
    connect(ds)(sql"UPDATE manager_items SET updated_at = now() - interval '2 days' WHERE id = ${prop.id}".update.run())
    assertEquals(a.autopilot.nextArticles(clock.now), List(s"goal ${g(5)}: next article proposed"))
    assertEquals(item(seedId(g(5)), ProposeArticles, g(5)).status, "pending")
    assert(
      connect(ds)(ManagerRepo.events(seedId(g(5)), None, 50)).exists(_.text.contains("its next article is proposed"))
    )
    clock.tick(a)
    assertNotEquals(a.scheduler.why(prop.id.toString).map(_.code), Right("blocked"), "no longer kept back")
    // open again: not proposed twice; an approved article to compile keeps the next one back
    assertEquals(a.autopilot.nextArticles(clock.now), Nil)
    transact(ds)(ManagerRepo.upsert(seedId(g(5)), ArticleCompile, "zzfocus_next", ItemOrigin.Chain, 0, reopen = false))
    clock.tick(a)
    val back = a.scheduler.why(prop.id.toString).fold(e => fail(e.message), identity)
    assert(back.code == "blocked" && back.why.contains("1 to compile"), back)

/** The worklog, read for an assertion. */
private object QueueRepoLog:
  def has(ds: javax.sql.DataSource, prefix: String): Boolean =
    com.histmersion.db.QueueRows.list(ds, Queue.Kind.Log, all = true).exists(_.text.startsWith(prefix))
