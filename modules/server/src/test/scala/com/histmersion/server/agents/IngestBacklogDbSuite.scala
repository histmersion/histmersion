package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.{AgentsHandlers, LlmClient}
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.api.Autopilot.AutopilotSet
import com.histmersion.api.GoalPlans.*
import com.histmersion.api.Managers.*
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.api.RepairAcquire.RepairRequest
import com.histmersion.db.{AgentRepo, GoalLinkRepo, ManagerRepo, Migrate}
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.*

import java.time.Instant
import java.time.temporal.ChronoUnit

/** The watchers' ingest backlog against a `*_test` database (docs/research-process-managers.md "As built: the ingest
  * backlog"): a sweep's legal open-access picks past the day's allowance wait in the backlog — never the acquisition
  * list — and drain on the next day within the caps; one entry per DOI across watchers, linked to every goal that found
  * it; a work with no legal file goes to the acquisition list then; the status lines count it; the one-shot repair
  * moves the open acquisition items a watcher's sweep created for legal open-access works into the backlog, removes the
  * ones the corpus has, and never touches a person's. Models are scripted, the acquisition toolset a fixture
  * ([[StubUpstream]]): nothing reaches a provider or the network. Skipped without HISTMERSION_TEST_DB_URL.
  */
class IngestBacklogDbSuite extends com.histmersion.db.DbSuite with RunTranscripts:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val Goal = "zzagent-oa"
  private val Goal2 = "zzagent-oa2"
  private val Query = "zzagent oa tin"
  private val Query2 = "zzagent oa bronze"
  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  /** The crons off (a test drives every sweep itself), room for every run; the acquisition list's re-check off (it
    * would try the items a test leaves on the list, and this suite counts the backlog's ingests).
    */
  private lazy val baseCfg =
    val b = AppConfig.load()
    val m = Model.wire
    b.copy(
      db = b.db.copy(url = testDb.get),
      acquire = b.acquire.copy(recheck = b.acquire.recheck.copy(enabled = false)),
      agents = b.agents.copy(
        enabled = true,
        prices = "scripted=1/2",
        maxConcurrentRuns = 20,
        scheduler = b.agents.scheduler.copy(enabled = false),
        autopilot = b.agents.autopilot.copy(backupHours = 0, lintHours = 0),
        roles = b.agents.roles
          .copy(extractor = m, reviewer = m, coldReader = m, skeptic = m, fixer = m, planner = m, writer = m)
      )
    )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers, StubUpstream) =
    val deps = Deps(cfg, ds)
    val h = Handlers(deps)
    val a = AgentsHandlers(TransitionSite(deps, h, Some(TestRuns.noonClock())), (_, _) => Right(c))
    val up = StubUpstream()
    a.attach(up)
    (a, h, up)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND (manager_id IS NOT NULL OR target_id LIKE 'zzagent%')".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%' OR text LIKE 'ingest backlog:%' OR text LIKE 'repair-acquire%'".update
        .run()
      sql"UPDATE agent_autopilot SET enabled = false, paused = '{}', changed_by = '', last_ensure_at = NULL".update
        .run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(a: AgentsHandlers, h: Handlers, goal: String = Goal, query: String = Query): Unit =
    assert(h.putGoal(goal, GoalPut(s"Zz $goal", notes = Some("where the tin came from"))).isRight)
    assert(a.addGoalLink(goal, LinkAdd(LinkKind.Query, query)).isRight)

  private def doi(n: String) = s"10.5555/zzagent.$n"
  private def work(n: String, oa: Boolean = true) =
    WorkRef(doi = Some(doi(n)), title = Some(s"Work $n"), year = Some(2023), authors = List("Zz"), isOa = oa)
  private def picks(ns: Seq[String]) =
    text(ns.map(n => s"""{"doi":"${doi(n)}","why":"fits $n"}""").mkString("""{"picks":[""", ",", "]}"))

  /** The fixture's legal ingest: stores the source, unless the DOI has no legal copy. */
  private def legalIngest(h: Handlers, noCopy: Set[String] = Set.empty)(d: String): Either[String, IngestReport] =
    if noCopy.contains(d) then Left(s"${IngestBacklog.NoLegalCopy} $d: no fetcher had it")
    else
      val ck = ck0(d)
      assert(h.putSource(ck, SourcePut(title = s"Ingested $d", year = Some(2023), doi = Some(d))).isRight)
      Right(
        IngestReport(
          ck,
          Some(d),
          Some(s"Ingested $d"),
          created = true,
          Some(PdfInfo("unpaywall", Some(s"https://example.invalid/$d.pdf"), 2048, None)),
          0,
          None,
          Nil
        )
      )
  private def ck0(d: String) = "zzagent" + d.replaceAll("[^a-z0-9]", "")

  private def watcher(a: AgentsHandlers, goal: String, perDay: Int, auto: Boolean = true): ManagerInfo =
    val m = a.managing
      .create(
        ManagerCreate(
          "watch",
          Some(goal),
          ManagerConfig(sweeps = Some(List("acquisition")), autoIngest = Some(auto), autoIngestPerDay = Some(perDay)),
          paused = Some(false)
        )
      )
      .fold(e => fail(e.message), identity)
    m

  private def sweepRun(mid: String, goal: String): RunInfo =
    AgentRepo.get(ds, ManagerRepo.itemFor(ds, mid, AcquisitionSweep, goal).get.runId.get).get

  private def acquire(h: Handlers): List[Queue.QueueItem] =
    h.ops.queueList("acquire", all = false).toOption.get.items.filter(_.text.contains("zzagent"))

  private def backlog(mid: String): List[ManagerItem] =
    ManagerRepo.items(ds, mid, None, 0, 500)._1.filter(_.kind == OaIngest)

  private def ingestCalls(up: StubUpstream): List[String] =
    import scala.jdk.CollectionConverters.*
    up.calls.asScala.toList.filter(_.startsWith("ingest"))

  test(
    "a sweep finding 30 legal OA works with an allowance of 20: 20 ingested, 10 in the backlog, 0 on the acquisition list; the next day the backlog drains"
  ):
    val c = ScriptedClient(Model)
    val ns = (1 to 30).map(i => f"w$i%02d")
    c.scripts += Seq(picks(ns))
    val (a, h, up) = handlers(c); seed(a, h)
    up.found = Map(Query -> ns.map(work(_)).toList)
    up.onIngest = legalIngest(h)
    val (b, hb, upb) = (a, h, up)
    val m = watcher(b, Goal, perDay = 20)
    val now = Instant.now()
    b.managers.tick(now)
    val run = sweepRun(m.id, Goal)
    assertEquals((run.params.autoIngest, run.params.oaBacklog), (Some(20), Some(true)))
    b.engine.execute(run.id)
    val done = AgentRepo.get(ds, run.id).get
    assertEquals(done.status, "done", done.error)
    assertEquals(ingestCalls(upb).size, 20)
    assertEquals(backlog(m.id).map(_.status).distinct, List("pending"))
    assertEquals(backlog(m.id).size, 10)
    assertEquals(acquire(hb), Nil, "no legal open-access work reaches the person's acquisition list")
    val closed = AgentRepo.steps(ds, run.id, 100000).find(_.name == "close").flatMap(_.output).getOrElse("")
    assert(
      closed.contains("30 picked, 20 ingested") && closed.contains("10 to the ingest backlog") &&
        closed.contains(" 0 queued for acquisition"),
      closed
    )
    assertEquals(
      AgentRepo.steps(ds, run.id, 100).map(_.name),
      List("discover", "rank", "ingest", "backlog", "queue", "close")
    )
    val first = backlog(m.id).minBy(_.id)
    assertEquals(first.state.oa.map(w => (w.goals, w.title, w.foundBy)), Some((List(Goal), "Work w21", Some(run.id))))
    // the same day: the allowance is spent, nothing drains; the status says when it will
    b.managers.tick(now)
    assertEquals(ingestCalls(upb).size, 20)
    val info = b.managing.get(m.id).fold(e => fail(e.message), identity).manager
    assertEquals((info.backlog, info.backlogDays), (10, Some(1)))
    assert(
      ManagersMarkdown.line(info).contains("10 legal open-access works in the ingest backlog (~1 more day"),
      ManagersMarkdown.line(info)
    )
    val st = b.managing.autopilot
    assertEquals((st.backlog, st.backlogDays), (10, Some(1)))
    assert(AutopilotMarkdown.status(st).contains("10 legal open-access works in the ingest backlog"))
    // a new sweep the same day: its open-access finds wait behind the backlog
    c.scripts += Seq(picks(Seq("x1")))
    up.found = Map(Query -> List(work("x1")))
    b.managing
      .patchItem(m.id, ManagerRepo.itemFor(ds, m.id, AcquisitionSweep, Goal).get.id.toString, ItemPatch("reopen"))
      .fold(e => fail(e.message), identity)
    b.managers.tick(now)
    val again = sweepRun(m.id, Goal)
    assertEquals(
      (again.params.autoIngest, again.params.oaBacklog),
      (None, Some(true)),
      "0 left: no ingest step, the backlog keeps them"
    )
    assert(
      ManagerRepo
        .events(ds, m.id, None, 50)
        .exists(e => e.kind == Feed.Started && e.text.contains("10 works wait in the ingest backlog first"))
    )
    b.engine.execute(again.id)
    assertEquals(backlog(m.id).size, 11)
    assertEquals(acquire(hb), Nil)
    // the next day: the backlog drains, the oldest first, a few each tick
    val tomorrow = now.plus(1, ChronoUnit.DAYS)
    (1 to 6).foreach(_ => b.managers.tick(tomorrow))
    assertEquals(backlog(m.id).map(_.status).distinct, List("done"))
    assertEquals(
      ingestCalls(upb).drop(20).take(3),
      List(s"ingest ${doi("w21")}", s"ingest ${doi("w22")}", s"ingest ${doi("w23")}")
    )
    assertEquals(ingestCalls(upb).last, s"ingest ${doi("x1")}", "the later find last")
    assert(connect(ds)(GoalLinkRepo.links(Goal)).exists(l => l.kind == LinkKind.Source && l.ref == ck0(doi("w30"))))
    assertEquals(
      ManagerRepo
        .events(ds, m.id, None, 200)
        .count(e => e.kind == Feed.Ingested && e.text.contains("from the backlog")),
      11
    )
    assert(
      h.ops.queueList("log", all = true).toOption.get.items.exists(_.text.startsWith("ingest backlog: 3 ingested"))
    )
    assertEquals(b.managing.get(m.id).toOption.get.manager.backlog, 0)

  test(
    "one entry per DOI across watchers, linked to both goals; a work with no legal file goes to the acquisition list on its day"
  ):
    val c = ScriptedClient(Model)
    c.scripts += Seq(picks(Seq("x", "y", "z")))
    c.scripts += Seq(picks(Seq("x")))
    val (a, h, up) = handlers(c); seed(a, h); seed(a, h, Goal2, Query2)
    up.found = Map(Query -> List(work("x"), work("y"), work("z", oa = false)), Query2 -> List(work("x")))
    up.onIngest = legalIngest(h, Set(doi("y")))
    val w1 = watcher(a, Goal, perDay = 2)
    val w2 = watcher(a, Goal2, perDay = 2)
    // both spent today's allowance already
    List(w1, w2).foreach(w =>
      (1 to 2).foreach(_ => ManagerRepo.event(ds, w.id, None, None, Feed.Ingested, "[[zzagent_earlier]] ingested"))
    )
    val now = Instant.now()
    a.managers.tick(now)
    a.engine.execute(sweepRun(w1.id, Goal).id)
    a.engine.execute(sweepRun(w2.id, Goal2).id)
    assertEquals(ingestCalls(up), Nil)
    val open = ManagerRepo.backlogOpen(ds)
    assertEquals(open.map(_.target).sorted, List(doi("x"), doi("y")), "x once, though both goals found it")
    val x = open.find(_.target == doi("x")).get
    assertEquals((x.managerId, x.state.oa.map(_.goals)), (w1.id, Some(List(Goal, Goal2))))
    assertEquals(acquire(h).map(i => Scheduling.dois(i.text)), List(Set(doi("z"))), "only the closed work")
    // the next day
    val tomorrow = now.plus(1, ChronoUnit.DAYS)
    a.managers.tick(tomorrow)
    a.managers.tick(tomorrow)
    assertEquals(ManagerRepo.itemFor(ds, w1.id, OaIngest, doi("x")).get.status, "done")
    assert(
      List(Goal, Goal2).forall(g =>
        connect(ds)(GoalLinkRepo.links(g)).exists(l => l.kind == LinkKind.Source && l.ref == ck0(doi("x")))
      )
    )
    val y = ManagerRepo.itemFor(ds, w1.id, OaIngest, doi("y")).get
    assertEquals(y.status, "stopped")
    assert(y.outcome.exists(_.contains("to the acquisition list")), y.outcome)
    val ys = acquire(h).filter(i => Scheduling.dois(i.text).contains(doi("y")))
    assertEquals(ys.size, 1)
    assert(ys.head.text.contains("(open access)") && ys.head.text.contains(s"for goal $Goal"), ys.head.text)
    assert(
      connect(ds)(GoalLinkRepo.links(Goal)).exists(l => l.kind == LinkKind.QueueItem && l.ref == ys.head.id.toString)
    )
    assertEquals(acquire(h).size, 2, "no duplicate acquisition item")

  test("the shared cap (autopilot on) bounds the drain across watchers; a paused watcher's backlog waits"):
    val (a, h, up) = handlers(
      ScriptedClient(Model),
      baseCfg.copy(agents =
        baseCfg.agents.copy(
          autopilot =
            baseCfg.agents.autopilot.copy(backupHours = 0, lintHours = 0, ingestPerDay = 2, ensureMinutes = 100000)
        )
      )
    )
    seed(a, h); seed(a, h, Goal2, Query2)
    up.onIngest = legalIngest(h)
    val w1 = watcher(a, Goal, perDay = 5)
    val w2 = watcher(a, Goal2, perDay = 5)
    (1 to 3).foreach(i => a.oaBacklog.add(w1.id, Goal, OaWork(doi(s"a$i"), s"A$i")))
    (1 to 3).foreach(i => a.oaBacklog.add(w2.id, Goal2, OaWork(doi(s"b$i"), s"B$i")))
    connect(ds)(sql"UPDATE agent_autopilot SET enabled = true, last_ensure_at = now()".update.run())
    val now = Instant.now()
    a.managers.tick(now)
    assertEquals(
      ingestCalls(up),
      List(s"ingest ${doi("a1")}", s"ingest ${doi("a2")}"),
      "two in all today, the oldest first"
    )
    a.managers.tick(now)
    assertEquals(ingestCalls(up).size, 2)
    val st = a.managing.autopilot
    assertEquals((st.backlog, st.backlogDays), (4, Some(2)), "2 a day from tomorrow")
    a.managing.action(w2.id, "pause", None).fold(e => fail(e.message), identity)
    val tomorrow = now.plus(1, ChronoUnit.DAYS)
    (1 to 3).foreach(_ => a.managers.tick(tomorrow))
    assertEquals(ingestCalls(up).drop(2), List(s"ingest ${doi("a3")}"), "w2 paused: its backlog waits")
    assertEquals(
      a.managing.get(w2.id).toOption.get.manager.backlogDays,
      None,
      "a paused watcher's backlog does not drain"
    )

  test(
    "repair-acquire: a sweep's open-access acquisition items move into the backlog, one in the corpus is removed, a closed one and a person's stay"
  ):
    val c = ScriptedClient(Model)
    c.scripts += Seq(picks(Seq("a", "b", "c")))
    val (a, h, up) = handlers(c); seed(a, h)
    up.found = Map(Query -> List(work("a"), work("b", oa = false), work("c")))
    up.onIngest = legalIngest(h)
    // the old behaviour: a watcher without auto-ingest queues every pick (the overflow looked the same)
    val m = watcher(a, Goal, perDay = 5, auto = false)
    // a person's own item, in the same words, linked to the goal by its DOI
    val mine = h.ops
      .queueAdd(
        "acquire",
        QueueAdd(
          s"Zz (2023). Mine. https://doi.org/${doi("p")} (open access) — for goal $Goal: mine",
          topic = Some(s"goal $Goal")
        )
      )
      .toOption
      .get
    assert(transact(ds)(GoalLinkRepo.link(Goal, LinkKind.QueueItem, mine.id.toString, Some(doi("p")))))
    a.managers.tick(Instant.now())
    a.engine.execute(sweepRun(m.id, Goal).id)
    assertEquals(acquire(h).size, 4)
    // c reached the corpus meanwhile; the watcher now auto-ingests
    assert(h.putSource("zzagent2023_c", SourcePut(title = "C", year = Some(2023), doi = Some(doi("c")))).isRight)
    a.managing
      .update(m.id, ManagerUpdate(Some(m.config.copy(autoIngest = Some(true)))))
      .fold(e => fail(e.message), identity)

    val dry = a.oaBacklog.repair(RepairRequest(dryRun = true, Some("test")))
    assertEquals(
      dry.rows.map(r => (r.doi, r.action)).sortBy(_._1),
      List(doi("a") -> RepairAcquire.Move, doi("b") -> RepairAcquire.Keep, doi("c") -> RepairAcquire.Remove)
    )
    assertEquals((dry.moved, dry.removed, dry.kept, dry.backlog), (1, 1, 1, 1))
    assert(dry.untouched >= 1, "the person's item")
    assertEquals(acquire(h).size, 4, "a dry run changes nothing")
    assertEquals(backlog(m.id), Nil)
    assert(RepairAcquire.markdown(dry).contains("would move 1 legal open-access work"))

    val done = a.oaBacklog.repair(RepairRequest(by = Some("test")))
    assertEquals((done.moved, done.removed, done.kept), (1, 1, 1))
    val left = acquire(h)
    assertEquals(
      left.map(i => Scheduling.dois(i.text)).toSet,
      Set(Set(doi("b")), Set(doi("p"))),
      "the closed work and the person's stay"
    )
    val entry = backlog(m.id)
    assertEquals(entry.map(_.target), List(doi("a")))
    assertEquals(entry.head.state.oa.map(_.goals), Some(List(Goal)))
    assert(entry.head.state.oa.flatMap(_.acquireText).exists(_.contains(doi("a"))), "its old wording kept")
    assertEquals(done.rows.find(_.doi == doi("a")).flatMap(_.backlogItem), Some(entry.head.id))
    assert(
      !connect(ds)(GoalLinkRepo.links(Goal)).exists(l =>
        l.kind == LinkKind.QueueItem && !left.exists(_.id.toString == l.ref)
      ),
      "no goal link to a removed item"
    )
    assert(connect(ds)(GoalLinkRepo.links(Goal)).exists(l => l.kind == LinkKind.Source && l.ref == "zzagent2023_c"))
    assert(
      h.ops.queueList("log", all = true).toOption.get.items.exists(_.text.startsWith("repair-acquire (test): 1 legal"))
    )
    // idempotent
    val again = a.oaBacklog.repair(RepairRequest())
    assertEquals((again.moved, again.removed), (0, 0))
    // and the moved work drains like any other
    a.managers.tick(Instant.now())
    assertEquals(ingestCalls(up), List(s"ingest ${doi("a")}"))
    assertEquals(backlog(m.id).head.status, "done")

  test("the backlog in the repair's and the switch's answers; an entry steered by its DOI like any item"):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(a, h)
    val m = watcher(a, Goal, perDay = 5)
    a.oaBacklog.add(m.id, Goal, OaWork(doi("q"), "Q"))
    assertEquals(a.oaBacklog.repair(RepairRequest(dryRun = true)).backlog, 1)
    val on = a.managing.setAutopilot(AutopilotSet(on = true, Some("test"))).fold(e => fail(e.message), identity)
    assertEquals(on.backlog, 1)
    // a person can reprioritise or skip an entry like any item, by its DOI
    val i = a.managing
      .patchItem(m.id, doi("q"), ItemPatch("priority", priority = Some(5)))
      .fold(e => fail(e.message), identity)
    assertEquals((i.kind, i.priority), (OaIngest, 5))
    assert(a.managing.addItem(m.id, EnqueueRequest("ingest", "not a doi")).isLeft)
    val added = a.managing
      .addItem(m.id, EnqueueRequest("ingest", s"https://doi.org/${doi("r")}"))
      .fold(e => fail(e.message), identity)
    assertEquals((added.kind, added.target), (OaIngest, doi("r")))
