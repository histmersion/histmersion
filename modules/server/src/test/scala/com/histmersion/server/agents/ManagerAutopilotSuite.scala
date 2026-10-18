package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.Autopilot.AutopilotSet
import com.histmersion.core.GoalPlans.LinkKind
import com.histmersion.core.Managers.*
import com.histmersion.core.ReviewLedger.{Ledger, absorb}
import com.histmersion.db.{AgentRepo, AutopilotRepo, GoalLinkRepo, GoalRepo, ManagerRepo, Migrate, QueueRepo, ReviewLedgerRepo}
import com.histmersion.server.{Deps, Handlers}
import io.circe.Json
import sttp.ai.core.agent.*

import java.time.Instant

/** The autopilot against a `*_test` database (docs/research-process-managers.md "As built: the
  * autopilot"): one switch carries a planned goal through plan → sources → articles → review to
  * `reviewed` with the checked auto approvals on — and with them off nothing is approved by itself; the
  * sub-goals an approved plan creates get their managers (no sub-goals at the depth limit); off pauses
  * what it runs and on resumes exactly that; the approval caps; new filter sources extend an article;
  * the ingests all watchers share; the housekeeping. Models are scripted ([[ScriptedClient]]), a review
  * run is settled by hand; no provider is called. Skipped without HISTMERSION_TEST_DB_URL. */
class ManagerAutopilotSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout         = scala.concurrent.duration.Duration(180, "s")

  private val Goal  = "zzagent-apgoal"
  private val Ck    = "zzagent2019_tin"
  private val Ck2   = "zzagent2024_iso"
  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))
  private val t0 = Instant.parse("2026-10-19T12:00:00Z")

  /** Checked auto approvals ON (article-plan, goal-plan, article-publish = checked), the autopilot's
    * housekeeping off, room for every manager's run. */
  private lazy val autoCfg =
    val b = AppConfig.load()
    val m = Model.wire
    b.copy(db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(enabled = true, prices = "scripted=1/2", maxConcurrentRuns = 20,
        roles = b.agents.roles.copy(extractor = m, reviewer = m, coldReader = m, skeptic = m, fixer = m, planner = m, writer = m),
        seed = b.agents.seed.copy(minSources = 2),
        approvals = b.agents.approvals.copy(articlePublish = "checked", articlePlan = "auto", goalPlan = "auto",
          auto = b.agents.approvals.auto.copy(publishMinSources = 2)),
        autopilot = b.agents.autopilot.copy(backupHours = 0, lintHours = 0, ingestPerDay = 2),
        review = b.agents.review.copy(spotChecks = 0)))

  /** The same with every approval a person's (the shipped defaults). */
  private lazy val manualCfg =
    autoCfg.copy(agents = autoCfg.agents.copy(approvals = autoCfg.agents.approvals.copy(articlePublish = "manual",
      articlePlan = "manual", goalPlan = "manual")))

  private lazy val ds = testPool(autoCfg.db)

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private def str(s: String) = Json.fromString(s).noSpaces

  private def handlers(c: LlmClient, cfg: AppConfig = autoCfg): (AgentsHandlers, Handlers) =
    val deps = Deps(cfg, ds)
    val h    = Handlers(deps)
    (AgentsHandlers(deps, h, (_, _) => Right(c)), h)

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
  override def afterEach(context: AfterEach): Unit   = clean()

  private def seed(h: Handlers): Unit =
    assert(h.putSource(Ck, SourcePut(title = "Bronze Age tin", year = Some(2019), topics = List("zzagent_tin"),
      status = Some(SourceStatus.Current),
      summary = Some("The Uluburun ship carried ten tons of copper and one ton of tin."),
      body = Some("The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."))).isRight)
    assert(h.putSource(Ck2, SourcePut(title = "Tin isotopes", year = Some(2024), topics = List("zzagent_tin"),
      body = Some("We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia."))).isRight)
    assert(h.patchSource(Ck2, SourcePatch(metadata = Some(MetadataPatch(status = Some(SourceStatus.Contested))))).isRight)
    assert(h.putGoal(Goal, GoalPut("Zz tin in the Bronze Age", notes = Some("where the tin came from"))).isRight)

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
    s"""{"relevant":true,"reason":"bears on tin","claims":[{"claim":${str(claim)},"quote":${str(quote)},"date":null,"place":null,"confidence":"high"}]}"""

  private def draftJson(body: String) =
    s"""{"title":"Zz new","summary":"Where Aegean tin came from.","body":${str(body)},"section":null,"reasons":[]}"""

  private def item(mid: String, kind: String, target: String): ManagerItem = ManagerRepo.itemFor(ds, mid, kind, target).get
  private def runOf(mid: String, kind: String, target: String): RunInfo =
    AgentRepo.get(ds, item(mid, kind, target).runId.getOrElse(fail(s"$mid $kind $target has no run: ${item(mid, kind, target)}"))).get

  private def why(r: RunInfo): String =
    (r.error.toList ++ AgentRepo.steps(ds, r.id, 200).filter(_.error.isDefined).map(s => s"${s.name}#${s.attempt}: ${s.error.get}"))
      .mkString("\n")

  private def exec(a: AgentsHandlers, id: String): RunInfo =
    a.engine.execute(id)
    AgentRepo.get(ds, id).get

  /** The watchers' sweeps need no model (offline: nothing found): run what they queued. */
  private def drainWatchers(a: AgentsHandlers): Unit =
    AgentRepo.list(ds, Some("queued"), 200).filter(_.managerId.exists(_.startsWith("watch:"))).foreach(r => a.engine.execute(r.id))

  private def status(g: String): String = GoalRepo.get(ds, g).get.status

  /** A clock from the wall clock (the switch records real times) that moves six minutes a tick — past the
    * autopilot's ensure interval every tick. */
  private final class Clock(var now: Instant = Instant.now()): // a var: the test's own moving time
    def tick(a: AgentsHandlers, minutes: Int = 6): List[String] =
      now = now.plusSeconds(minutes.toLong * 60L)
      a.managers.tick(now)

  // ── end to end ─────────────────────────────────────────────────────────────────────────────────

  test("autopilot, checked approvals: a planned goal is planned, seeded, compiled and reviewed with no person"):
    val c = ScriptedClient(Model)
    val (a, h) = handlers(c); seed(h)
    a.managers.ensureDefaults()
    val on = a.managing.setAutopilot(AutopilotSet(on = true, Some("test"))).fold(e => fail(e.message), identity)
    assert(on.on)
    assert(on.changes.contains(s"${seedId(Goal)} created") && on.changes.contains(s"${watchId(Goal)} created") &&
      on.changes.contains(s"$ReviewId resumed") && on.changes.contains(s"$EnrichId resumed"), on.changes)
    assertEquals(status(Goal), GoalStatus.Seeding, "managed: seeding")
    val seedM = ManagerRepo.get(ds, seedId(Goal)).get
    assertEquals((seedM.state, seedM.cron), ("active", Some(AutopilotPolicy.stagger("@weekly", seedId(Goal)))))
    assertEquals(ManagerRepo.get(ds, watchId(Goal)).map(m => (m.state, m.config.autoIngest)), Some(("active", Some(true))))
    assertEquals(item(seedId(Goal), PlanGoalItem, Goal).priority, 1)
    assert(on.goals.exists(g => g.slug == Goal && g.managers.size == 2), on.goals)
    // again: nothing more to do
    assertEquals(a.managing.setAutopilot(AutopilotSet(on = true)).toOption.get.changes, List("already on"))

    // 1. plan-goal first (the proposal waits for it); its plan is approved by the checks, the run applies it
    c.scripts += Seq(text(planJson))
    val clock = Clock()
    clock.tick(a)
    assertEquals(item(seedId(Goal), ProposeArticles, Goal).status, "pending", "waits for the plan")
    val plan = runOf(seedId(Goal), PlanGoalItem, Goal)
    assertEquals((plan.pipeline, plan.params.subGoals), ("plan-goal", None), "a root goal may be split")
    drainWatchers(a)
    assertEquals(exec(a, plan.id).status, "waiting", why(AgentRepo.get(ds, plan.id).get))
    clock.tick(a)
    val gp = a.approvalsList(Some("all"), None).toOption.get.find(a => a.kind == "goal-plan" && a.subject.contains(Goal)).get
    assertEquals(gp.state, "approved")
    assert(gp.note.exists(_.startsWith("autopilot: approved — 0 sub-goals, 1 query")), gp.note)
    assertEquals(exec(a, plan.id).status, "done", why(AgentRepo.get(ds, plan.id).get))
    assertEquals(GoalLinkRepo.refs(ds, Goal, LinkKind.Query), List("Bronze Age tin isotopes"))
    assert(ManagerRepo.events(ds, seedId(Goal), None, 100).exists(e => e.kind == Feed.Approval && e.text.contains("approved by the autopilot")))
    assert(QueueRepo.list(ds, Queue.Kind.Log, all = true).exists(_.text.startsWith("autopilot: goal-plan")))

    // 2. propose-articles: the article plan is approved by the checks and becomes a compile item
    c.scripts += Seq(text(articlesJson))
    clock.tick(a)
    assertEquals(item(seedId(Goal), PlanGoalItem, Goal).status, "done")
    val prop = runOf(seedId(Goal), ProposeArticles, Goal)
    assertEquals(exec(a, prop.id).status, "done", why(prop))
    c.scripts ++= List(
      Seq(text(extract("The Uluburun ship carried ten tons of copper and one ton of tin.", "The ship carried tin."))),
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(text(draftJson(articleBody))))
    clock.tick(a)
    val ap = a.approvalsList(Some("all"), None).toOption.get.find(a => a.kind == "article-plan" && a.subject.contains(Goal)).get
    assertEquals(ap.state, "approved")
    assert(ap.note.exists(_.contains("every slug free")), ap.note)
    assertEquals(item(seedId(Goal), ProposeArticles, Goal).status, "done")

    // 3. the compile: its draft passes the checks and is published; the review follows as an item
    val comp = runOf(seedId(Goal), ArticleCompile, "zzagent_new")
    assertEquals(comp.params.chainReview, Some(false))
    assertEquals(exec(a, comp.id).status, "waiting", why(AgentRepo.get(ds, comp.id).get))
    clock.tick(a)
    val pub = a.approvalsList(Some("all"), None).toOption.get.find(a => a.kind == "article-publish" && a.subject.contains("zzagent_new")).get
    assertEquals(pub.state, "approved")
    assert(pub.note.exists(_.startsWith("autopilot: approved — filter 2 sources")), pub.note)
    assertEquals(exec(a, comp.id).status, "done", why(AgentRepo.get(ds, comp.id).get))
    assert(h.getArticle("zzagent_new").isRight)
    clock.tick(a)
    assertEquals(item(seedId(Goal), ArticleCompile, "zzagent_new").status, "done")
    clock.tick(a)
    assertEquals(status(Goal), GoalStatus.Compiled, "an article, nothing left to compile")

    // 4. the review (settled by hand: it converged) → the goal is reviewed
    val rev = runOf(ReviewId, ArticleReview, "zzagent_new")
    assert(AgentRepo.transition(ds, rev.id, RunState.Queued, RunState.Running).isDefined)
    ReviewLedgerRepo.put(ds, absorb(Ledger("zzagent_new"), Nil, rev.id, 1)._1)
    assert(AgentRepo.transition(ds, rev.id, RunState.Running, RunState.Done).isDefined)
    clock.tick(a)
    assertEquals(item(ReviewId, ArticleReview, "zzagent_new").status, "done")
    clock.tick(a)
    assertEquals(status(Goal), GoalStatus.Reviewed)
    assert(ManagerRepo.events(ds, seedId(Goal), None, 200).exists(e => e.kind == Feed.Autopilot && e.text.contains("compiled → reviewed")))
    assert(QueueRepo.list(ds, Queue.Kind.Log, all = true).exists(_.text.contains(s"goal $Goal: compiled → reviewed")))
    val st = a.managing.autopilot
    val g = st.goals.find(_.slug == Goal).get
    assertEquals((g.status, g.articles, g.openApprovals), (GoalStatus.Reviewed, 1, 0))
    assert(g.doneLast7 >= 3, g)
    assert(AutopilotMarkdown.status(st).contains(s"**$Goal** _(reviewed)_"))

  test("autopilot, approvals a person's (the defaults): managers and plans start, nothing is approved by itself"):
    val c = ScriptedClient(Model)
    val (a, h) = handlers(c, manualCfg); seed(h)
    a.managers.ensureDefaults()
    a.managing.setAutopilot(AutopilotSet(on = true)).fold(e => fail(e.message), identity)
    c.scripts += Seq(text(planJson))
    val clock = Clock()
    clock.tick(a)
    val plan = runOf(seedId(Goal), PlanGoalItem, Goal)
    drainWatchers(a)
    assertEquals(exec(a, plan.id).status, "waiting")
    (1 to 3).foreach(_ => clock.tick(a))
    val open = a.approvalsList(None, None).toOption.get.filter(_.subject.contains(Goal))
    assertEquals(open.map(a => (a.kind, a.state)), List(("goal-plan", "open")))
    assertEquals(AgentRepo.get(ds, plan.id).get.status, "waiting")
    assertEquals(item(seedId(Goal), ProposeArticles, Goal).status, "pending", "the proposal waits for the plan")
    assert(!a.approvalsList(Some("all"), None).toOption.get.exists(_.note.exists(_.startsWith("autopilot:"))))
    assertEquals(a.managing.autopilot.goals.find(_.slug == Goal).map(_.openApprovals), Some(1))
    assert(a.managing.autopilot.approvals.forall(_.mode == "person"), a.managing.autopilot.approvals)
    // a manager's own autoApprove wins over the defaults
    val sm = ManagerRepo.get(ds, seedId(Goal)).get
    ManagerRepo.setConfig(ds, sm.id, sm.config.copy(autoApprove = Some(true)), sm.cron, sm.nextTickAt.flatMap(ManagerPolicy.parseInstant))
    clock.tick(a)
    assertEquals(a.approvalsList(Some("all"), None).toOption.get.find(_.kind == "goal-plan").map(_.state), Some("approved"))

  // ── sub-goals, off and on ──────────────────────────────────────────────────────────────────────

  test("sub-goals get their managers; no sub-goals at the depth limit; off pauses what it runs, on resumes exactly that"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(h.putGoal("zzagent-apchild", GoalPut("Zz child", parentSlug = Some(Goal))).isRight)
    assert(h.putGoal("zzagent-apgrand", GoalPut("Zz grandchild", parentSlug = Some("zzagent-apchild"))).isRight)
    a.managers.ensureDefaults()
    a.managing.setAutopilot(AutopilotSet(on = true)).fold(e => fail(e.message), identity)
    List(Goal, "zzagent-apchild", "zzagent-apgrand").foreach { g =>
      assertEquals(ManagerRepo.get(ds, seedId(g)).map(_.state), Some("active"), g)
      assertEquals(ManagerRepo.get(ds, watchId(g)).map(_.state), Some("active"), g)
      assertEquals(status(g), GoalStatus.Seeding, g)
    }
    Clock().tick(a)
    assertEquals(runOf(seedId("zzagent-apchild"), PlanGoalItem, "zzagent-apchild").params.subGoals, None, "depth 1: may split")
    assertEquals(runOf(seedId("zzagent-apgrand"), PlanGoalItem, "zzagent-apgrand").params.subGoals, Some(0), "depth 2: no sub-goals")
    // a person pauses one watcher; off pauses the others it runs, on resumes those only
    a.managing.action(watchId("zzagent-apchild"), "pause", None).fold(e => fail(e.message), identity)
    val off = a.managing.setAutopilot(AutopilotSet(on = false)).fold(e => fail(e.message), identity)
    assert(!off.on)
    assert(off.changes.exists(_.contains("paused 5 managers")), off.changes)
    assert(ManagerRepo.list(ds).filter(m => m.kind == "seed" || m.kind == "watch").forall(_.state == "paused"))
    assertEquals(ManagerRepo.get(ds, ReviewId).map(_.state), Some("active"), "the library-wide ones stay")
    assertEquals(Clock().tick(a).filter(_.startsWith("goal")), Nil, "off: nothing ensured")
    a.managing.setAutopilot(AutopilotSet(on = true)).fold(e => fail(e.message), identity)
    assertEquals(ManagerRepo.get(ds, watchId("zzagent-apchild")).map(_.state), Some("paused"), "the person's pause holds")
    assertEquals(ManagerRepo.list(ds).count(m => (m.kind == "seed" || m.kind == "watch") && m.state == "active"), 5)
    assertEquals(AutopilotRepo.get(ds).paused, Nil)
    // a goal added while it is on gets its managers at the next ensure
    assert(h.putGoal("zzagent-aplate", GoalPut("Zz later")).isRight)
    Clock(Instant.now().plusSeconds(3600)).tick(a)
    assertEquals(ManagerRepo.get(ds, seedId("zzagent-aplate")).map(_.state), Some("active"))

  test("a reviewed goal gets a watcher on the slow cron and no seeding manager"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(h.patchGoal(Goal, GoalPatch(status = Some(GoalStatus.Reviewed))).isRight)
    a.managing.setAutopilot(AutopilotSet(on = true)).fold(e => fail(e.message), identity)
    assertEquals(ManagerRepo.get(ds, seedId(Goal)), None)
    assertEquals(ManagerRepo.get(ds, watchId(Goal)).flatMap(_.cron), Some(AutopilotPolicy.stagger("@monthly", watchId(Goal))))

  // ── caps ───────────────────────────────────────────────────────────────────────────────────────

  test("article-plan cap: the area's daily cap leaves a second plan for a person; a manager that says no is never decided"):
    val capped = autoCfg.copy(agents = autoCfg.agents.copy(approvals = autoCfg.agents.approvals.copy(
      auto = autoCfg.agents.approvals.auto.copy(articlesPerGoalPerDay = 2))))
    val (a, h) = handlers(ScriptedClient(Model), capped); seed(h)
    import io.circe.syntax.*
    def plan(slugs: String*) = ArticlePlans.ArticlePlan(Goal, "r", slugs.toList.map(s =>
      ArticlePlans.ProposedArticle(s, s"Zz $s", "concept", ArticleFilter(topics = List("zzagent_tin")))))
    val p1 = AgentRepo.openSubject(ds, None, ApprovalKind.ArticlePlan, Goal, plan("zzagent_p1", "zzagent_p2").asJson.noSpaces)
    val p2 = AgentRepo.openSubject(ds, None, ApprovalKind.ArticlePlan, Goal, plan("zzagent_p3").asJson.noSpaces)
    val now = Instant.now()
    a.autoApprovals.sweep(now)
    assertEquals(AgentRepo.approval(ds, p1.id).map(_.state), Some("approved"))
    assertEquals(AgentRepo.approval(ds, p2.id).map(_.state), Some("open"), "2 of 2 articles approved today: the third waits")
    a.autoApprovals.judge(AgentRepo.approval(ds, p2.id).get, None, None, now) match
      case AutopilotPolicy.Verdict.Leave(w) => assert(w.contains("daily cap"), w)
      case v => fail(v.toString)
    // a thin filter is rejected with why
    val p3 = AgentRepo.openSubject(ds, None, ApprovalKind.ArticlePlan, "zzagent-other", ArticlePlans.ArticlePlan("zzagent-other", "r",
      List(ArticlePlans.ProposedArticle("zzagent_p4", "Zz", "concept", ArticleFilter(query = Some("zzagent nothing at all"))))).asJson.noSpaces)
    a.autoApprovals.sweep(now)
    val d3 = AgentRepo.approval(ds, p3.id).get
    assertEquals(d3.state, "rejected")
    assert(d3.note.exists(_.contains("returns 0 source(s), fewer than 2")), d3.note)
    // the manager whose run opened it says autoApprove = false: never decided by code
    val m = ManagerInfo("seed:x", "seed", config = ManagerConfig(autoApprove = Some(false)))
    assert(!a.autoApprovals.checked(AgentRepo.approval(ds, p2.id).get, Some(m)))

  test("shared ingest cap: every watcher's ingests today count; at the cap a sweep ingests nothing"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    a.managing.setAutopilot(AutopilotSet(on = true)).fold(e => fail(e.message), identity)
    ManagerRepo.event(ds, watchId(Goal), None, None, Feed.Ingested, "[[zzagent_a]] ingested")
    val now = Instant.now()
    // the goal's plan-goal run starts beside it: a sweep does not wait for it
    Clock(now).tick(a, 0)
    assert(item(seedId(Goal), PlanGoalItem, Goal).runId.isDefined)
    assertEquals(runOf(watchId(Goal), AcquisitionSweep, Goal).params.autoIngest, Some(1), "min(own 5 − 1, shared 2 − 1)")
    assertEquals(a.managing.autopilot.ingestedToday, 1)
    ManagerRepo.event(ds, watchId(Goal), None, None, Feed.Ingested, "[[zzagent_b]] ingested")
    assertEquals(a.autopilot.sharedIngestLeft(now), Some(0))

  // ── new sources reach the article ──────────────────────────────────────────────────────────────

  test("extend: an article whose filter returns uncited, unreasoned sources gets an article-extend item, then rests"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(h.putArticle("zzagent_x", ArticlePut("Zz tin", "concept", s"Tin [[$Ck]].", tags = List("zzx"),
      filter = Some(ArticleFilter(topics = List("zzagent_tin"))))).isRight)
    assertEquals(h.newFilterSources("zzagent_x"), Some(1))
    a.managers.ensureDefaults()
    // no selection (refill batch 0): only the extension
    a.managing.update(ReviewId, ManagerUpdate(Some(ManagerConfig(extendMinNew = Some(1), refillBatch = Some(0),
      includeTags = Some(List("zzx"))))))
    a.managing.action(ReviewId, "resume", None)
    val out = a.managers.tick(t0)
    assert(out.exists(_.contains("1 to extend")), out)
    val x = item(ReviewId, ArticleExtend, "zzagent_x")
    assertEquals(x.origin, "selection")
    assert(ManagerRepo.events(ds, ReviewId, None, 20).exists(_.text.contains("1 source it neither cites nor gives a reason for")))
    // settled: within the cooldown it is not extended again
    connect(ds)(sql"UPDATE manager_items SET status = 'done', run_id = NULL WHERE id = ${x.id}".update.run())
    connect(ds)(sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id = 'zzagent_x'".update.run())
    a.managers.tick(t0.plusSeconds(2 * 3600))
    assertEquals(item(ReviewId, ArticleExtend, "zzagent_x").status, "done")
    // a reason for the source: nothing new
    assert(h.patchArticle("zzagent_x", ArticlePatch(reasons = List(CoverageReason(Ck2, "a later study, cited elsewhere")))).isRight)
    assertEquals(h.newFilterSources("zzagent_x"), Some(0))

  // ── housekeeping ───────────────────────────────────────────────────────────────────────────────

  test("housekeeping: the safe lint repairs run on their cadence, recorded and logged; off, nothing runs"):
    val hk = autoCfg.copy(agents = autoCfg.agents.copy(autopilot = autoCfg.agents.autopilot.copy(lintHours = 24)))
    val (a, _) = handlers(ScriptedClient(Model), hk)
    assertEquals(a.autopilot.tick(t0), Nil, "off")
    a.managing.setAutopilot(AutopilotSet(on = true)).fold(e => fail(e.message), identity)
    val out = a.autopilot.tick(t0)
    assert(out.contains("lint --fix started"), out)
    assert(AutopilotRepo.get(ds).lastLintAt.isDefined)
    val deadline = System.currentTimeMillis() + 60000
    while !QueueRepo.list(ds, Queue.Kind.Log, all = true).exists(_.text.startsWith("autopilot: lint --fix")) &&
        System.currentTimeMillis() < deadline do Thread.sleep(200)
    assert(QueueRepo.list(ds, Queue.Kind.Log, all = true).exists(_.text.startsWith("autopilot: lint --fix")))
    assert(!a.autopilot.tick(t0.plusSeconds(3600)).exists(_.contains("lint")), "not due again within the day")
