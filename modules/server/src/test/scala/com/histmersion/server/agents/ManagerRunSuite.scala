package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.Managers.*
import com.histmersion.core.ReviewArticle.{Finding, Reviewer, Severity}
import com.histmersion.core.ReviewLedger.*
import com.histmersion.db.{AgentRepo, Db, ManagerRepo, Migrate, QueueRepo, ReviewLedgerRepo}
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.*

import java.time.Instant
import scala.jdk.CollectionConverters.*

/** The process managers against a `*_test` database (docs/research-process-managers.md §6): the
  * supervisor's tick with a fixed clock — selection, dispatch, evaluation and the stop rules over
  * several runs, holds, pause (with and without its runs), starvation, the global cap — crash and
  * restart (a run committed but never submitted, a settle never evaluated, a run interrupted at boot),
  * the lease, and the hints (snapshotted into the run, reaching the fixer's and the skeptic's user
  * message, once-hints retired). Runs are scripted ([[PanelClient]]) or settled by hand; no provider
  * is ever called. Skipped without HISTMERSION_TEST_DB_URL. */
class ManagerRunSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout         = scala.concurrent.duration.Duration(180, "s")

  private val Tag   = "zzmgr"
  private val Ck    = "zzagent2019_tin"
  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))
  private val t0 = Instant.parse("2026-10-15T12:00:00Z")

  private val Body =
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

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(enabled = true, prices = "scripted=1/2",
        roles = b.agents.roles.copy(reviewer = Model.wire, coldReader = Model.wire, skeptic = Model.wire, fixer = Model.wire),
        managers = b.agents.managers.copy(review = b.agents.managers.review.copy(cooldownMinutes = 30, inferFilter = false)),
        // the scripted rules reviewer looks nothing up: ReviewArticleRunSuite holds the spot-checks
        review = b.agents.review.copy(spotChecks = 0)))

  private lazy val ds = Db.dataSource(baseCfg.db)

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers) =
    val deps = Deps(cfg, ds)
    val h    = Handlers(deps)
    (AgentsHandlers(deps, h, (_, _) => Right(c)), h)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM agent_leases WHERE name LIKE 'zz%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run()
      sql"DELETE FROM approvals WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM article_versions WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM review_ledgers WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit   = clean()

  private def seed(h: Handlers, slugs: String*): Unit =
    assert(h.putSource(Ck, SourcePut(title = "Bronze Age tin", year = Some(2019),
      body = Some("The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."))).isRight)
    slugs.foreach(s => assert(h.putArticle(s, ArticlePut(s"Tin $s", "concept", Body, tags = List(Tag))).isRight))

  /** The review manager, active, narrowed to this suite's articles. */
  private def activeReview(a: AgentsHandlers, cfg: ManagerConfig = ManagerConfig()): ManagerInfo =
    a.managers.ensureDefaults()
    val m = a.managing.update(ReviewId, ManagerUpdate(Some(cfg.copy(includeTags = Some(List(Tag)))))).fold(e => fail(e.message), identity)
    assertEquals(m.state, "paused", "created paused")
    a.managing.action(ReviewId, "resume", None).fold(e => fail(e.message), identity)

  private def finding(id: String, r: Reviewer, kind: String = "synth") =
    Finding(id, r, Severity.Major, kind, s"where $id", s"quote $id", s"problem $id", "fix")

  /** Settle a manager's run by hand: done (with a ledger of `majors` open MAJORs raised by it), or another state. */
  private def settle(run: String, slug: String, majors: List[Finding], status: RunState = RunState.Done): Unit =
    assert(AgentRepo.transition(ds, run, RunState.Queued, RunState.Running).isDefined)
    val l = absorb(Ledger(slug), majors, run, 1)._1
    ReviewLedgerRepo.put(ds, l)
    assert(AgentRepo.transition(ds, run, RunState.Running, status, error = Option.when(status == RunState.Failed)("boom")).isDefined)

  private def item(slug: String): ManagerItem = ManagerRepo.itemFor(ds, ReviewId, ArticleReview, slug).get
  private def events: List[ManagerEvent] = ManagerRepo.events(ds, ReviewId, None, 200).reverse

  test("selection, dispatch and the stop rules over runs: 3 runs to converge; max runs, no progress, churn"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_m1", "zzagent_m2")
    activeReview(a, ManagerConfig(maxRuns = Some(2), maxRunsPerArticle = Some(3)))
    // m1 has open MAJORs (selected first), m2 was never reviewed
    ReviewLedgerRepo.put(ds, absorb(Ledger("zzagent_m1"), List(finding("R1", Reviewer.Rules), finding("S1", Reviewer.Skeptic)), "old", 1)._1)
    val out1 = a.managers.tick(t0)
    assert(out1.exists(_.contains("selected 2")), out1)
    val i1 = item("zzagent_m1"); val i2 = item("zzagent_m2")
    assertEquals((i1.priority, i2.priority), (1, 0))
    assertEquals((i1.status, i2.status), ("active", "active"))
    val r1 = AgentRepo.get(ds, i1.runId.get).get
    assertEquals((r1.origin, r1.managerId, r1.managerItem, r1.status), (Some("manager"), Some(ReviewId), Some(i1.id), "queued"))
    assertEquals((r1.params.fix, r1.params.rounds), (Some(true), Some(3)))
    assertEquals(i1.state.before, Some(2))
    // m1: 2 → 1 → 0 over three runs; each Again waits for the cooldown
    settle(r1.id, "zzagent_m1", List(finding("R1", Reviewer.Rules)))
    a.managers.tick(t0)
    assertEquals(item("zzagent_m1").status, "pending")
    assertEquals(item("zzagent_m1").notBefore, Some("2026-10-15T12:30:00Z"))
    a.managers.tick(t0.plusSeconds(60))
    assertEquals(item("zzagent_m1").status, "pending", "not before the cooldown")
    a.managers.tick(t0.plusSeconds(31 * 60))
    val run2 = item("zzagent_m1").runId.get
    assertNotEquals(run2, r1.id)
    settle(run2, "zzagent_m1", List(finding("R2", Reviewer.Rules)))
    a.managers.tick(t0.plusSeconds(32 * 60))
    a.managers.tick(t0.plusSeconds(70 * 60))
    val run3 = item("zzagent_m1").runId.get
    settle(run3, "zzagent_m1", Nil)
    a.managers.tick(t0.plusSeconds(71 * 60))
    val done = item("zzagent_m1")
    assertEquals((done.status, done.outcome, done.attempts), ("done", Some("converged after 3 runs"), 3))
    assertEquals(done.state.history.map(_.after), List(1, 1, 0))
    // m2: runs that end with only a missing-critique MAJOR while the article's critique checklist has no
    // open pair to work down (0 → 0): the first is no window yet (again), the second is omission churn
    val m2run = item("zzagent_m2").runId.get
    assertEquals(item("zzagent_m2").state.pairsBefore, Some(0))
    settle(m2run, "zzagent_m2", List(finding("S1", Reviewer.Skeptic, "missing-critique")))
    a.managers.tick(t0.plusSeconds(72 * 60))
    assertEquals(item("zzagent_m2").status, "pending")
    assert(item("zzagent_m2").outcome.exists(_.contains("0 open critique pairs")), item("zzagent_m2").outcome)
    a.managers.tick(t0.plusSeconds(103 * 60))
    val m2run2 = item("zzagent_m2").runId.get
    assertNotEquals(m2run2, m2run)
    settle(m2run2, "zzagent_m2", List(finding("S1", Reviewer.Skeptic, "missing-critique")))
    a.managers.tick(t0.plusSeconds(104 * 60))
    val stopped = item("zzagent_m2")
    assertEquals(stopped.state.history.map(h => (h.pairsBefore, h.pairsAfter, h.churn)),
      List((Some(0), Some(0), true), (Some(0), Some(0), true)))
    assertEquals(stopped.status, "stopped")
    assert(stopped.outcome.exists(o => o.contains("omission churn") && o.contains("task #")), stopped.outcome)
    val tasks = QueueRepo.list(ds, Queue.Kind.Tasks, all = false).filter(_.text.contains("zzagent_m2"))
    assertEquals(tasks.size, 1)
    assert(tasks.head.text.contains("review manager stopped: omission churn"), tasks.head.text)
    // the evaluation happened once per run, whatever the ticks
    a.managers.tick(t0.plusSeconds(105 * 60))
    assertEquals(events.count(e => e.kind == Feed.Stopped && e.itemId.contains(stopped.id)), 1)
    // re-open in one click: pending again, its runs counted from zero
    val re = a.managing.patchItem(ReviewId, "zzagent_m2", ItemPatch("reopen")).fold(e => fail(e.message), identity)
    assertEquals((re.status, re.attempts, re.state.reopened), ("pending", 0, 1))

  test("omission churn no longer stops an article whose open critique pairs go down (§6.8)"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_m5")
    // two critics of the cited source: the checklist has two open pairs
    List("zzagent2020_critic", "zzagent2021_critic").foreach { ck =>
      assert(h.putSource(ck, SourcePut(title = s"Against $ck", year = Some(2020), body = Some("The tin was not from Anatolia."))).isRight)
      assert(h.patchSource(ck, SourcePatch(edges = Some(EdgesPatch(add = List(EdgeInput(Ck, EdgeKind.Refutes)))))).isRight)
    }
    activeReview(a, ManagerConfig(maxRunsPerArticle = Some(4), noProgressRuns = Some(2), cooldownMinutes = Some(0)))
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_m5")).fold(e => fail(e.message), identity)
    a.managers.tick(t0)
    assertEquals(item("zzagent_m5").state.pairsBefore, Some(2))
    val sk = finding("S1", Reviewer.Skeptic, "missing-critique")
    settle(item("zzagent_m5").runId.get, "zzagent_m5", List(sk))
    // between the runs the article takes up one critique, in the paragraph that cites the source: 2 → 1
    val addressed = Body.replace(s"Tin reached the Aegean by sea [[$Ck]].",
      s"Tin reached the Aegean by sea [[$Ck]], which [[zzagent2020_critic]] disputes.")
    assert(h.patchArticle("zzagent_m5", ArticlePatch(body = Some(addressed))).isRight)
    assertEquals(h.openCritiques("zzagent_m5"), Some(1))
    // (no cooldown: the tick that evaluates a run dispatches the next)
    a.managers.tick(t0)
    val run2 = item("zzagent_m5")
    assertEquals((run2.status, run2.state.pairsBefore), ("active", Some(1)))
    settle(run2.runId.get, "zzagent_m5", List(sk))
    a.managers.tick(t0.plusSeconds(60))
    // the old rule stopped after run 1 (only a fresh missing-critique open); the open-MAJOR rule would
    // stop now (1 → 1 over two runs) — but the pairs went 2 → 1, so the item goes on
    val going = item("zzagent_m5")
    assertEquals(going.status, "active", going.outcome)
    assert(going.outcome.exists(_.contains("1 open MAJOR, 1 open critique pair after run 2; again")), going.outcome)
    assertEquals(going.state.history.map(m => (m.after, m.pairsBefore, m.pairsAfter)), List((1, Some(2), Some(1)), (1, Some(1), Some(1))))
    // a third run that leaves the pairs where they were: the window 1 → 1 stalls, omission churn
    settle(going.runId.get, "zzagent_m5", List(sk))
    a.managers.tick(t0.plusSeconds(120))
    assert(item("zzagent_m5").outcome.exists(_.contains("omission churn")), item("zzagent_m5").outcome)

  test("max runs per article and no progress stop with a task; a failed run backs off, the third stops"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_m3", "zzagent_m4")
    activeReview(a, ManagerConfig(maxRunsPerArticle = Some(2), noProgressRuns = Some(1), cooldownMinutes = Some(0)))
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_m3")).fold(e => fail(e.message), identity)
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_m4")).fold(e => fail(e.message), identity)
    a.managers.tick(t0)
    // m3: no progress in one run (2 before, 2 after)
    ReviewLedgerRepo.put(ds, absorb(Ledger("zzagent_m3"), List(finding("R1", Reviewer.Rules), finding("R2", Reviewer.Rules)), "x", 1)._1)
    val m3 = item("zzagent_m3")
    connect(ds)(sql"UPDATE manager_items SET state = '{\"before\":2}'::jsonb WHERE id = ${m3.id}".update.run())
    settle(m3.runId.get, "zzagent_m3", List(finding("R1", Reviewer.Rules), finding("R2", Reviewer.Rules)))
    // m4: fails three times
    settle(item("zzagent_m4").runId.get, "zzagent_m4", Nil, RunState.Failed)
    a.managers.tick(t0)
    assert(item("zzagent_m3").outcome.exists(_.contains("did not go down over 1 run")), item("zzagent_m3").outcome)
    val f1 = item("zzagent_m4")
    assertEquals((f1.status, f1.failures, f1.notBefore), ("pending", 1, Some("2026-10-15T12:15:00Z")))
    a.managers.tick(t0.plusSeconds(16 * 60))
    settle(item("zzagent_m4").runId.get, "zzagent_m4", Nil, RunState.Failed)
    a.managers.tick(t0.plusSeconds(16 * 60))
    assertEquals(item("zzagent_m4").notBefore, Some("2026-10-15T12:46:00Z"))
    a.managers.tick(t0.plusSeconds(47 * 60))
    settle(item("zzagent_m4").runId.get, "zzagent_m4", Nil, RunState.Failed)
    a.managers.tick(t0.plusSeconds(47 * 60))
    val f3 = item("zzagent_m4")
    assertEquals((f3.status, f3.failures), ("failed", 3))
    assert(QueueRepo.list(ds, Queue.Kind.Tasks, all = false).exists(_.text.contains("after 3 failed runs")))

  test("capacity: the global cap counts a person's runs; maxRuns; another writer on the article waits"):
    val (a, h) = handlers(PanelClient(Model), baseCfg.copy(agents = baseCfg.agents.copy(maxConcurrentRuns = 2))); seed(h, "zzagent_c1", "zzagent_c2", "zzagent_c3")
    activeReview(a, ManagerConfig(maxRuns = Some(2)))
    // a person's run on c1 takes one of the two places and holds c1
    val mine = a.startRun(RunRequest("review-article", "zzagent_c1")).fold(e => fail(e.message), identity)
    List("zzagent_c1", "zzagent_c2", "zzagent_c3").foreach(s => a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, s)))
    a.managers.tick(t0)
    assertEquals(List("zzagent_c1", "zzagent_c2", "zzagent_c3").map(item(_).status), List("pending", "active", "pending"))
    assert(ManagerRepo.get(ds, ReviewId).get.statusNote.exists(_.startsWith("at capacity")), ManagerRepo.get(ds, ReviewId).get.statusNote)
    assertEquals(a.engine.cancel(mine.id).map(_.status), Right("cancelled"))
    a.managers.tick(t0)
    // c1 is free now and first in line (oldest of the same priority)
    assertEquals(List("zzagent_c1", "zzagent_c2", "zzagent_c3").map(item(_).status), List("active", "active", "pending"))

  test("pause stops new runs; pause with runs pauses them and resume brings them back; stop cancels"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_p1", "zzagent_p2")
    activeReview(a)
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_p1"))
    a.managing.action(ReviewId, "pause", None)
    a.managers.tick(t0)
    assertEquals(item("zzagent_p1").status, "pending", "a paused manager starts nothing")
    a.managing.action(ReviewId, "resume", None)
    a.managers.tick(t0)
    val run = item("zzagent_p1").runId.get
    assertEquals(AgentRepo.get(ds, run).get.status, "queued")
    val p = a.managing.action(ReviewId, "pause", Some(true)).fold(e => fail(e.message), identity)
    assert(p.pauseRuns)
    assertEquals(AgentRepo.get(ds, run).get.status, "paused")
    // pausing it is not a settle: the item stays active
    a.managers.tick(t0)
    assertEquals(item("zzagent_p1").status, "active")
    a.managing.action(ReviewId, "resume", None)
    assertEquals(AgentRepo.get(ds, run).get.status, "queued")
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_p2"))
    val s = a.managing.action(ReviewId, "stop", None).fold(e => fail(e.message), identity)
    assertEquals(s.state, "stopped")
    assertEquals(AgentRepo.get(ds, run).get.status, "cancelled")
    assertEquals(List("zzagent_p1", "zzagent_p2").map(item(_).status), List("cancelled", "cancelled"))
    assert(a.managers.tick(t0).isEmpty || item("zzagent_p2").status == "cancelled")

  test("starved: a role with no usable model starts nothing and says so once; freed, it starts"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_s1")
    activeReview(a, ManagerConfig(models = Some(List(ModelOverride("skeptic", "local:unpriced")))))
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_s1"))
    a.managers.tick(t0); a.managers.tick(t0)
    assertEquals(item("zzagent_s1").status, "pending")
    val m = ManagerRepo.get(ds, ReviewId).get
    assert(m.statusNote.exists(_.startsWith("starved: skeptic")), m.statusNote)
    assertEquals(events.count(_.kind == Feed.Starved), 1, "one event per change, not per tick")
    a.managing.update(ReviewId, ManagerUpdate(Some(ManagerConfig(includeTags = Some(List(Tag))))))
    a.managers.tick(t0)
    assertEquals(item("zzagent_s1").status, "active")

  test("holds: until a date, until a source exists; released by the tick"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_h1", "zzagent_h2")
    activeReview(a)
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_h1"))
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_h2"))
    a.managing.patchItem(ReviewId, "zzagent_h1", ItemPatch("hold", until = Some("2026-10-16"))).fold(e => fail(e.message), identity)
    a.managing.patchItem(ReviewId, "zzagent_h2", ItemPatch("hold", untilSource = Some("zzagent2030_new"))).fold(e => fail(e.message), identity)
    a.managers.tick(t0)
    assertEquals(List("zzagent_h1", "zzagent_h2").map(item(_).status), List("held", "held"))
    assert(h.putSource("zzagent2030_new", SourcePut(title = "New", year = Some(2030))).isRight)
    a.managers.tick(t0)
    assertEquals(item("zzagent_h2").status, "active")
    a.managers.tick(Instant.parse("2026-10-16T00:00:01Z"))
    assertEquals(item("zzagent_h1").status, "active")

  test("crash: a run committed but never submitted runs exactly once on restart, and is evaluated once"):
    val c = PanelClient(Model)
    val (a, h) = handlers(c); seed(h, "zzagent_k1")
    activeReview(a)
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_k1"))
    a.managers.tick(t0) // committed and submitted to an engine that never started: as if the process died
    val run = item("zzagent_k1").runId.get
    // a fresh process: its engine re-enqueues the queued run, which passes at once
    c.add("rules", Seq(AgentResponse(rulesPass, Nil, StopReason.EndTurn, Usage1, Some("scripted"))))
    c.add("cold", Seq(AgentResponse(coldPass, Nil, StopReason.EndTurn, Usage1, Some("scripted"))))
    c.add("skeptic", Seq(AgentResponse(skepticPass, Nil, StopReason.EndTurn, Usage1, Some("scripted"))))
    val (b, _) = handlers(c)
    val (_, queued) = b.engine.reconcile()
    assertEquals(queued.count(_ == run), 1)
    b.engine.execute(run)
    b.engine.execute(run) // a second delivery does nothing: it is no longer queued
    assertEquals(AgentRepo.get(ds, run).get.status, "done")
    assertEquals(c.served("rules"), 1)
    // the settle was never evaluated (the process died again): the next process evaluates it once
    val (d, _) = handlers(c)
    d.managers.tick(t0); d.managers.tick(t0)
    assertEquals(item("zzagent_k1").status, "done")
    assertEquals(events.count(e => e.kind == Feed.Evaluated && e.runId.contains(run)), 1)

  test("crash: a manager's run running at boot is paused interrupted and resumes by itself"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_b1")
    activeReview(a)
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_b1"))
    a.managers.tick(t0)
    val run = item("zzagent_b1").runId.get
    assert(AgentRepo.transition(ds, run, RunState.Queued, RunState.Running).isDefined)
    val (b, _) = handlers(PanelClient(Model))
    b.engine.reconcile()
    val r = AgentRepo.get(ds, run).get
    assertEquals((r.status, r.error, r.resumeAt.isDefined), ("paused", Some("interrupted"), true))
    b.managers.tick(t0)
    assertEquals(item("zzagent_b1").status, "active", "a paused run keeps its item active")
    assert(b.engine.resumeDue(Instant.now().plusSeconds(120)).contains(run))
    assertEquals(AgentRepo.get(ds, run).get.status, "queued")

  test("restart: runs interrupted or paused at boot hold their places; maxRuns 2 never has more than 2 running (#1803)"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_r1", "zzagent_r2", "zzagent_r3", "zzagent_r4")
    activeReview(a, ManagerConfig(maxRuns = Some(2), cooldownMinutes = Some(0)))
    List("zzagent_r1", "zzagent_r2").foreach(s => a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, s)))
    a.managers.tick(t0)
    val (run1, run2) = (item("zzagent_r1").runId.get, item("zzagent_r2").runId.get)
    // at the crash: r1 was running, r2 had stalled (paused, resuming by itself); two more items wait
    assert(AgentRepo.transition(ds, run1, RunState.Queued, RunState.Running).isDefined)
    assert(AgentRepo.transition(ds, run2, RunState.Queued, RunState.Running).isDefined)
    assert(AgentRepo.transition(ds, run2, RunState.Running, RunState.Paused, error = Some("stalled: no sign of life"),
      resumeAt = Some(Instant.now().plusSeconds(600))).isDefined)
    List("zzagent_r3", "zzagent_r4").foreach(s => a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, s)))
    def live = connect(ds)(sql"""SELECT count(*)::int FROM jobs WHERE kind = 'agent' AND manager_id = $ReviewId
                                AND status IN ('queued', 'running')""".query[Int].run().head)
    // the restart: boot pauses r1 as interrupted; the first tick must not take its place
    val (b, _) = handlers(PanelClient(Model))
    b.engine.reconcile()
    assertEquals(AgentRepo.get(ds, run1).map(r => (r.status, r.resumeAt.isDefined)), Some(("paused", true)))
    assertEquals(ManagerRepo.running(ds, ReviewId), 2, "an interrupted and a stalled run both resume by themselves")
    b.managers.tick(t0)
    assertEquals(List("zzagent_r3", "zzagent_r4").map(item(_).status), List("pending", "pending"))
    // the engine resumes both: still two, and the next tick starts nothing
    assertEquals(b.engine.resumeDue(Instant.now().plusSeconds(1200)).toSet, Set(run1, run2))
    assertEquals(live, 2)
    b.managers.tick(t0)
    assertEquals(live, 2)
    assertEquals(List("zzagent_r3", "zzagent_r4").map(item(_).status), List("pending", "pending"))
    // a run waiting for an approval goes on when it is decided: it holds its place too
    connect(ds)(sql"UPDATE jobs SET status = 'waiting' WHERE id = $run1".update.run())
    b.managers.tick(t0)
    assertEquals(item("zzagent_r3").status, "pending")
    connect(ds)(sql"UPDATE jobs SET status = 'queued' WHERE id = $run1".update.run())
    // a run settles: exactly one place frees
    settle(run2, "zzagent_r2", Nil)
    b.managers.tick(t0)
    assertEquals(live, 2)
    assertEquals(List("zzagent_r3", "zzagent_r4").map(item(_).status), List("active", "pending"))
    // a pause only a person lifts frees its place: resuming it is their call
    val run3 = item("zzagent_r3").runId.get
    assert(AgentRepo.transition(ds, run3, RunState.Queued, RunState.Paused, error = Some("requested: paused on request")).isDefined)
    assertEquals(ManagerRepo.running(ds, ReviewId), 1)

  test("lease: one holder; another takes over only after it expires; renewing keeps it"):
    assert(ManagerRepo.claim(ds, "zzlease", "one", 2))
    assert(!ManagerRepo.claim(ds, "zzlease", "two", 2))
    assert(ManagerRepo.claim(ds, "zzlease", "one", 2), "the holder renews")
    Thread.sleep(2300)
    assert(ManagerRepo.claim(ds, "zzlease", "two", 2), "taken over after the ttl")
    assert(!ManagerRepo.claim(ds, "zzlease", "one", 2), "the old holder lost it")
    assertEquals(ManagerRepo.holder(ds, "zzlease").map(_._1), Some("two"))
    ManagerRepo.release(ds, "zzlease", "two")
    assert(ManagerRepo.claim(ds, "zzlease", "one", 2), "free once released")

  test("lease: a second server's agents wait for the lease; its holder's start runs them"):
    val (a, _) = handlers(PanelClient(Model))
    val other = AgentLease(ds, 60, owner = "elsewhere:1")
    connect(ds)(sql"DELETE FROM agent_leases WHERE name = 'agents'".update.run())
    assert(other.claim())
    val started = java.util.concurrent.atomic.AtomicBoolean(false)
    val mine = AgentLease(ds, 60, owner = "here:2")
    assert(!mine.start(() => started.set(true)))
    assert(!started.get)
    assertEquals(mine.status.map(s => (s._1, s._4)), Some(("elsewhere:1", false)))
    other.stop()
    assert(mine.claim())
    mine.stop()
    connect(ds)(sql"DELETE FROM agent_leases WHERE name = 'agents'".update.run())

  test("hints: snapshotted into the run, they reach the fixer's and the skeptic's user message; a once-hint is retired"):
    val c = PanelClient(Model)
    val (a, h) = handlers(c); seed(h, "zzagent_n1")
    activeReview(a, ManagerConfig(rounds = Some(2)))
    a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, "zzagent_n1"))
    val standing = a.managing.addHint(ReviewId, HintAdd("Prefer the 2019 dating.")).fold(e => fail(e.message), identity)
    val once = a.managing.addHint(ReviewId, HintAdd("Check the tin isotopes this time.", item = Some("zzagent_n1"), once = Some(true)))
      .fold(e => fail(e.message), identity)
    a.managers.tick(t0)
    val run = item("zzagent_n1").runId.get
    assertEquals(AgentRepo.get(ds, run).get.params.notes, Some(List("Prefer the 2019 dating.", "Check the tin isotopes this time.")))
    assert(ManagerRepo.hint(ds, once.id).get.retiredAt.isDefined, "a once-hint is used up")
    assert(ManagerRepo.hint(ds, standing.id).get.retiredAt.isEmpty)
    assertEquals(ManagerRepo.hint(ds, standing.id).get.uses, 1)
    // the run: a rules MAJOR, the fixer's gloss, verified, then a clean final panel
    def text(j: String) = AgentResponse(j, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
    c.add("rules", Seq(text(rulesMajor)), Seq(text(glossFixed)), Seq(text(rulesPass)))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skepticPass)), Seq(text(skepticPass)))
    c.add("fixer", Seq(text(fixerGloss)))
    a.engine.execute(run)
    assertEquals(AgentRepo.get(ds, run).get.status, "done", AgentRepo.get(ds, run).get.error)
    def user(w: String) = c.requests.get(w).asScala.toList.map(_.head.history.entries.collectFirst {
      case ConversationEntry.UserPrompt(p) => p }.getOrElse(""))
    val notes = "<operator-notes>\n- Prefer the 2019 dating.\n- Check the tin isotopes this time.\n</operator-notes>"
    assert(user("fixer").forall(_.contains(notes)), user("fixer").head.takeRight(400))
    assert(user("skeptic").nonEmpty && user("skeptic").forall(_.contains(notes)))
    assert(user("rules").forall(!_.contains("operator-notes")), "the rules reviewer is not given the notes")
    assert(user("cold").forall(!_.contains("operator-notes")), "nor the cold reader")
    // the next run gets only the standing hint
    a.managers.tick(t0)
    assertEquals(item("zzagent_n1").status, "done")

  test("hint effects: prioritise by target, hold until a date, skip"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_e1", "zzagent_e2", "zzagent_e3")
    a.managers.ensureDefaults()
    List("zzagent_e1", "zzagent_e2").foreach(s => a.managing.addItem(ReviewId, EnqueueRequest(ArticleReview, s, Some(0))))
    a.managing.addHint(ReviewId, HintAdd("e3 first", effect = Some(HintEffect(HintEffect.Prioritise, targets = List("zzagent_e3"), priority = Some(7)))))
    assertEquals(item("zzagent_e3").priority, 7)
    a.managing.addHint(ReviewId, HintAdd("wait for the new edition", effect = Some(HintEffect(HintEffect.HoldAction,
      targets = List("zzagent_e1"), until = Some("2027-01-01")))))
    assertEquals(item("zzagent_e1").status, "held")
    assertEquals(item("zzagent_e1").hold.flatMap(_.until), Some("2027-01-01"))
    a.managing.addHint(ReviewId, HintAdd("not this one", effect = Some(HintEffect(HintEffect.Skip, tag = Some(Tag)))))
    assertEquals(item("zzagent_e2").status, "stopped")
    assert(a.managing.addHint(ReviewId, HintAdd("x", effect = Some(HintEffect("frobnicate", targets = List("a"))))).isLeft)
    assert(events.exists(e => e.kind == Feed.Hint && e.text.contains("hold: 1 item")), events.map(_.text))

  test("the Review button: enqueue routes to the review manager (created paused), priority 10"):
    val (a, h) = handlers(PanelClient(Model)); seed(h, "zzagent_q1")
    val i = a.managing.enqueue(EnqueueRequest("review", "zzagent_q1")).fold(e => fail(e.message), _.items.head)
    assertEquals((i.managerId, i.priority, i.status, i.origin), (ReviewId, 10, "pending", "person"))
    assertEquals(ManagerRepo.get(ds, ReviewId).map(_.state), Some("paused"))
    assert(a.managing.enqueue(EnqueueRequest("review", "zzagent_nope")).isLeft)

  // ── scripted answers (the shapes ReviewArticleRunSuite uses) ────────────────────────────────────

  private def findingJson(sev: String, where: String, problem: String) =
    s"""{"severity":"$sev","kind":"term","where":"$where","quote":"","problem":"$problem","fix":"fix it","evidence":[]}"""
  private def rules(verdict: String, findings: String*) =
    s"""{"verdict":"$verdict","findings":[${findings.mkString(",")}],"filter":"topics:[tin] → 2","uncitedRelevant":[],"statusesSilent":[],"spotChecks":[],"checks":[]}"""
  private val rulesPass  = rules("PASS")
  private val rulesMajor = rules("MAJOR", findingJson("major", "Intro", "tin is not defined"))
  private val coldPass =
    """{"verdict":"PASS","explainBack":"It asks how tin reached the Aegean. By sea. The date is argued. The mines are open.","emptySlots":[],"findings":[],"termsUndefined":0,"opaqueSentences":0,"lostAt":null}"""
  private val skepticPass = """{"verdict":"PASS","findings":[],"proposals":[],"claimsChecked":6,"checks":[]}"""
  private val glossFixed =
    """{"checks":[{"id":"R1","fixed":true,"quote":"Tin, the scarce half of bronze, reached the Aegean by sea","note":"checked"}],"findings":[],"lessons":[]}"""
  private val fixerGloss =
    """{"edits":[{"op":"replace","anchor":"Tin reached the Aegean by sea","text":"Tin, the scarce half of bronze, reached the Aegean by sea"}],"applied":["R1"],"rejected":[],"unresolvedMoved":[],"notes":"","reasons":[]}"""
