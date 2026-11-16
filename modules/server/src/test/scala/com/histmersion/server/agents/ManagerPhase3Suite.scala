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
import com.histmersion.domain.automation.managers.{ManagerDefaultsViews, Zero}

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.domain.automation.goals.GoalLinkRepo
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.server.{Deps, ServerWiring}
import sttp.ai.core.agent.*

import java.time.Instant
import com.histmersion.db.*
import com.histmersion.api.automation.{AgentsMarkdown, Goal, ManagersMarkdown, Queue}
import com.histmersion.domain.automation.Goal as DomainGoal
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.{PdfInfo, Query, WorkRef}
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.config.DbConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentRolesConfig
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.ByteCount
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.server.views.WorkListView
import com.histmersion.domain.automation.managers.ManagersControllers
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.config.AgentSchedulerConfig
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.agents.RunService

/** The process managers' phase 3 against a `*_test` database (docs/research-process-managers.md §4 (c), §6): the
  * watcher (created paused with its cron and one item per sweep; an acquisition sweep whose OPT-IN auto-ingest takes
  * the legal open-access picks up to the daily cap and queues the rest; the currency check; what each sweep found
  * recorded as events), the managers' crons (fired once when due, a paused manager fires nothing, a firing re-opens a
  * watcher's settled sweeps). Models are scripted and the acquisition toolset is a fixture ([[StubUpstream]]): nothing
  * reaches a provider or the network. Skipped without HISTMERSION_TEST_DB_URL.
  */
class ManagerPhase3Suite extends DbSuite with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(180, "s")

  private val Goal = DomainGoal.Slug("zzagent-watch")
  private val GoalSlug = com.histmersion.api.automation.Goal.Slug("zzagent-watch")
  private val GoalTarget = ManagerItem.Target("zzagent-watch")
  private val Query = "zzagent tin trade"
  private val QueryRef = LinkAdd.Ref(Query)
  private val Review = ManagerInfo.Id(ReviewId)
  private val Enrich = ManagerInfo.Id(EnrichId)
  private val SweepKind = ManagerItem.Kind(AcquisitionSweep)
  private val CurrencyKind = ManagerItem.Kind(CurrencyCheck)
  private val EnrichKind = ManagerItem.Kind(SourceEnrich)
  private val Open = WorkRef.IsOa(true)
  private val Closed = WorkRef.IsOa(false)
  private val StartedKind = ManagerEvent.Kind(Feed.Started)
  private val IngestedKind = ManagerEvent.Kind(Feed.Ingested)
  private val SweptKind = ManagerEvent.Kind(Feed.Swept)
  private val CronKind = ManagerEvent.Kind(Feed.Cron)
  private val HintKind = ManagerEvent.Kind(Feed.Hint)
  private val StarvedKind = ManagerEvent.Kind(Feed.Starved)
  private val Model = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("scripted"))
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

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
      )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsControllers, ServerWiring, StubUpstream) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    val a = AgentsControllers(ServerEngineSite(deps, h), (_, _) => Right(c))
    val up = StubUpstream()
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
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(a: AgentsControllers, h: ServerWiring): Unit =
    assert(
      h.goals
        .put(
          Goal,
          com.histmersion.api.automation.Goal.Put(
            com.histmersion.api.automation.Goal.Title("Zz tin watched"),
            notes = Some(com.histmersion.api.automation.Goal.Notes("where the tin came from"))
          )
        )
        .isRight
    )
    assert(a.addGoalLink(Goal, LinkAdd(LinkKind.Query, QueryRef)).isRight)

  private def ref(g: DomainGoal.Slug) = com.histmersion.api.automation.Goal.Ref(g.value)

  private def work(doi: String, title: String, oa: WorkRef.IsOa) =
    WorkRef(
      doi = (Some(doi)).map(Doi.apply),
      title = (Some(title)).map(com.histmersion.api.library.Title.apply),
      year = (Some(2023)).map(Year.apply),
      authors = (List("Zz")).map(com.histmersion.api.library.AuthorName.apply),
      isOa = oa
    )

  /** The fixture's legal ingest: stores the source (as an ingest would) unless the DOI has no legal copy. */
  private def legalIngest(h: ServerWiring, noCopy: Set[String])(doi: String): Either[Problem, Ingest.Report] =
    if noCopy.contains(doi) then Left(Problem.NotFound.Copy(Problem.Raw(doi), Vector.empty))
    else
      val ck = Citekey.assume("zzagent" + doi.replaceAll("[^a-z0-9]", ""))
      assert(
        h.library.sources
          .put(
            ck,
            Source.Put(
              title = com.histmersion.api.library.Title(s"Ingested $doi"),
              year = (Some(2023)).map(Year.apply),
              doi = (Some(doi)).map(Doi.apply)
            )
          )
          .isRight
      )
      Right(
        Ingest.Report(
          com.histmersion.api.library.Citekey(ck.value),
          Some(Doi(doi)),
          Some(com.histmersion.api.library.Title(s"Ingested $doi")),
          created = Ingest.Report.Created(true),
          Some(
            PdfInfo(
              PdfInfo.Source("unpaywall"),
              Some(PdfInfo.Url(s"https://example.invalid/$doi.pdf")),
              ByteCount(2048),
              None
            )
          ),
          Ingest.Report.EdgesAdded(0),
          None,
          Nil
        )
      )

  private def item(mid: ManagerInfo.Id, kind: ManagerItem.Kind): ManagerItem =
    connect(ds)(ManagerRepo.itemFor(mid, kind, GoalTarget)).get

  private def events(mid: ManagerInfo.Id, kind: ManagerEvent.Kind): List[ManagerEvent] =
    connect(ds)(ManagerRepo.events(mid, None, PageSize(200))).filter(_.kind == kind)

  private def acquire(h: ServerWiring): List[Queue.QueueItem] =
    h.workListView
      .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(true))
      .toOption
      .get
      .items
      .filter(_.text.value.contains("zzagent"))

  test("watch: created paused with its cron and one item per sweep; nothing starts before it is resumed"):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(a, h)
    val now = Instant.parse("2026-10-17T12:00:00Z")
    val m = a.managing
      .create(ManagerCreate(ManagerCreate.Kind("watch"), Some(GoalSlug)), now)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      (m.id, m.kind, m.goal.map(_.value: String), m.state, m.cron),
      (watchId(ref(Goal)), "watch", Some(Goal), "paused", Some("@weekly"))
    )
    assertEquals(m.nextTickAt, Some("2026-10-18T00:00:00Z"))
    assertEquals(m.effective.autoIngest, Some(false), "auto-ingest is opt-in")
    assertEquals(
      Transactor(ds)
        .read(ManagerRepo.itemPage(m.id, None, Offset(0), PageSize(10)))
        ._1
        .map(i => (i.kind, i.target.value: String, i.status))
        .sortBy(_._1.value),
      List((AcquisitionSweep, Goal, "pending"), (CurrencyCheck, Goal, "pending"))
    )
    assert(
      a.managing
        .create(ManagerCreate(ManagerCreate.Kind("watch"), Some(GoalSlug)))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("already exists"))
    )
    assert(
      a.managing
        .create(ManagerCreate(ManagerCreate.Kind("watch")))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("give its goal"))
    )
    assertEquals(a.managers.tick(now).filter(_.value.contains("started")), Nil, "paused: nothing starts")
    assertEquals(connect(ds)(AgentRepo.forManager(m.id)), Vector.empty)

  test(
    "watch sweep with auto-ingest ON: legal OA picks ingested up to the daily cap (one with no legal copy queued instead), the open ones past it in the backlog, the closed queued; events record it"
  ):
    val c = ScriptedClient(Model)
    c.scripts += Seq(
      text(
        """{"picks":[{"doi":"10.5555/zzagent.a","why":"core"},{"doi":"10.5555/zzagent.b","why":"closed"},""" +
          """{"doi":"10.5555/zzagent.c","why":"no copy"},{"doi":"10.5555/zzagent.d","why":"open"},{"doi":"10.5555/zzagent.e","why":"over cap"}]}"""
      )
    )
    val (a, h, up) = handlers(c); seed(a, h)
    up.found = Map(
      Query -> List(
        work("10.5555/zzagent.a", "A", oa = Open),
        work("10.5555/zzagent.b", "B", oa = Closed),
        work("10.5555/zzagent.c", "C", oa = Open),
        work("10.5555/zzagent.d", "D", oa = Open),
        work("10.5555/zzagent.e", "E", oa = Open)
      )
    )
    up.onIngest = legalIngest(h, Set("10.5555/zzagent.c"))
    val now = Instant.now()
    val m = a.managing
      .create(
        ManagerCreate(
          ManagerCreate.Kind("watch"),
          Some(GoalSlug),
          ManagerConfig(
            autoIngest = Some(ManagerConfig.AutoIngest(true)),
            autoIngestPerDay = Some(ManagerConfig.AutoIngestPerDay(2))
          )
        ),
        now
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.managing
      .action(ApiIds.manager(m.id), ManagersControllers.Action("resume"), None)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val out = a.managers.tick(now)
    val sweep = item(m.id, SweepKind)
    assertEquals(sweep.status, "active", out.mkString("\n"))
    assertEquals(
      item(m.id, CurrencyKind).status,
      "active",
      "the currency check starts beside it: another pipeline on the goal, and no per-manager cap since the central scheduler"
    )
    val run = connect(ds)(AgentRepo.get(sweep.runId.get)).get
    assertEquals(
      (run.pipeline, run.origin, run.params.autoIngest, run.params.oaBacklog),
      ("acquisition-sweep", Some("manager"), Some(2), Some(true))
    )
    assert(events(m.id, StartedKind).exists(_.text.value.contains("2 works left today")), events(m.id, StartedKind))
    a.engine.execute(ApiIds.run(run.id))
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(done.status, "done", done.error)
    import scala.jdk.CollectionConverters.*
    val asked = up.calls.asScala.toList.filter(_.startsWith("ingest"))
    assertEquals(
      asked,
      List("ingest 10.5555/zzagent.a", "ingest 10.5555/zzagent.c", "ingest 10.5555/zzagent.d"),
      "only open-access picks, until two were ingested"
    )
    assertEquals(
      h.library.sources
        .get(Citekey("zzagent105555zzagenta"), body = SourcesControllers.GetBody(false))
        .map(_.source.title),
      Right("Ingested 10.5555/zzagent.a")
    )
    assert(
      connect(ds)(GoalLinkRepo.links(Goal)).exists(l =>
        l.kind == LinkKind.Source && l.ref == GoalLink.Ref("zzagent105555zzagentd")
      )
    )
    val queued = acquire(h).map(_.text)
    assertEquals(queued.size, 2, queued.mkString("\n"))
    assert(List("zzagent.b", "zzagent.c").forall(d => queued.exists(_.value.contains(d))), queued)
    assertEquals(
      Transactor(ds).read(ManagerRepo.backlogOpenIn()).map(i => (i.managerId, i.target)),
      List(m.id -> "10.5555/zzagent.e"),
      "the open-access pick past the cap waits in the ingest backlog, not on the acquisition list"
    )
    assert(
      done.progress.exists(p =>
        p.value.contains("2 ingested (legal open access [[zzagent105555zzagenta]] [[zzagent105555zzagentd]])") &&
          p.value.contains("1 to the ingest backlog") && p.value.contains("2 queued for acquisition")
      ),
      done.progress
    )
    assertEquals(events(m.id, IngestedKind).size, 2)
    // the evaluation: done, and the sweep's own event says what it found
    a.managers.tick(now)
    assertEquals(item(m.id, SweepKind).status, "done")
    val swept = events(m.id, SweptKind)
    assertEquals(swept.size, 1)
    assert(
      swept.head.text.value.contains("5 picked") && swept.head.text.value
        .contains("picked: A (https://doi.org/10.5555/zzagent.a, open access)"),
      swept.head.text
    )
    // the currency check runs next (no model): done, its own event
    val cc = item(m.id, CurrencyKind)
    assertEquals(cc.status, "active")
    a.engine.execute(ApiIds.run(cc.runId.get))
    a.managers.tick(now)
    assertEquals(item(m.id, CurrencyKind).status, "done")
    assert(
      events(m.id, SweptKind).exists(_.text.value.startsWith("currency-check goal zzagent-watch")),
      events(m.id, SweptKind)
    )

    // the same day, the next sweep has nothing left of the cap: it ingests nothing
    c.scripts += Seq(text("""{"picks":[{"doi":"10.5555/zzagent.f","why":"open"}]}"""))
    up.found = Map(Query -> List(work("10.5555/zzagent.f", "F", oa = Open)))
    a.managing
      .patchItem(ApiIds.manager(m.id), ManagerItem.Ref(sweep.id.toString), ItemPatch(ItemPatch.Action("reopen")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.managers.tick(now)
    val again = connect(ds)(AgentRepo.get(item(m.id, SweepKind).runId.get)).get
    assertEquals(again.params.autoIngest, None, "the daily cap is spent")
    assert(events(m.id, StartedKind).exists(_.text.value.contains("0 works left today")))
    a.engine.execute(ApiIds.run(again.id))
    assertEquals(up.calls.asScala.count(_.startsWith("ingest")), 3, "no further ingest")
    assert(
      !acquire(h).exists(_.text.value.contains("zzagent.f")),
      "an open-access find past the cap is not the person's to buy"
    )
    assertEquals(
      Transactor(ds).read(ManagerRepo.backlogOpenIn()).map(_.target),
      List("10.5555/zzagent.e", "10.5555/zzagent.f")
    )

  test("watch sweep with auto-ingest OFF (the default): every pick goes to the acquisition list, nothing is ingested"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core"}]}"""))
    val (a, h, up) = handlers(c); seed(a, h)
    up.found = Map(Query -> List(work("10.5555/zzagent.a", "A", oa = Open)))
    up.onIngest = legalIngest(h, Set.empty)
    val m = a.managing
      .create(
        ManagerCreate(
          ManagerCreate.Kind("watch"),
          Some(GoalSlug),
          ManagerConfig(sweeps = Some(List(Sweep.Wire("acquisition"))))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      Transactor(ds).read(ManagerRepo.itemPage(m.id, None, Offset(0), PageSize(10)))._1.map(_.kind),
      List(AcquisitionSweep),
      "only the sweeps it names"
    )
    a.managing
      .action(ApiIds.manager(m.id), ManagersControllers.Action("resume"), None)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.managers.tick(Instant.now())
    val run = connect(ds)(AgentRepo.get(item(m.id, SweepKind).runId.get)).get
    assertEquals(run.params.autoIngest, None)
    a.engine.execute(ApiIds.run(run.id))
    assertEquals(connect(ds)(AgentRepo.get(run.id)).get.status, "done")
    import scala.jdk.CollectionConverters.*
    assertEquals(up.calls.asScala.count(_.startsWith("ingest")), 0)
    assertEquals(acquire(h).count(_.text.value.contains("zzagent.a")), 1)
    assert(
      connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(1000))).forall(_.name.value != "ingest"),
      "no ingest step without auto-ingest"
    )
    assertEquals(events(m.id, IngestedKind), Nil)

  test(
    "cron: a due watcher re-opens its settled sweeps once and moves on; a paused one fires nothing; an open one is left to finish"
  ):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(a, h)
    val t0 = Instant.parse("2026-10-17T12:00:00Z")
    val m = a.managing
      .create(
        ManagerCreate(
          ManagerCreate.Kind("watch"),
          Some(GoalSlug),
          ManagerConfig(maxRuns = Some(ManagerConfig.MaxRuns(0)))
        ),
        t0
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    // settle both items by hand (skip), as if they ran
    List(SweepKind, CurrencyKind).foreach(k =>
      a.managing
        .patchItem(
          ApiIds.manager(m.id),
          ManagerItem.Ref(item(m.id, k).id.toString),
          ItemPatch(ItemPatch.Action("skip"))
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    )
    val sunday = Instant.parse("2026-10-18T00:00:30Z")
    assertEquals(a.managers.tick(sunday).filter(_.value.contains("cron")), Nil, "paused: its cron does not fire")
    a.managing
      .action(ApiIds.manager(m.id), ManagersControllers.Action("resume"), None)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      a.managers.tick(Instant.parse("2026-10-17T13:00:00Z")).filter(_.value.contains("cron")),
      Nil,
      "not due yet"
    )
    val out = a.managers.tick(sunday)
    assert(out.map(_.value).contains(s"${m.id}: cron fired"), out)
    assertEquals(connect(ds)(ManagerRepo.get(m.id)).get.nextTickAt, Some("2026-10-25T00:00:00Z"))
    assertEquals((item(m.id, SweepKind).status, item(m.id, SweepKind).origin), ("pending", "cron"))
    assertEquals(item(m.id, CurrencyKind).status, "pending")
    assert(
      events(m.id, CronKind).head.text.value.contains("acquisition-sweep due, currency-check due"),
      events(m.id, CronKind)
    )
    // the same time again: fired already
    assertEquals(a.managers.tick(sunday).filter(_.value.contains("cron")), Nil)
    // missed three weeks: one firing, and the still-pending items stay as they are
    val later = a.managers.tick(Instant.parse("2026-11-12T09:00:00Z"))
    assertEquals(later.count(_.value.contains("cron fired")), 1)
    assert(
      events(m.id, CronKind).head.text.value.contains("acquisition-sweep still open"),
      events(m.id, CronKind).head.text
    )
    assertEquals(connect(ds)(ManagerRepo.get(m.id)).get.nextTickAt, Some("2026-11-15T00:00:00Z"))
    // agents.scheduler.enabled = false stops every cron
    val off = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents
          .modify(_.scheduler)
          .setTo(baseCfg.agents.scheduler.modify(_.enabled).setTo(AgentSchedulerConfig.Enabled(false)))
      )
    val (b, _, _) = handlers(ScriptedClient(Model), off)
    assertEquals(b.managers.tick(Instant.parse("2026-12-01T00:00:00Z")).filter(_.value.contains("cron")), Nil)

  test(
    "cron on other managers: checked when set; the review manager's refills whatever its low water; the enrich manager has none"
  ):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(a, h)
    a.managers.ensureDefaults()
    val t0 = Instant.parse("2026-10-17T12:00:00Z")
    assert(
      a.managing
        .update(ApiIds.manager(Review), ManagerUpdate(cron = Some(ManagerUpdate.Cron("*/5 * * * *"))), t0)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("min-interval"))
    )
    assert(
      a.managing
        .update(ApiIds.manager(Review), ManagerUpdate(cron = Some(ManagerUpdate.Cron("nope"))), t0)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("five fields"))
    )
    assert(
      a.managing
        .update(ApiIds.manager(Enrich), ManagerUpdate(cron = Some(ManagerUpdate.Cron("@daily"))), t0)
        .left
        .exists(_.problems.sentences.mkString("; ").contains("no cron"))
    )
    val r = a.managing
      .update(
        ApiIds.manager(Review),
        ManagerUpdate(
          Some(ManagerConfig(lowWater = Some(ManagerConfig.LowWater(0)), maxRuns = Some(ManagerConfig.MaxRuns(0)))),
          Some(ManagerUpdate.Cron("@daily"))
        ),
        t0
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((r.cron, r.nextTickAt), (Some("@daily"), Some("2026-10-18T00:00:00Z")))
    a.managing
      .action(ApiIds.manager(Review), ManagersControllers.Action("resume"), None)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val out = a.managers.tick(Instant.parse("2026-10-18T00:01:00Z"))
    assert(out.map(_.value).contains("review: cron fired"), out)
    assert(events(Review, CronKind).head.text.value.contains("selection refilled"))
    // cleared: no cron, no next firing
    val cleared = a.managing
      .update(ApiIds.manager(Review), ManagerUpdate(cron = Some(ManagerUpdate.Cron(""))), t0)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((cleared.cron, cleared.nextTickAt), (None, None))

  // ── LLM-proposed hint effects, person-confirmed ────────────────────────────────────────────────

  private val CkA = Citekey("zzagent2020_hinta")
  private val CkB = Citekey("zzagent2021_hintb")

  /** The (paused) enrich manager with two source items. */
  private def enrichItems(a: AgentsControllers, h: ServerWiring): Unit =
    assert(
      h.library.sources
        .put(CkA, Source.Put(title = com.histmersion.api.library.Title("Hint A"), year = (Some(2020)).map(Year.apply)))
        .isRight
    )
    assert(
      h.library.sources
        .put(CkB, Source.Put(title = com.histmersion.api.library.Title("Hint B"), year = (Some(2021)).map(Year.apply)))
        .isRight
    )
    a.managers.ensureDefaults()
    List(CkA, CkB).foreach(ck =>
      a.managing
        .addItem(
          ApiIds.manager(Enrich),
          EnqueueRequest(
            EnqueueRequest.Kind("enrich"),
            EnqueueRequest.Target(ck.value),
            Some(EnqueueRequest.Priority(0))
          )
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    )

  private def enrichItem(ck: Citekey): ManagerItem =
    connect(ds)(ManagerRepo.itemFor(Enrich, EnrichKind, ManagerItem.Target(ck.value))).get

  private def openHintApprovals(a: AgentsControllers): List[ApprovalInfo] =
    a.approvalsList(None, None).toOption.get.filter(_.kind.value == "hint-effect")

  test(
    "a model's effect (MCP: propose) waits for a person: nothing changes until approved; approve applies it, reject leaves it"
  ):
    val (a, h, _) = handlers(ScriptedClient(Model)); enrichItems(a, h)
    val hold = HintEffect(
      HintEffect.Action("hold"),
      List(HintEffect.Targets(CkA.value)),
      until = Some(HintEffect.Until("2026-12-01"))
    )
    val hint = a.managing
      .addHint(
        ApiIds.manager(Enrich),
        HintAdd(
          HintAdd.Text("wait with A until December"),
          effect = Some(hold),
          author = Some(HintAdd.Author("mcp")),
          propose = Some(HintAdd.Propose(true))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(hint.effect, None, "not applied")
    assertEquals(enrichItem(CkA).status, "pending")
    val ap = openHintApprovals(a)
    assertEquals(ap.size, 1)
    assertEquals(
      (ap.head.subject, ap.head.hintEffect.map(p => (p.hint.value, p.effect.action, p.by))),
      (Some(EnrichId), Some((hint.id.value, "hold", "mcp")))
    )
    assertEquals(hint.proposal.map(_.value), Some(ap.head.id.value))
    assert(
      ManagersMarkdown.hint(hint).contains(s"approval #${ap.head.id} waits for a person"),
      ManagersMarkdown.hint(hint)
    )
    // approve: applied through the same path as a person's own hold, and recorded on the hint
    val decided = a
      .decide(ApiIds.approval(ap.head.id), Approval.Outcome("approve"), DecisionRequest())
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(decided.state, "approved")
    assert(decided.result.exists(_.value.contains("hold")), decided.result)
    assertEquals((enrichItem(CkA).status, enrichItem(CkA).hold.flatMap(_.until)), ("held", Some("2026-12-01")))
    assertEquals(enrichItem(CkB).status, "pending")
    val after = Transactor(ds).read(ManagerRepo.hint(hint.id)).get
    assertEquals((after.effect.map(_.action), after.proposal), (Some("hold"), None))
    // a rejected one changes nothing
    val skip = a.managing
      .addHint(
        ApiIds.manager(Enrich),
        HintAdd(
          HintAdd.Text("drop B"),
          effect = Some(HintEffect(HintEffect.Action("skip"), List(HintEffect.Targets(CkB.value)))),
          propose = Some(HintAdd.Propose(true))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val ap2 = openHintApprovals(a).head
    assertEquals(
      a.decide(ApiIds.approval(ap2.id), Approval.Outcome("reject"), DecisionRequest()).map(_.state),
      Right("rejected")
    )
    assertEquals(enrichItem(CkB).status, "pending")
    assertEquals(Transactor(ds).read(ManagerRepo.hint(skip.id)).get.effect, None)

  test(
    "a proposed settings change applies on approval; a person's own hint cannot carry one; a bad one is refused up front"
  ):
    val (a, h, _) = handlers(ScriptedClient(Model)); enrichItems(a, h)
    assert(
      a.managing
        .addHint(
          ApiIds.manager(Enrich),
          HintAdd(
            HintAdd.Text("slower"),
            effect = Some(
              HintEffect(
                HintEffect.Action("config"),
                config = Some(ManagerConfig(maxRuns = Some(ManagerConfig.MaxRuns(1))))
              )
            )
          )
        )
        .left
        .exists(_.problems.sentences.mkString("; ").contains("config instead"))
    )
    assert(
      a.managing
        .addHint(
          ApiIds.manager(Enrich),
          HintAdd(
            HintAdd.Text("x"),
            effect = Some(
              HintEffect(
                HintEffect.Action("config"),
                config = Some(ManagerConfig(rounds = Some(ManagerConfig.Rounds(99))))
              )
            ),
            propose = Some(HintAdd.Propose(true))
          )
        )
        .left
        .exists(_.problems.sentences.mkString("; ").contains("rounds"))
    )
    a.managing
      .addHint(
        ApiIds.manager(Enrich),
        HintAdd(
          HintAdd.Text("one at a time please"),
          effect = Some(
            HintEffect(
              HintEffect.Action("config"),
              config = Some(ManagerConfig(maxRuns = Some(ManagerConfig.MaxRuns(1))))
            )
          ),
          propose = Some(HintAdd.Propose(true))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      a.managing.get(ApiIds.manager(Enrich)).toOption.get.manager.config.maxRuns,
      None,
      "not before approval"
    )
    val ap = openHintApprovals(a).head
    assert(AgentsMarkdown.change(ap).contains("settings: maxRuns=1"), AgentsMarkdown.change(ap))
    assertEquals(
      a.decide(ApiIds.approval(ap.id), Approval.Outcome("approve"), DecisionRequest()).map(_.state),
      Right("approved")
    )
    assertEquals(a.managing.get(ApiIds.manager(Enrich)).toOption.get.manager.config.maxRuns, Some(1))

  test(
    "suggest: the server's planner model reads the hint; code sends an unknown target back; the proposal waits for approval"
  ):
    val c = ScriptedClient(Model)
    c.scripts ++= List(
      Seq(
        text(
          s"""{"action":"hold","why":"until December","targets":["$CkA","zzagent_nope"],"tag":null,"until":"2026-12-01","untilSource":null,"priority":null,"settings":[]}"""
        )
      ),
      Seq(
        text(
          s"""{"action":"hold","why":"until December","targets":["$CkA"],"tag":null,"until":"2026-12-01","untilSource":null,"priority":null,"settings":[]}"""
        )
      )
    )
    val (a, h, _) = handlers(c); enrichItems(a, h)
    val hint = a.managing
      .addHint(
        ApiIds.manager(Enrich),
        HintAdd(HintAdd.Text("please leave A alone until December"), suggest = Some(HintAdd.Suggest(true)))
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val run = connect(ds)(AgentRepo.list(None, PageSize(20))).find(_.pipeline.value == "hint-effect").get
    assertEquals(
      (run.target, run.origin, run.params.hint.map(_.value)),
      (EnrichId, Some("person"), Some(hint.id.value))
    )
    a.engine.execute(ApiIds.run(run.id))
    val done = connect(ds)(AgentRepo.get(run.id)).get
    assertEquals(done.status, "done", done.error)
    assertEquals(c.steps.size, 2, "the unknown target was sent back")
    val sent = c.steps.head.head.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }.get
    assert(sent.contains("please leave A alone until December") && sent.contains(CkA.value), sent)
    assertEquals(enrichItem(CkA).status, "pending", "nothing applied by the run")
    val ap = openHintApprovals(a)
    assertEquals(
      ap.map(_.hintEffect.map(p => (p.effect.action, p.effect.targets.map(_.value: String), p.hint.value))),
      List(Some(("hold", List(CkA), hint.id.value)))
    )
    assertEquals(ap.head.runId, Some(run.id))
    assert(
      connect(ds)(ManagerRepo.events(Enrich, None, PageSize(20)))
        .exists(e => e.kind == HintKind && e.text.value.contains("a model proposed hold"))
    )
    assertEquals(
      a.decide(ApiIds.approval(ap.head.id), Approval.Outcome("approve"), DecisionRequest()).map(_.state),
      Right("approved")
    )
    assertEquals(enrichItem(CkA).status, "held")
    // a suggestion and an effect together are refused; with agents off a suggestion is refused before the hint is kept
    assert(
      a.managing
        .addHint(
          ApiIds.manager(Enrich),
          HintAdd(
            HintAdd.Text("x"),
            effect = Some(HintEffect(HintEffect.Action("skip"), List(HintEffect.Targets(CkB.value)))),
            suggest = Some(HintAdd.Suggest(true))
          )
        )
        .isLeft
    )
    val off = baseCfg.modify(_.agents).setTo(baseCfg.agents.modify(_.enabled).setTo(AgentsConfig.Enabled(false)))
    val (b, _, _) = handlers(ScriptedClient(Model), off)
    val before = Transactor(ds).read(ManagerRepo.hints(Enrich, live = false)).size
    assert(
      b.managing
        .addHint(ApiIds.manager(Enrich), HintAdd(HintAdd.Text("x"), suggest = Some(HintAdd.Suggest(true))))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("switched off (agents.enabled)"))
    )
    assertEquals(Transactor(ds).read(ManagerRepo.hints(Enrich, live = false)).size, before)

  // ── the per-manager daily cap ──────────────────────────────────────────────────────────────────

  test(
    "daily cap: once a manager's metered spend today reaches its dailyUsd it starts nothing (said once); raised, it goes on"
  ):
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(a, h)
    val m = a.managing
      .create(
        ManagerCreate(
          ManagerCreate.Kind("watch"),
          Some(GoalSlug),
          ManagerConfig(sweeps = Some(List(Sweep.Wire("currency"))), dailyUsd = Some(ManagerConfig.DailyUsd(0.01)))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    // a run of this manager that already cost 0.02 USD today (and one plan-billed step: 0 USD, tokens only)
    val spent = com.augustnagro.magnum.transact(ds)(
      TestRuns.createIn(
        ds,
        Pipeline.CurrencyCheck,
        AgentsControllers.Target("zzagent-watch"),
        None,
        BigDecimal(1),
        AgentsControllers.BudgetTokens(1000),
        RunParams(),
        None,
        Some(Goal),
        None,
        Origin.Manager,
        dryRun = RunService.DryRun(false),
        Some(ApiIds.manager(m.id)),
        None
      )
    )
    connect(ds):
      sql"""INSERT INTO agent_steps (job_id, seq, attempt, name, kind, status, tokens_in, tokens_out, cost_usd)
            VALUES ($spent, 0, 1, 'rank', 'llm', 'done', 1000, 200, 0.02), ($spent, 1, 1, 'rank', 'llm', 'done', 5000, 300, 0)""".update
        .run()
      sql"UPDATE jobs SET status = 'done' WHERE id = $spent".update.run()
    val info = a.managing.get(ApiIds.manager(m.id)).toOption.get.manager
    assertEquals((info.spentTodayUsd, info.tokensToday), (0.02, 6500L))
    assert(ManagersMarkdown.line(info).contains("today 0.0200 of 0.0100 USD, 6.5k tokens"), ManagersMarkdown.line(info))
    a.managing
      .action(ApiIds.manager(m.id), ManagersControllers.Action("resume"), None)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val now = Instant.now()
    assertEquals(a.managers.tick(now).filter(_.value.contains("started")), Nil)
    assertEquals(item(m.id, CurrencyKind).status, "pending")
    val note = connect(ds)(ManagerRepo.get(m.id)).get.statusNote.map(_.render).getOrElse("")
    assert(
      note.startsWith("budget: its spend today (0.0200 USD, metered calls only) reached its daily-usd (0.0100)"),
      note
    )
    a.managers.tick(now)
    assertEquals(
      events(m.id, StarvedKind).count(_.text.value.startsWith("budget: its spend today")),
      1,
      "said once, not every tick"
    )
    // the cap raised: it starts
    a.managing
      .update(
        ApiIds.manager(m.id),
        ManagerUpdate(
          Some(ManagerConfig(sweeps = Some(List(Sweep.Wire("currency"))), dailyUsd = Some(ManagerConfig.DailyUsd(1.0))))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.managers.tick(now)
    assertEquals(item(m.id, CurrencyKind).status, "active")
    // a manager without a cap of its own takes agents.managers.daily-usd (0 = none)
    assertEquals(ManagerPolicy.dailyCap(ManagerConfig(), ManagerDefaultsViews.defaults(baseCfg.agents.managers)), None)
    assertEquals(
      ManagerPolicy
        .dailyCap(
          ManagerConfig(),
          ManagerDefaultsViews.defaults(
            baseCfg.agents.managers.modify(_.dailyUsd).setTo(AgentManagersConfig.DailyUsd(3.0))
          )
        ),
      Some(3.0)
    )
