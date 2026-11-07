package com.histmersion.server.agents

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.TestRuns

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.LeaseRepo

import com.histmersion.domain.automation.agents.{AgentLease, AgentsControllers, LlmClient}
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.ReviewArticle.{Finding, Reviewer, Severity}
import com.histmersion.domain.ReviewLedger.*
import com.histmersion.domain.automation.worklists.{QueueRows}
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.domain.research.workflows.ReviewLedgerFixtures
import com.histmersion.server.{Deps, ServerWiring}
import sttp.ai.core.agent.*

import java.time.Instant
import scala.jdk.CollectionConverters.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.EdgesPatch
import com.histmersion.api.library.Edge
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article
import com.histmersion.domain.research.Slug

/** The process managers against a `*_test` database (docs/research-process-managers.md §6): the supervisor's tick with
  * a fixed clock — selection, dispatch, evaluation and the stop rules over several runs, holds, pause (with and without
  * its runs), starvation, the global cap — crash and restart (a run committed but never submitted, a settle never
  * evaluated, a run interrupted at boot), the lease, and the hints (snapshotted into the run, reaching the fixer's and
  * the skeptic's user message, once-hints retired). Runs are scripted ([[PanelClient]]) or settled by hand; no provider
  * is ever called. Skipped without HISTMERSION_TEST_DB_URL.
  */
class ManagerRunSuite extends com.histmersion.db.DbSuite with RunTranscripts:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val IncludeTag = ManagerConfig.IncludeTags("zzmgr")
  private val ArticleTag = Article.Put.Tags("zzmgr")
  private val SkipTag = HintEffect.Tag("zzmgr")
  private val Ck = com.histmersion.domain.library.Citekey("zzagent2019_tin")
  private val ApiCk = com.histmersion.api.library.Citekey("zzagent2019_tin")
  private val Review = ManagerInfo.Id(ReviewId)
  private val ReviewLight = ApiIds.manager(Review)
  private val ReviewKind = ManagerItem.Kind(ArticleReview)
  private val ReviewEnqueue = EnqueueRequest.Kind(ArticleReview)
  private val StoppedKind = ManagerEvent.Kind(Feed.Stopped)
  private val StarvedKind = ManagerEvent.Kind(Feed.Starved)
  private val EvaluatedKind = ManagerEvent.Kind(Feed.Evaluated)
  private val HintKind = ManagerEvent.Kind(Feed.Hint)
  private val Model = ModelRef(ProviderKind.Local, com.histmersion.api.automation.Agents.ModelRef.Model("scripted"))
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))
  private val t0 = Instant.parse("2026-10-15T12:00:00Z")

  private val Body = Article.Body(
    s"""Tin reached the Aegean by sea [[$Ck]].
       |
       |## Disagreements
       |
       |The date of the wreck is argued [[$Ck]].
       |
       |## Open questions
       |
       |Where the tin was mined is open [[$Ck]].
       |
       |## Not used
       |
       |- none
       |""".stripMargin
  )

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.modify(
      _.db).setTo(b.db.modify(_.url).setTo(testDb.get)).modify(_.agents).setTo(b.agents.modify(
        _.enabled).setTo(true).modify(_.prices).setTo("scripted=1/2").modify(_.roles).setTo(b.agents.roles.modify(_.reviewer).setTo(Model.wire).modify(_.coldReader).setTo(Model.wire).modify(_.skeptic).setTo(Model.wire).modify(_.fixer).setTo(Model.wire)
      )).modify(_.researchAgents).setTo(b.researchAgents.copy(
        managers = b.researchAgents.managers
          .modify(_.review).setTo(b.researchAgents.managers.review.modify(_.cooldownMinutes).setTo(30).modify(_.inferFilter).setTo(false)),
        // the scripted rules reviewer looks nothing up: ReviewArticleRunSuite holds the spot-checks
        review = b.researchAgents.review.modify(_.spotChecks).setTo(0)
      )
    )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsControllers, ServerWiring) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    (AgentsControllers(ServerEngineSite(deps, h), (_, _) => Right(c)), h)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers".update.run()
      // a manager's runs, whatever their target (a selection that was not narrowed reaches other suites' rows)
      sql"DELETE FROM jobs WHERE kind = 'agent' AND manager_id IS NOT NULL".update.run()
      sql"DELETE FROM agent_leases WHERE name LIKE 'zz%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run()
      sql"DELETE FROM approvals WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM article_versions WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM review_ledgers WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(h: ServerWiring, slugs: com.histmersion.domain.research.Slug*): Unit =
    assert(
      h.library.sources
        .put(
          Ck,
          Source.Put(
            title = com.histmersion.api.library.Title("Bronze Age tin"),
            year = (Some(2019)).map(com.histmersion.api.catalogue.Year.apply),
            body = Some(
              Source.OcrMarkdown(
                "The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."
              )
            )
          )
        )
        .isRight
    )
    slugs.foreach(s =>
      assert(h.research.articles.put(s, Article.Put(Article.Title(s"Tin $s"), com.histmersion.api.research.Article.Kind.Wire("concept"), Body, tags = List(ArticleTag))).isRight)
    )

  /** The review manager, active, narrowed to this suite's articles. */
  private def activeReview(a: AgentsControllers, cfg: ManagerConfig = ManagerConfig()): ManagerInfo =
    a.managers.ensureDefaults()
    val m = a.managing
      .update(ReviewLight, ManagerUpdate(Some(cfg.copy(includeTags = Some(List(IncludeTag))))))
      .fold(e => fail(e.message.value), identity)
    assertEquals(m.state, "paused", "created paused")
    a.managing.action(ReviewLight, com.histmersion.domain.automation.managers.ManagersControllers.Action("resume"), None).fold(e => fail(e.message.value), identity)

  private def finding(id: Finding.Id, r: Reviewer, kind: Finding.Kind = Finding.Kind("synth")) =
    Finding(id, r, Severity.Major, kind, Finding.Where(s"where $id"), Finding.Quote(s"quote $id"), Finding.Problem(s"problem $id"), com.histmersion.domain.ReviewArticle.Finding.Fix("fix"))

  /** Settle a manager's run by hand: done (with a ledger of `majors` open MAJORs raised by it), or another state. */
  private def settle(run: RunInfo.Id, slug: Ledger.Slug, majors: List[Finding], status: RunState = RunState.Done): Unit =
    assert(TestRuns.transition(ds, ApiIds.run(run), RunState.Queued, RunState.Running).isDefined)
    val l = absorb(Ledger(slug), majors, ApiIds.run(run), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    ReviewLedgerFixtures.put(ds, l)
    assert(
      TestRuns
        .transition(ds, ApiIds.run(run), RunState.Running, status, error = Option.when(status == RunState.Failed)(com.histmersion.util.jobs.JobRow.Error("boom")))
        .isDefined
    )

  private def item(target: ManagerItem.Target): ManagerItem = connect(ds)(ManagerRepo.itemFor(Review, ReviewKind, target)).get
  private def events: List[ManagerEvent] = connect(ds)(ManagerRepo.events(Review, None, com.histmersion.PageSize(200))).reverse

  test("selection, dispatch and the stop rules over runs: 3 runs to converge; max runs, no progress, churn"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_m1"), Slug("zzagent_m2"))
    activeReview(a, ManagerConfig(maxRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRuns(2)), maxRunsPerArticle = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRunsPerArticle(3))))
    // m1 has open MAJORs (selected first), m2 was never reviewed
    ReviewLedgerFixtures.put(
      ds,
      absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("zzagent_m1")), List(finding(Finding.Id("R1"), Reviewer.Rules), finding(Finding.Id("S1"), Reviewer.Skeptic)), com.histmersion.domain.automation.Run.Id("old"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    )
    val out1 = a.managers.tick(t0)
    assert(out1.exists(_.value.contains("selected 2")), out1)
    val i1 = item(ManagerItem.Target("zzagent_m1")); val i2 = item(ManagerItem.Target("zzagent_m2"))
    assertEquals((i1.priority, i2.priority), (1, 0))
    assertEquals(
      (i1.status, i2.status),
      ("active", "active"),
      s"$out1 ${events.map(_.text)} ${connect(ds)(AgentRepo.list(None, com.histmersion.PageSize(50))).filter(r => r.status.value == "queued" || r.status.value == "running").map(r => (r.target, r.pipeline, r.status))}"
    )
    val r1 = connect(ds)(AgentRepo.get(i1.runId.get)).get
    assertEquals(
      (r1.origin, r1.managerId, r1.managerItem, r1.status),
      (Some("manager"), Some(ReviewId), Some(i1.id), "queued")
    )
    assertEquals((r1.params.fix, r1.params.rounds), (Some(true), Some(3)))
    assertEquals(i1.state.before, Some(2))
    // m1: 2 → 1 → 0 over three runs; each Again waits for the cooldown
    settle(r1.id, Ledger.Slug("zzagent_m1"), List(finding(Finding.Id("R1"), Reviewer.Rules)))
    a.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_m1")).status, "pending")
    assertEquals(item(ManagerItem.Target("zzagent_m1")).notBefore, Some("2026-10-15T12:30:00Z"))
    a.managers.tick(t0.plusSeconds(60))
    assertEquals(item(ManagerItem.Target("zzagent_m1")).status, "pending", "not before the cooldown")
    a.managers.tick(t0.plusSeconds(31 * 60))
    val run2 = item(ManagerItem.Target("zzagent_m1")).runId.get
    assertNotEquals(run2, r1.id)
    settle(run2, Ledger.Slug("zzagent_m1"), List(finding(Finding.Id("R2"), Reviewer.Rules)))
    a.managers.tick(t0.plusSeconds(32 * 60))
    a.managers.tick(t0.plusSeconds(70 * 60))
    val run3 = item(ManagerItem.Target("zzagent_m1")).runId.get
    settle(run3, Ledger.Slug("zzagent_m1"), Nil)
    a.managers.tick(t0.plusSeconds(71 * 60))
    val done = item(ManagerItem.Target("zzagent_m1"))
    assertEquals((done.status, done.outcome, done.attempts), ("done", Some("converged after 3 runs"), 3))
    assertEquals(done.state.history.map(_.after), List(1, 1, 0))
    // m2: runs that end with only a missing-critique MAJOR while the article's critique checklist has no
    // open pair to work down (0 → 0): the first is no window yet (again), the second is omission churn
    val m2run = item(ManagerItem.Target("zzagent_m2")).runId.get
    assertEquals(item(ManagerItem.Target("zzagent_m2")).state.pairsBefore, Some(0))
    settle(m2run, Ledger.Slug("zzagent_m2"), List(finding(Finding.Id("S1"), Reviewer.Skeptic, Finding.Kind("missing-critique"))))
    a.managers.tick(t0.plusSeconds(72 * 60))
    assertEquals(item(ManagerItem.Target("zzagent_m2")).status, "pending")
    assert(item(ManagerItem.Target("zzagent_m2")).outcome.exists(_.value.contains("0 open critique pairs")), item(ManagerItem.Target("zzagent_m2")).outcome)
    a.managers.tick(t0.plusSeconds(103 * 60))
    val m2run2 = item(ManagerItem.Target("zzagent_m2")).runId.get
    assertNotEquals(m2run2, m2run)
    settle(m2run2, Ledger.Slug("zzagent_m2"), List(finding(Finding.Id("S1"), Reviewer.Skeptic, Finding.Kind("missing-critique"))))
    a.managers.tick(t0.plusSeconds(104 * 60))
    val stopped = item(ManagerItem.Target("zzagent_m2"))
    assertEquals(
      stopped.state.history.map(h => (h.pairsBefore, h.pairsAfter, h.churn)),
      List((Some(0), Some(0), true), (Some(0), Some(0), true))
    )
    assertEquals(stopped.status, "stopped")
    assert(stopped.outcome.exists(o => o.value.contains("omission churn") && o.value.contains("task #")), stopped.outcome)
    val tasks = QueueRows.list(ds, Queue.Kind.Tasks, all = false).filter(_.text.value.contains("zzagent_m2"))
    assertEquals(tasks.size, 1)
    assert(tasks.head.text.value.contains("review manager stopped: omission churn"), tasks.head.text)
    // the evaluation happened once per run, whatever the ticks
    a.managers.tick(t0.plusSeconds(105 * 60))
    assertEquals(events.count(e => e.kind == StoppedKind && e.itemId.contains(stopped.id)), 1)
    // re-open in one click: pending again, its runs counted from zero
    val re = a.managing.patchItem(ReviewLight,com.histmersion.api.automation.Managers.ManagerItem.Ref("zzagent_m2"), ItemPatch(com.histmersion.api.automation.Managers.ItemPatch.Action("reopen"))).fold(e => fail(e.message.value), identity)
    assertEquals((re.status, re.attempts, re.state.reopened), ("pending", 0, 1))

  test("omission churn no longer stops an article whose open critique pairs go down (§6.8)"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_m5"))
    // two critics of the cited source: the checklist has two open pairs
    List("zzagent2020_critic", "zzagent2021_critic").map(com.histmersion.domain.library.Citekey(_)).foreach { ck =>
      assert(
        h.library.sources
          .put(
            ck,
            Source.Put(title = com.histmersion.api.library.Title(s"Against $ck"), year = (Some(2020)).map(com.histmersion.api.catalogue.Year.apply), body = Some(com.histmersion.api.library.Source.OcrMarkdown("The tin was not from Anatolia.")))
          )
          .isRight
      )
      assert(
        h.library.sources
          .patch(
            ck,
            Source.Patch(edges =
              Some(EdgesPatch(add = List(com.histmersion.api.library.Edge.Input(ApiCk, Edge.Kind.Refutes))))
            )
          )
          .isRight
      )
    }
    activeReview(a, ManagerConfig(maxRunsPerArticle = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRunsPerArticle(4)), noProgressRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.NoProgressRuns(2)), cooldownMinutes = Some(com.histmersion.api.automation.Managers.ManagerConfig.CooldownMinutes(0))))
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_m5"))).fold(e => fail(e.message.value), identity)
    a.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_m5")).state.pairsBefore, Some(2))
    val sk = finding(Finding.Id("S1"), Reviewer.Skeptic, Finding.Kind("missing-critique"))
    settle(item(ManagerItem.Target("zzagent_m5")).runId.get, Ledger.Slug("zzagent_m5"), List(sk))
    // between the runs the article takes up one critique, in the paragraph that cites the source: 2 → 1
    val addressed = Article.Body(Body.value.replace(
      s"Tin reached the Aegean by sea [[$Ck]].",
      s"Tin reached the Aegean by sea [[$Ck]], which [[zzagent2020_critic]] disputes."
    ))
    assert(h.research.articles.patch(com.histmersion.domain.research.Slug("zzagent_m5"), Article.Patch(body = Some(addressed))).isRight)
    assertEquals(h.research.articles.openCritiques(com.histmersion.domain.research.Slug("zzagent_m5")), Some(1))
    // (no cooldown: the tick that evaluates a run dispatches the next)
    a.managers.tick(t0)
    val run2 = item(ManagerItem.Target("zzagent_m5"))
    assertEquals((run2.status, run2.state.pairsBefore), ("active", Some(1)))
    settle(run2.runId.get, Ledger.Slug("zzagent_m5"), List(sk))
    a.managers.tick(t0.plusSeconds(60))
    // the old rule stopped after run 1 (only a fresh missing-critique open); the open-MAJOR rule would
    // stop now (1 → 1 over two runs) — but the pairs went 2 → 1, so the item goes on
    val going = item(ManagerItem.Target("zzagent_m5"))
    assertEquals(going.status, "active", going.outcome)
    assert(going.outcome.exists(_.value.contains("1 open MAJOR, 1 open critique pair after run 2; again")), going.outcome)
    assertEquals(
      going.state.history.map(m => (m.after, m.pairsBefore, m.pairsAfter)),
      List((1, Some(2), Some(1)), (1, Some(1), Some(1)))
    )
    // a third run that leaves the pairs where they were: the window 1 → 1 stalls, omission churn
    settle(going.runId.get, Ledger.Slug("zzagent_m5"), List(sk))
    a.managers.tick(t0.plusSeconds(120))
    assert(item(ManagerItem.Target("zzagent_m5")).outcome.exists(_.value.contains("omission churn")), item(ManagerItem.Target("zzagent_m5")).outcome)

  test("max runs per article and no progress stop with a task; a failed run backs off, the third stops"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_m3"), Slug("zzagent_m4"))
    activeReview(a, ManagerConfig(maxRunsPerArticle = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRunsPerArticle(2)), noProgressRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.NoProgressRuns(1)), cooldownMinutes = Some(com.histmersion.api.automation.Managers.ManagerConfig.CooldownMinutes(0))))
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_m3"))).fold(e => fail(e.message.value), identity)
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_m4"))).fold(e => fail(e.message.value), identity)
    a.managers.tick(t0)
    // m3: no progress in one run (2 before, 2 after)
    ReviewLedgerFixtures.put(
      ds,
      absorb(Ledger(com.histmersion.domain.ReviewLedger.Ledger.Slug("zzagent_m3")), List(finding(Finding.Id("R1"), Reviewer.Rules), finding(Finding.Id("R2"), Reviewer.Rules)), com.histmersion.domain.automation.Run.Id("x"), com.histmersion.domain.ReviewLedger.Closure.Round(1))._1
    )
    val m3 = item(ManagerItem.Target("zzagent_m3"))
    connect(ds)(sql"UPDATE manager_items SET state = '{\"before\":2}'::jsonb WHERE id = ${m3.id}".update.run())
    settle(m3.runId.get, Ledger.Slug("zzagent_m3"), List(finding(Finding.Id("R1"), Reviewer.Rules), finding(Finding.Id("R2"), Reviewer.Rules)))
    // m4: fails three times
    settle(item(ManagerItem.Target("zzagent_m4")).runId.get, Ledger.Slug("zzagent_m4"), Nil, RunState.Failed)
    a.managers.tick(t0)
    assert(item(ManagerItem.Target("zzagent_m3")).outcome.exists(_.value.contains("did not go down over 1 run")), item(ManagerItem.Target("zzagent_m3")).outcome)
    val f1 = item(ManagerItem.Target("zzagent_m4"))
    assertEquals((f1.status, f1.failures, f1.notBefore), ("pending", 1, Some("2026-10-15T12:15:00Z")))
    a.managers.tick(t0.plusSeconds(16 * 60))
    settle(item(ManagerItem.Target("zzagent_m4")).runId.get, Ledger.Slug("zzagent_m4"), Nil, RunState.Failed)
    a.managers.tick(t0.plusSeconds(16 * 60))
    assertEquals(item(ManagerItem.Target("zzagent_m4")).notBefore, Some("2026-10-15T12:46:00Z"))
    a.managers.tick(t0.plusSeconds(47 * 60))
    settle(item(ManagerItem.Target("zzagent_m4")).runId.get, Ledger.Slug("zzagent_m4"), Nil, RunState.Failed)
    a.managers.tick(t0.plusSeconds(47 * 60))
    val f3 = item(ManagerItem.Target("zzagent_m4"))
    assertEquals((f3.status, f3.failures), ("failed", 3))
    assert(QueueRows.list(ds, Queue.Kind.Tasks, all = false).exists(_.text.value.contains("after 3 failed runs")))

  test("capacity: the global cap counts a person's runs; maxRuns; another writer on the article waits"):
    val (a, h) = handlers(PanelClient(Model), baseCfg.modify(_.agents).setTo(baseCfg.agents.modify(_.maxConcurrentRuns).setTo(2)));
    seed(h, Slug("zzagent_c1"), Slug("zzagent_c2"), Slug("zzagent_c3"))
    activeReview(a, ManagerConfig(maxRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRuns(2))))
    // a person's run on c1 takes one of the two places and holds c1
    val mine = a.startRun(RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("review-article"), com.histmersion.api.automation.Agents.RunRequest.Target("zzagent_c1"))).fold(e => fail(e.message.value), identity)
    List("zzagent_c1", "zzagent_c2", "zzagent_c3").foreach(s =>
      a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,s))
    )
    a.managers.tick(t0)
    assertEquals(
      List("zzagent_c1", "zzagent_c2", "zzagent_c3").map(s => item(ManagerItem.Target(s)).status),
      List("pending", "active", "pending")
    )
    assert(
      connect(ds)(ManagerRepo.get(Review)).get.statusNote.exists(_.value.startsWith("at capacity")),
      connect(ds)(ManagerRepo.get(Review)).get.statusNote
    )
    assertEquals(a.engine.cancel(ApiIds.run(mine.id)).map(_.status), Right("cancelled"))
    a.managers.tick(t0)
    // c1 is free now and first in line (oldest of the same priority)
    assertEquals(
      List("zzagent_c1", "zzagent_c2", "zzagent_c3").map(s => item(ManagerItem.Target(s)).status),
      List("active", "active", "pending")
    )

  test("pause stops new runs; pause with runs pauses them and resume brings them back; stop cancels"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_p1"), Slug("zzagent_p2"))
    activeReview(a)
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_p1")))
    a.managing.action(ReviewLight,com.histmersion.domain.automation.managers.ManagersControllers.Action("pause"), None)
    a.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_p1")).status, "pending", "a paused manager starts nothing")
    a.managing.action(ReviewLight,com.histmersion.domain.automation.managers.ManagersControllers.Action("resume"), None)
    a.managers.tick(t0)
    val run = item(ManagerItem.Target("zzagent_p1")).runId.get
    assertEquals(connect(ds)(AgentRepo.get(run)).get.status, "queued")
    val p = a.managing.action(ReviewLight,com.histmersion.domain.automation.managers.ManagersControllers.Action("pause"), Some(com.histmersion.api.automation.Managers.ManagerInfo.PauseRuns(true))).fold(e => fail(e.message.value), identity)
    assert(p.pauseRuns)
    assertEquals(connect(ds)(AgentRepo.get(run)).get.status, "paused")
    // pausing it is not a settle: the item stays active
    a.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_p1")).status, "active")
    a.managing.action(ReviewLight,com.histmersion.domain.automation.managers.ManagersControllers.Action("resume"), None)
    assertEquals(connect(ds)(AgentRepo.get(run)).get.status, "queued")
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_p2")))
    val s = a.managing.action(ReviewLight,com.histmersion.domain.automation.managers.ManagersControllers.Action("stop"), None).fold(e => fail(e.message.value), identity)
    assertEquals(s.state, "stopped")
    assertEquals(connect(ds)(AgentRepo.get(run)).get.status, "cancelled")
    assertEquals(List("zzagent_p1", "zzagent_p2").map(s => item(ManagerItem.Target(s)).status), List("cancelled", "cancelled"))
    assert(a.managers.tick(t0).isEmpty || item(ManagerItem.Target("zzagent_p2")).status.value == "cancelled")

  test("starved: a role with no usable model starts nothing and says so once; freed, it starts"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_s1"))
    activeReview(a, ManagerConfig(models = Some(List(ModelOverride(com.histmersion.api.automation.Agents.ModelOverride.Role("skeptic"), com.histmersion.api.automation.Agents.ModelOverride.Model("local:unpriced"))))))
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_s1")))
    a.managers.tick(t0); a.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_s1")).status, "pending")
    val m = connect(ds)(ManagerRepo.get(Review)).get
    assert(m.statusNote.exists(_.value.startsWith("starved: skeptic")), m.statusNote)
    assertEquals(events.count(_.kind == StarvedKind), 1, "one event per change, not per tick")
    a.managing.update(ReviewLight, ManagerUpdate(Some(ManagerConfig(includeTags = Some(List(IncludeTag))))))
    a.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_s1")).status, "active")

  test("holds: until a date, until a source exists; released by the tick"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_h1"), Slug("zzagent_h2"))
    activeReview(a)
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_h1")))
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_h2")))
    a.managing
      .patchItem(ReviewLight,com.histmersion.api.automation.Managers.ManagerItem.Ref("zzagent_h1"), ItemPatch(com.histmersion.api.automation.Managers.ItemPatch.Action("hold"), until = Some(com.histmersion.api.automation.Managers.ItemPatch.Until("2026-10-16"))))
      .fold(e => fail(e.message.value), identity)
    a.managing
      .patchItem(ReviewLight,com.histmersion.api.automation.Managers.ManagerItem.Ref("zzagent_h2"), ItemPatch(com.histmersion.api.automation.Managers.ItemPatch.Action("hold"), untilSource = Some(com.histmersion.api.automation.Managers.ItemPatch.UntilSource("zzagent2030_new"))))
      .fold(e => fail(e.message.value), identity)
    a.managers.tick(t0)
    assertEquals(List("zzagent_h1", "zzagent_h2").map(s => item(ManagerItem.Target(s)).status), List("held", "held"))
    assert(h.library.sources.put(com.histmersion.domain.library.Citekey("zzagent2030_new"), Source.Put(title = com.histmersion.api.library.Title("New"), year = (Some(2030)).map(com.histmersion.api.catalogue.Year.apply))).isRight)
    a.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_h2")).status, "active")
    a.managers.tick(Instant.parse("2026-10-16T00:00:01Z"))
    assertEquals(item(ManagerItem.Target("zzagent_h1")).status, "active")

  test("crash: a run committed but never submitted runs exactly once on restart, and is evaluated once"):
    val c = PanelClient(Model)
    val (a, h) = handlers(c); seed(h, Slug("zzagent_k1"))
    activeReview(a)
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_k1")))
    a.managers.tick(t0) // committed and submitted to an engine that never started: as if the process died
    val run = item(ManagerItem.Target("zzagent_k1")).runId.get
    // a fresh process: its engine re-enqueues the queued run, which passes at once
    c.add("rules", Seq(AgentResponse(rulesPass, Nil, StopReason.EndTurn, Usage1, Some("scripted"))))
    c.add("cold", Seq(AgentResponse(coldPass, Nil, StopReason.EndTurn, Usage1, Some("scripted"))))
    c.add("skeptic", Seq(AgentResponse(skepticPass, Nil, StopReason.EndTurn, Usage1, Some("scripted"))))
    val (b, _) = handlers(c)
    val (_, queued) = b.engine.reconcile()
    assertEquals(queued.count(_ == ApiIds.run(run)), 1)
    b.engine.execute(ApiIds.run(run))
    b.engine.execute(ApiIds.run(run)) // a second delivery does nothing: it is no longer queued
    assertEquals(connect(ds)(AgentRepo.get(run)).get.status, "done")
    assertEquals(c.served("rules"), 1)
    // the settle was never evaluated (the process died again): the next process evaluates it once
    val (d, _) = handlers(c)
    d.managers.tick(t0); d.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_k1")).status, "done")
    assertEquals(events.count(e => e.kind == EvaluatedKind && e.runId.contains(run)), 1)

  test("crash: a manager's run running at boot is paused interrupted and resumes by itself"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_b1"))
    activeReview(a)
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_b1")))
    a.managers.tick(t0)
    val run = item(ManagerItem.Target("zzagent_b1")).runId.get
    assert(TestRuns.transition(ds, ApiIds.run(run), RunState.Queued, RunState.Running).isDefined)
    val (b, _) = handlers(PanelClient(Model))
    b.engine.reconcile()
    val r = connect(ds)(AgentRepo.get(run)).get
    assertEquals((r.status, r.error, r.resumeAt.isDefined), ("paused", Some("interrupted"), true))
    b.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_b1")).status, "active", "a paused run keeps its item active")
    assert(b.engine.resumeDue(Instant.now().plusSeconds(120)).contains(ApiIds.run(run)))
    assertEquals(connect(ds)(AgentRepo.get(run)).get.status, "queued")

  test(
    "restart: runs interrupted or paused at boot hold their places; maxRuns 2 never has more than 2 running (#1803)"
  ):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_r1"), Slug("zzagent_r2"), Slug("zzagent_r3"), Slug("zzagent_r4"))
    activeReview(a, ManagerConfig(maxRuns = Some(com.histmersion.api.automation.Managers.ManagerConfig.MaxRuns(2)), cooldownMinutes = Some(com.histmersion.api.automation.Managers.ManagerConfig.CooldownMinutes(0))))
    List("zzagent_r1", "zzagent_r2").foreach(s => a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue, EnqueueRequest.Target(s))))
    a.managers.tick(t0)
    val (run1, run2) = (item(ManagerItem.Target("zzagent_r1")).runId.get, item(ManagerItem.Target("zzagent_r2")).runId.get)
    // at the crash: r1 was running, r2 had stalled (paused, resuming by itself); two more items wait
    assert(TestRuns.transition(ds, ApiIds.run(run1), RunState.Queued, RunState.Running).isDefined)
    assert(TestRuns.transition(ds, ApiIds.run(run2), RunState.Queued, RunState.Running).isDefined)
    assert(
      TestRuns
        .transition(
          ds,
          ApiIds.run(run2),
          RunState.Running,
          RunState.Paused,
          error = Some(com.histmersion.util.jobs.JobRow.Error("stalled: no sign of life")),
          resumeAt = Some(Instant.now().plusSeconds(600))
        )
        .isDefined
    )
    List("zzagent_r3", "zzagent_r4").foreach(s => a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue, EnqueueRequest.Target(s))))
    def live = connect(ds)(sql"""SELECT count(*)::int FROM jobs WHERE kind = 'agent' AND manager_id = $ReviewId
                                AND status IN ('queued', 'running')""".query[Int].run().head)
    // the restart: boot pauses r1 as interrupted; the first tick must not take its place
    val (b, _) = handlers(PanelClient(Model))
    b.engine.reconcile()
    assertEquals(connect(ds)(AgentRepo.get(run1)).map(r => (r.status, r.resumeAt.isDefined)), Some(("paused", true)))
    assertEquals(
      connect(ds)(ManagerRepo.running(Review)),
      2,
      "an interrupted and a stalled run both resume by themselves"
    )
    b.managers.tick(t0)
    assertEquals(List("zzagent_r3", "zzagent_r4").map(s => item(ManagerItem.Target(s)).status), List("pending", "pending"))
    // the engine resumes both: still two, and the next tick starts nothing
    assertEquals(b.engine.resumeDue(Instant.now().plusSeconds(1200)).toSet, Set(ApiIds.run(run1), ApiIds.run(run2)))
    assertEquals(live, 2)
    b.managers.tick(t0)
    assertEquals(live, 2)
    assertEquals(List("zzagent_r3", "zzagent_r4").map(s => item(ManagerItem.Target(s)).status), List("pending", "pending"))
    // a run waiting for a person's approval frees its place (#3762 proposal 1): the next item starts
    connect(ds)(sql"UPDATE jobs SET status = 'waiting' WHERE id = $run1".update.run())
    b.managers.tick(t0)
    assertEquals(List("zzagent_r3", "zzagent_r4").map(s => item(ManagerItem.Target(s)).status), List("active", "pending"))
    assertEquals(connect(ds)(ManagerRepo.running(Review)), 2, "run2 and run3: the waiting run1 holds no place")
    // decided, run1 goes on beside them (the cap overshoots until runs settle); nothing new starts meanwhile
    connect(ds)(sql"UPDATE jobs SET status = 'queued' WHERE id = $run1".update.run())
    b.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_r4")).status, "pending")
    settle(run2, Ledger.Slug("zzagent_r2"), Nil)
    b.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_r4")).status, "pending", "run1 and run3 hold the two places")
    // a pause only a person lifts frees its place: resuming it is their call
    val run3 = item(ManagerItem.Target("zzagent_r3")).runId.get
    assert(
      TestRuns
        .transition(ds, ApiIds.run(run3), RunState.Queued, RunState.Paused, error = Some(com.histmersion.util.jobs.JobRow.Error("requested: paused on request")))
        .isDefined
    )
    assertEquals(connect(ds)(ManagerRepo.running(Review)), 1)

  test("lease: one holder; another takes over only after it expires; renewing keeps it"):
    assert(transact(ds)(LeaseRepo.claim(com.histmersion.domain.automation.agents.LeaseRepo.LeaseName("zzlease"), com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner("one"), com.histmersion.domain.automation.agents.LeaseRepo.TtlSeconds(2))))
    assert(!transact(ds)(LeaseRepo.claim(com.histmersion.domain.automation.agents.LeaseRepo.LeaseName("zzlease"), com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner("two"), com.histmersion.domain.automation.agents.LeaseRepo.TtlSeconds(2))))
    assert(transact(ds)(LeaseRepo.claim(com.histmersion.domain.automation.agents.LeaseRepo.LeaseName("zzlease"), com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner("one"), com.histmersion.domain.automation.agents.LeaseRepo.TtlSeconds(2))), "the holder renews")
    Thread.sleep(2300)
    assert(transact(ds)(LeaseRepo.claim(com.histmersion.domain.automation.agents.LeaseRepo.LeaseName("zzlease"), com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner("two"), com.histmersion.domain.automation.agents.LeaseRepo.TtlSeconds(2))), "taken over after the ttl")
    assert(!transact(ds)(LeaseRepo.claim(com.histmersion.domain.automation.agents.LeaseRepo.LeaseName("zzlease"), com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner("one"), com.histmersion.domain.automation.agents.LeaseRepo.TtlSeconds(2))), "the old holder lost it")
    assertEquals(connect(ds)(LeaseRepo.holder(com.histmersion.domain.automation.agents.LeaseRepo.LeaseName("zzlease"))).map(_._1), Some("two"))
    transact(ds)(LeaseRepo.release(com.histmersion.domain.automation.agents.LeaseRepo.LeaseName("zzlease"), com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner("two")))
    assert(transact(ds)(LeaseRepo.claim(com.histmersion.domain.automation.agents.LeaseRepo.LeaseName("zzlease"), com.histmersion.domain.automation.agents.LeaseRepo.LeaseOwner("one"), com.histmersion.domain.automation.agents.LeaseRepo.TtlSeconds(2))), "free once released")

  test("lease: a second server's agents wait for the lease; its holder's start runs them"):
    val (a, _) = handlers(PanelClient(Model))
    val other = AgentLease(ds, com.histmersion.domain.automation.agents.AgentLease.TtlSeconds(60), owner = com.histmersion.domain.automation.agents.AgentLease.Owner("elsewhere:1"))
    connect(ds)(sql"DELETE FROM agent_leases WHERE name = 'agents'".update.run())
    assert(other.claim())
    val started = java.util.concurrent.atomic.AtomicBoolean(false)
    val mine = AgentLease(ds, com.histmersion.domain.automation.agents.AgentLease.TtlSeconds(60), owner = com.histmersion.domain.automation.agents.AgentLease.Owner("here:2"))
    assert(!mine.start(() => started.set(true)))
    assert(!started.get)
    assertEquals(mine.status.map(s => (s._1, s._4)), Some(("elsewhere:1", false)))
    other.stop()
    assert(mine.claim())
    mine.stop()
    connect(ds)(sql"DELETE FROM agent_leases WHERE name = 'agents'".update.run())

  test(
    "hints: snapshotted into the run, they reach the fixer's and the skeptic's user message; a once-hint is retired"
  ):
    val c = PanelClient(Model)
    val (a, h) = handlers(c); seed(h, Slug("zzagent_n1"))
    activeReview(a, ManagerConfig(rounds = Some(com.histmersion.api.automation.Managers.ManagerConfig.Rounds(2))))
    a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue,com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_n1")))
    val standing = a.managing.addHint(ReviewLight,HintAdd(com.histmersion.api.automation.Managers.HintAdd.Text("Prefer the 2019 dating."))).fold(e => fail(e.message.value), identity)
    val once = a.managing
      .addHint(ReviewLight,HintAdd(com.histmersion.api.automation.Managers.HintAdd.Text("Check the tin isotopes this time."), item = Some(com.histmersion.api.automation.Managers.HintAdd.Item("zzagent_n1")), once = Some(com.histmersion.api.automation.Managers.HintAdd.Once(true))))
      .fold(e => fail(e.message.value), identity)
    a.managers.tick(t0)
    val run = item(ManagerItem.Target("zzagent_n1")).runId.get
    assertEquals(
      connect(ds)(AgentRepo.get(run)).get.params.notes,
      Some(List("Prefer the 2019 dating.", "Check the tin isotopes this time."))
    )
    assert(Transactor(ds).read(ManagerRepo.hint(once.id)).get.retiredAt.isDefined, "a once-hint is used up")
    assert(Transactor(ds).read(ManagerRepo.hint(standing.id)).get.retiredAt.isEmpty)
    assertEquals(Transactor(ds).read(ManagerRepo.hint(standing.id)).get.uses, 1)
    // the run: a rules MAJOR, the fixer's gloss, verified, then a clean final panel
    def text(j: String) = AgentResponse(j, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
    c.add("rules", Seq(text(rulesMajor)), Seq(text(glossFixed)), Seq(text(rulesPass)))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skepticPass)), Seq(text(skepticPass)))
    c.add("fixer", Seq(text(fixerGloss)))
    a.engine.execute(ApiIds.run(run))
    assertEquals(connect(ds)(AgentRepo.get(run)).get.status, "done", connect(ds)(AgentRepo.get(run)).get.error)
    def user(w: String) = c.requests
      .get(w)
      .asScala
      .toList
      .map(
        _.head.history.entries
          .collectFirst { case ConversationEntry.UserPrompt(p) =>
            p
          }
          .getOrElse("")
      )
    val notes = "<operator-notes>\n- Prefer the 2019 dating.\n- Check the tin isotopes this time.\n</operator-notes>"
    assert(user("fixer").forall(_.contains(notes)), user("fixer").head.takeRight(400))
    assert(user("skeptic").nonEmpty && user("skeptic").forall(_.contains(notes)))
    assert(user("rules").forall(!_.contains("operator-notes")), "the rules reviewer is not given the notes")
    assert(user("cold").forall(!_.contains("operator-notes")), "nor the cold reader")
    // the next run gets only the standing hint
    a.managers.tick(t0)
    assertEquals(item(ManagerItem.Target("zzagent_n1")).status, "done")

  test("hint effects: prioritise by target, hold until a date, skip"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_e1"), Slug("zzagent_e2"), Slug("zzagent_e3"))
    a.managers.ensureDefaults()
    List("zzagent_e1", "zzagent_e2").foreach(s =>
      a.managing.addItem(ReviewLight, EnqueueRequest(ReviewEnqueue, EnqueueRequest.Target(s), Some(com.histmersion.api.automation.Managers.EnqueueRequest.Priority(0))))
    )
    a.managing.addHint(
      ReviewLight,
      HintAdd(
        com.histmersion.api.automation.Managers.HintAdd.Text("e3 first"),
        effect = Some(HintEffect(HintEffect.Action(HintEffect.Prioritise), targets = List(com.histmersion.api.automation.Managers.HintEffect.Targets("zzagent_e3")), priority = Some(com.histmersion.api.automation.Managers.HintEffect.Priority(7))))
      )
    )
    assertEquals(item(ManagerItem.Target("zzagent_e3")).priority, 7)
    a.managing.addHint(
      ReviewLight,
      HintAdd(
        com.histmersion.api.automation.Managers.HintAdd.Text("wait for the new edition"),
        effect = Some(HintEffect(HintEffect.Action(HintEffect.HoldAction), targets = List(com.histmersion.api.automation.Managers.HintEffect.Targets("zzagent_e1")), until = Some(HintEffect.Until("2027-01-01"))))
      )
    )
    assertEquals(item(ManagerItem.Target("zzagent_e1")).status, "held")
    assertEquals(item(ManagerItem.Target("zzagent_e1")).hold.flatMap(_.until), Some("2027-01-01"))
    a.managing.addHint(ReviewLight,HintAdd(com.histmersion.api.automation.Managers.HintAdd.Text("not this one"), effect = Some(HintEffect(HintEffect.Action(HintEffect.Skip), tag = Some(SkipTag)))))
    assertEquals(item(ManagerItem.Target("zzagent_e2")).status, "stopped")
    assert(
      a.managing.addHint(ReviewLight,HintAdd(com.histmersion.api.automation.Managers.HintAdd.Text("x"), effect = Some(HintEffect(com.histmersion.api.automation.Managers.HintEffect.Action("frobnicate"), targets = List(com.histmersion.api.automation.Managers.HintEffect.Targets("a")))))).isLeft
    )
    assert(events.exists(e => e.kind == HintKind && e.text.value.contains("hold: 1 item")), events.map(_.text))

  test("the Review button: enqueue routes to the review manager (created paused), priority 10"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, Slug("zzagent_q1"))
    val i = a.managing.enqueue(EnqueueRequest(com.histmersion.api.automation.Managers.EnqueueRequest.Kind("review"), com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_q1"))).fold(e => fail(e.message.value), _.items.head)
    assertEquals((i.managerId, i.priority, i.status, i.origin), (ReviewId, 10, "pending", "person"))
    assertEquals(connect(ds)(ManagerRepo.get(Review)).map(_.state), Some("paused"))
    assert(a.managing.enqueue(EnqueueRequest(com.histmersion.api.automation.Managers.EnqueueRequest.Kind("review"), com.histmersion.api.automation.Managers.EnqueueRequest.Target("zzagent_nope"))).isLeft)

  // ── scripted answers (the shapes ReviewArticleRunSuite uses) ────────────────────────────────────

  private def findingJson(sev: String, where: String, problem: String) =
    s"""{"severity":"$sev","kind":"term","where":"$where","quote":"","problem":"$problem","fix":"fix it","evidence":[]}"""
  private def rules(verdict: String, findings: String*) =
    s"""{"verdict":"$verdict","findings":[${findings.mkString(
        ","
      )}],"filter":"topics:[tin] → 2","uncitedRelevant":[],"statusesSilent":[],"spotChecks":[],"checks":[]}"""
  private val rulesPass = rules("PASS")
  private val rulesMajor = rules("MAJOR", findingJson("major", "Intro", "tin is not defined"))
  private val coldPass =
    """{"verdict":"PASS","explainBack":"It asks how tin reached the Aegean. By sea. The date is argued. The mines are open.","emptySlots":[],"findings":[],"termsUndefined":0,"opaqueSentences":0,"lostAt":null}"""
  private val skepticPass = """{"verdict":"PASS","findings":[],"proposals":[],"claimsChecked":6,"checks":[]}"""
  private val glossFixed =
    """{"checks":[{"id":"R1","fixed":true,"quote":"Tin, the scarce half of bronze, reached the Aegean by sea","note":"checked"}],"findings":[],"lessons":[]}"""
  private val fixerGloss =
    """{"edits":[{"op":"replace","anchor":"Tin reached the Aegean by sea","text":"Tin, the scarce half of bronze, reached the Aegean by sea"}],"applied":["R1"],"rejected":[],"unresolvedMoved":[],"notes":"","reasons":[]}"""
