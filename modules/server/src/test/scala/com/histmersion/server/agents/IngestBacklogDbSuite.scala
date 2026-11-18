package com.histmersion
package server.agents

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ScriptedClient
import com.histmersion.domain.automation.agents.TestRuns

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.{AgentsControllers, LlmClient}
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Autopilot.AutopilotSet
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.api.library.RepairAcquire.RepairRequest
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.goals.GoalLinkRepo
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.util.database.Migrate
import com.histmersion.server.{Deps, ServerWiring}
import sttp.ai.core.agent.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import com.histmersion.db.*
import com.histmersion.api.automation.{Goal, ManagersMarkdown, Queue}
import com.histmersion.domain.automation.Goal as DomainGoal
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.{PdfInfo, Query, RepairAcquire, WorkRef}
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.config.DbConfig
import com.histmersion.config.RecheckConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentSchedulerConfig
import com.histmersion.config.AgentAutopilotConfig
import com.histmersion.config.AgentRolesConfig
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.ByteCount
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.server.views.WorkListView
import com.histmersion.domain.automation.managers.ManagersControllers
import com.histmersion.api.automation.Activity
import com.histmersion.api.Api

/** The watchers' ingest backlog against a `*_test` database (docs/research-process-managers.md "As built: the ingest
  * backlog"): a sweep's legal open-access picks past the day's allowance wait in the backlog — never the acquisition
  * list — and drain on the next day within the caps; one entry per DOI across watchers, linked to every goal that found
  * it; a work with no legal file goes to the acquisition list then; the status lines count it; the one-shot repair
  * moves the open acquisition items a watcher's sweep created for legal open-access works into the backlog, removes the
  * ones the corpus has, and never touches a person's. Models are scripted, the acquisition toolset a fixture
  * ([[StubUpstream]]): nothing reaches a provider or the network. Skipped without HISTMERSION_TEST_DB_URL.
  */
class IngestBacklogDbSuite extends DbSuite with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val Goal = DomainGoal.Slug("zzagent-oa")
  private val Goal2 = DomainGoal.Slug("zzagent-oa2")
  private val Query = "zzagent oa tin"
  private val Query2 = "zzagent oa bronze"
  private val QueryRef = LinkAdd.Ref(Query)
  private val Query2Ref = LinkAdd.Ref(Query2)
  private val Model = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("scripted"))
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  /** The crons off (a test drives every sweep itself), room for every run; the acquisition list's re-check off (it
    * would try the items a test leaves on the list, and this suite counts the backlog's ingests).
    */
  private lazy val baseCfg =
    val b = AppConfig.load()
    val m = Model.wire
    b.modify(_.db)
      .setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.acquire)
      .setTo(b.acquire.modify(_.recheck).setTo(b.acquire.recheck.modify(_.enabled).setTo(RecheckConfig.Enabled(false))))
      .modify(_.agents)
      .setTo(
        b.agents
          .modify(_.enabled)
          .setTo(AgentsConfig.Enabled(true))
          .modify(_.prices)
          .setTo(AgentsConfig.Prices("scripted=1/2"))
          .modify(_.maxConcurrentRuns)
          .setTo(AgentsConfig.MaxConcurrentRuns(20))
          .modify(_.scheduler)
          .setTo(b.agents.scheduler.modify(_.enabled).setTo(AgentSchedulerConfig.Enabled(false)))
          .modify(_.autopilot)
          .setTo(
            b.agents.autopilot
              .modify(_.backupHours)
              .setTo(AgentAutopilotConfig.BackupHours(0))
              .modify(_.lintHours)
              .setTo(AgentAutopilotConfig.LintHours(0))
          )
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
      )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsControllers, ServerWiring, StubUpstream) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    val a = AgentsControllers(ServerEngineSite(deps, h, Some(TestRuns.noonClock())), (_, _) => Right(c))
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

  private def seed(
      a: AgentsControllers,
      h: ServerWiring,
      goal: DomainGoal.Slug = Goal,
      query: LinkAdd.Ref = QueryRef
  ): Unit =
    assert(
      h.goals
        .put(
          goal,
          com.histmersion.api.automation.Goal.Put(
            com.histmersion.api.automation.Goal.Title(s"Zz $goal"),
            notes = Some(com.histmersion.api.automation.Goal.Notes("where the tin came from"))
          )
        )
        .isRight
    )
    assert(a.addGoalLink(goal, LinkAdd(LinkKind.Query, query)).isRight)

  private def doi(n: String) = s"10.5555/zzagent.$n"
  private def work(n: String, oa: WorkRef.IsOa = WorkRef.IsOa(true)) =
    WorkRef(
      doi = (Some(doi(n))).map(Doi.apply),
      title = (Some(s"Work $n")).map(com.histmersion.api.library.Title.apply),
      year = (Some(2023)).map(Year.apply),
      authors = (List("Zz")).map(com.histmersion.api.library.AuthorName.apply),
      isOa = oa
    )
  private def picks(ns: Seq[String]) =
    text(ns.map(n => s"""{"doi":"${doi(n)}","why":"fits $n"}""").mkString("""{"picks":[""", ",", "]}"))

  /** The fixture's legal ingest: stores the source, unless the DOI has no legal copy. */
  private def legalIngest(h: ServerWiring, noCopy: Set[String] = Set.empty)(d: String): Either[Problem, Ingest.Report] =
    if noCopy.contains(d) then Left(Problem.NotFound.Copy(Problem.Raw(d), Vector.empty))
    else
      val ck = ck0(d)
      assert(
        h.library.sources
          .put(
            ck,
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
          com.histmersion.api.library.Citekey(ck.value),
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
  private def ck0(d: String): Citekey = Citekey.assume("zzagent" + d.replaceAll("[^a-z0-9]", ""))

  private def watcher(
      a: AgentsControllers,
      goal: DomainGoal.Slug,
      perDay: ManagerConfig.AutoIngestPerDay,
      auto: ManagerConfig.AutoIngest = ManagerConfig.AutoIngest(true)
  ): ManagerInfo =
    val m = a.managing
      .create(
        ManagerCreate(
          ManagerCreate.Kind("watch"),
          Some(com.histmersion.api.automation.Goal.Slug(goal.value)),
          ManagerConfig(
            sweeps = Some(List(Sweep.Wire("acquisition"))),
            autoIngest = Some(auto),
            autoIngestPerDay = Some(perDay)
          ),
          paused = Some(ManagerCreate.Paused(false))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    m

  private def sweepRun(mid: ManagerInfo.Id, goal: DomainGoal.Slug): RunInfo =
    connect(ds)(
      AgentRepo.get(
        connect(ds)(
          ManagerRepo.itemFor(mid, ManagerItem.Kind(AcquisitionSweep), ManagerItem.Target(goal.value))
        ).get.runId.get
      )
    ).get

  private def acquire(h: ServerWiring): List[Queue.QueueItem] =
    h.workListView
      .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(false))
      .toOption
      .get
      .items
      .filter(_.text.value.contains("zzagent"))

  private def backlog(mid: ManagerInfo.Id): List[ManagerItem] =
    Transactor(ds)
      .read(ManagerRepo.itemPage(mid, None, Offset(0), PageSize(500)))
      ._1
      .filter(_.kind == ManagerItem.Kind(OaIngest))

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
    val m = watcher(b, Goal, perDay = ManagerConfig.AutoIngestPerDay(20))
    val now = Instant.now()
    b.managers.tick(now)
    val run = sweepRun(m.id, Goal)
    assertEquals((run.params.autoIngest, run.params.oaBacklog), (Some(20), Some(true)))
    b.engine.execute(ApiIds.run(run.id))
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(done.status, "done", done.error)
    assertEquals(ingestCalls(upb).size, 20)
    assertEquals(backlog(m.id).map(_.status).distinct, List("pending"))
    assertEquals(backlog(m.id).size, 10)
    assertEquals(acquire(hb), Nil, "no legal open-access work reaches the person's acquisition list")
    val closed = connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(100000)))
      .find(_.name.value == "close")
      .flatMap(_.output)
      .map(_.value)
      .getOrElse("")
    assert(
      closed.contains("30 picked, 20 ingested") && closed.contains("10 to the ingest backlog") &&
        closed.contains(" 0 queued for acquisition"),
      closed
    )
    assertEquals(
      connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(100))).map(_.name),
      List("discover", "rank", "ingest", "backlog", "queue", "close")
    )
    val first = backlog(m.id).minBy(r => (r.id.value: Long))
    assertEquals(
      first.state.oa.map(w => (w.goals.map(_.value: String), w.title, w.foundBy.map(_.value: String))),
      Some((List(Goal), "Work w21", Some(run.id.value: String)))
    )
    // the same day: the allowance is spent, nothing drains; the status says when it will
    b.managers.tick(now)
    assertEquals(ingestCalls(upb).size, 20)
    val info =
      b.managing.get(ApiIds.manager(m.id)).fold(e => fail(e.problems.sentences.mkString("; ")), identity).manager
    assertEquals((info.backlog, info.backlogDays), (10, Some(1)))
    assert(
      ManagersMarkdown.line(info).contains("10 legal open-access works in the ingest backlog (~1 more day"),
      ManagersMarkdown.line(info)
    )
    val st = b.managing.autopilot
    assertEquals((st.backlog, st.backlogDays), (10, Some(1)))
    assert(b.autopilotPage(st).contains("10 legal open-access works in the ingest backlog"))
    // a new sweep the same day: its open-access finds wait behind the backlog
    c.scripts += Seq(picks(Seq("x1")))
    up.found = Map(Query -> List(work("x1")))
    b.managing
      .patchItem(
        ApiIds.manager(m.id),
        ManagerItem.Ref(
          connect(ds)(
            ManagerRepo.itemFor(m.id, ManagerItem.Kind(AcquisitionSweep), ManagerItem.Target(Goal.value))
          ).get.id.toString
        ),
        ItemPatch(ItemPatch.Action("reopen"))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    b.managers.tick(now)
    val again = sweepRun(m.id, Goal)
    assertEquals(
      (again.params.autoIngest, again.params.oaBacklog),
      (None, Some(true)),
      "0 left: no ingest step, the backlog keeps them"
    )
    assert(
      connect(ds)(ManagerRepo.events(m.id, None, PageSize(50)))
        .exists(e => e.kind.value == Feed.Started && e.text.value.contains("10 works wait in the ingest backlog first"))
    )
    b.engine.execute(ApiIds.run(again.id))
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
    assert(
      connect(ds)(GoalLinkRepo.links(Goal)).exists(l =>
        l.kind == LinkKind.Source && l.ref == GoalLink.Ref(ck0(doi("w30")).value)
      )
    )
    assertEquals(
      connect(ds)(ManagerRepo.events(m.id, None, PageSize(200)))
        .count(e => e.kind.value == Feed.Ingested && e.text.value.contains("from the backlog")),
      11
    )
    assert(
      h.workListView
        .list(WorkListControllers.KindOfList("log"), all = WorkListView.IncludeAll(true))
        .toOption
        .get
        .items
        .exists(_.text.value.startsWith("ingest backlog: 3 ingested"))
    )
    assertEquals(b.managing.get(ApiIds.manager(m.id)).toOption.get.manager.backlog, 0)

  test(
    "one entry per DOI across watchers, linked to both goals; a work with no legal file goes to the acquisition list on its day"
  ):
    val c = ScriptedClient(Model)
    c.scripts += Seq(picks(Seq("x", "y", "z")))
    c.scripts += Seq(picks(Seq("x")))
    val (a, h, up) = handlers(c); seed(a, h); seed(a, h, Goal2, Query2Ref)
    up.found = Map(Query -> List(work("x"), work("y"), work("z", oa = WorkRef.IsOa(false))), Query2 -> List(work("x")))
    up.onIngest = legalIngest(h, Set(doi("y")))
    val w1 = watcher(a, Goal, perDay = ManagerConfig.AutoIngestPerDay(2))
    val w2 = watcher(a, Goal2, perDay = ManagerConfig.AutoIngestPerDay(2))
    // both spent today's allowance already
    List(w1, w2).foreach(w =>
      (1 to 2).foreach(_ =>
        transact(ds)(
          ManagerRepo.event(
            w.id,
            None,
            None,
            ManagerRepo.EventKind(Feed.Ingested),
            ManagerRepo.EventText("[[zzagent_earlier]] ingested")
          )
        )
      )
    )
    val now = Instant.now()
    a.managers.tick(now)
    a.engine.execute(ApiIds.run(sweepRun(w1.id, Goal).id))
    a.engine.execute(ApiIds.run(sweepRun(w2.id, Goal2).id))
    assertEquals(ingestCalls(up), Nil)
    val open = Transactor(ds).read(ManagerRepo.backlogOpenIn())
    assertEquals(open.map(_.target.value).sorted, List(doi("x"), doi("y")), "x once, though both goals found it")
    val x = open.find(_.target.value == doi("x")).get
    assertEquals((x.managerId, x.state.oa.map(_.goals.map(_.value: String))), (w1.id, Some(List(Goal, Goal2))))
    assertEquals(acquire(h).map(i => Scheduling.dois(i.text.value)), List(Set(doi("z"))), "only the closed work")
    // the next day
    val tomorrow = now.plus(1, ChronoUnit.DAYS)
    a.managers.tick(tomorrow)
    a.managers.tick(tomorrow)
    assertEquals(
      connect(ds)(ManagerRepo.itemFor(w1.id, ManagerItem.Kind(OaIngest), ManagerItem.Target(doi("x")))).get.status,
      "done"
    )
    assert(
      List(Goal, Goal2).forall(g =>
        connect(ds)(GoalLinkRepo.links(g)).exists(l =>
          l.kind == LinkKind.Source && l.ref == GoalLink.Ref(ck0(doi("x")).value)
        )
      )
    )
    val y = connect(ds)(ManagerRepo.itemFor(w1.id, ManagerItem.Kind(OaIngest), ManagerItem.Target(doi("y")))).get
    assertEquals(y.status, "stopped")
    assert(y.outcome.exists(_.value.contains("to the acquisition list")), y.outcome)
    val ys = acquire(h).filter(i => Scheduling.dois(i.text.value).map(_.value).contains(doi("y")))
    assertEquals(ys.size, 1)
    assert(ys.head.text.value.contains("(open access)") && ys.head.text.value.contains(s"for goal $Goal"), ys.head.text)
    assert(
      connect(ds)(GoalLinkRepo.links(Goal)).exists(l =>
        l.kind == LinkKind.QueueItem && l.ref == GoalLink.Ref(ys.head.id.toString)
      )
    )
    assertEquals(acquire(h).size, 2, "no duplicate acquisition item")

  test("the shared cap (autopilot on) bounds the drain across watchers; a paused watcher's backlog waits"):
    val (a, h, up) = handlers(
      ScriptedClient(Model),
      baseCfg
        .modify(_.agents)
        .setTo(
          baseCfg.agents
            .modify(_.autopilot)
            .setTo(
              baseCfg.agents.autopilot
                .modify(_.backupHours)
                .setTo(AgentAutopilotConfig.BackupHours(0))
                .modify(_.lintHours)
                .setTo(AgentAutopilotConfig.LintHours(0))
                .modify(_.ingestPerDay)
                .setTo(AgentAutopilotConfig.IngestPerDay(2))
                .modify(_.ensureMinutes)
                .setTo(AgentAutopilotConfig.EnsureMinutes(100000))
            )
        )
    )
    seed(a, h); seed(a, h, Goal2, Query2Ref)
    up.onIngest = legalIngest(h)
    val w1 = watcher(a, Goal, perDay = ManagerConfig.AutoIngestPerDay(5))
    val w2 = watcher(a, Goal2, perDay = ManagerConfig.AutoIngestPerDay(5))
    (1 to 3).foreach(i =>
      a.oaBacklog.add(w1.id, Goal, OaWork(Doi(doi(s"a$i")), com.histmersion.api.library.Title(s"A$i")))
    )
    (1 to 3).foreach(i =>
      a.oaBacklog.add(w2.id, Goal2, OaWork(Doi(doi(s"b$i")), com.histmersion.api.library.Title(s"B$i")))
    )
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
    a.managing
      .action(ApiIds.manager(w2.id), ManagersControllers.Action("pause"), None)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val tomorrow = now.plus(1, ChronoUnit.DAYS)
    (1 to 3).foreach(_ => a.managers.tick(tomorrow))
    assertEquals(ingestCalls(up).drop(2), List(s"ingest ${doi("a3")}"), "w2 paused: its backlog waits")
    assertEquals(
      a.managing.get(ApiIds.manager(w2.id)).toOption.get.manager.backlogDays,
      None,
      "a paused watcher's backlog does not drain"
    )

  test(
    "repair-acquire: a sweep's open-access acquisition items move into the backlog, one in the corpus is removed, a closed one and a person's stay"
  ):
    val c = ScriptedClient(Model)
    c.scripts += Seq(picks(Seq("a", "b", "c")))
    val (a, h, up) = handlers(c); seed(a, h)
    up.found = Map(Query -> List(work("a"), work("b", oa = WorkRef.IsOa(false)), work("c")))
    up.onIngest = legalIngest(h)
    // the old behaviour: a watcher without auto-ingest queues every pick (the overflow looked the same)
    val m = watcher(a, Goal, perDay = ManagerConfig.AutoIngestPerDay(5), auto = ManagerConfig.AutoIngest(false))
    // a person's own item, in the same words, linked to the goal by its DOI
    val mine = h.workLists
      .add(
        WorkListControllers.ListName("acquire"),
        QueueAdd(
          Queue.QueueItem.Text(s"Zz (2023). Mine. https://doi.org/${doi("p")} (open access) — for goal $Goal: mine"),
          topic = Some(Queue.QueueItem.Topic(s"goal $Goal"))
        )
      )
      .toOption
      .get
    assert(
      transact(ds)(
        GoalLinkRepo.link(Goal, LinkKind.QueueItem, GoalLink.Ref(mine.id.toString), Some(GoalLink.Note(doi("p"))))
      )
    )
    a.managers.tick(Instant.now())
    a.engine.execute(ApiIds.run(sweepRun(m.id, Goal).id))
    assertEquals(acquire(h).size, 4)
    // c reached the corpus meanwhile; the watcher now auto-ingests
    assert(
      h.library.sources
        .put(
          Citekey("zzagent2023_c"),
          Source.Put(
            title = com.histmersion.api.library.Title("C"),
            year = (Some(2023)).map(Year.apply),
            doi = (Some(doi("c"))).map(Doi.apply)
          )
        )
        .isRight
    )
    a.managing
      .update(
        ApiIds.manager(m.id),
        ManagerUpdate(Some(m.config.copy(autoIngest = Some(ManagerConfig.AutoIngest(true)))))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)

    val dry = a.oaBacklog.repair(RepairRequest(dryRun = RepairRequest.DryRun(true), Some(Activity.Actor("test"))))
    assertEquals(
      dry.rows.map(r => (r.doi, r.action)).sortBy(_._1.value),
      List(doi("a") -> RepairAcquire.Move, doi("b") -> RepairAcquire.Keep, doi("c") -> RepairAcquire.Remove)
    )
    assertEquals((dry.moved, dry.removed, dry.kept, dry.backlog), (1, 1, 1, 1))
    assert(dry.untouched.value >= 1, "the person's item")
    assertEquals(acquire(h).size, 4, "a dry run changes nothing")
    assertEquals(backlog(m.id), Nil)
    assert(RepairAcquire.markdown(dry).value.contains("would move 1 legal open-access work"))

    val done = a.oaBacklog.repair(RepairRequest(by = Some(Activity.Actor("test"))))
    assertEquals((done.moved, done.removed, done.kept), (1, 1, 1))
    val left = acquire(h)
    assertEquals(
      left.map(i => Scheduling.dois(i.text.value)).toSet,
      Set(Set(doi("b")), Set(doi("p"))),
      "the closed work and the person's stay"
    )
    val entry = backlog(m.id)
    assertEquals(entry.map(_.target), List(doi("a")))
    assertEquals(entry.head.state.oa.map(_.goals.map(_.value: String)), Some(List(Goal)))
    assert(entry.head.state.oa.flatMap(_.acquireText).exists(_.value.contains(doi("a"))), "its old wording kept")
    assertEquals(done.rows.find(_.doi.value == doi("a")).flatMap(_.backlogItem).map(_.value), Some(entry.head.id.value))
    assert(
      !connect(ds)(GoalLinkRepo.links(Goal)).exists(l =>
        l.kind == LinkKind.QueueItem && !left.exists(i => GoalLink.Ref(i.id.toString) == l.ref)
      ),
      "no goal link to a removed item"
    )
    assert(
      connect(ds)(GoalLinkRepo.links(Goal)).exists(l =>
        l.kind == LinkKind.Source && l.ref == GoalLink.Ref("zzagent2023_c")
      )
    )
    assert(
      h.workListView
        .list(WorkListControllers.KindOfList("log"), all = WorkListView.IncludeAll(true))
        .toOption
        .get
        .items
        .exists(_.text.value.startsWith("repair-acquire (test): 1 legal"))
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
    val m = watcher(a, Goal, perDay = ManagerConfig.AutoIngestPerDay(5))
    a.oaBacklog.add(m.id, Goal, OaWork(Doi(doi("q")), com.histmersion.api.library.Title("Q")))
    assertEquals(a.oaBacklog.repair(RepairRequest(dryRun = RepairRequest.DryRun(true))).backlog, 1)
    val on = a.managing
      .setAutopilot(AutopilotSet(on = AutopilotSet.On(true), Some(AutopilotSet.By("test"))))
      .left
      .map(Api.Error(_))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(on.backlog, 1)
    // a person can reprioritise or skip an entry like any item, by its DOI
    val i = a.managing
      .patchItem(
        ApiIds.manager(m.id),
        ManagerItem.Ref(doi("q")),
        ItemPatch(ItemPatch.Action("priority"), priority = Some(ItemPatch.Priority(5)))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((i.kind, i.priority), (OaIngest, 5))
    assert(
      a.managing
        .addItem(
          ApiIds.manager(m.id),
          EnqueueRequest(EnqueueRequest.Kind("ingest"), EnqueueRequest.Target("not a doi"))
        )
        .isLeft
    )
    val added = a.managing
      .addItem(
        ApiIds.manager(m.id),
        EnqueueRequest(EnqueueRequest.Kind("ingest"), EnqueueRequest.Target(s"https://doi.org/${doi("r")}"))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((added.kind, added.target), (OaIngest, doi("r")))
