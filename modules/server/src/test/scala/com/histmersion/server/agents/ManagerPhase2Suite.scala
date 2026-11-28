package com.histmersion
package server.agents
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ScriptedClient
import com.histmersion.domain.automation.agents.TestRuns

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.automation.agents.{AgentsControllers, LlmClient}
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.research.workflows.ReviewManagerPolicy
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.domain.automation.goals.GoalLinkRepo
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.server.{Deps, ServerWiring}
import sttp.ai.core.agent.*

import java.time.Instant
import com.histmersion.db.*
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.Goal as DomainGoal
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.MetadataPatch
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.config.DbConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentRolesConfig
import com.histmersion.config.AgentApprovalsConfig
import com.histmersion.config.AgentSeedConfig
import com.histmersion.config.AgentInferFilterConfig
import com.histmersion.config.AgentReviewConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.managers.ManagersControllers
import com.histmersion.domain.automation.Approval
import com.histmersion.api.library.Filter
import com.histmersion.config.AgentManagersConfig
import com.histmersion.api.automation.Managers
import com.histmersion.api.Api
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The process managers' phase 2 against a `*_test` database (docs/research-process-managers.md §4, §6): infer-filter
  * before a review (the filter checked against the corpus, held for its approval, the review waiting for it), the
  * seeding manager (propose-articles → the always-manual article-plan approval → compile items with chainReview off → a
  * chained review item and the goal link), the enrich manager absorbing auto-enrich, stale selection, and the enqueue
  * routing of the page buttons. Models are scripted ([[ScriptedClient]]); no provider is called. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ManagerPhase2Suite extends DbSuite with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val PutTag = Article.Put.Tags("zzmgr2")
  private val IncludeTag = ManagerConfig.IncludeTags("zzmgr2")
  private val Goal = DomainGoal.Slug("zzagent-goal2")
  private val GoalSlug = api.automation.Goal.Slug("zzagent-goal2")
  private val GoalSubject = ApprovalInfo.Subject("zzagent-goal2")
  private val Ck = Citekey("zzagent2019_tin")
  private val Ck2 = Citekey("zzagent2024_iso")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val Review = ManagerInfo.Id(ReviewId)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val Enrich = ManagerInfo.Id(EnrichId)
  private val InferKind = ManagerItem.Kind(InferFilter)
  private val ReviewKind = ManagerItem.Kind(ArticleReview)
  private val ProposeKind = ManagerItem.Kind(ProposeArticles)
  private val CompileKind = ManagerItem.Kind(ArticleCompile)
  private val EnrichKind = ManagerItem.Kind(SourceEnrich)
  private val ExtendKind = ManagerItem.Kind(ArticleExtend)
  private val Model = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("scripted"))
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))
  private val t0 = Instant.parse("2026-10-17T12:00:00Z")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val baseCfg =
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
          .setTo(b.agents.approvals.modify(_.articlePublish).setTo(AgentApprovalsConfig.Gate("auto")))
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
          .modify(_.inferFilter)
          .setTo(b.researchAgents.inferFilter.modify(_.minHits).setTo(AgentInferFilterConfig.MinHits(1)))
          .modify(_.review)
          .setTo(b.researchAgents.review.modify(_.spotChecks).setTo(AgentReviewConfig.SpotChecks(0)))
      )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private def str(s: String) = Json.fromString(s).noSpaces

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsControllers, ServerWiring) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    (AgentsControllers(ServerEngineSite(deps, h, Some(TestRuns.noonClock())), (_, _) => Right(c)), h)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers".update.run()
      // a manager's runs, whatever their target (a selection that was not narrowed reaches other suites' rows)
      sql"DELETE FROM jobs WHERE kind = 'agent' AND manager_id IS NOT NULL".update.run()
      sql"DELETE FROM approvals WHERE subject LIKE 'zzagent%' OR citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM article_versions WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM review_ledgers WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(h: ServerWiring): Unit =
    assert(
      h.library.sources
        .put(
          Ck,
          Source.Put(
            title = api.library.Title("Bronze Age tin"),
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
            title = api.library.Title("Tin isotopes"),
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
          api.automation.Goal.Put(
            api.automation.Goal.Title("Zz tin in the Bronze Age"),
            notes = Some(api.automation.Goal.Notes("where the tin came from"))
          )
        )
        .isRight
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def ref(g: DomainGoal.Slug) = api.automation.Goal.Ref(g.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def goalTarget(g: DomainGoal.Slug) = ManagerItem.Target(g.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def ckTarget(c: Citekey) = ManagerItem.Target(c.value)

  private def item(mid: ManagerInfo.Id, kind: ManagerItem.Kind, target: ManagerItem.Target): ManagerItem =
    connect(ds)(ManagerRepo.itemFor(mid, kind, target)).get

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def why(r: RunInfo): String =
    (r.error.toList ++ connect(ds)(AgentRepo.steps(ApiIds.job(r.id), AgentRepo.OutputChars(200)))
      .filter(_.error.isDefined)
      .map(s => s"${s.name}#${s.attempt}: ${s.error.get.sentence}"))
      .mkString("\n")

  // ── infer-filter before the review ─────────────────────────────────────────────────────────────

  test(
    "infer-filter: an unfiltered article's review waits for it; the filter is checked, approved, recorded; then the review runs"
  ):
    val c = ScriptedClient(Model)
    // the first answer names a topic nobody has (0 sources): code sends it back; the second passes
    c.scripts ++= List(
      Seq(text("""{"rationale":"tin","query":null,"topics":["zzagent_nope"],"tags":[],"extraQueries":[]}""")),
      Seq(text("""{"rationale":"the tin sources","query":null,"topics":["zzagent_tin"],"tags":[],"extraQueries":[]}"""))
    )
    val (a, h) = handlers(c); seed(h)
    assert(
      h.research.articles
        .put(
          Slug("zzagent_f1"),
          Article
            .Put(Article.Title("Zz tin"), Article.Kind.Wire("concept"), Article.Body(articleBody), tags = List(PutTag))
        )
        .isRight
    )
    a.managers.ensureDefaults()
    a.managing
      .update(
        ApiIds.manager(Review),
        ManagerUpdate(
          Some(
            ManagerConfig(
              includeTags = Some(List(IncludeTag)),
              inferFilter = Some(ManagerConfig.InferFilter(true)),
              cooldownMinutes = Some(ManagerConfig.CooldownMinutes(0))
            )
          )
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.managing
      .action(ApiIds.manager(Review), ManagersControllers.Action("resume"), None)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val out = a.managers.tick(t0)
    assert(out.exists(_.value.contains("selected 2")), out)
    val inf = item(Review, InferKind, ManagerItem.Target("zzagent_f1"))
    val rev = item(Review, ReviewKind, ManagerItem.Target("zzagent_f1"))
    assertEquals((inf.status, rev.status, inf.priority, rev.priority), ("active", "pending", 0, 0))
    val run = connect(ds)(AgentRepo.get(inf.runId.get)).get
    assertEquals((run.pipeline, run.origin), ("infer-filter", Some("manager")))
    a.engine.execute(ApiIds.run(run.id))
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(done.status, "done", why(done))
    val failed = connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(4000)))
      .find(s => s.name.value == "propose" && s.status.value == "failed")
      .flatMap(_.error)
      .map(_.sentence)
      .getOrElse("")
    assert(failed.contains("returns 0 sources"), failed)
    // the run left an approval and did not wait for it; the item waits, and so does the review
    val appr = a
      .approvalsList(None, None)
      .left
      .map(Api.Error(_))
      .toOption
      .get
      .find(_.subject.contains(ApprovalInfo.Subject("zzagent_f1")))
      .get
    assertEquals(appr.kind, "article-filter")
    assertEquals(appr.filter.map(p => (p.total, p.cited, p.citedIn)), Some((2, 2, 2)))
    a.managers.tick(t0)
    assertEquals(item(Review, InferKind, ManagerItem.Target("zzagent_f1")).status, "held")
    assertEquals(
      item(Review, InferKind, ManagerItem.Target("zzagent_f1")).hold.flatMap(_.untilApproval).map(_.value),
      Some(appr.id.value)
    )
    assertEquals(
      item(Review, ReviewKind, ManagerItem.Target("zzagent_f1")).status,
      "pending",
      "the review waits for the filter"
    )
    assertEquals(
      ArticleFixtures.load(ds, Slug("zzagent_f1")).flatMap(_.filter),
      None,
      "nothing recorded before approval"
    )
    // approve: the filter is recorded, the item done, the review dispatched
    assertEquals(
      a.decide(ApiIds.approval(appr.id), Approval.Outcome("approve"), DecisionRequest()).map(_.state),
      Right("approved")
    )
    assertEquals(
      ArticleFixtures.load(ds, Slug("zzagent_f1")).flatMap(_.filter),
      Some(Article.Filter(topics = List(Filter.Topics("zzagent_tin"))))
    )
    a.managers.tick(t0)
    assertEquals(item(Review, InferKind, ManagerItem.Target("zzagent_f1")).status, "done")
    assert(item(Review, InferKind, ManagerItem.Target("zzagent_f1")).outcome.exists(_.value.contains("approved")))
    val r2 = item(Review, ReviewKind, ManagerItem.Target("zzagent_f1"))
    assertEquals(r2.status, "active")
    assertEquals(connect(ds)(AgentRepo.get(r2.runId.get)).map(_.pipeline), Some("review-article"))

  test("infer-filter with article-filter = auto records at once; a person's enqueue brings an infer-filter item too"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(
      text("""{"rationale":"the tin sources","query":null,"topics":["zzagent_tin"],"tags":[],"extraQueries":[]}""")
    )
    val auto =
      baseCfg
        .modify(_.agents)
        .setTo(
          baseCfg.agents
            .modify(_.approvals)
            .setTo(baseCfg.agents.approvals.modify(_.articleFilter).setTo(AgentApprovalsConfig.Gate("auto")))
        )
    val (a, h) = handlers(c, auto); seed(h)
    assert(
      h.research.articles
        .put(
          Slug("zzagent_f2"),
          Article
            .Put(Article.Title("Zz tin"), Article.Kind.Wire("concept"), Article.Body(articleBody), tags = List(PutTag))
        )
        .isRight
    )
    val r = a.managing
      .enqueue(EnqueueRequest(EnqueueRequest.Kind("review"), EnqueueRequest.Target("zzagent_f2")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.managing.update(ApiIds.manager(Review), ManagerUpdate(Some(ManagerConfig(includeTags = Some(List(IncludeTag))))))
    assertEquals(r.items.map(_.kind), List(ArticleReview))
    assertEquals(item(Review, InferKind, ManagerItem.Target("zzagent_f2")).priority, 10, "at the review's priority")
    a.managing.action(ApiIds.manager(Review), ManagersControllers.Action("resume"), None)
    a.managers.tick(t0)
    val run = item(Review, InferKind, ManagerItem.Target("zzagent_f2")).runId.get
    a.engine.execute(ApiIds.run(run))
    assertEquals(
      ArticleFixtures.load(ds, Slug("zzagent_f2")).flatMap(_.filter).map(_.topics),
      Some(List("zzagent_tin"))
    )
    a.managers.tick(t0)
    assertEquals(item(Review, InferKind, ManagerItem.Target("zzagent_f2")).status, "done")
    assertEquals(item(Review, ReviewKind, ManagerItem.Target("zzagent_f2")).status, "active")

  // ── the seeding manager ────────────────────────────────────────────────────────────────────────

  private def extract(quote: String, claim: String) =
    s"""{"relevant":true,"reason":"bears on tin","claims":[{"claim":${str(claim)},"quote":${str(
        quote
      )},"date":null,"place":null,"confidence":"high"}]}"""

  private def draftJson(body: String) =
    s"""{"title":"Zz new","summary":"Where Aegean tin came from.","body":${str(body)},"section":null,"reasons":[]}"""

  test(
    "seed: propose-articles → the article-plan approval (always a person) → compile items without a chained run → a review item and the goal link"
  ):
    val c = ScriptedClient(Model)
    // one article passes; one has a slug that exists, one a filter that returns too little
    c.scripts += Seq(
      text(
        """{"rationale":"tin trade","articles":[
        |{"slug":"zzagent_new","title":"Zz new","kind":null,"query":null,"topics":["zzagent_tin"],"tags":[],"why":"origin of tin"},
        |{"slug":"zzagent_old","title":"Old","kind":"concept","query":null,"topics":["zzagent_tin"],"tags":[],"why":"again"},
        |{"slug":"zzagent_thin","title":"Thin","kind":"myth","query":"Uluburun copper Anatolia nowhere","topics":[],"tags":[],"why":"thin"}]}""".stripMargin
      )
    )
    val (a, h) = handlers(c); seed(h)
    assert(
      h.research.articles
        .put(
          Slug("zzagent_old"),
          Article.Put(Article.Title("Old"), Article.Kind.Wire("concept"), Article.Body("Old text."))
        )
        .isRight
    )
    val m =
      a.managing
        .create(ManagerCreate(ManagerCreate.Kind("seed"), Some(GoalSlug), paused = Some(ManagerCreate.Paused(false))))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((m.id, m.kind, m.goal.map(_.value: String)), (seedId(ref(Goal)), "seed", Some(Goal)))
    assert(a.managing.create(ManagerCreate(ManagerCreate.Kind("seed"), Some(GoalSlug))).isLeft, "one per area")
    a.managers.tick(t0)
    val prop = item(m.id, ProposeKind, goalTarget(Goal))
    val run = connect(ds)(AgentRepo.get(prop.runId.get)).get
    assertEquals((run.pipeline, run.goal.map(_.value: String)), ("propose-articles", Some(Goal)))
    a.engine.execute(ApiIds.run(run.id))
    assertEquals(connect(ds)(AgentRepo.get(run.id)).get.status, "done", why(connect(ds)(AgentRepo.get(run.id)).get))
    val appr = a.approvalsList(None, None).left.map(Api.Error(_)).toOption.get.find(_.subject.contains(GoalSubject)).get
    assertEquals(appr.kind, "article-plan")
    val plan = appr.articles.get
    assertEquals(plan.articles.map(_.slug), List("zzagent_new"))
    // word for word the text the plan held before its dropped entries were typed (G60 step 5)
    assertEquals(
      plan.dropped.map(_.sentence),
      List(
        "'zzagent_old': an article with this slug exists (or did)",
        "'zzagent_thin': search {query: \"Uluburun copper Anatolia nowhere\"} returns 0 sources; at least 2"
      )
    )
    a.managers.tick(t0)
    assertEquals(item(m.id, ProposeKind, goalTarget(Goal)).status, "held")
    assert(
      connect(ds)(ManagerRepo.itemFor(m.id, CompileKind, ManagerRepo.ItemRow.Target("zzagent_new"))).isEmpty,
      "nothing compiled before approval"
    )
    // approve: one compile item, dispatched with chainReview off
    assert(a.decide(ApiIds.approval(appr.id), Approval.Outcome("approve"), DecisionRequest()).isRight)
    val compile = item(m.id, CompileKind, ManagerItem.Target("zzagent_new"))
    assertEquals(compile.state.spec.map(_.filter.topics), Some(List("zzagent_tin")))
    c.scripts ++= List(
      Seq(text(extract("The Uluburun ship carried ten tons of copper and one ton of tin.", "The ship carried tin."))),
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(text(draftJson(articleBody)))
    )
    a.managers.tick(t0)
    assertEquals(item(m.id, ProposeKind, goalTarget(Goal)).status, "done")
    val crun = connect(ds)(AgentRepo.get(item(m.id, CompileKind, ManagerItem.Target("zzagent_new")).runId.get)).get
    assertEquals(
      (crun.pipeline, crun.params.chainReview, crun.goal.map(_.value: String), crun.params.title),
      ("compile-article", Some(false), Some(Goal), Some("Zz new"))
    )
    a.engine.execute(ApiIds.run(crun.id))
    assertEquals(connect(ds)(AgentRepo.get(crun.id)).get.status, "done", why(connect(ds)(AgentRepo.get(crun.id)).get))
    assert(
      connect(ds)(AgentRepo.list(None, PageSize(50))).forall(r => !r.parent.contains(crun.id)),
      "the compile chained no review run"
    )
    a.managers.tick(t0)
    assertEquals(item(m.id, CompileKind, ManagerItem.Target("zzagent_new")).status, "done")
    val chained = item(Review, ReviewKind, ManagerItem.Target("zzagent_new"))
    assertEquals((chained.origin, chained.priority, chained.status), ("chain", 2, "pending"))
    assertEquals(connect(ds)(ManagerRepo.get(Review)).map(_.state), Some("paused"))
    assert(
      connect(ds)(GoalLinkRepo.links(Goal)).exists(l =>
        l.kind == LinkKind.Article && l.ref == api.automation.Goal.Plans.GoalLink.Ref("zzagent_new")
      )
    )

  test("seed: a rejected plan compiles nothing; Extend on a goal queues the next proposal"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(
      text(
        """{"rationale":"tin","articles":[{"slug":"zzagent_r","title":"R","kind":null,"query":null,"topics":["zzagent_tin"],"tags":[],"why":"w"}]}"""
      )
    )
    val (a, h) = handlers(c); seed(h)
    val r = a.managing
      .enqueue(EnqueueRequest(EnqueueRequest.Kind("seed"), EnqueueRequest.Target("zzagent-goal2")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((r.managerId, r.items.map(_.kind)), (seedId(ref(Goal)), List(ProposeArticles)))
    assertEquals(connect(ds)(ManagerRepo.get(seedId(ref(Goal)))).map(_.state), Some("paused"))
    a.managing.action(ApiIds.manager(seedId(ref(Goal))), ManagersControllers.Action("resume"), None)
    a.managers.tick(t0)
    a.engine.execute(ApiIds.run(item(seedId(ref(Goal)), ProposeKind, goalTarget(Goal)).runId.get))
    a.managers.tick(t0)
    val appr = a.approvalsList(None, None).left.map(Api.Error(_)).toOption.get.find(_.subject.contains(GoalSubject)).get
    assert(a.decide(ApiIds.approval(appr.id), Approval.Outcome("reject"), DecisionRequest()).isRight)
    a.managers.tick(t0)
    assertEquals(item(seedId(ref(Goal)), ProposeKind, goalTarget(Goal)).status, "stopped")
    assert(
      connect(ds)(ManagerRepo.itemFor(seedId(ref(Goal)), CompileKind, ManagerRepo.ItemRow.Target("zzagent_r"))).isEmpty
    )
    // Extend again: the settled item is re-opened
    assertEquals(
      a.managing
        .enqueue(EnqueueRequest(EnqueueRequest.Kind("seed"), EnqueueRequest.Target("zzagent-goal2")))
        .map(_.items.map(_.status)),
      Right(List("pending"))
    )

  // ── the enrich manager (auto-enrich) ───────────────────────────────────────────────────────────

  test("enrich: an ingest queues an item (manager created paused); enriched sources are skipped; Enrich on an article"):
    val c = ScriptedClient(Model)
    val (a, h) = handlers(
      c,
      baseCfg.modify(_.agents).setTo(baseCfg.agents.modify(_.autoEnrich).setTo(AgentsConfig.AutoEnrich(true)))
    );
    seed(h)
    val note = a.autoEnrich(Ck).getOrElse(fail("no note"))
    assert(note.value.startsWith("enrichment queued: item #") && note.value.contains("paused"), note)
    val i = item(Enrich, EnrichKind, ckTarget(Ck))
    assertEquals((i.origin, i.status), ("ingest", "pending"))
    // a second ingest of the same source does not double it
    a.autoEnrich(Ck)
    assertEquals(connect(ds)(ManagerRepo.counts(Enrich)).pending, 1)
    // the source got findings meanwhile: the manager settles the item without a run
    assert(
      h.library.sources.patch(Ck, Source.Patch(notes = Some(Source.Notes("enriched by hand")))).isRight
    )
    a.managing.action(ApiIds.manager(Enrich), ManagersControllers.Action("resume"), None)
    a.managers.tick(t0)
    assertEquals(item(Enrich, EnrichKind, ckTarget(Ck)).status, "done")
    assert(item(Enrich, EnrichKind, ckTarget(Ck)).outcome.exists(_.value.contains("already enriched")))
    // Enrich on an article: only its unenriched cited sources
    assert(
      h.research.articles
        .put(
          Slug("zzagent_e1"),
          Article.Put(Article.Title("Zz"), Article.Kind.Wire("concept"), Article.Body(articleBody))
        )
        .isRight
    )
    val r = a.managing
      .enqueue(EnqueueRequest(EnqueueRequest.Kind("enrich"), EnqueueRequest.Target("zzagent_e1")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.items.map(_.target.value: String), List(Ck2))
    assert(r.skipped.exists(_.value.contains(Ck.value)), r.skipped)
    a.managers.tick(t0)
    val run = connect(ds)(AgentRepo.get(item(Enrich, EnrichKind, ckTarget(Ck2)).runId.get)).get
    assertEquals((run.pipeline, run.origin, run.managerId), ("enrich-source", Some("manager"), Some(Enrich)))
    // with the managers off, auto-enrich queues a run at once, as before
    val off = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents
          .modify(_.autoEnrich)
          .setTo(AgentsConfig.AutoEnrich(true))
          .modify(_.managers)
          .setTo(baseCfg.agents.managers.modify(_.enabled).setTo(AgentManagersConfig.Enabled(false)))
      )
    val (b, _) = handlers(c, off)
    connect(ds)(sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run())
    assert(b.autoEnrich(Ck2).exists(_.value.startsWith("enrichment queued: run ")), b.autoEnrich(Ck2))

  // ── stale and extend ───────────────────────────────────────────────────────────────────────────

  test("stale: a converged article whose filter gained a source is selected again, at the lowest priority"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(
      h.research.articles
        .put(
          Slug("zzagent_s1"),
          Article.Put(
            Article.Title("Zz tin"),
            Article.Kind.Wire("concept"),
            Article.Body(articleBody),
            tags = List(PutTag),
            filter = Some(Article.Filter(topics = List(Filter.Topics("zzagent_tin"))))
          )
        )
        .isRight
    )
    a.managers.ensureDefaults()
    a.managing.update(
      ApiIds.manager(Review),
      ManagerUpdate(
        Some(
          ManagerConfig(includeTags = Some(List(IncludeTag)), staleAfterDays = Some(ManagerConfig.StaleAfterDays(90)))
        )
      )
    )
    a.managing.action(ApiIds.manager(Review), ManagersControllers.Action("resume"), None)
    // it converged when its filter returned one source
    val (id, _) = transact(ds)(
      ManagerRepo.upsert(
        Review,
        ReviewKind,
        ManagerRepo.ItemRow.Target("zzagent_s1"),
        ItemOrigin.Person,
        ManagerRepo.ItemRow.Priority(10),
        reopen = false,
        ItemState(filterHits = Some(Managers.ItemState.FilterHits(1)))
      )
    )
    connect(ds)(
      sql"UPDATE manager_items SET status = 'done', outcome = 'converged after 1 run' WHERE id = $id".update.run()
    )
    a.managers.tick(t0)
    val again = connect(ds)(ManagerRepo.item(id)).get
    assertEquals((again.priority.value, again.origin), (ReviewManagerPolicy.StalePriority.value, "selection"))
    assert(again.outcome.exists(_.value.contains("its filter gained 1 source")), again.outcome)
    assert(Set("pending", "active").contains(again.status.value), again.status)

  test("extend: the article button queues an extension in the review manager; it compiles without a chained run"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(
      h.research.articles
        .put(
          Slug("zzagent_x1"),
          Article.Put(
            Article.Title("Zz tin"),
            Article.Kind.Wire("concept"),
            Article.Body(s"Tin [[$Ck]]."),
            filter = Some(Article.Filter(topics = List(Filter.Topics("zzagent_tin"))))
          )
        )
        .isRight
    )
    val r = a.managing
      .enqueue(EnqueueRequest(EnqueueRequest.Kind("extend"), EnqueueRequest.Target("zzagent_x1")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((r.managerId, r.items.map(i => (i.kind, i.priority))), (Review, List((ArticleExtend, 10))))
    a.managing.update(ApiIds.manager(Review), ManagerUpdate(Some(ManagerConfig(includeTags = Some(List(IncludeTag))))))
    a.managing.action(ApiIds.manager(Review), ManagersControllers.Action("resume"), None)
    a.managers.tick(t0)
    val run = connect(ds)(AgentRepo.get(item(Review, ExtendKind, ManagerItem.Target("zzagent_x1")).runId.get)).get
    assertEquals((run.pipeline, run.params.chainReview), ("compile-article", Some(false)))
    assert(a.managing.enqueue(EnqueueRequest(EnqueueRequest.Kind("frobnicate"), EnqueueRequest.Target("x"))).isLeft)
    assert(
      a.managing
        .addItem(
          ApiIds.manager(seedId(ref(Goal))),
          EnqueueRequest(EnqueueRequest.Kind("compile"), EnqueueRequest.Target("zzagent_y"))
        )
        .isLeft,
      "no such manager"
    )
