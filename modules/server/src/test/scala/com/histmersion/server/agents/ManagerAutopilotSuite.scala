package com.histmersion
package server.agents

import com.histmersion.api.Problem
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ScriptedClient
import com.histmersion.domain.automation.agents.TestRuns

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Autopilot.AutopilotSet
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.ReviewLedger.{absorb, Ledger}
import com.histmersion.domain.automation.worklists.QueueRows
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.autopilot.AutopilotRepo
import com.histmersion.domain.automation.goals.{GoalLinkRepo, GoalRepo}
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.util.database.Migrate
import com.histmersion.domain.research.workflows.ReviewLedgerFixtures
import com.histmersion.server.{Deps, ServerWiring}
import io.circe.Json
import sttp.ai.core.agent.*

import java.time.Instant
import com.histmersion.api.automation.{AgentScheduler, Goal, Queue}
import com.histmersion.domain.automation.Goal as DomainGoal
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.MetadataPatch
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.config.DbConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentRolesConfig
import com.histmersion.config.AgentApprovalsConfig
import com.histmersion.config.AutoApprovalsConfig
import com.histmersion.config.AgentAutopilotConfig
import com.histmersion.config.AgentSeedConfig
import com.histmersion.config.AgentReviewConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.automation.Run
import com.histmersion.domain.AutopilotPolicy
import com.histmersion.domain.research.Slug
import com.histmersion.domain.ReviewLedger
import com.histmersion.api.views.AutopilotStatus
import com.histmersion.domain.automation.managers.ManagersControllers
import com.histmersion.api.automation.Managers
import com.histmersion.api.library.Filter

/** The autopilot against a `*_test` database (docs/research-process-managers.md "As built: the autopilot"): one switch
  * carries a planned goal through plan → sources → articles → review to `reviewed` with the checked auto approvals on —
  * and with them off nothing is approved by itself; the sub-goals an approved plan creates get their managers (no
  * sub-goals at the depth limit); off pauses what it runs and on resumes exactly that; the approval caps; new filter
  * sources extend an article; the ingests all watchers share; the housekeeping. Models are scripted
  * ([[ScriptedClient]]), a review run is settled by hand; no provider is called. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class ManagerAutopilotSuite extends DbSuite with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val Goal = DomainGoal.Slug("zzagent-apgoal")
  private val ChildGoal = DomainGoal.Slug("zzagent-apchild")
  private val GrandGoal = DomainGoal.Slug("zzagent-apgrand")
  private val GoalSlug = com.histmersion.api.automation.Goal.Slug("zzagent-apgoal")
  private val GoalSubject = ApprovalInfo.Subject("zzagent-apgoal")
  private val GoalQuerySubject = ApprovalQuery.Subject("zzagent-apgoal")
  private val GoalPlanGoal = Article.Plans.ArticlePlan.Goal("zzagent-apgoal")
  private val Ck = Citekey("zzagent2019_tin")
  private val Ck2 = Citekey("zzagent2024_iso")
  private val ApiCk2 = com.histmersion.api.library.Citekey("zzagent2024_iso")
  private val Review = ManagerInfo.Id(ReviewId)
  private val PlanGoalKind = ManagerItem.Kind(PlanGoalItem)
  private val ProposeKind = ManagerItem.Kind(ProposeArticles)
  private val CompileKind = ManagerItem.Kind(ArticleCompile)
  private val ReviewKind = ManagerItem.Kind(ArticleReview)
  private val ExtendKind = ManagerItem.Kind(ArticleExtend)
  private val SweepKind = ManagerItem.Kind(AcquisitionSweep)
  private val NewTarget = ManagerItem.Target("zzagent_new")
  private val IngestedKind = ManagerRepo.EventKind(Feed.Ingested)
  private val Model = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("scripted"))
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))
  private val t0 = Instant.parse("2026-10-19T12:00:00Z")

  /** Checked auto approvals ON (article-plan, goal-plan, article-publish = checked), the autopilot's housekeeping off,
    * room for every manager's run.
    */
  private lazy val autoCfg =
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
          .setTo(AgentsConfig.MaxConcurrentRuns(20))
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
          .modify(_.approvals)
          .setTo(
            b.agents.approvals
              .modify(_.articlePublish)
              .setTo(AgentApprovalsConfig.Gate("checked"))
              .modify(_.articlePlan)
              .setTo(AgentApprovalsConfig.Gate("auto"))
              .modify(_.goalPlan)
              .setTo(AgentApprovalsConfig.Gate("auto"))
              .modify(_.auto)
              .setTo(
                b.agents.approvals.auto.modify(_.publishMinSources).setTo(AutoApprovalsConfig.PublishMinSources(2))
              )
          )
          .modify(_.autopilot)
          .setTo(
            b.agents.autopilot
              .modify(_.backupHours)
              .setTo(AgentAutopilotConfig.BackupHours(0))
              .modify(_.lintHours)
              .setTo(AgentAutopilotConfig.LintHours(0))
              .modify(_.ingestPerDay)
              .setTo(AgentAutopilotConfig.IngestPerDay(2))
          )
      )
      .modify(_.libraryAgents)
      .setTo(
        b.libraryAgents
          .modify(_.seed)
          .setTo(b.libraryAgents.seed.modify(_.minSources).setTo(AgentSeedConfig.MinSources(2)))
      )
      .modify(_.researchAgents)
      .setTo(
        b.researchAgents
          .modify(_.review)
          .setTo(b.researchAgents.review.modify(_.spotChecks).setTo(AgentReviewConfig.SpotChecks(0)))
      )

  /** The same with every approval a person's (the shipped defaults). */
  private lazy val manualCfg =
    autoCfg
      .modify(_.agents)
      .setTo(
        autoCfg.agents
          .modify(_.approvals)
          .setTo(
            autoCfg.agents.approvals
              .modify(_.articlePublish)
              .setTo(AgentApprovalsConfig.Gate("manual"))
              .modify(_.articlePlan)
              .setTo(AgentApprovalsConfig.Gate("manual"))
              .modify(_.goalPlan)
              .setTo(AgentApprovalsConfig.Gate("manual"))
          )
      )

  private lazy val ds = testPool(autoCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  /** Noon UTC of the day it runs: the digest and the crons see the same hour whenever the suite runs. */
  private val suiteClock: java.time.Clock = TestRuns.noonClock()

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private def str(s: String) = Json.fromString(s).noSpaces

  private def handlers(c: LlmClient, cfg: AppConfig = autoCfg): (AgentsControllers, ServerWiring) =
    val deps = Deps(cfg, ds, ds, clock = suiteClock)
    val h = ServerWiring(deps)
    (AgentsControllers(ServerEngineSite(deps, h), (_, _) => Right(c)), h)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND manager_id IS NOT NULL".update.run()
      sql"DELETE FROM approvals WHERE subject LIKE 'zz%' OR citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zz%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zz%'".update.run()
      sql"DELETE FROM article_versions WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM review_ledgers WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%' OR text LIKE 'autopilot%'".update.run()
      sql"""UPDATE agent_autopilot SET enabled = false, paused = '{}', changed_by = '', last_ensure_at = NULL,
                                       last_backup_at = NULL, last_lint_at = NULL""".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(h: ServerWiring): Unit =
    assert(
      h.library.sources
        .put(
          Ck,
          Source.Put(
            title = com.histmersion.api.library.Title("Bronze Age tin"),
            year = (Some(2019)).map(Year.apply),
            topics = List(Source.Topics("zzagent_tin")),
            status = Some(Source.Status.Current),
            summary = Some(Source.Summary("The Uluburun ship carried ten tons of copper and one ton of tin.")),
            body = Some(
              Source.OcrMarkdown(
                "The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."
              )
            )
          )
        )
        .isRight
    )
    assert(
      h.library.sources
        .put(
          Ck2,
          Source.Put(
            title = com.histmersion.api.library.Title("Tin isotopes"),
            year = (Some(2024)).map(Year.apply),
            topics = List(Source.Topics("zzagent_tin")),
            body = Some(
              Source.OcrMarkdown("We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia.")
            )
          )
        )
        .isRight
    )
    assert(
      h.library.sources
        .patch(
          Ck2,
          Source
            .Patch(metadata = Some(MetadataPatch(status = Some(Source.Status.Contested))))
        )
        .isRight
    )
    assert(
      h.goals
        .put(
          Goal,
          com.histmersion.api.automation.Goal.Put(
            com.histmersion.api.automation.Goal.Title("Zz tin in the Bronze Age"),
            notes = Some(com.histmersion.api.automation.Goal.Notes("where the tin came from"))
          )
        )
        .isRight
    )

  private val articleBody =
    s"""Tin reached the Aegean by sea [[$Ck]].
       |
       |## Disagreements
       |
       |Its origin is disputed: [[$Ck2]] (contested) places it in Central Asia.
       |
       |## Open questions
       |
       |Where the tin was mined is open [[$Ck]].""".stripMargin

  private val planJson =
    """{"rationale":"where Bronze Age tin came from","subGoals":[],"queries":["Bronze Age tin isotopes"],"works":[]}"""

  private val articlesJson =
    """{"rationale":"tin trade","articles":[
      |{"slug":"zzagent_new","title":"Zz new","kind":null,"query":null,"topics":["zzagent_tin"],"tags":[],"why":"origin of tin"}]}""".stripMargin

  private def extract(quote: String, claim: String) =
    s"""{"relevant":true,"reason":"bears on tin","claims":[{"claim":${str(claim)},"quote":${str(
        quote
      )},"date":null,"place":null,"confidence":"high"}]}"""

  private def draftJson(body: String) =
    s"""{"title":"Zz new","summary":"Where Aegean tin came from.","body":${str(body)},"section":null,"reasons":[]}"""

  private def ref(g: DomainGoal.Slug) = com.histmersion.api.automation.Goal.Ref(g.value)
  private def itemTarget(g: DomainGoal.Slug) = ManagerItem.Target(g.value)

  private def item(mid: ManagerInfo.Id, kind: ManagerItem.Kind, target: ManagerItem.Target): ManagerItem =
    connect(ds)(ManagerRepo.itemFor(mid, kind, target)).get
  private def runOf(mid: ManagerInfo.Id, kind: ManagerItem.Kind, target: ManagerItem.Target): RunInfo =
    connect(ds)(
      AgentRepo.get(
        item(mid, kind, target).runId.getOrElse(fail(s"$mid $kind $target has no run: ${item(mid, kind, target)}"))
      )
    ).get

  private def why(r: RunInfo): String =
    (r.error.toList ++ connect(ds)(AgentRepo.steps(ApiIds.job(r.id), AgentRepo.OutputChars(200)))
      .filter(_.error.isDefined)
      .map(s => s"${s.name}#${s.attempt}: ${s.error.get}"))
      .mkString("\n")

  private def exec(a: AgentsControllers, id: RunInfo.Id): RunInfo =
    a.engine.execute(ApiIds.run(id))
    connect(ds)(AgentRepo.get(id)).get

  /** The watchers' sweeps need no model (offline: nothing found): run what they queued. */
  private def drainWatchers(a: AgentsControllers): Unit =
    connect(ds)(AgentRepo.list(Some(Run.State.Wire("queued")), PageSize(200)))
      .filter(_.managerId.exists(_.value.startsWith("watch:")))
      .foreach(r => a.engine.execute(ApiIds.run(r.id)))

  private def status(g: DomainGoal.Slug): com.histmersion.api.automation.Goal.StatusValue =
    connect(ds)(GoalRepo.get(g)).get.status

  /** A clock from the wall clock (the switch records real times) that moves six minutes a tick — past the autopilot's
    * ensure interval every tick.
    */
  final private class Clock(var now: Instant = Instant.now(suiteClock)): // a var: the test's own moving time
    def tick(a: AgentsControllers, minutes: Int = 6): List[ManagerContext.TickLine] =
      now = now.plusSeconds(minutes.toLong * 60L)
      a.managers.tick(now)

  // ── end to end ─────────────────────────────────────────────────────────────────────────────────

  test("autopilot, checked approvals: a planned goal is planned, seeded, compiled and reviewed with no person"):
    val c = ScriptedClient(Model)
    val (a, h) = handlers(c); seed(h)
    a.managers.ensureDefaults()
    // the review manager's own selection narrowed to this goal (other suites' articles must not take its slots)
    a.managing
      .update(
        ApiIds.manager(Review),
        ManagerUpdate(Some(ManagerConfig(includeGoal = Some(ManagerConfig.IncludeGoal("zzagent-apgoal")))))
      )
      .fold(e => fail(e.message.value), identity)
    val on = a.managing
      .setAutopilot(AutopilotSet(on = AutopilotSet.On(true), Some(AutopilotSet.By("test"))))
      .fold(e => fail(e.message.value), identity)
    assert(on.on.value)
    assert(
      on.changes.contains(s"${seedId(ref(Goal))} created") && on.changes.contains(s"${watchId(ref(Goal))} created") &&
        on.changes.contains(s"$ReviewId resumed") && on.changes.contains(s"$EnrichId resumed"),
      on.changes
    )
    assertEquals(status(Goal), com.histmersion.domain.automation.Goal.Status.Seeding, "managed: seeding")
    val seedM = connect(ds)(ManagerRepo.get(seedId(ref(Goal)))).get
    assertEquals(
      (seedM.state, seedM.cron.map(_.value: String)),
      (
        "active",
        Some(AutopilotPolicy.stagger(AutopilotPolicy.Cron("@weekly"), AutopilotPolicy.ManagerRow.Id.seed(Goal)))
      )
    )
    assertEquals(
      connect(ds)(ManagerRepo.get(watchId(ref(Goal)))).map(m => (m.state, m.config.autoIngest)),
      Some(("active", Some(true)))
    )
    assertEquals(item(seedId(ref(Goal)), PlanGoalKind, itemTarget(Goal)).priority, 1)
    assert(on.goals.exists(g => g.slug.value == Goal.value && g.managers.size == 2), on.goals)
    // again: nothing more to do
    assertEquals(
      a.managing.setAutopilot(AutopilotSet(on = AutopilotSet.On(true))).toOption.get.changes,
      List("already on")
    )

    // 1. plan-goal first (the proposal waits for it); its plan is approved by the checks, the run applies it
    c.scripts += Seq(text(planJson))
    val clock = Clock()
    clock.tick(a)
    assertEquals(item(seedId(ref(Goal)), ProposeKind, itemTarget(Goal)).status, "pending", "waits for the plan")
    val plan = runOf(seedId(ref(Goal)), PlanGoalKind, itemTarget(Goal))
    assertEquals((plan.pipeline, plan.params.subGoals), ("plan-goal", None), "a root goal may be split")
    drainWatchers(a)
    assertEquals(exec(a, plan.id).status, "waiting", why(connect(ds)(AgentRepo.get(plan.id)).get))
    clock.tick(a)
    val gp =
      a.approvalsList(Some(ApprovalQuery.State("all")), None)
        .toOption
        .get
        .find(a => a.kind.value == "goal-plan" && a.subject.contains(GoalSubject))
        .get
    assertEquals(gp.state, "approved")
    assert(gp.note.exists(_.value.startsWith("autopilot: approved — 0 sub-goals, 1 query")), gp.note)
    assertEquals(exec(a, plan.id).status, "done", why(connect(ds)(AgentRepo.get(plan.id)).get))
    assertEquals(Transactor(ds).read(GoalLinkRepo.refsIn(Goal, LinkKind.Query)), List("Bronze Age tin isotopes"))
    assert(
      connect(ds)(ManagerRepo.events(seedId(ref(Goal)), None, PageSize(100)))
        .exists(e => e.kind.value == Feed.Approval && e.text.value.contains("approved by the autopilot"))
    )
    assert(QueueRows.list(ds, Queue.Kind.Log, all = true).exists(_.text.value.startsWith("autopilot: goal-plan")))

    // 2. propose-articles: the article plan is approved by the checks and becomes a compile item
    c.scripts += Seq(text(articlesJson))
    clock.tick(a)
    assertEquals(item(seedId(ref(Goal)), PlanGoalKind, itemTarget(Goal)).status, "done")
    val prop = runOf(seedId(ref(Goal)), ProposeKind, itemTarget(Goal))
    assertEquals(exec(a, prop.id).status, "done", why(prop))
    c.scripts ++= List(
      Seq(text(extract("The Uluburun ship carried ten tons of copper and one ton of tin.", "The ship carried tin."))),
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(text(draftJson(articleBody)))
    )
    clock.tick(a)
    val ap = a
      .approvalsList(Some(ApprovalQuery.State("all")), None)
      .toOption
      .get
      .find(a => a.kind.value == "article-plan" && a.subject.contains(GoalSubject))
      .get
    assertEquals(ap.state, "approved")
    assert(ap.note.exists(_.value.contains("every slug free")), ap.note)
    assertEquals(item(seedId(ref(Goal)), ProposeKind, itemTarget(Goal)).status, "done")

    // 3. the compile: its draft passes the checks and is published; the review follows as an item
    val comp = runOf(seedId(ref(Goal)), CompileKind, NewTarget)
    assertEquals(comp.params.chainReview, Some(false))
    assertEquals(exec(a, comp.id).status, "waiting", why(connect(ds)(AgentRepo.get(comp.id)).get))
    clock.tick(a)
    val pub = a
      .approvalsList(Some(ApprovalQuery.State("all")), None)
      .toOption
      .get
      .find(a => a.kind.value == "article-publish" && a.subject.contains(ApprovalInfo.Subject("zzagent_new")))
      .get
    assertEquals(pub.state, "approved")
    assert(pub.note.exists(_.value.startsWith("autopilot: approved — filter 2 sources")), pub.note)
    assertEquals(exec(a, comp.id).status, "done", why(connect(ds)(AgentRepo.get(comp.id)).get))
    assert(h.research.articles.get(Slug("zzagent_new")).isRight)
    clock.tick(a)
    assertEquals(item(seedId(ref(Goal)), CompileKind, NewTarget).status, "done")
    clock.tick(a)
    assertEquals(
      status(Goal),
      com.histmersion.domain.automation.Goal.Status.Compiled,
      "an article, nothing left to compile"
    )

    // 4. the review (settled by hand: it converged) → the goal is reviewed
    val rev = runOf(Review, ReviewKind, NewTarget)
    assert(TestRuns.transition(ds, ApiIds.run(rev.id), RunState.Queued, RunState.Running).isDefined)
    ReviewLedgerFixtures.put(
      ds,
      absorb(Ledger(Ledger.Slug("zzagent_new")), Nil, ApiIds.run(rev.id), ReviewLedger.Closure.Round(1))._1
    )
    assert(TestRuns.transition(ds, ApiIds.run(rev.id), RunState.Running, RunState.Done).isDefined)
    clock.tick(a)
    assertEquals(item(Review, ReviewKind, NewTarget).status, "done")
    clock.tick(a)
    assertEquals(status(Goal), com.histmersion.domain.automation.Goal.Status.Reviewed)
    assert(
      connect(ds)(ManagerRepo.events(seedId(ref(Goal)), None, PageSize(200)))
        .exists(e => e.kind.value == Feed.Autopilot && e.text.value.contains("compiled → reviewed"))
    )
    assert(
      QueueRows.list(ds, Queue.Kind.Log, all = true).exists(_.text.value.contains(s"goal $Goal: compiled → reviewed"))
    )
    val st = a.managing.autopilot
    note("autopilot, the goal reviewed", a.autopilotPage(st))
    val g = st.goals.find(_.slug.value == Goal.value).get
    assertEquals(
      (g.status, g.articles, g.openApprovals),
      (com.histmersion.domain.automation.Goal.Status.Reviewed, 1, 0)
    )
    assert(g.doneLast7.value >= 3, g)
    assert(a.autopilotPage(st).contains(s"**$Goal** _(reviewed)_"))
    assert(!a.autopilotPage(st).contains("crons are off"))
    assert(
      a.autopilotPage(st.modify(_.cronsEnabled).setTo(AutopilotStatus.CronsEnabled(false))).contains("crons are off")
    )

  test("autopilot, approvals a person's (the defaults): managers and plans start, nothing is approved by itself"):
    val c = ScriptedClient(Model)
    val (a, h) = handlers(c, manualCfg); seed(h)
    a.managers.ensureDefaults()
    a.managing.setAutopilot(AutopilotSet(on = AutopilotSet.On(true))).fold(e => fail(e.message.value), identity)
    c.scripts += Seq(text(planJson))
    val clock = Clock()
    clock.tick(a)
    val plan = runOf(seedId(ref(Goal)), PlanGoalKind, itemTarget(Goal))
    drainWatchers(a)
    assertEquals(exec(a, plan.id).status, "waiting")
    (1 to 3).foreach(_ => clock.tick(a))
    val open = a.approvalsList(None, None).toOption.get.filter(_.subject.contains(GoalSubject))
    assertEquals(open.map(a => (a.kind, a.state)), List(("goal-plan", "open")))
    assertEquals(connect(ds)(AgentRepo.get(plan.id)).get.status, "waiting")
    // the proposal waits for the plan, which waits for a person: nothing is held for a focus; the scheduler
    // keeps the proposal back (its plan comes first), and the waiting plan run holds no place
    val waits = item(seedId(ref(Goal)), ProposeKind, itemTarget(Goal))
    assertEquals((waits.status, waits.hold), ("pending", None), "the proposal waits for the plan")
    val why = a.scheduler.why(ManagerItem.Ref(waits.id.toString)).fold(e => fail(e.message.value), identity)
    assert(why.code.value == "blocked" && why.why.value.contains("plan comes first"), why)
    assertEquals(
      a.scheduler.why(ManagerItem.Ref(item(seedId(ref(Goal)), PlanGoalKind, itemTarget(Goal)).id.toString)).map(_.code),
      Right(AgentScheduler.Code.Person)
    )
    assert(
      a.focus.info().waiting.exists(w => w.slug.value == Goal.value && w.code.value == GoalFocus.Code.Person.value),
      a.focus.info().waiting
    )
    assert(
      !a.approvalsList(Some(ApprovalQuery.State("all")), None)
        .toOption
        .get
        .exists(_.note.exists(_.value.startsWith("autopilot:")))
    )
    note("autopilot, approvals a person's", a.autopilotPage(a.managing.autopilot))
    assertEquals(a.managing.autopilot.goals.find(_.slug.value == Goal.value).map(_.openApprovals), Some(1))
    assert(a.managing.autopilot.approvals.forall(_.mode.value == "person"), a.managing.autopilot.approvals)
    // a manager's own autoApprove wins over the defaults
    val sm = connect(ds)(ManagerRepo.get(seedId(ref(Goal)))).get
    transact(ds)(
      ManagerRepo.setConfig(
        sm.id,
        sm.config.modify(_.autoApprove).setTo(Some(ManagerConfig.AutoApprove(true))),
        sm.cron,
        sm.nextTickAt.flatMap(t => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(t.value)))
      )
    )
    // the plan is code's to decide now
    clock.tick(a)
    clock.tick(a)
    assertEquals(
      a.approvalsList(Some(ApprovalQuery.State("all")), None)
        .toOption
        .get
        .find(_.kind.value == "goal-plan")
        .map(_.state),
      Some("approved")
    )

  // ── sub-goals, off and on ──────────────────────────────────────────────────────────────────────

  test(
    "sub-goals get their managers; no sub-goals at the depth limit; off pauses what it runs, on resumes exactly that"
  ):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(
      h.goals
        .put(
          ChildGoal,
          com.histmersion.api.automation.Goal
            .Put(com.histmersion.api.automation.Goal.Title("Zz child"), parentSlug = Some(GoalSlug))
        )
        .isRight
    )
    assert(
      h.goals
        .put(
          GrandGoal,
          com.histmersion.api.automation.Goal.Put(
            com.histmersion.api.automation.Goal.Title("Zz grandchild"),
            parentSlug = Some(com.histmersion.api.automation.Goal.Slug("zzagent-apchild"))
          )
        )
        .isRight
    )
    a.managers.ensureDefaults()
    a.managing.setAutopilot(AutopilotSet(on = AutopilotSet.On(true))).fold(e => fail(e.message.value), identity)
    List(Goal, ChildGoal, GrandGoal).foreach { g =>
      assertEquals(connect(ds)(ManagerRepo.get(seedId(ref(g)))).map(_.state), Some("active"), g)
      assertEquals(connect(ds)(ManagerRepo.get(watchId(ref(g)))).map(_.state), Some("active"), g)
      assertEquals(status(g), com.histmersion.domain.automation.Goal.Status.Seeding, g)
    }
    Clock().tick(a)
    assertEquals(
      runOf(seedId(ref(ChildGoal)), PlanGoalKind, itemTarget(ChildGoal)).params.subGoals,
      None,
      "depth 1: may split"
    )
    assertEquals(
      runOf(seedId(ref(GrandGoal)), PlanGoalKind, itemTarget(GrandGoal)).params.subGoals,
      Some(0),
      "depth 2: no sub-goals"
    )
    // a person pauses one watcher; off pauses the others it runs, on resumes those only
    a.managing
      .action(ApiIds.manager(watchId(ref(ChildGoal))), ManagersControllers.Action("pause"), None)
      .fold(e => fail(e.message.value), identity)
    val off =
      a.managing.setAutopilot(AutopilotSet(on = AutopilotSet.On(false))).fold(e => fail(e.message.value), identity)
    assert(!off.on.value)
    assert(off.changes.exists(_.value.contains("paused 5 managers")), off.changes)
    assert(
      connect(ds)(ManagerRepo.list())
        .filter(m => m.kind.value == "seed" || m.kind.value == "watch")
        .forall(_.state.value == "paused")
    )
    assertEquals(connect(ds)(ManagerRepo.get(Review)).map(_.state), Some("active"), "the library-wide ones stay")
    assertEquals(Clock().tick(a).filter(_.value.startsWith("goal")), Nil, "off: nothing ensured")
    a.managing.setAutopilot(AutopilotSet(on = AutopilotSet.On(true))).fold(e => fail(e.message.value), identity)
    assertEquals(
      connect(ds)(ManagerRepo.get(watchId(ref(ChildGoal)))).map(_.state),
      Some("paused"),
      "the person's pause holds"
    )
    assertEquals(
      connect(ds)(ManagerRepo.list()).count(m =>
        (m.kind.value == "seed" || m.kind.value == "watch") && m.state.value == "active"
      ),
      5
    )
    assertEquals(transact(ds)(AutopilotRepo.get()).paused, Nil)
    // a goal added while it is on gets its managers at the next ensure
    assert(
      h.goals
        .put(
          DomainGoal.Slug("zzagent-aplate"),
          com.histmersion.api.automation.Goal.Put(com.histmersion.api.automation.Goal.Title("Zz later"))
        )
        .isRight
    )
    Clock(Instant.now(suiteClock).plusSeconds(3600)).tick(a)
    assertEquals(
      connect(ds)(ManagerRepo.get(seedId(com.histmersion.api.automation.Goal.Ref("zzagent-aplate")))).map(_.state),
      Some("active")
    )
    // the New research area form's watcher, asked for while it is on, is created active (paused only when asked)
    assert(
      h.goals
        .put(
          DomainGoal.Slug("zzagent-apform"),
          com.histmersion.api.automation.Goal.Put(com.histmersion.api.automation.Goal.Title("Zz form"))
        )
        .isRight
    )
    assertEquals(
      a.managing
        .create(
          ManagerCreate(
            Managers.ManagerCreate.Kind("watch"),
            Some(com.histmersion.api.automation.Goal.Slug("zzagent-apform"))
          )
        )
        .map(_.state),
      Right("active")
    )
    assertEquals(
      a.managing
        .create(
          ManagerCreate(
            Managers.ManagerCreate.Kind("seed"),
            Some(com.histmersion.api.automation.Goal.Slug("zzagent-apform")),
            paused = Some(Managers.ManagerCreate.Paused(true))
          )
        )
        .map(_.state),
      Right("paused")
    )

  test("a reviewed goal gets a watcher on the slow cron and no seeding manager"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(
      h.goals
        .patch(
          Goal,
          com.histmersion.api.automation.Goal
            .Patch(status =
              Some(
                com.histmersion.api.automation.Goal.StatusValue(com.histmersion.domain.automation.Goal.Status.Reviewed)
              )
            )
        )
        .isRight
    )
    a.managing.setAutopilot(AutopilotSet(on = AutopilotSet.On(true))).fold(e => fail(e.message.value), identity)
    assertEquals(connect(ds)(ManagerRepo.get(seedId(ref(Goal)))), None)
    assertEquals(
      connect(ds)(ManagerRepo.get(watchId(ref(Goal)))).flatMap(_.cron).map(_.value: String),
      Some(AutopilotPolicy.stagger(AutopilotPolicy.Cron("@monthly"), AutopilotPolicy.ManagerRow.Id.watch(Goal)))
    )

  // ── caps ───────────────────────────────────────────────────────────────────────────────────────

  test(
    "article-plan cap: the area's daily cap leaves a second plan for a person; a manager that says no is never decided"
  ):
    val capped = autoCfg
      .modify(_.agents)
      .setTo(
        autoCfg.agents
          .modify(_.approvals)
          .setTo(
            autoCfg.agents.approvals
              .modify(_.auto)
              .setTo(
                autoCfg.agents.approvals.auto
                  .modify(_.articlesPerGoalPerDay)
                  .setTo(AutoApprovalsConfig.ArticlesPerGoalPerDay(2))
              )
          )
      )
    val (a, h) = handlers(ScriptedClient(Model), capped); seed(h)
    import io.circe.syntax.*
    def plan(slugs: Article.Slug*) = Article.Plans.ArticlePlan(
      GoalPlanGoal,
      Article.Plans.ArticlePlan.Rationale("r"),
      slugs.toList.map(s =>
        Article.Plans.ProposedArticle(
          s,
          Article.Title(s"Zz $s"),
          Article.Kind.Wire("concept"),
          Article.Filter(topics = List(Filter.Topics("zzagent_tin")))
        )
      )
    )
    val p1 =
      TestRuns
        .approvals(ds)
        .openSubject(
          None,
          ApprovalKind.ArticlePlan,
          GoalQuerySubject,
          ApprovalService.Payload(plan(Article.Slug("zzagent_p1"), Article.Slug("zzagent_p2")).asJson.noSpaces)
        )
    val p2 =
      TestRuns
        .approvals(ds)
        .openSubject(
          None,
          ApprovalKind.ArticlePlan,
          GoalQuerySubject,
          ApprovalService.Payload(plan(Article.Slug("zzagent_p3")).asJson.noSpaces)
        )
    val now = Instant.now(suiteClock)
    a.autoApprovals.sweep(now)
    assertEquals(TestRuns.approvals(ds).approval(ApiIds.approval(p1.id)).map(_.state), Some("approved"))
    assertEquals(
      TestRuns.approvals(ds).approval(ApiIds.approval(p2.id)).map(_.state),
      Some("open"),
      "2 of 2 articles approved today: the third waits"
    )
    a.autoApprovals.judge(TestRuns.approvals(ds).approval(ApiIds.approval(p2.id)).get, None, None, now) match
      case AutopilotPolicy.Verdict.Leave(w) => assert(w.value.contains("daily cap"), w)
      case v                                => fail(v.toString)
    // a thin filter is rejected with why
    val p3 = TestRuns
      .approvals(ds)
      .openSubject(
        None,
        ApprovalKind.ArticlePlan,
        ApprovalQuery.Subject("zzagent-other"),
        ApprovalService.Payload(
          Article.Plans
            .ArticlePlan(
              Article.Plans.ArticlePlan.Goal("zzagent-other"),
              Article.Plans.ArticlePlan.Rationale("r"),
              List(
                Article.Plans
                  .ProposedArticle(
                    Article.Slug("zzagent_p4"),
                    Article.Title("Zz"),
                    Article.Kind.Wire("concept"),
                    Article.Filter(query = Some(Filter.Q("zzagent nothing at all")))
                  )
              )
            )
            .asJson
            .noSpaces
        )
      )
    a.autoApprovals.sweep(now)
    val d3 = TestRuns.approvals(ds).approval(ApiIds.approval(p3.id)).get
    assertEquals(d3.state, "rejected")
    assert(d3.note.exists(_.value.contains("returns 0 source(s), fewer than 2")), d3.note)
    // the manager whose run opened it says autoApprove = false: never decided by code
    val m = ManagerInfo(
      ManagerInfo.Id("seed:x"),
      ManagerInfo.Kind("seed"),
      config = ManagerConfig(autoApprove = Some(ManagerConfig.AutoApprove(false)))
    )
    assert(!a.autoApprovals.checked(TestRuns.approvals(ds).approval(ApiIds.approval(p2.id)).get, Some(m)))

  test("older approvals a person decides never crowd the code-decided ones out of a tick"):
    val personPlans =
      autoCfg
        .modify(_.agents)
        .setTo(
          autoCfg.agents
            .modify(_.approvals)
            .setTo(autoCfg.agents.approvals.modify(_.goalPlan).setTo(AgentApprovalsConfig.Gate("manual")))
        )
    val (a, h) = handlers(ScriptedClient(Model), personPlans); seed(h)
    import io.circe.syntax.*
    val waiting =
      (1 to 60).map(i =>
        TestRuns
          .approvals(ds)
          .openSubject(
            None,
            ApprovalKind.GoalPlan,
            ApprovalQuery.Subject(s"zzagent-crowd-$i"),
            ApprovalService.Payload("{}")
          )
      )
    val plan = TestRuns
      .approvals(ds)
      .openSubject(
        None,
        ApprovalKind.ArticlePlan,
        GoalQuerySubject,
        ApprovalService.Payload(
          Article.Plans
            .ArticlePlan(
              GoalPlanGoal,
              Article.Plans.ArticlePlan.Rationale("r"),
              List(
                Article.Plans
                  .ProposedArticle(
                    Article.Slug("zzagent_crowded"),
                    Article.Title("Zz crowded"),
                    Article.Kind.Wire("concept"),
                    Article.Filter(topics = List(Filter.Topics("zzagent_tin")))
                  )
              )
            )
            .asJson
            .noSpaces
        )
      )
    a.autoApprovals.sweep(Instant.now(suiteClock))
    assertEquals(TestRuns.approvals(ds).approval(ApiIds.approval(plan.id)).map(_.state), Some("approved"))
    assert(waiting.forall(w => TestRuns.approvals(ds).approval(ApiIds.approval(w.id)).exists(_.state.value == "open")))

  test("shared ingest cap: every watcher's ingests today count; at the cap a sweep ingests nothing"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    a.managing.setAutopilot(AutopilotSet(on = AutopilotSet.On(true))).fold(e => fail(e.message.value), identity)
    transact(ds)(
      ManagerRepo.event(watchId(ref(Goal)), None, None, IngestedKind, ManagerRepo.EventText("[[zzagent_a]] ingested"))
    )
    val now = Instant.now(suiteClock)
    // the goal's plan-goal run starts beside it: a sweep does not wait for it
    Clock(now).tick(a, 0)
    assert(item(seedId(ref(Goal)), PlanGoalKind, itemTarget(Goal)).runId.isDefined)
    assertEquals(
      runOf(watchId(ref(Goal)), SweepKind, itemTarget(Goal)).params.autoIngest,
      Some(1),
      "min(own 5 − 1, shared 2 − 1)"
    )
    assertEquals(a.managing.autopilot.ingestedToday, 1)
    transact(ds)(
      ManagerRepo.event(watchId(ref(Goal)), None, None, IngestedKind, ManagerRepo.EventText("[[zzagent_b]] ingested"))
    )
    assertEquals(a.autopilot.sharedIngestLeft(now), Some(0))

  // ── new sources reach the article ──────────────────────────────────────────────────────────────

  test("extend: an article whose filter returns uncited, unreasoned sources gets an article-extend item, then rests"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(
      h.research.articles
        .put(
          Slug("zzagent_x"),
          Article.Put(
            Article.Title("Zz tin"),
            Article.Kind.Wire("concept"),
            Article.Body(s"Tin [[$Ck]]."),
            tags = List(Article.Put.Tags("zzx")),
            filter = Some(Article.Filter(topics = List(Filter.Topics("zzagent_tin"))))
          )
        )
        .isRight
    )
    assertEquals(h.research.articles.newFilterSources(Slug("zzagent_x")), Some(1))
    a.managers.ensureDefaults()
    // no selection (refill batch 0): only the extension
    a.managing.update(
      ApiIds.manager(Review),
      ManagerUpdate(
        Some(
          ManagerConfig(
            extendMinNew = Some(ManagerConfig.ExtendMinNew(1)),
            refillBatch = Some(ManagerConfig.RefillBatch(0)),
            includeTags = Some(List(ManagerConfig.IncludeTags("zzx")))
          )
        )
      )
    )
    a.managing.action(ApiIds.manager(Review), ManagersControllers.Action("resume"), None)
    val out = a.managers.tick(t0)
    assert(out.exists(_.value.contains("1 to extend")), out)
    val x = item(Review, ExtendKind, ManagerItem.Target("zzagent_x"))
    assertEquals(x.origin, "selection")
    assert(
      connect(ds)(ManagerRepo.events(Review, None, PageSize(20)))
        .exists(_.text.value.contains("1 source it neither cites nor gives a reason for"))
    )
    // settled: within the cooldown it is not extended again
    connect(ds)(sql"UPDATE manager_items SET status = 'done', run_id = NULL WHERE id = ${x.id}".update.run())
    connect(ds)(sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id = 'zzagent_x'".update.run())
    a.managers.tick(t0.plusSeconds(2 * 3600))
    assertEquals(item(Review, ExtendKind, ManagerItem.Target("zzagent_x")).status, "done")
    // a reason for the source: nothing new
    assert(
      h.research.articles
        .patch(
          Slug("zzagent_x"),
          Article
            .Patch(reasons = List(Coverage.Reason(ApiCk2, Problem("a later study, cited elsewhere"))))
        )
        .isRight
    )
    assertEquals(h.research.articles.newFilterSources(Slug("zzagent_x")), Some(0))

  // ── housekeeping ───────────────────────────────────────────────────────────────────────────────

  test("housekeeping: the safe lint repairs run on their cadence, recorded and logged; off, nothing runs"):
    val hk = autoCfg
      .modify(_.agents)
      .setTo(
        autoCfg.agents
          .modify(_.autopilot)
          .setTo(autoCfg.agents.autopilot.modify(_.lintHours).setTo(AgentAutopilotConfig.LintHours(24)))
      )
    val (a, _) = handlers(ScriptedClient(Model), hk)
    assertEquals(a.autopilot.tick(t0), Nil, "off")
    a.managing.setAutopilot(AutopilotSet(on = AutopilotSet.On(true))).fold(e => fail(e.message.value), identity)
    val out = a.autopilot.tick(t0)
    assert(out.contains("lint --fix started"), out)
    assert(transact(ds)(AutopilotRepo.get()).lastLintAt.isDefined)
    val deadline = System.currentTimeMillis() + 60000
    while !QueueRows.list(ds, Queue.Kind.Log, all = true).exists(_.text.value.startsWith("autopilot: lint --fix")) &&
      System.currentTimeMillis() < deadline
    do Thread.sleep(200)
    assert(QueueRows.list(ds, Queue.Kind.Log, all = true).exists(_.text.value.startsWith("autopilot: lint --fix")))
    assert(!a.autopilot.tick(t0.plusSeconds(3600)).exists(_.contains("lint")), "not due again within the day")
