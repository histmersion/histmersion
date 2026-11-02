package com.histmersion.server.agents

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.automation.agents.{AgentsHandlers, LlmClient}
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
import io.circe.Json
import sttp.ai.core.agent.*

import java.time.Instant
import com.histmersion.db.*
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.MetadataPatch
import com.histmersion.domain.library.Source
import com.histmersion.api.research.Article

/** The process managers' phase 2 against a `*_test` database (docs/research-process-managers.md §4, §6): infer-filter
  * before a review (the filter checked against the corpus, held for its approval, the review waiting for it), the
  * seeding manager (propose-articles → the always-manual article-plan approval → compile items with chainReview off → a
  * chained review item and the goal link), the enrich manager absorbing auto-enrich, stale selection, and the enqueue
  * routing of the page buttons. Models are scripted ([[ScriptedClient]]); no provider is called. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class ManagerPhase2Suite extends com.histmersion.db.DbSuite with RunTranscripts:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val Tag = "zzmgr2"
  private val Goal = "zzagent-goal2"
  private val Ck = "zzagent2019_tin"
  private val Ck2 = "zzagent2024_iso"
  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))
  private val t0 = Instant.parse("2026-10-17T12:00:00Z")

  private lazy val baseCfg =
    val b = AppConfig.load()
    val m = Model.wire
    b.copy(
      db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(
        enabled = true,
        prices = "scripted=1/2",
        roles = b.agents.roles
          .copy(extractor = m, reviewer = m, coldReader = m, skeptic = m, fixer = m, planner = m, writer = m),
        approvals = b.agents.approvals.copy(articlePublish = "auto")
      ),
      libraryAgents = b.libraryAgents.copy(seed = b.libraryAgents.seed.copy(minSources = 2)),
      researchAgents = b.researchAgents.copy(
        inferFilter = b.researchAgents.inferFilter.copy(minHits = 1),
        review = b.researchAgents.review.copy(spotChecks = 0)
      )
    )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private def str(s: String) = Json.fromString(s).noSpaces

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsHandlers, ServerWiring) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    (AgentsHandlers(ServerEngineSite(deps, h, Some(TestRuns.noonClock())), (_, _) => Right(c)), h)

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
          com.histmersion.api.library.Source.Put(
            title = "Bronze Age tin",
            year = Some(2019),
            topics = List("zzagent_tin"),
            status = Some(Source.Status.Current),
            summary = Some("The Uluburun ship carried ten tons of copper and one ton of tin."),
            body = Some(
              "The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."
            )
          )
        )
        .isRight
    )
    assert(
      h.library.sources
        .put(
          Ck2,
          com.histmersion.api.library.Source.Put(
            title = "Tin isotopes",
            year = Some(2024),
            topics = List("zzagent_tin"),
            body = Some("We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia.")
          )
        )
        .isRight
    )
    assert(
      h.library.sources
        .patch(
          Ck2,
          com.histmersion.api.library.Source
            .Patch(metadata = Some(MetadataPatch(status = Some(Source.Status.Contested))))
        )
        .isRight
    )
    assert(
      h.goals
        .put(
          Goal,
          com.histmersion.api.automation.Goal.Put("Zz tin in the Bronze Age", notes = Some("where the tin came from"))
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

  private def item(mid: String, kind: String, target: String): ManagerItem =
    connect(ds)(ManagerRepo.itemFor(mid, kind, target)).get

  private def why(r: RunInfo): String =
    (r.error.toList ++ connect(ds)(AgentRepo.steps(r.id, 200))
      .filter(_.error.isDefined)
      .map(s => s"${s.name}#${s.attempt}: ${s.error.get}"))
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
      h.research.articles.put("zzagent_f1", Article.Put("Zz tin", "concept", articleBody, tags = List(Tag))).isRight
    )
    a.managers.ensureDefaults()
    a.managing
      .update(
        ReviewId,
        ManagerUpdate(
          Some(ManagerConfig(includeTags = Some(List(Tag)), inferFilter = Some(true), cooldownMinutes = Some(0)))
        )
      )
      .fold(e => fail(e.message), identity)
    a.managing.action(ReviewId, "resume", None).fold(e => fail(e.message), identity)
    val out = a.managers.tick(t0)
    assert(out.exists(_.contains("selected 2")), out)
    val inf = item(ReviewId, InferFilter, "zzagent_f1")
    val rev = item(ReviewId, ArticleReview, "zzagent_f1")
    assertEquals((inf.status, rev.status, inf.priority, rev.priority), ("active", "pending", 0, 0))
    val run = connect(ds)(AgentRepo.get(inf.runId.get)).get
    assertEquals((run.pipeline, run.origin), ("infer-filter", Some("manager")))
    a.engine.execute(run.id)
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(done.status, "done", why(done))
    val failed = connect(ds)(AgentRepo.steps(run.id, 4000))
      .find(s => s.name == "propose" && s.status == "failed")
      .flatMap(_.error)
      .getOrElse("")
    assert(failed.contains("returns 0 sources"), failed)
    // the run left an approval and did not wait for it; the item waits, and so does the review
    val appr = a.approvalsList(None, None).toOption.get.find(_.subject.contains("zzagent_f1")).get
    assertEquals(appr.kind, "article-filter")
    assertEquals(appr.filter.map(p => (p.total, p.cited, p.citedIn)), Some((2, 2, 2)))
    a.managers.tick(t0)
    assertEquals(item(ReviewId, InferFilter, "zzagent_f1").status, "held")
    assertEquals(item(ReviewId, InferFilter, "zzagent_f1").hold.flatMap(_.untilApproval), Some(appr.id))
    assertEquals(item(ReviewId, ArticleReview, "zzagent_f1").status, "pending", "the review waits for the filter")
    assertEquals(ArticleFixtures.load(ds, "zzagent_f1").flatMap(_.filter), None, "nothing recorded before approval")
    // approve: the filter is recorded, the item done, the review dispatched
    assertEquals(a.decide(appr.id, "approve", DecisionRequest()).map(_.state), Right("approved"))
    assertEquals(
      ArticleFixtures.load(ds, "zzagent_f1").flatMap(_.filter),
      Some(Article.Filter(topics = List("zzagent_tin")))
    )
    a.managers.tick(t0)
    assertEquals(item(ReviewId, InferFilter, "zzagent_f1").status, "done")
    assert(item(ReviewId, InferFilter, "zzagent_f1").outcome.exists(_.contains("approved")))
    val r2 = item(ReviewId, ArticleReview, "zzagent_f1")
    assertEquals(r2.status, "active")
    assertEquals(connect(ds)(AgentRepo.get(r2.runId.get)).map(_.pipeline), Some("review-article"))

  test("infer-filter with article-filter = auto records at once; a person's enqueue brings an infer-filter item too"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(
      text("""{"rationale":"the tin sources","query":null,"topics":["zzagent_tin"],"tags":[],"extraQueries":[]}""")
    )
    val auto =
      baseCfg.copy(agents = baseCfg.agents.copy(approvals = baseCfg.agents.approvals.copy(articleFilter = "auto")))
    val (a, h) = handlers(c, auto); seed(h)
    assert(
      h.research.articles.put("zzagent_f2", Article.Put("Zz tin", "concept", articleBody, tags = List(Tag))).isRight
    )
    val r = a.managing.enqueue(EnqueueRequest("review", "zzagent_f2")).fold(e => fail(e.message), identity)
    a.managing.update(ReviewId, ManagerUpdate(Some(ManagerConfig(includeTags = Some(List(Tag))))))
    assertEquals(r.items.map(_.kind), List(ArticleReview))
    assertEquals(item(ReviewId, InferFilter, "zzagent_f2").priority, 10, "at the review's priority")
    a.managing.action(ReviewId, "resume", None)
    a.managers.tick(t0)
    val run = item(ReviewId, InferFilter, "zzagent_f2").runId.get
    a.engine.execute(run)
    assertEquals(ArticleFixtures.load(ds, "zzagent_f2").flatMap(_.filter).map(_.topics), Some(List("zzagent_tin")))
    a.managers.tick(t0)
    assertEquals(item(ReviewId, InferFilter, "zzagent_f2").status, "done")
    assertEquals(item(ReviewId, ArticleReview, "zzagent_f2").status, "active")

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
    assert(h.research.articles.put("zzagent_old", Article.Put("Old", "concept", "Old text.")).isRight)
    val m =
      a.managing.create(ManagerCreate("seed", Some(Goal), paused = Some(false))).fold(e => fail(e.message), identity)
    assertEquals((m.id, m.kind, m.goal), (seedId(Goal), "seed", Some(Goal)))
    assert(a.managing.create(ManagerCreate("seed", Some(Goal))).isLeft, "one per area")
    a.managers.tick(t0)
    val prop = item(m.id, ProposeArticles, Goal)
    val run = connect(ds)(AgentRepo.get(prop.runId.get)).get
    assertEquals((run.pipeline, run.goal), ("propose-articles", Some(Goal)))
    a.engine.execute(run.id)
    assertEquals(connect(ds)(AgentRepo.get(run.id)).get.status, "done", why(connect(ds)(AgentRepo.get(run.id)).get))
    val appr = a.approvalsList(None, None).toOption.get.find(_.subject.contains(Goal)).get
    assertEquals(appr.kind, "article-plan")
    val plan = appr.articles.get
    assertEquals(plan.articles.map(_.slug), List("zzagent_new"))
    assert(
      plan.dropped.exists(_.contains("zzagent_old")) && plan.dropped.exists(_.contains("zzagent_thin")),
      plan.dropped
    )
    a.managers.tick(t0)
    assertEquals(item(m.id, ProposeArticles, Goal).status, "held")
    assert(
      connect(ds)(ManagerRepo.itemFor(m.id, ArticleCompile, "zzagent_new")).isEmpty,
      "nothing compiled before approval"
    )
    // approve: one compile item, dispatched with chainReview off
    assert(a.decide(appr.id, "approve", DecisionRequest()).isRight)
    val compile = item(m.id, ArticleCompile, "zzagent_new")
    assertEquals(compile.state.spec.map(_.filter.topics), Some(List("zzagent_tin")))
    c.scripts ++= List(
      Seq(text(extract("The Uluburun ship carried ten tons of copper and one ton of tin.", "The ship carried tin."))),
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(text(draftJson(articleBody)))
    )
    a.managers.tick(t0)
    assertEquals(item(m.id, ProposeArticles, Goal).status, "done")
    val crun = connect(ds)(AgentRepo.get(item(m.id, ArticleCompile, "zzagent_new").runId.get)).get
    assertEquals(
      (crun.pipeline, crun.params.chainReview, crun.goal, crun.params.title),
      ("compile-article", Some(false), Some(Goal), Some("Zz new"))
    )
    a.engine.execute(crun.id)
    assertEquals(connect(ds)(AgentRepo.get(crun.id)).get.status, "done", why(connect(ds)(AgentRepo.get(crun.id)).get))
    assert(
      connect(ds)(AgentRepo.list(None, 50)).forall(r => !r.parent.contains(crun.id)),
      "the compile chained no review run"
    )
    a.managers.tick(t0)
    assertEquals(item(m.id, ArticleCompile, "zzagent_new").status, "done")
    val chained = item(ReviewId, ArticleReview, "zzagent_new")
    assertEquals((chained.origin, chained.priority, chained.status), ("chain", 2, "pending"))
    assertEquals(connect(ds)(ManagerRepo.get(ReviewId)).map(_.state), Some("paused"))
    assert(connect(ds)(GoalLinkRepo.links(Goal)).exists(l => l.kind == LinkKind.Article && l.ref == "zzagent_new"))

  test("seed: a rejected plan compiles nothing; Extend on a goal queues the next proposal"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(
      text(
        """{"rationale":"tin","articles":[{"slug":"zzagent_r","title":"R","kind":null,"query":null,"topics":["zzagent_tin"],"tags":[],"why":"w"}]}"""
      )
    )
    val (a, h) = handlers(c); seed(h)
    val r = a.managing.enqueue(EnqueueRequest("seed", Goal)).fold(e => fail(e.message), identity)
    assertEquals((r.managerId, r.items.map(_.kind)), (seedId(Goal), List(ProposeArticles)))
    assertEquals(connect(ds)(ManagerRepo.get(seedId(Goal))).map(_.state), Some("paused"))
    a.managing.action(seedId(Goal), "resume", None)
    a.managers.tick(t0)
    a.engine.execute(item(seedId(Goal), ProposeArticles, Goal).runId.get)
    a.managers.tick(t0)
    val appr = a.approvalsList(None, None).toOption.get.find(_.subject.contains(Goal)).get
    assert(a.decide(appr.id, "reject", DecisionRequest()).isRight)
    a.managers.tick(t0)
    assertEquals(item(seedId(Goal), ProposeArticles, Goal).status, "stopped")
    assert(connect(ds)(ManagerRepo.itemFor(seedId(Goal), ArticleCompile, "zzagent_r")).isEmpty)
    // Extend again: the settled item is re-opened
    assertEquals(a.managing.enqueue(EnqueueRequest("seed", Goal)).map(_.items.map(_.status)), Right(List("pending")))

  // ── the enrich manager (auto-enrich) ───────────────────────────────────────────────────────────

  test("enrich: an ingest queues an item (manager created paused); enriched sources are skipped; Enrich on an article"):
    val c = ScriptedClient(Model)
    val (a, h) = handlers(c, baseCfg.copy(agents = baseCfg.agents.copy(autoEnrich = true))); seed(h)
    val note = a.autoEnrich(Ck).getOrElse(fail("no note"))
    assert(note.startsWith("enrichment queued: item #") && note.contains("paused"), note)
    val i = item(EnrichId, SourceEnrich, Ck)
    assertEquals((i.origin, i.status), ("ingest", "pending"))
    // a second ingest of the same source does not double it
    a.autoEnrich(Ck)
    assertEquals(connect(ds)(ManagerRepo.counts(EnrichId)).pending, 1)
    // the source got findings meanwhile: the manager settles the item without a run
    assert(
      h.library.sources.patch(Ck, com.histmersion.api.library.Source.Patch(notes = Some("enriched by hand"))).isRight
    )
    a.managing.action(EnrichId, "resume", None)
    a.managers.tick(t0)
    assertEquals(item(EnrichId, SourceEnrich, Ck).status, "done")
    assert(item(EnrichId, SourceEnrich, Ck).outcome.exists(_.contains("already enriched")))
    // Enrich on an article: only its unenriched cited sources
    assert(h.research.articles.put("zzagent_e1", Article.Put("Zz", "concept", articleBody)).isRight)
    val r = a.managing.enqueue(EnqueueRequest("enrich", "zzagent_e1")).fold(e => fail(e.message), identity)
    assertEquals(r.items.map(_.target), List(Ck2))
    assert(r.skipped.exists(_.contains(Ck)), r.skipped)
    a.managers.tick(t0)
    val run = connect(ds)(AgentRepo.get(item(EnrichId, SourceEnrich, Ck2).runId.get)).get
    assertEquals((run.pipeline, run.origin, run.managerId), ("enrich-source", Some("manager"), Some(EnrichId)))
    // with the managers off, auto-enrich queues a run at once, as before
    val off = baseCfg.copy(agents =
      baseCfg.agents.copy(autoEnrich = true, managers = baseCfg.agents.managers.copy(enabled = false))
    )
    val (b, _) = handlers(c, off)
    connect(ds)(sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run())
    assert(b.autoEnrich(Ck2).exists(_.startsWith("enrichment queued: run ")), b.autoEnrich(Ck2))

  // ── stale and extend ───────────────────────────────────────────────────────────────────────────

  test("stale: a converged article whose filter gained a source is selected again, at the lowest priority"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(
      h.research.articles
        .put(
          "zzagent_s1",
          Article.Put(
            "Zz tin",
            "concept",
            articleBody,
            tags = List(Tag),
            filter = Some(Article.Filter(topics = List("zzagent_tin")))
          )
        )
        .isRight
    )
    a.managers.ensureDefaults()
    a.managing.update(
      ReviewId,
      ManagerUpdate(Some(ManagerConfig(includeTags = Some(List(Tag)), staleAfterDays = Some(90))))
    )
    a.managing.action(ReviewId, "resume", None)
    // it converged when its filter returned one source
    val (id, _) = transact(ds)(
      ManagerRepo.upsert(
        ReviewId,
        ArticleReview,
        "zzagent_s1",
        ItemOrigin.Person,
        10,
        reopen = false,
        ItemState(filterHits = Some(1))
      )
    )
    connect(ds)(
      sql"UPDATE manager_items SET status = 'done', outcome = 'converged after 1 run' WHERE id = $id".update.run()
    )
    a.managers.tick(t0)
    val again = connect(ds)(ManagerRepo.item(id)).get
    assertEquals((again.priority, again.origin), (ReviewManagerPolicy.StalePriority, "selection"))
    assert(again.outcome.exists(_.contains("its filter gained 1 source")), again.outcome)
    assert(Set("pending", "active").contains(again.status), again.status)

  test("extend: the article button queues an extension in the review manager; it compiles without a chained run"):
    val (a, h) = handlers(ScriptedClient(Model)); seed(h)
    assert(
      h.research.articles
        .put(
          "zzagent_x1",
          Article.Put("Zz tin", "concept", s"Tin [[$Ck]].", filter = Some(Article.Filter(topics = List("zzagent_tin"))))
        )
        .isRight
    )
    val r = a.managing.enqueue(EnqueueRequest("extend", "zzagent_x1")).fold(e => fail(e.message), identity)
    assertEquals((r.managerId, r.items.map(i => (i.kind, i.priority))), (ReviewId, List((ArticleExtend, 10))))
    a.managing.update(ReviewId, ManagerUpdate(Some(ManagerConfig(includeTags = Some(List(Tag))))))
    a.managing.action(ReviewId, "resume", None)
    a.managers.tick(t0)
    val run = connect(ds)(AgentRepo.get(item(ReviewId, ArticleExtend, "zzagent_x1").runId.get)).get
    assertEquals((run.pipeline, run.params.chainReview), ("compile-article", Some(false)))
    assert(a.managing.enqueue(EnqueueRequest("frobnicate", "x")).isLeft)
    assert(a.managing.addItem(seedId(Goal), EnqueueRequest("compile", "zzagent_y")).isLeft, "no such manager")
