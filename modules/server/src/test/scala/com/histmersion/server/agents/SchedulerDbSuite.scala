package com.histmersion
package server.agents
import com.histmersion.util.json.RawJson.*

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ScriptedClient
import com.histmersion.domain.automation.agents.TestRuns

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
import com.histmersion.domain.automation.Goal as DomainGoal
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.agents.ApprovalService
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.domain.research.Slug
import com.histmersion.config.DbConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentRolesConfig
import com.histmersion.config.AgentSchedulerConfig
import com.histmersion.config.AgentAutopilotConfig
import com.histmersion.config.AgentSeedConfig
import com.histmersion.config.WatchManagerConfig
import com.histmersion.api.library.Doi
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Filter
import com.histmersion.api.ByteCount
import com.histmersion.api.automation.Managers
import com.histmersion.config.AgentApprovalsConfig
import com.histmersion.domain.automation.Approval
import com.histmersion.config.AutoApprovalsConfig
import com.histmersion.domain.automation.worklists.QueueRows
import com.histmersion.api.Api

/** The central scheduler against a `*_test` database (docs/research-goal-advancement.md "As built"): 20 goals under the
  * autopilot — `goal-first` with 3 runs at once gives every place to the best goal (a pin, then a person's priority,
  * then the oldest), the queue says why each item waits and behind what; switched live to `type-balanced` with more
  * room the starts spread over the job types; the focus holds nothing any more (its pins and priorities are the goals'
  * priorities); the ingest backlog drains for every goal; goal plans are decided by policy whatever the focus. Models
  * are scripted and never called (the runs are dispatched, not executed); the acquisition upstream is a fixture.
  * Skipped without HISTMERSION_TEST_DB_URL.
  */
class SchedulerDbSuite extends DbSuite with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val Model = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("scripted"))
  private val goals = (1 to 20).toList.map(i => DomainGoal.Slug.applyUnsafe(f"zzfocus-g$i%02d"))
  private def g(i: Int) = goals(i - 1)
  private def ref(g: DomainGoal.Slug) = Goal.Ref(g.value)
  private def apiSlug(g: DomainGoal.Slug) = Goal.Slug(g.value)
  private def target(g: DomainGoal.Slug) = ManagerItem.Target(g.value)
  private def goalLink(g: DomainGoal.Slug) = GoalLink.Ref(g.value)
  private val Art = Slug("zzfocus_art")
  private val ArtLink = GoalLink.Ref("zzfocus_art")
  private val ArtRunTarget = RunInfo.Target("zzfocus_art")
  private val ArtItem = ManagerItem.Target("zzfocus_art")
  private val Review = ManagerInfo.Id(ReviewId)
  private val ReviewLight = ApiIds.manager(Review)
  private val ReviewKind = ManagerItem.Kind(ArticleReview)
  private val CompileKind = ManagerItem.Kind(ArticleCompile)
  private val ProposeKind = ManagerItem.Kind(ProposeArticles)
  private val PlanGoalKind = ManagerItem.Kind(PlanGoalItem)
  private val IngestKind = ManagerItem.Kind(OaIngest)

  /** 3 runs at once, goal-first, the crons off (nothing fires by itself), the backlog drained whole. */
  private lazy val cfg =
    val b = AppConfig.load()
    val m = Model.wire
    b.modify(_.db)
      .setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.agents)
      .setTo(
        b.agents
          .modify(_.enabled)
          .setTo(AgentsConfig.Enabled(true))
          .modify(_.prices)
          .setTo(AgentsConfig.Prices("scripted=1/2"))
          .modify(_.maxConcurrentRuns)
          .setTo(AgentsConfig.MaxConcurrentRuns(3))
          .modify(_.roles)
          .setTo(
            b.agents.roles
              .modify(_.extractor)
              .setTo(AgentRolesConfig.Extractor(m.value))
              .modify(_.reviewer)
              .setTo(AgentRolesConfig.Reviewer(m.value))
              .modify(_.coldReader)
              .setTo(AgentRolesConfig.ColdReader(m.value))
              .modify(_.skeptic)
              .setTo(AgentRolesConfig.Skeptic(m.value))
              .modify(_.fixer)
              .setTo(AgentRolesConfig.Fixer(m.value))
              .modify(_.planner)
              .setTo(AgentRolesConfig.Planner(m.value))
              .modify(_.writer)
              .setTo(AgentRolesConfig.Writer(m.value))
          )
          .modify(_.scheduler)
          .setTo(
            b.agents.scheduler
              .modify(_.enabled)
              .setTo(AgentSchedulerConfig.Enabled(false))
              .modify(_.strategy)
              .setTo(AgentSchedulerConfig.Strategy("goal-first"))
              .modify(_.minShare)
              .setTo(AgentSchedulerConfig.MinShare((0: Double)))
          )
          .modify(_.autopilot)
          .setTo(
            b.agents.autopilot
              .modify(_.backupHours)
              .setTo(AgentAutopilotConfig.BackupHours(0))
              .modify(_.lintHours)
              .setTo(AgentAutopilotConfig.LintHours(0))
              .modify(_.ingestPerDay)
              .setTo(AgentAutopilotConfig.IngestPerDay(100))
              .modify(_.maxActiveGoals)
              .setTo(AgentAutopilotConfig.MaxActiveGoals(3))
          )
      )
      .modify(_.libraryAgents)
      .setTo(
        b.libraryAgents
          .modify(_.seed)
          .setTo(b.libraryAgents.seed.modify(_.minSources).setTo(AgentSeedConfig.MinSources(2)))
          .modify(_.managers)
          .setTo(
            b.libraryAgents.managers
              .modify(_.watch)
              .setTo(b.libraryAgents.managers.watch.modify(_.drainPerTick).setTo(WatchManagerConfig.DrainPerTick(50)))
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

  private def doi(slug: DomainGoal.Slug) = Doi(s"10.5555/$slug")
  private def ck(d: String) = Citekey.applyUnsafe("zzfocus" + d.replaceAll("[^a-z0-9]", ""))

  /** 20 goals with a query each (no plan needed): g05 has an article to review, g10 enough sources. */
  private def seed(a: AgentsControllers, h: ServerWiring, up: StubUpstream): Unit =
    goals.foreach { s =>
      assert(h.goals.put(s, Goal.Put(Goal.Title(s"Zz $s"))).isRight)
      assert(a.addGoalLink(s, LinkAdd(LinkKind.Query, LinkAdd.Ref(s"zzfocus query $s"))).isRight)
    }
    List("zzfocus_s1", "zzfocus_s2").map(Citekey.applyUnsafe(_)).foreach { c =>
      assert(
        h.library.sources
          .put(
            c,
            Source.Put(
              title = com.histmersion.api.library.Title(s"Zz $c"),
              year = (Some(2020)).map(Year.apply),
              topics = List(Source.Topics("zzfocus_t")),
              body = Some(Source.OcrMarkdown("Tin."))
            )
          )
          .isRight
      )
      assert(transact(ds)(GoalLinkRepo.link(g(10), LinkKind.Source, GoalLink.Ref(c.value), None)))
    }
    assert(
      h.research.articles
        .put(
          Art,
          Article.Put(
            Article.Title("Zz art"),
            Article.Kind.Wire("concept"),
            Article.Body("Tin came by sea [[zzfocus_s1]]."),
            filter = Some(Article.Filter(topics = List(Filter.Topics("zzfocus_t"))))
          )
        )
        .isRight
    )
    assert(transact(ds)(GoalLinkRepo.link(g(5), LinkKind.Article, ArtLink, None)))
    up.onIngest = d =>
      assert(
        h.library.sources
          .put(
            ck(d),
            Source.Put(
              title = com.histmersion.api.library.Title(s"Ingested $d"),
              year = (Some(2023)).map(Year.apply),
              doi = (Some(d)).map(Doi.apply)
            )
          )
          .isRight
      )
      Right(
        Ingest.Report(
          com.histmersion.api.library.Citekey(ck(d).value),
          Some(Doi(d)),
          Some(com.histmersion.api.library.Title(s"Ingested $d")),
          created = Ingest.Report.Created(true),
          Some(
            PdfInfo(
              PdfInfo.Source("unpaywall"),
              Some(PdfInfo.Url(s"https://example.invalid/$d.pdf")),
              ByteCount(2048),
              None
            )
          ),
          Ingest.Report.EdgesAdded(0),
          None,
          Nil
        )
      )

  /** An open goal plan of `goal` with `kids` planned sub-goals. */
  private def plan(goal: DomainGoal.Slug, kids: Int = 0) =
    TestRuns
      .approvals(ds)
      .openSubject(
        None,
        ApprovalKind.GoalPlan,
        ApprovalQuery.Subject(goal.value),
        ApprovalService.Payload(
          Plan(
            apiSlug(goal),
            Plan.Rationale("r"),
            (1 to kids).toList.map(i => PlannedGoal(Goal.Slug(s"${goal.value}-k$i"), Goal.Title(s"Zz k$i"))),
            List(Plan.Queries("q"))
          ).asJson.noSpaces
        )
      )

  private def on(a: AgentsControllers): Unit =
    a.managers.ensureDefaults()
    // the review manager's own selection narrowed to these goals' article (other suites' articles stay out)
    a.managing
      .update(
        ReviewLight,
        ManagerUpdate(Some(ManagerConfig(includeGoal = Some(ManagerConfig.IncludeGoal(g(5).value)))))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.managing
      .setAutopilot(AutopilotSet(on = AutopilotSet.On(true), Some(AutopilotSet.By("test"))))
      .left
      .map(Api.Error(_))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)

  private def item(mid: ManagerInfo.Id, kind: ManagerItem.Kind, target: ManagerItem.Target): ManagerItem =
    connect(ds)(ManagerRepo.itemFor(mid, kind, target)).getOrElse(fail(s"no $kind item for $target in $mid"))

  /** The runs the managers dispatched on these goals (and the article), by pipeline. */
  private def runs(): Vector[RunInfo] =
    connect(ds)(AgentRepo.list(None, PageSize(1000))).filter(r =>
      r.managerId.isDefined && r.target.value.startsWith("zzfocus")
    )

  final private class Clock(var now: Instant = Instant.now()): // a var: the test's own moving time
    def tick(a: AgentsControllers, minutes: Int = 6): List[ManagerContext.TickLine] =
      now = now.plusSeconds(minutes.toLong * 60L)
      a.managers.tick(now)

  test(
    "goal-first, 3 runs at once: the pinned goal, then a person's priority, then the oldest; the queue says why; nothing is held"
  ):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    a.focus
      .set(FocusSet(FocusSet.Action("pin"), Some(apiSlug(g(20))), by = Some(FocusSet.By("test"))))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.focus
      .set(FocusSet(FocusSet.Action("priority"), Some(apiSlug(g(15))), Some(FocusSet.Value(5))))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    goals.foreach(s =>
      a.oaBacklog.add(watchId(ref(s)), s, OaWork(doi(s), com.histmersion.api.library.Title(s"Work of $s")))
    )
    val clock = Clock()
    clock.tick(a)

    // every place to the pinned goal: its proposal, its sweep and its currency check (finish-first inside it)
    assertEquals(
      runs().map(r => (r.pipeline, r.target)).toSet,
      Set(("propose-articles", g(20)), ("acquisition-sweep", g(20)), ("currency-check", g(20)))
    )
    // the focus holds nothing: the others are pending, in the scheduler's queue
    assertEquals(connect(ds)(ManagerRepo.focusHeldCount()), 0)
    assertEquals(item(seedId(ref(g(2))), ProposeKind, target(g(2))).status, "pending")
    val st = a.scheduler.status(Some(PageSize(500)))
    note("scheduler status", AgentSchedulerMarkdown.status(st))
    assertEquals((st.strategy, st.strategyFrom, st.dry), ("goal-first", "config", false))
    assertEquals((st.maxRuns, st.started.size), (3, 3))
    assert(
      s"${st.started.head.why}".contains("gathering share") || s"${st.started.head.why}".contains("goal-first"),
      st.started.head.why
    )
    val first = st.waiting.head
    assertEquals((first.goal.map(_.value: String), first.position, first.code), (Some(g(15)), 1, "capacity"))
    assert(s"${first.why}".contains("goal-first") && s"${first.why}".contains("no place free"), first.why)
    assert(first.behind.nonEmpty, first)
    // then the oldest goals, by age and slug (they were made in the same second)
    assertEquals(
      st.waiting.map(_.goal.map(_.value: String)).distinct.take(4),
      List(Some(g(15)), Some(g(1)), Some(g(2)), Some(g(3)))
    )
    // why, for one item: by id and by target
    val w =
      a.scheduler
        .why(ManagerItem.Ref(item(seedId(ref(g(15))), ProposeKind, target(g(15))).id.toString))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((w.code, w.position), ("capacity", 1))
    val running =
      a.scheduler.why(ManagerItem.Ref(g(20).value)).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    note("why waiting", AgentSchedulerMarkdown.why(w))
    note("why running", AgentSchedulerMarkdown.why(running))
    note(
      "why unknown",
      a.scheduler
        .why(Managers.ManagerItem.Ref("zzfocus-nope"))
        .fold(_.problems.sentences.mkString("; "), AgentSchedulerMarkdown.why)
    )
    assertEquals(running.code, AgentScheduler.Code.Running)
    assert(a.scheduler.why(Managers.ManagerItem.Ref("zzfocus-nope")).isLeft)
    // the status as Markdown names the strategy and the queue
    val md = AgentSchedulerMarkdown.status(st)
    assert(md.startsWith("# Scheduler: goal-first") && md.contains(s"1. #${first.item}"), md.take(600))
    // the ingest backlog drains for every goal (no AI allowance)
    assertEquals(
      goals.count(s => item(watchId(ref(s)), IngestKind, ManagerItem.Target(doi(s).value)).status.value == "done"),
      20
    )
    // the manager whose items wait says why in its status line
    assert(
      connect(ds)(ManagerRepo.get(seedId(ref(g(2)))))
        .flatMap(_.statusNote)
        .exists(_.render.contains("the scheduler: capacity")),
      connect(ds)(ManagerRepo.get(seedId(ref(g(2))))).flatMap(_.statusNote)
    )

    // switched live to type-balanced, with three more places: the starts spread over the job types
    val sw =
      a.scheduler
        .set(
          AgentScheduler.SchedulerSet(
            AgentScheduler.SchedulerSet.Strategy("type-balanced"),
            Some(AgentScheduler.SchedulerSet.By("test"))
          )
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((sw.strategy, sw.strategyFrom), ("type-balanced", "person"))
    assert(QueueRepoLog.has(ds, "agent scheduler: strategy type-balanced"), "a worklog line")
    val (b, _, _) = handlers(
      cfg.modify(_.agents).setTo(cfg.agents.modify(_.maxConcurrentRuns).setTo(AgentsConfig.MaxConcurrentRuns(6)))
    )
    clock.tick(b)
    val next = runs().map(r => (r.pipeline, r.target)).toSet -- Set(
      (RunInfo.Pipeline("propose-articles"), RunInfo.Target(g(20).value)),
      (RunInfo.Pipeline("acquisition-sweep"), RunInfo.Target(g(20).value)),
      (RunInfo.Pipeline("currency-check"), RunInfo.Target(g(20).value))
    )
    assertEquals(
      next.map((pipeline, target) => (pipeline.value: String, target.value: String)),
      Set(
        ("review-article", Art.value: String),
        ("propose-articles", g(15).value: String),
        ("acquisition-sweep", g(15).value: String)
      )
    )
    assertEquals(b.scheduler.status().strategy, "type-balanced")
    note("scheduler status type-balanced", AgentSchedulerMarkdown.status(b.scheduler.status(Some(PageSize(500)))))
    // back to the config's
    assertEquals(
      b.scheduler.set(AgentScheduler.SchedulerSet(AgentScheduler.SchedulerSet.Strategy("default"))).map(_.strategy),
      Right("goal-first")
    )
    assert(b.scheduler.set(AgentScheduler.SchedulerSet(AgentScheduler.SchedulerSet.Strategy("fastest"))).isLeft)

  test("with agents idle the status is a dry pass: what one would start, nothing started"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    a.autopilot.ensureNow(Instant.now())
    val st = a.scheduler.status()
    note("dry pass", AgentSchedulerMarkdown.status(st))
    assert(st.dry.value, st)
    assertEquals(st.started.size, 3)
    assertEquals(runs(), Vector.empty, "a dry pass starts nothing")

  test("goal-plan manual: the plan's run waits for a person and holds no place; the next goal's work takes it"):
    val one =
      cfg.modify(_.agents).setTo(cfg.agents.modify(_.maxConcurrentRuns).setTo(AgentsConfig.MaxConcurrentRuns(1)))
    val (a, h, up) = handlers(one); seed(a, h, up)
    val planned = DomainGoal.Slug("zzfocus-plan")
    assert(h.goals.put(planned, Goal.Put(Goal.Title("Zz a goal with no plan"))).isRight)
    on(a)
    a.focus
      .set(FocusSet(FocusSet.Action("pin"), Some(apiSlug(planned))))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val clock = Clock()
    clock.tick(a)
    val planItem = item(seedId(ref(planned)), PlanGoalKind, target(planned))
    assertEquals(planItem.status, "active", "the pinned goal's plan takes the one place")
    assertEquals(runs().size, 1)
    // its run now waits for a person to decide the plan (goal-plan = manual): the place frees
    val run = planItem.runId.get
    connect(ds)(sql"UPDATE jobs SET status = 'waiting' WHERE id = $run".update.run())
    clock.tick(a)
    assertEquals(runs().count(r => r.status.value == "queued"), 1, "another goal's item started in the freed place")
    assertEquals(a.scheduler.why(ManagerItem.Ref(planItem.id.toString)).map(_.code), Right(AgentScheduler.Code.Person))
    // its proposal waits for the plan without holding anything; the status counts what waits for a person
    val prop =
      a.scheduler
        .why(ManagerItem.Ref(item(seedId(ref(planned)), ProposeKind, target(planned)).id.toString))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(prop.code.value == "blocked" && s"${prop.why}".contains("plan comes first"), prop)
    assert(a.scheduler.status().forPerson.value >= 1)

  test("gathering goals keep their place: a goal still gathering sources starts beside the pinned goal under review"):
    val two =
      cfg.modify(_.agents).setTo(cfg.agents.modify(_.maxConcurrentRuns).setTo(AgentsConfig.MaxConcurrentRuns(2)))
    val (a, h, up) = handlers(two); seed(a, h, up)
    on(a)
    a.focus
      .set(FocusSet(FocusSet.Action("pin"), Some(apiSlug(g(5)))))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    Clock().tick(a)
    val started = runs().map(r => (r.pipeline, r.target)).toSet
    assert(started.contains((Agents.RunInfo.Pipeline("review-article"), ArtRunTarget)), started)
    assertEquals(started.size, 2)
    val other = (started - ((Agents.RunInfo.Pipeline("review-article"), ArtRunTarget))).head
    assert(other._2.value != g(5).value && other._2.value != g(10).value, s"a gathering goal's item, not $other")
    assert(
      a.scheduler.status().started.exists(s => s"${s.why}".contains("gathering share")),
      a.scheduler.status().started
    )

  test("goal plans are decided by code whatever the focus, within the open sub-goals"):
    val auto = cfg
      .modify(_.agents)
      .setTo(
        cfg.agents
          .modify(_.approvals)
          .setTo(cfg.agents.approvals.modify(_.goalPlan).setTo(AgentApprovalsConfig.Gate("auto")))
      )
    val (a, h, up) = handlers(auto); seed(a, h, up)
    on(a)
    Clock().tick(a)
    val now = Instant.now()
    def judged(p: ApprovalInfo) =
      a.autoApprovals.judge(TestRuns.approvals(ds).approval(ApiIds.approval(p.id)).get, None, None, now)
    assert(
      judged(plan(g(7), 0)).isInstanceOf[AutopilotPolicy.Verdict.Approve],
      "not active in any focus: decided all the same"
    )
    assert(judged(plan(g(1), 3)).isInstanceOf[AutopilotPolicy.Verdict.Approve])
    judged(plan(g(1), 6)) match
      case AutopilotPolicy.Verdict.Leave(w) => assert(s"$w".contains("limit on open sub-goals"), w)
      case v                                => fail(v.toString)

  test(
    "goal plans wait while too many planned goals are unfinished: no plan-goal run, none decided by code, a person still may"
  ):
    val auto = cfg
      .modify(_.agents)
      .setTo(
        cfg.agents
          .modify(_.approvals)
          .setTo(cfg.agents.approvals.modify(_.goalPlan).setTo(AgentApprovalsConfig.Gate("auto")))
          .modify(_.autopilot)
          .setTo(cfg.agents.autopilot.modify(_.maxUnfinishedGoals).setTo(AgentAutopilotConfig.MaxUnfinishedGoals(2)))
      )
    val (a, h, up) = handlers(auto); seed(a, h, up)
    val unplanned = DomainGoal.Slug("zzfocus-plan")
    assert(h.goals.put(unplanned, Goal.Put(Goal.Title("Zz a goal with no plan"))).isRight)
    // three goals with an approved plan, none reviewed: one more than the limit
    List(g(1), g(2), g(3)).foreach(s =>
      connect(ds)(sql"UPDATE goals SET plan = '{}'::jsonb WHERE slug = $s".update.run())
    )
    on(a)
    a.focus
      .set(FocusSet(FocusSet.Action("pin"), Some(apiSlug(unplanned))))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    Clock().tick(a)
    val planItem = item(seedId(ref(unplanned)), PlanGoalKind, target(unplanned))
    assertEquals(planItem.status, "pending", "no plan-goal run starts")
    assert(!runs().exists(_.pipeline.value == Pipeline.PlanGoal.wire.value), runs())
    val why = a.scheduler
      .why(ManagerItem.Ref(planItem.id.toString))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(
      why.code.value == "blocked" && s"${why.why}".contains("drop to 2") && s"${why.why}"
        .contains("3 goals with an approved plan"),
      why
    )
    // an open goal plan is neither decided by code nor reported to its manager; the digest and the status say why
    val p = plan(g(4))
    val _ = a.autoApprovals.sweep(Instant.now())
    assertEquals(TestRuns.approvals(ds).approval(ApiIds.approval(p.id)).map(_.state), Some("open"))
    a.autoApprovals.judge(TestRuns.approvals(ds).approval(ApiIds.approval(p.id)).get, None, None, Instant.now()) match
      case AutopilotPolicy.Verdict.Leave(w) => assert(s"$w".contains("limit on unfinished goals"), w)
      case v                                => fail(v.toString)
    val d = a.digest.digest()
    assert(d.goalPlansWait.exists(_.value.contains("drop to 2")), d)
    assertEquals(d.kinds.find(_.kind.value == "goal-plan").map(_.decidedBy), Some(ApprovalDigest.Waits))
    assert(a.autopilot.status().goalPlansWait.exists(_.value.contains("3 goals")))
    // a person decides it all the same
    assertEquals(
      a.decide(
        ApiIds.approval(p.id),
        Approval.Outcome("approve"),
        DecisionRequest(Some(com.histmersion.api.automation.Approval.Note("by hand")))
      ).map(_.state),
      Right("approved")
    )
    // one planned goal reviewed: at the limit, goal plans are offered again
    assertEquals(a.focus.unfinishedPlanned(), 3)
    connect(ds)(sql"UPDATE goals SET status = 'reviewed' WHERE slug = ${g(1)}".update.run())
    assertEquals(a.focus.goalPlansWait(), None)
    assertEquals(a.digest.digest().goalPlansWait, None)

  test("the digest: open approvals by kind; one worklog line a UTC day after digest-hour; a kind decided at once"):
    val morning = Instant.parse("2031-03-03T06:59:00Z")
    val (a, h, up) = handlers(digestClock = java.time.Clock.fixed(morning, java.time.ZoneOffset.UTC)); seed(a, h, up)
    val ps = List(g(1), g(2), g(3)).map(x => plan(x))
    val d = a.digest.digest()
    val plans = d.kinds.find(_.kind.value == "goal-plan").getOrElse(fail(d.toString))
    assertEquals((plans.count, plans.decidedBy), (3, "person"))
    assertEquals(plans.ids.map(_.value), ps.map(_.id).sortBy(x => (x.value: Long)).map(_.value))
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
    assert(
      a.digest.decideAll(ApprovalDigest.BulkDecide(ApprovalDigest.BulkDecide.Decision("approve"))).isLeft,
      "never every open approval"
    )
    assert(
      a.digest
        .decideAll(
          ApprovalDigest.BulkDecide(
            ApprovalDigest.BulkDecide.Decision("approve"),
            kind = Some(com.histmersion.api.automation.Approval.Kind.Wire("nope"))
          )
        )
        .isLeft
    )
    val dry = a.digest
      .decideAll(
        ApprovalDigest.BulkDecide(
          ApprovalDigest.BulkDecide.Decision("reject"),
          kind = Some(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")),
          dryRun = ApprovalDigest.BulkDecide.DryRun(true)
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((dry.matched, dry.decided.size, dry.dryRun), (3, 3, true))
    assert(
      ps.forall(p => TestRuns.approvals(ds).approval(ApiIds.approval(p.id)).exists(_.state.value == "open")),
      "a dry run decides nothing"
    )
    val done = a.digest
      .decideAll(
        ApprovalDigest.BulkDecide(
          ApprovalDigest.BulkDecide.Decision("reject"),
          kind = Some(com.histmersion.api.automation.Approval.Kind.Wire("goal-plan")),
          subject = Some(ApprovalDigest.BulkDecide.Subject(g(2).value)),
          by = Some(ApprovalDigest.BulkDecide.By("test"))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((done.matched, done.decided.map(_.value), done.failed), (1, List(ps(1).id.value), Nil))
    assertEquals(
      TestRuns
        .approvals(ds)
        .approval(ApiIds.approval(ps(1).id))
        .map(x => (x.state, x.note.exists(_.value.contains("in bulk by test")))),
      Some(("rejected", true))
    )
    assert(QueueRepoLog.has(ds, "approvals rejected in bulk by test"), "a worklog line")

  test("the daily caps scale with the backlog, up to the ceiling"):
    val scaled = cfg
      .modify(_.agents)
      .setTo(
        cfg.agents
          .modify(_.approvals)
          .setTo(
            cfg.agents.approvals
              .modify(_.goalPlan)
              .setTo(AgentApprovalsConfig.Gate("auto"))
              .modify(_.auto)
              .setTo(
                cfg.agents.approvals.auto
                  .modify(_.goalPlansPerDay)
                  .setTo(AutoApprovalsConfig.GoalPlansPerDay(1))
                  .modify(_.goalPlansBacklogShare)
                  .setTo(AutoApprovalsConfig.GoalPlansBacklogShare((0.5: Double)))
                  .modify(_.goalPlansCeiling)
                  .setTo(AutoApprovalsConfig.GoalPlansCeiling(3))
              )
          )
      )
    val (a, h, up) = handlers(scaled); seed(a, h, up)
    (1 to 6).foreach(i => plan(g(i)))
    assertEquals(a.autoApprovals.goalPlanCap, 3, "max(1, ceil(6 x 0.5)) = 3, at most 3")
    assert(
      a.managing.autopilot.approvals.exists(r =>
        r.kind.value == "goal-plan" && r.cap.value.contains("3 today for a backlog of 6")
      ),
      a.managing.autopilot.approvals
    )
    val (b, _, _) = handlers()
    assertEquals(
      b.autoApprovals.goalPlanCap.value,
      cfg.agents.approvals.auto.goalPlansPerDay.value,
      "share 0: the fixed cap"
    )

  test("drain: nothing new starts, the runs in flight finish, drained once none runs; undrain and on lift it"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    val drained = a.drain.set(Autopilot.DrainSet(on = Autopilot.DrainSet.On(true), Some(Autopilot.DrainSet.By("test"))))
    note("drain on, nothing running", a.autopilotPage(drained))
    assert(drained.drain.exists(_.drained.value), drained.drain)
    val clock = Clock()
    clock.tick(a)
    assertEquals(runs(), Vector.empty, "nothing starts while draining")
    assert(
      a.scheduler.status().waiting.forall(_.code.value == "drain"),
      a.scheduler.status().waiting.map(_.code).distinct
    )
    note("drained: scheduler", AgentSchedulerMarkdown.status(a.scheduler.status(Some(PageSize(500)))))
    note("drained: autopilot", a.autopilotPage(a.managing.autopilot))
    assert(a.autopilotPage(a.managing.autopilot).contains("DRAINED since"))
    assert(QueueRepoLog.has(ds, "autopilot: drained"), "a worklog line")
    // lifted: work starts; drained again while runs are in flight: draining, not drained, nothing new
    note(
      "drain off",
      a.autopilotPage(
        a.drain.set(Autopilot.DrainSet(on = Autopilot.DrainSet.On(false), Some(Autopilot.DrainSet.By("test"))))
      )
    )
    clock.tick(a)
    assertEquals(runs().size, 3)
    a.drain.set(Autopilot.DrainSet(on = Autopilot.DrainSet.On(true), Some(Autopilot.DrainSet.By("test"))))
    clock.tick(a)
    val d = a.managing.autopilot.drain.getOrElse(fail("draining"))
    assertEquals((d.drained, d.inFlight), (false, 3))
    note("draining, 3 in flight", a.autopilotPage(a.managing.autopilot))
    assert(a.autopilotPage(a.managing.autopilot).contains("DRAINING since"))
    // the runs in flight finish (here: cancelled): drained
    runs().foreach(r => a.engine.cancel(ApiIds.run(r.id)))
    clock.tick(a)
    assert(a.managing.autopilot.drain.exists(_.drained.value))
    assertEquals(runs().count(r => r.status.value == "queued"), 0, "still nothing new")
    // turning the autopilot on lifts it
    val st = a.managing
      .setAutopilot(AutopilotSet(on = AutopilotSet.On(true), Some(AutopilotSet.By("test"))))
      .left
      .map(Api.Error(_))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    note("on lifts the drain", a.autopilotPage(st))
    assert(st.changes.exists(_.value.contains("drain lifted")) && st.drain.isEmpty, st.changes)

  test("stalls: every unfinished goal with no progress, the reason and the action"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    plan(g(4))
    Clock().tick(a)
    val r = a.scheduler.stalls(Some(AgentAutopilotConfig.StallDays(0)))
    note("stalls", StallsMarkdown.report(r))
    assertEquals(r.goals, 20)
    assertEquals(r.stalled.size, 20, "days 0: every goal counts")
    assertEquals(r.stalled.find(_.slug.value == g(4).value).map(_.code), Some(Stalls.Code.Person))
    assert(r.stalled.exists(_.code.value == Stalls.Code.Queue), r.byReason)
    assertEquals(a.scheduler.stalls(Some(AgentAutopilotConfig.StallDays(3))).stalled, Nil, "they all started today")
    a.drain.set(Autopilot.DrainSet(on = Autopilot.DrainSet.On(true)))
    note("stalls while draining", StallsMarkdown.report(a.scheduler.stalls(Some(AgentAutopilotConfig.StallDays(0)))))
    assert(
      a.scheduler.stalls(Some(AgentAutopilotConfig.StallDays(0))).stalled.forall(_.code.value == Stalls.Code.Drain)
    )

  test(
    "seed before the plan: sourceless goals sweep their candidates (no model) and ingest legal open access within the caps"
  ):
    val (a, h, up) = handlers(); seed(a, h, up)
    def cand(id: Source.Candidate.Id, goal: DomainGoal.Slug, oa: Source.Candidate.IsOa) =
      Source.Candidate(
        id,
        Source.Candidate.Goal(goal.value),
        Doi(s"10.5555/zzseed.$id"),
        com.histmersion.api.library.Title(s"Zz seed $id"),
        isOa = oa
      )
    up.sweepFinds = Map(
      g(1).value -> List(
        cand(Source.Candidate.Id(1), g(1), oa = Source.Candidate.IsOa(true)),
        cand(Source.Candidate.Id(2), g(1), oa = Source.Candidate.IsOa(false))
      ),
      g(2).value -> List(cand(Source.Candidate.Id(3), g(2), oa = Source.Candidate.IsOa(true))),
      g(3).value -> Nil,
      g(4).value -> Nil
    )
    on(a)
    val clock = Clock()
    clock.tick(a)
    import scala.jdk.CollectionConverters.*
    // the two first sourceless goals (g01, g02: oldest, then slug) were seeded; g10 has sources and is not
    def seeded =
      goals.filter(x =>
        connect(ds)(ManagerRepo.events(watchId(ref(x)), None, PageSize(100)))
          .exists(_.text.value.startsWith("seed before the plan"))
      )
    assertEquals(seeded, List(g(1), g(2)))
    // their legal open-access finds went through the ingest backlog and were ingested in the same tick
    assertEquals(
      up.calls.asScala.toList.filter(_.startsWith("ingest 10.5555/zzseed")),
      List("ingest 10.5555/zzseed.1", "ingest 10.5555/zzseed.3")
    )
    assert(
      connect(ds)(GoalLinkRepo.links(g(1))).exists(l =>
        l.kind == LinkKind.Source && l.ref.value == ck("10.5555/zzseed.1").value
      ),
      connect(ds)(GoalLinkRepo.links(g(1)))
    )
    assert(QueueRepoLog.has(ds, "seed before the plan: "), "a worklog line")
    // the next tick seeds the next two; a goal seeded today is not seeded again
    clock.tick(a)
    assertEquals(seeded, List(g(1), g(2), g(3), g(4)))
    assertEquals(
      connect(ds)(ManagerRepo.events(watchId(ref(g(1))), None, PageSize(100)))
        .count(_.text.value.startsWith("seed before the plan")),
      1
    )
    // draining: no seeding
    a.drain.set(Autopilot.DrainSet(on = Autopilot.DrainSet.On(true)))
    clock.tick(a)
    assertEquals(seeded.size, 4)

  test("#3763: no proposal while a goal's articles are in progress; once they are done, the next one is proposed"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    val clock = Clock()
    clock.tick(a)
    // g05's article has not passed review: its proposal is kept back
    val prop = item(seedId(ref(g(5))), ProposeKind, target(g(5)))
    val why =
      a.scheduler.why(ManagerItem.Ref(prop.id.toString)).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(
      why.code.value == "blocked" && s"${why.why}".contains("articles in progress come first (1 to pass review)"),
      why
    )
    assertEquals(a.autopilot.nextArticles(clock.now), Nil)
    // the article passes its review (a clean ledger, no review item open), and the proposal had settled days ago
    ReviewLedgerFixtures.put(ds, ReviewLedger.Ledger(ReviewLedger.Ledger.Slug("zzfocus_art")))
    connect(ds)(ManagerRepo.itemFor(Review, ReviewKind, ArtItem))
      .foreach(i =>
        transact(ds)(
          ManagerRepo.settle(
            i.id,
            ItemStatus.Done,
            ManagerRepo.ItemRow.Outcome("converged"),
            List(ItemStatus.Pending, ItemStatus.Active, ItemStatus.Held)
          )
        )
      )
    transact(ds)(
      ManagerRepo.settle(
        prop.id,
        ItemStatus.Done,
        ManagerRepo.ItemRow.Outcome("approval #1 approved"),
        List(ItemStatus.Pending)
      )
    )
    connect(ds)(sql"UPDATE manager_items SET updated_at = now() - interval '2 days' WHERE id = ${prop.id}".update.run())
    assertEquals(a.autopilot.nextArticles(clock.now), List(s"goal ${g(5)}: next article proposed"))
    assertEquals(item(seedId(ref(g(5))), ProposeKind, target(g(5))).status, "pending")
    assert(
      connect(ds)(ManagerRepo.events(seedId(ref(g(5))), None, PageSize(50)))
        .exists(_.text.value.contains("its next article is proposed"))
    )
    clock.tick(a)
    assertNotEquals(
      a.scheduler.why(ManagerItem.Ref(prop.id.toString)).map(_.code),
      Right("blocked"),
      "no longer kept back"
    )
    // open again: not proposed twice; an approved article to compile keeps the next one back
    assertEquals(a.autopilot.nextArticles(clock.now), Nil)
    transact(ds)(
      ManagerRepo.upsert(
        seedId(ref(g(5))),
        CompileKind,
        ManagerRepo.ItemRow.Target("zzfocus_next"),
        ItemOrigin.Chain,
        ManagerRepo.ItemRow.Priority(0),
        reopen = false
      )
    )
    clock.tick(a)
    val back =
      a.scheduler.why(ManagerItem.Ref(prop.id.toString)).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(back.code.value == "blocked" && s"${back.why}".contains("1 to compile"), back)

/** The worklog, read for an assertion. */
private object QueueRepoLog:
  def has(ds: javax.sql.DataSource, prefix: String): Boolean =
    QueueRows.list(ds, Queue.Kind.Log, all = true).exists(_.text.value.startsWith(prefix))
