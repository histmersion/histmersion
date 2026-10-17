package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.GoalPlans.*
import com.histmersion.core.Managers.*
import com.histmersion.db.{AgentRepo, Db, GoalLinkRepo, ManagerRepo, Migrate}
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.*

import java.time.Instant

/** The process managers' phase 3 against a `*_test` database (docs/research-process-managers.md §4 (c),
  * §6): the watcher (created paused with its cron and one item per sweep; an acquisition sweep whose
  * OPT-IN auto-ingest takes the legal open-access picks up to the daily cap and queues the rest; the
  * currency check; what each sweep found recorded as events), the managers' crons (fired once when due,
  * a paused manager fires nothing, a firing re-opens a watcher's settled sweeps). Models are scripted and
  * the acquisition toolset is a fixture ([[StubUpstream]]): nothing reaches a provider or the network.
  * Skipped without HISTMERSION_TEST_DB_URL. */
class ManagerPhase3Suite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout         = scala.concurrent.duration.Duration(180, "s")

  private val Goal  = "zzagent-watch"
  private val Query = "zzagent tin trade"
  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private lazy val baseCfg =
    val b = AppConfig.load()
    val m = Model.wire
    b.copy(db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(enabled = true, prices = "scripted=1/2",
        roles = b.agents.roles.copy(extractor = m, reviewer = m, coldReader = m, skeptic = m, fixer = m, planner = m, writer = m)))

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
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND manager_id IS NOT NULL".update.run()
      sql"DELETE FROM approvals WHERE subject LIKE 'zzagent%' OR citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run()
      sql"DELETE FROM goal_schedules WHERE goal_slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit   = clean()

  private def seed(a: AgentsHandlers, h: Handlers): Unit =
    assert(h.putGoal(Goal, GoalPut("Zz tin watched", notes = Some("where the tin came from"))).isRight)
    assert(a.addGoalLink(Goal, LinkAdd(LinkKind.Query, Query)).isRight)

  private def work(doi: String, title: String, oa: Boolean) =
    WorkRef(doi = Some(doi), title = Some(title), year = Some(2023), authors = List("Zz"), isOa = oa)

  /** The fixture's legal ingest: stores the source (as an ingest would) unless the DOI has no legal copy. */
  private def legalIngest(h: Handlers, noCopy: Set[String])(doi: String): Either[String, IngestReport] =
    if noCopy.contains(doi) then Left(s"no legal open-access copy of $doi")
    else
      val ck = "zzagent" + doi.replaceAll("[^a-z0-9]", "")
      assert(h.putSource(ck, SourcePut(title = s"Ingested $doi", year = Some(2023), doi = Some(doi))).isRight)
      Right(IngestReport(ck, Some(doi), Some(s"Ingested $doi"), created = true,
        Some(PdfInfo("unpaywall", Some(s"https://example.invalid/$doi.pdf"), 2048, None)), 0, None, Nil))

  private def item(mid: String, kind: String): ManagerItem = ManagerRepo.itemFor(ds, mid, kind, Goal).get

  private def events(mid: String, kind: String): List[ManagerEvent] =
    ManagerRepo.events(ds, mid, None, 200).filter(_.kind == kind)

  private def acquire(h: Handlers): List[Queue.QueueItem] =
    h.ops.queueList("acquire", all = true).toOption.get.items.filter(_.text.contains("zzagent"))

  test("watch: created paused with its cron and one item per sweep; nothing starts before it is resumed"):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(a, h)
    val now = Instant.parse("2026-10-17T12:00:00Z")
    val m = a.managing.create(ManagerCreate("watch", Some(Goal)), now).fold(e => fail(e.message), identity)
    assertEquals((m.id, m.kind, m.goal, m.state, m.cron), (watchId(Goal), "watch", Some(Goal), "paused", Some("@weekly")))
    assertEquals(m.nextTickAt, Some("2026-10-18T00:00:00Z"))
    assertEquals(m.effective.autoIngest, Some(false), "auto-ingest is opt-in")
    assertEquals(ManagerRepo.items(ds, m.id, None, 0, 10)._1.map(i => (i.kind, i.target, i.status)).sortBy(_._1),
      List((AcquisitionSweep, Goal, "pending"), (CurrencyCheck, Goal, "pending")))
    assert(a.managing.create(ManagerCreate("watch", Some(Goal))).left.exists(_.message.contains("exists already")))
    assert(a.managing.create(ManagerCreate("watch")).left.exists(_.message.contains("give its goal")))
    assertEquals(a.managers.tick(now).filter(_.contains("started")), Nil, "paused: nothing starts")
    assertEquals(AgentRepo.forManager(ds, m.id), Vector.empty)

  test("watch sweep with auto-ingest ON: legal OA picks ingested up to the daily cap (one with no legal copy queued instead), the rest queued; events record it"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core"},{"doi":"10.5555/zzagent.b","why":"closed"},""" +
      """{"doi":"10.5555/zzagent.c","why":"no copy"},{"doi":"10.5555/zzagent.d","why":"open"},{"doi":"10.5555/zzagent.e","why":"over cap"}]}"""))
    val (a, h, up) = handlers(c); seed(a, h)
    up.found = Map(Query -> List(work("10.5555/zzagent.a", "A", oa = true), work("10.5555/zzagent.b", "B", oa = false),
      work("10.5555/zzagent.c", "C", oa = true), work("10.5555/zzagent.d", "D", oa = true), work("10.5555/zzagent.e", "E", oa = true)))
    up.onIngest = legalIngest(h, Set("10.5555/zzagent.c"))
    val now = Instant.now()
    val m = a.managing.create(ManagerCreate("watch", Some(Goal),
      ManagerConfig(autoIngest = Some(true), autoIngestPerDay = Some(2))), now).fold(e => fail(e.message), identity)
    a.managing.action(m.id, "resume", None).fold(e => fail(e.message), identity)
    val out = a.managers.tick(now)
    val sweep = item(m.id, AcquisitionSweep)
    assertEquals(sweep.status, "active", out.mkString("\n"))
    assertEquals(item(m.id, CurrencyCheck).status, "pending", "the currency check waits: the sweep works on the same goal")
    val run = AgentRepo.get(ds, sweep.runId.get).get
    assertEquals((run.pipeline, run.origin, run.params.autoIngest), ("acquisition-sweep", Some("manager"), Some(2)))
    assert(events(m.id, Feed.Started).exists(_.text.contains("2 works left today")), events(m.id, Feed.Started))
    a.engine.execute(run.id)
    val done = AgentRepo.get(ds, run.id).get
    assertEquals(done.status, "done", done.error)
    import scala.jdk.CollectionConverters.*
    val asked = up.calls.asScala.toList.filter(_.startsWith("ingest"))
    assertEquals(asked, List("ingest 10.5555/zzagent.a", "ingest 10.5555/zzagent.c", "ingest 10.5555/zzagent.d"),
      "only open-access picks, until two were ingested")
    assertEquals(h.getSource("zzagent105555zzagenta", body = false).map(_.source.title), Right("Ingested 10.5555/zzagent.a"))
    assert(GoalLinkRepo.links(ds, Goal).exists(l => l.kind == LinkKind.Source && l.ref == "zzagent105555zzagentd"))
    val queued = acquire(h).map(_.text)
    assertEquals(queued.size, 3, queued.mkString("\n"))
    assert(List("zzagent.b", "zzagent.c", "zzagent.e").forall(d => queued.exists(_.contains(d))), queued)
    assert(done.progress.exists(_.contains("2 ingested (legal open access [[zzagent105555zzagenta]] [[zzagent105555zzagentd]])")),
      done.progress)
    assertEquals(events(m.id, Feed.Ingested).size, 2)
    // the evaluation: done, and the sweep's own event says what it found
    a.managers.tick(now)
    assertEquals(item(m.id, AcquisitionSweep).status, "done")
    val swept = events(m.id, Feed.Swept)
    assertEquals(swept.size, 1)
    assert(swept.head.text.contains("5 picked") && swept.head.text.contains("picked: A (https://doi.org/10.5555/zzagent.a, open access)"),
      swept.head.text)
    // the currency check runs next (no model): done, its own event
    val cc = item(m.id, CurrencyCheck)
    assertEquals(cc.status, "active")
    a.engine.execute(cc.runId.get)
    a.managers.tick(now)
    assertEquals(item(m.id, CurrencyCheck).status, "done")
    assert(events(m.id, Feed.Swept).exists(_.text.startsWith("currency-check goal zzagent-watch")), events(m.id, Feed.Swept))

    // the same day, the next sweep has nothing left of the cap: it ingests nothing
    c.scripts += Seq(text("""{"picks":[{"doi":"10.5555/zzagent.f","why":"open"}]}"""))
    up.found = Map(Query -> List(work("10.5555/zzagent.f", "F", oa = true)))
    a.managing.patchItem(m.id, sweep.id.toString, ItemPatch("reopen")).fold(e => fail(e.message), identity)
    a.managers.tick(now)
    val again = AgentRepo.get(ds, item(m.id, AcquisitionSweep).runId.get).get
    assertEquals(again.params.autoIngest, None, "the daily cap is spent")
    assert(events(m.id, Feed.Started).exists(_.text.contains("0 works left today")))
    a.engine.execute(again.id)
    assertEquals(up.calls.asScala.count(_.startsWith("ingest")), 3, "no further ingest")
    assert(acquire(h).exists(_.text.contains("zzagent.f")))

  test("watch sweep with auto-ingest OFF (the default): every pick goes to the acquisition list, nothing is ingested"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core"}]}"""))
    val (a, h, up) = handlers(c); seed(a, h)
    up.found = Map(Query -> List(work("10.5555/zzagent.a", "A", oa = true)))
    up.onIngest = legalIngest(h, Set.empty)
    val m = a.managing.create(ManagerCreate("watch", Some(Goal), ManagerConfig(sweeps = Some(List("acquisition"))))).fold(e => fail(e.message), identity)
    assertEquals(ManagerRepo.items(ds, m.id, None, 0, 10)._1.map(_.kind), List(AcquisitionSweep), "only the sweeps it names")
    a.managing.action(m.id, "resume", None).fold(e => fail(e.message), identity)
    a.managers.tick(Instant.now())
    val run = AgentRepo.get(ds, item(m.id, AcquisitionSweep).runId.get).get
    assertEquals(run.params.autoIngest, None)
    a.engine.execute(run.id)
    assertEquals(AgentRepo.get(ds, run.id).get.status, "done")
    import scala.jdk.CollectionConverters.*
    assertEquals(up.calls.asScala.count(_.startsWith("ingest")), 0)
    assertEquals(acquire(h).count(_.text.contains("zzagent.a")), 1)
    assert(AgentRepo.steps(ds, run.id, 1000).forall(_.name != "ingest"), "no ingest step without auto-ingest")
    assertEquals(events(m.id, Feed.Ingested), Nil)

  test("cron: a due watcher re-opens its settled sweeps once and moves on; a paused one fires nothing; an open one is left to finish"):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(a, h)
    val t0 = Instant.parse("2026-10-17T12:00:00Z")
    val m = a.managing.create(ManagerCreate("watch", Some(Goal), ManagerConfig(maxRuns = Some(0))), t0).fold(e => fail(e.message), identity)
    // settle both items by hand (skip), as if they ran
    List(AcquisitionSweep, CurrencyCheck).foreach(k =>
      a.managing.patchItem(m.id, item(m.id, k).id.toString, ItemPatch("skip")).fold(e => fail(e.message), identity))
    val sunday = Instant.parse("2026-10-18T00:00:30Z")
    assertEquals(a.managers.tick(sunday).filter(_.contains("cron")), Nil, "paused: its cron does not fire")
    a.managing.action(m.id, "resume", None).fold(e => fail(e.message), identity)
    assertEquals(a.managers.tick(Instant.parse("2026-10-17T13:00:00Z")).filter(_.contains("cron")), Nil, "not due yet")
    val out = a.managers.tick(sunday)
    assert(out.contains(s"${m.id}: cron fired"), out)
    assertEquals(ManagerRepo.get(ds, m.id).get.nextTickAt, Some("2026-10-25T00:00:00Z"))
    assertEquals((item(m.id, AcquisitionSweep).status, item(m.id, AcquisitionSweep).origin), ("pending", "cron"))
    assertEquals(item(m.id, CurrencyCheck).status, "pending")
    assert(events(m.id, Feed.Cron).head.text.contains("acquisition-sweep due, currency-check due"), events(m.id, Feed.Cron))
    // the same time again: fired already
    assertEquals(a.managers.tick(sunday).filter(_.contains("cron")), Nil)
    // missed three weeks: one firing, and the still-pending items stay as they are
    val later = a.managers.tick(Instant.parse("2026-11-12T09:00:00Z"))
    assertEquals(later.count(_.contains("cron fired")), 1)
    assert(events(m.id, Feed.Cron).head.text.contains("acquisition-sweep still open"), events(m.id, Feed.Cron).head.text)
    assertEquals(ManagerRepo.get(ds, m.id).get.nextTickAt, Some("2026-11-15T00:00:00Z"))
    // agents.scheduler.enabled = false stops every cron
    val off = baseCfg.copy(agents = baseCfg.agents.copy(scheduler = baseCfg.agents.scheduler.copy(enabled = false)))
    val (b, _, _) = handlers(ScriptedClient(Model), off)
    assertEquals(b.managers.tick(Instant.parse("2026-12-01T00:00:00Z")).filter(_.contains("cron")), Nil)

  test("cron on other managers: checked when set; the review manager's refills whatever its low water; the enrich manager has none"):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(a, h)
    a.managers.ensureDefaults()
    val t0 = Instant.parse("2026-10-17T12:00:00Z")
    assert(a.managing.update(ReviewId, ManagerUpdate(cron = Some("*/5 * * * *")), t0).left.exists(_.message.contains("min-interval")))
    assert(a.managing.update(ReviewId, ManagerUpdate(cron = Some("nope")), t0).left.exists(_.message.contains("five fields")))
    assert(a.managing.update(EnrichId, ManagerUpdate(cron = Some("@daily")), t0).left.exists(_.message.contains("no cron")))
    val r = a.managing.update(ReviewId, ManagerUpdate(Some(ManagerConfig(lowWater = Some(0), maxRuns = Some(0))), Some("@daily")), t0)
      .fold(e => fail(e.message), identity)
    assertEquals((r.cron, r.nextTickAt), (Some("@daily"), Some("2026-10-18T00:00:00Z")))
    a.managing.action(ReviewId, "resume", None).fold(e => fail(e.message), identity)
    val out = a.managers.tick(Instant.parse("2026-10-18T00:01:00Z"))
    assert(out.contains("review: cron fired"), out)
    assert(events(ReviewId, Feed.Cron).head.text.contains("selection refilled"))
    // cleared: no cron, no next firing
    val cleared = a.managing.update(ReviewId, ManagerUpdate(cron = Some("")), t0).fold(e => fail(e.message), identity)
    assertEquals((cleared.cron, cleared.nextTickAt), (None, None))
