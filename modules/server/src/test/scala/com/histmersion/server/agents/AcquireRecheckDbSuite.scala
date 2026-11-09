package com.histmersion
package server.agents

import com.histmersion.api.Problem
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ScriptedClient

import com.histmersion.util.database.Transactor

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.automation.agents.*

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.library.Acquire.Checks.*
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.automation.autopilot.AutopilotRepo
import com.histmersion.util.database.Migrate
import com.histmersion.util.jobs.JobRepo
import com.histmersion.plugin.{Access, Fetch, FetchedFile, SourceFetcher, WorkKey}
import com.histmersion.pluginhost.FetchChain
import com.histmersion.server.{Deps, ServerWiring}
import com.histmersion.domain.library.acquisition.*
import com.histmersion.api.library.{FollowupRow, RefRow, UpdateNotice, WorkRef}

import java.time.Instant
import com.histmersion.db.*
import com.histmersion.api.automation.{AgentsApi, Queue}
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.library.{AcquireChecksMarkdown, PdfInfo}
import com.histmersion.api.Api
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.config.DbConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentAutopilotConfig
import com.histmersion.domain.AcquireRecheck
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.acquisition.AcquireCheckRow
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.util.jobs.Job
import com.histmersion.domain.automation.agents.Halt.Stopped
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.ByteCount
import com.histmersion.server.views.WorkListView
import com.histmersion.api.library.Acquire
import com.histmersion.server.AcquisitionTesting
import com.histmersion.domain.library.acquisition.RetractionWatchClient
import com.histmersion.config.AcquireConfig
import com.histmersion.util.jobs.JobQuery
import com.histmersion.util.jobs.JobRow

/** The acquisition list's legal re-check against a `*_test` database (docs/research-process-managers.md "As built: the
  * acquisition list's re-check"): an item the corpus holds with its text is closed, a metadata-only source is fetched
  * into, the others are tried through the legal chain only — a fetcher the config marks unauthorised is never asked,
  * even with acquire.allow-unauthorised = true — within the caps and the shared allowance; what each check found is
  * kept with the item and shown; an item is due again after every-days or a new fetcher generation; nothing runs while
  * the autopilot drains. The upstreams are fixtures: nothing reaches the network. Skipped without
  * HISTMERSION_TEST_DB_URL.
  */
class AcquireRecheckDbSuite extends DbSuite with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Model = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("scripted"))

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.modify(_.db)
      .setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.agents)
      .setTo(
        b.agents
          .modify(_.enabled)
          .setTo(AgentsConfig.Enabled(true))
          .modify(_.autopilot)
          .setTo(
            b.agents.autopilot
              .modify(_.backupHours)
              .setTo(AgentAutopilotConfig.BackupHours(0))
              .modify(_.lintHours)
              .setTo(AgentAutopilotConfig.LintHours(0))
              .modify(_.ingestPerDay)
              .setTo(AgentAutopilotConfig.IngestPerDay(0))
          )
      )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzrecheck%'".update.run()
      sql"DELETE FROM queue_items WHERE text ILIKE '%zzrecheck%' OR text LIKE 'acquire re-check:%'".update.run()
      sql"DELETE FROM jobs WHERE kind = ${RecheckJob.JobKind}".update.run()
      sql"UPDATE agent_autopilot SET enabled = false, paused = '{}', changed_by = '', last_ensure_at = NULL".update
        .run()
    val _ = transact(ds)(AutopilotRepo.setDrain(on = false, AutopilotRepo.Row.ChangedBy("test")))

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  /** The fixture's legal ingest: what `answer` says for each DOI; records the DOI and the source it was asked into. */
  final private class RecheckUpstream extends AgentUpstream:
    @volatile var generation = AcquireRecheck.LastCheck.Generation("g1")
    @volatile var answer: (Doi, Option[Citekey]) => Either[Problem, Ingest.Report] = (d, _) =>
      Left(
        Problem(
          s"${IngestBacklog.NoLegalCopy} $d: no legal open-access PDF found — unpaywall: no copy — none"
        )
      )
    val asked = java.util.concurrent.ConcurrentLinkedQueue[(Doi, Option[Citekey])]()
    def meta(doi: Doi) = Left(Problem("not stubbed"))
    def refs(citekey: Citekey, apply: AgentUpstream.RefsApply) = Left(Problem("not stubbed"))
    def assess(citekey: Citekey, apply: AgentUpstream.AssessApply) = Left(Problem("not stubbed"))
    def followups(id: AgentUpstream.FollowupsId, since: Option[Year], limit: Option[FollowupsInput.Limit]) = Left(
      Problem("not stubbed")
    )
    def discover(query: Search.Query, limit: AgentUpstream.Limit) = Left(Problem("not stubbed"))
    override def ingestLegalInto(doi: Doi, into: Option[Citekey]) = { asked.add(doi -> into); answer(doi, into) }
    override def fetcherGeneration = generation

  private def setup(cfg: AppConfig = baseCfg): (AgentsControllers, ServerWiring, RecheckUpstream) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    val a = AgentsControllers(ServerEngineSite(deps, h), (_, _) => Right(ScriptedClient(Model)))
    val up = RecheckUpstream()
    a.attach(up)
    (a, h, up)

  private def doi(n: String) = s"10.5555/zzrecheck.$n"
  private def checkItem(i: Queue.QueueItem.Id): AcquireCheckRow.Item =
    AcquireCheckRow.Item(i.value)
  private def jobKey(id: SpatialHierarchyResult.Job.Id): Job.Id =
    Job.Id.applyUnsafe(id.value)
  private def item(h: ServerWiring, n: String): Queue.QueueItem.Id =
    h.workLists
      .add(
        WorkListControllers.ListName("acquire"),
        QueueAdd(
          Queue.QueueItem.Text(s"Zzrecheck $n, A work — https://doi.org/${doi(n)}"),
          topic = Some(Queue.QueueItem.Topic("zzrecheck"))
        )
      )
      .toOption
      .get
      .id
  private def asked(up: RecheckUpstream): List[(Doi, Option[Citekey])] =
    import scala.jdk.CollectionConverters.*
    up.asked.asScala.toList
  private def found(d: Doi, ck: Citekey) =
    Right(
      Ingest.Report(
        com.histmersion.api.library.Citekey(ck.value),
        Some(com.histmersion.api.library.Doi(d.value)),
        Some(com.histmersion.api.library.Title("found")),
        created = Ingest.Report.Created(true),
        Some(PdfInfo(PdfInfo.Source("unpaywall"), None, ByteCount(2048), None)),
        Ingest.Report.EdgesAdded(0),
        None,
        Nil
      )
    )

  /** A pass at once, as the managers' tick runs it (a person's pass of more than one item is a job). */
  private def passNow(a: AgentsControllers, in: RecheckRequest): Either[Api.Error, RecheckResult] =
    a.acquireRecheck.pass(in, Instant.now()).toRight(Api.Error(Problem("a pass is running")))
  private def status(h: ServerWiring, id: Queue.QueueItem.Id): String =
    h.workListView
      .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(true))
      .toOption
      .get
      .items
      .find(_.id == id)
      .get
      .status
      .value

  test(
    "a pass: the item the corpus holds with its text closes, a metadata-only source is fetched into, the rest is tried and keeps why"
  ):
    val (a, h, up) = setup()
    assert(
      h.library.sources
        .put(
          Citekey("zzrecheck_full"),
          Source.Put(
            title = com.histmersion.api.library.Title("Full"),
            doi = (Some(doi("full"))).map(com.histmersion.api.library.Doi.apply),
            body = Some(Source.OcrMarkdown("The text."))
          )
        )
        .isRight
    )
    assert(
      h.library.sources
        .put(
          Citekey("zzrecheck_meta"),
          Source.Put(
            title = com.histmersion.api.library.Title("Meta"),
            doi = (Some(doi("meta"))).map(com.histmersion.api.library.Doi.apply),
            notes = Some(Source.Notes("Compiled."))
          )
        )
        .isRight
    )
    val full = item(h, "full"); val meta = item(h, "meta"); val none = item(h, "none")
    val noDoi = h.workLists
      .add(WorkListControllers.ListName("acquire"), QueueAdd(Queue.QueueItem.Text("Zzrecheck, a book with no DOI")))
      .toOption
      .get
      .id
    up.answer = (d, into) =>
      if d.value == doi("meta") then found(d, into.get)
      else
        Left(
          Problem(
            s"${IngestBacklog.NoLegalCopy} $d: no legal open-access PDF found — unpaywall: no copy — none; landing: refused — bot-protection 403"
          )
        )
    val r = passNow(a, RecheckRequest(limit = Some(Acquire.Checks.RecheckRequest.Limit(10))))
      .fold(e => fail(e.message.value), identity)
    assertEquals((r.closed, r.fetched, r.noCopy, r.failed), (1, 1, 1, 0), r.toString)
    assertEquals(
      asked(up).toSet,
      Set[(String, Option[String])](doi("meta") -> Some("zzrecheck_meta"), doi("none") -> None),
      "the closed one is not fetched"
    )
    assertEquals(status(h, full), Queue.Done)
    assert(
      h.workListView
        .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(true))
        .toOption
        .get
        .items
        .find(_.id == full)
        .get
        .reason
        .exists(_.value.contains("[[zzrecheck_full]]"))
    )
    assertEquals(
      (status(h, meta), status(h, none), status(h, noDoi)),
      (Queue.Open, Queue.Open, Queue.Open),
      "a found copy is closed by its conversion, not here"
    )
    assertEquals(SourceFixtures.load(ds, Citekey("zzrecheck_meta")).flatMap(_.notes), Some("Compiled."))
    // what each check found is kept, and shown with the item
    val listing =
      h.workListView.list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(false)).toOption.get
    val day = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
    assertEquals(
      listing.checks.find(_.item == none).map(_.reason),
      Some(s"checked $day: no legal copy (unpaywall: no copy — none; landing: refused — bot-protection 403)")
    )
    assertEquals(listing.checks.find(_.item == meta).map(_.outcome), Some(Fetched))
    assert(listing.markdown.map(_.value).getOrElse("").contains(s"_checked $day: no legal copy"))
    assert(
      h.workListView
        .list(WorkListControllers.KindOfList("log"), all = WorkListView.IncludeAll(true))
        .toOption
        .get
        .items
        .exists(_.text.value.startsWith("acquire re-check: 1 closed"))
    )
    // again at once: nothing is due (checked within every-days), nothing is asked
    val again = passNow(a, RecheckRequest(limit = Some(Acquire.Checks.RecheckRequest.Limit(10))))
      .fold(e => fail(e.message.value), identity)
    assertEquals((again.due, again.closed), (0, 0))
    assertEquals(asked(up).size, 2)

  test("due again after every-days, or at once under a new fetcher generation; one item by id, due or not"):
    val (a, h, up) = setup()
    val one = item(h, "one"); val two = item(h, "two")
    val _ = passNow(a, RecheckRequest(limit = Some(Acquire.Checks.RecheckRequest.Limit(10))))
    assertEquals(asked(up).size, 2)
    val later = Instant.now().plusSeconds(8L * 86400L)
    assertEquals(
      a.acquireRecheck.run(RecheckRequest(dryRun = Acquire.Checks.RecheckRequest.DryRun(true)), later).toOption.get.due,
      2,
      "a week later both are due"
    )
    assertEquals(
      a.acquireRecheck.run(RecheckRequest(dryRun = Acquire.Checks.RecheckRequest.DryRun(true))).toOption.get.due,
      0
    )
    up.generation = AcquireRecheck.LastCheck.Generation("g2") // a new fetcher, a new key
    val dry = a.acquireRecheck
      .run(RecheckRequest(dryRun = Acquire.Checks.RecheckRequest.DryRun(true)))
      .fold(e => fail(e.message.value), identity)
    assertEquals(dry.due, 2)
    assertEquals(asked(up).size, 2, "a dry run asks no fetcher")
    assertEquals(dry.rows.map(_.action).distinct, List(WouldTry))
    val byId = a.acquireRecheck
      .run(RecheckRequest(id = Some(RecheckRequest.Id(one.value))))
      .fold(e => fail(e.message.value), identity)
    assertEquals(byId.rows.map(_.item.value), List(one.value))
    assertEquals(asked(up).size, 3)
    assertEquals(AcquisitionTesting.checks(ds).of(List(checkItem(one))).get(checkItem(one)).map(_.checks), Some(2))
    assert(
      a.acquireRecheck.run(RecheckRequest(id = Some(Acquire.Checks.RecheckRequest.Id(999999)))).isLeft,
      "no such open item"
    )
    val _ = two

  test("the caps: per pass, per day; a status line with what is due"):
    val (a, h, up) = setup(
      baseCfg
        .modify(_.acquire)
        .setTo(
          baseCfg.acquire
            .modify(_.recheck)
            .setTo(baseCfg.acquire.recheck.modify(_.perPass).setTo(ItemCount(2)).modify(_.perDay).setTo(ItemCount(3)))
        )
    )
    (1 to 5).foreach(i => item(h, s"c$i"))
    val first = passNow(a, RecheckRequest()).toOption.get
    assertEquals((first.due, first.allowance, asked(up).size), (5, 2, 2))
    assert(first.note.exists(_.value.contains("3 due item(s) wait for the next pass")), first.note.toString)
    val second = passNow(a, RecheckRequest()).toOption.get
    assertEquals((second.allowance, asked(up).size), (1, 3), "the day's cap: 3")
    val st = a.acquireRecheck.status()
    assertEquals((st.due, st.triedToday, st.perDay), (2, 3, 3))
    assert(st.nextPass.exists(_.value.startsWith("tomorrow")), st.toString)
    assert(
      a.autopilotPage(a.autopilot.status()).contains("- acquire re-check: 2 of"),
      "the autopilot status says it"
    )

  test("under the autopilot's shared allowance: the watchers' backlog first, then at most share-percent of it"):
    val cfg = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents
          .modify(_.autopilot)
          .setTo(baseCfg.agents.autopilot.modify(_.ingestPerDay).setTo(AgentAutopilotConfig.IngestPerDay(8)))
      )
      .modify(_.acquire)
      .setTo(
        baseCfg.acquire
          .modify(_.recheck)
          .setTo(
            baseCfg.acquire.recheck.modify(_.perPass).setTo(ItemCount(10)).modify(_.sharePercent).setTo(Percent(25))
          )
      )
    val (a, h, up) = setup(cfg)
    connect(ds)(sql"UPDATE agent_autopilot SET enabled = true".update.run())
    (1 to 4).foreach(i => item(h, s"s$i"))
    up.answer = (d, _) => found(d, Citekey.applyUnsafe("zzrecheck" + d.value.replaceAll("[^a-z0-9]", "")))
    val r = passNow(a, RecheckRequest()).toOption.get
    assertEquals((r.allowance, r.fetched), (2, 2), "25 % of 8")
    assertEquals(
      a.autopilot.sharedIngestLeft(Instant.now()),
      Some(6),
      "the re-check's finds count against the shared allowance"
    )
    assertEquals(passNow(a, RecheckRequest()).toOption.get.allowance, 0)

  test("draining: the managers' pass does nothing and a pass by hand is refused, a dry run still lists"):
    val (a, h, up) = setup()
    val _ = item(h, "d1")
    val _ = transact(ds)(AutopilotRepo.setDrain(on = true, AutopilotRepo.Row.ChangedBy("test")))
    assertEquals(a.acquireRecheck.tick(Instant.now()), Nil)
    val r = a.acquireRecheck.run(RecheckRequest()).toOption.get
    assert(r.note.exists(_.value.contains("draining")), r.toString)
    assertEquals(
      a.acquireRecheck.run(RecheckRequest(dryRun = Acquire.Checks.RecheckRequest.DryRun(true))).toOption.get.due,
      1
    )
    assertEquals(asked(up), Nil)
    val _ = transact(ds)(AutopilotRepo.setDrain(on = false, AutopilotRepo.Row.ChangedBy("test")))
    assert(a.acquireRecheck.tick(Instant.now()).exists(_.value.startsWith("acquire re-check:")))
    assertEquals(asked(up).size, 1)
    assertEquals(a.acquireRecheck.tick(Instant.now()), Nil, "the next pass waits pass-minutes")

  // ── through the real acquisition handlers ──────────────────────────────────────────────────────

  private object Discovery extends DiscoveryClient:
    def search(
        query: Search.Query,
        since: Option[Year],
        sort: Option[DiscoveryClient.SearchSort],
        limit: DiscoveryClient.SearchLimit
    ) = Right(Nil)
    def meta(d: Doi) = Right(
      WorkRef(
        doi = (Some(d.value)).map(com.histmersion.api.library.Doi.apply),
        title = (Some(s"Work $d")).map(com.histmersion.api.library.Title.apply),
        year = (Some(2020)).map(com.histmersion.api.catalogue.Year.apply),
        authors = (List("Zz Zzrecheck")).map(com.histmersion.api.library.AuthorName.apply),
        citekey = (Some("zzrecheck2020_" + d.value.replaceAll("[^a-z0-9]", "").takeRight(6)))
          .map(com.histmersion.api.library.Citekey.apply)
      )
    )
    def references(d: Doi) = Right((DiscoveryClient.Backend("crossref"), Nil))
    def updates(d: Doi) = Right(Nil)
  private object NoCitations extends CitationsClient:
    def citing(work: WorkRef, since: Option[Year], limit: CitationsClient.CitingLimit) = Right(
      (CitationsClient.Backend("stub"), Nil)
    )
  private object NoWiki extends WikiClient:
    def refs(articleUrl: WikiClient.ArticleUrl) = Left(domain.Problem("not used here"))
  private object NoRw extends RetractionWatchClient:
    def csv() = Left(domain.Problem("not used here"))

  test(
    "through the real chain: the legal fetchers only, even with acquire.allow-unauthorised = true; a copy found goes into the metadata-only source"
  ):
    val unauthorisedAsked = java.util.concurrent.atomic.AtomicInteger(0)
    val unauthorised = new SourceFetcher:
      val access = Access.Unauthorised
      def fetch(w: WorkKey, c: Fetch.Context) =
        unauthorisedAsked.incrementAndGet()
        Fetch.Found(
          FetchedFile(
            FetchedFile.Source("stub"),
            None,
            FetchedFile.Bytes(("%PDF-1.4\n" + "u" * 2000).getBytes("UTF-8")),
            FetchedFile.Extension("pdf")
          )
        )
    val open = new SourceFetcher:
      val access = Access.Open
      def fetch(w: WorkKey, c: Fetch.Context) =
        if w.doi.exists(_.value == doi("oa")) then
          Fetch.Found(
            FetchedFile(
              FetchedFile.Source("unpaywall"),
              Some(FetchedFile.Url("https://example.invalid/oa.pdf")),
              FetchedFile.Bytes(("%PDF-1.4\n" + "o" * 2000).getBytes("UTF-8")),
              FetchedFile.Extension("pdf")
            )
          )
        else Fetch.Miss(plugin.Problem("no open-access location"))
    val chain = FetchChain(
      List(FetchChain.Step.Id("unpaywall") -> open, FetchChain.Step.Id("stub-unauthorised") -> unauthorised),
      allowUnauthorised = FetchChain.AllowUnauthorised(true)
    )
    val cfg = baseCfg
      .modify(_.acquire)
      .setTo(baseCfg.acquire.modify(_.allowUnauthorised).setTo(AcquireConfig.AllowUnauthorised(true)))
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    val a = AgentsControllers(ServerEngineSite(deps, h), (_, _) => Right(ScriptedClient(Model)))
    val acq = AcquisitionTesting.controllers(
      deps,
      Discovery,
      NoCitations,
      chain,
      NoWiki,
      NoRw,
      AcquisitionTesting.skeleton(deps),
      generation = "real"
    )
    a.attach(AgentUpstream.of(acq))
    assert(
      h.library.sources
        .put(
          Citekey("zzrecheck_kept"),
          Source.Put(
            title = com.histmersion.api.library.Title("Kept"),
            doi = (Some(doi("oa"))).map(com.histmersion.api.library.Doi.apply),
            notes = Some(Source.Notes("Compiled."))
          )
        )
        .isRight
    )
    val oa = item(h, "oa"); val closed = item(h, "closed")
    val r = passNow(a, RecheckRequest(limit = Some(Acquire.Checks.RecheckRequest.Limit(10))))
      .fold(e => fail(e.message.value), identity)
    assertEquals(unauthorisedAsked.get, 0, "a fetcher the config marks unauthorised is never asked")
    assertEquals((r.fetched, r.noCopy), (1, 1), r.toString)
    assertEquals(
      r.rows.find(_.item.value == oa.value).flatMap(_.citekey),
      Some("zzrecheck_kept"),
      "fetched into the metadata-only source"
    )
    assertEquals(
      SourceFixtures.load(ds, Citekey("zzrecheck_kept")).flatMap(_.notes),
      Some("Compiled."),
      "its enrichment stays"
    )
    assert(
      SourceFixtures
        .load(ds, Citekey.applyUnsafe("zzrecheck2020_" + doi("closed").replaceAll("[^a-z0-9]", "").takeRight(6)))
        .isEmpty,
      "no legal copy: no metadata-only source is created"
    )
    val why =
      AcquisitionTesting.checks(ds).of(List(checkItem(closed))).get(checkItem(closed)).map(_.reason.value).getOrElse("")
    assert(why.contains("no legal copy (unpaywall: no copy — no open-access location)"), why)
    assertEquals(
      AcquisitionTesting.checks(ds).of(List(checkItem(oa))).get(checkItem(oa)).map(_.generation),
      Some("real")
    )

  // ── the re-check job ───────────────────────────────────────────────────────────────────────────

  private def jobOf(r: Either[Api.Error, RecheckResult]): JobInfo =
    r.fold(e => fail(e.message.value), res => res.job.getOrElse(fail(s"not a job: $res")))

  /** The job once it ended (it runs on its own thread) and its slot is free. */
  private def ended(a: AgentsControllers, id: SpatialHierarchyResult.Job.Id): JobInfo =
    awaitTrue(s"job $id")(
      Transactor(ds).read(JobRepo.get(jobKey(id))).exists(j => JobState.parse(j.status).exists(_.isTerminal)) &&
        !a.acquireRecheck.jobRunning
    )
    Transactor(ds).read(JobRepo.get(jobKey(id))).get

  private def awaitTrue(what: String)(p: => Boolean): Unit =
    val until = System.currentTimeMillis() + 30000L
    while !p do
      if System.currentTimeMillis() > until then fail(s"timed out waiting for $what")
      Thread.sleep(20)

  /** An answer that waits for `release` once `started` (the item in flight while the test looks). */
  final private class Gate:
    val started = java.util.concurrent.CountDownLatch(1)
    val release = java.util.concurrent.CountDownLatch(1)
    def hold(d: Doi): Either[Problem, Ingest.Report] =
      started.countDown()
      val _ = release.await(30, java.util.concurrent.TimeUnit.SECONDS)
      Left(
        Problem(
          s"${IngestBacklog.NoLegalCopy} $d: no legal open-access PDF found — unpaywall: no copy — none"
        )
      )

  private val tightCfg = baseCfg
    .modify(_.agents)
    .setTo(
      baseCfg.agents
        .modify(_.autopilot)
        .setTo(baseCfg.agents.autopilot.modify(_.ingestPerDay).setTo(AgentAutopilotConfig.IngestPerDay(4)))
    )
    .modify(_.acquire)
    .setTo(
      baseCfg.acquire
        .modify(_.recheck)
        .setTo(
          baseCfg.acquire.recheck
            .modify(_.perPass)
            .setTo(ItemCount(1))
            .modify(_.perDay)
            .setTo(ItemCount(2))
            .modify(_.sharePercent)
            .setTo(Percent(0))
        )
    )

  test(
    "--all: a job over every open item with a DOI, due or not, past every cap; each check recorded, the finds counted"
  ):
    val (a, h, up) = setup(tightCfg)
    connect(ds)(sql"UPDATE agent_autopilot SET enabled = true".update.run())
    val ids = (1 to 6).toList.map(i => item(h, s"a$i"))
    val _ = h.workLists
      .add(WorkListControllers.ListName("acquire"), QueueAdd(Queue.QueueItem.Text("Zzrecheck, a book with no DOI")))
      .toOption
      .get
      .id
    // two of them were checked an hour ago: not due, still tried
    ids
      .take(2)
      .foreach(i =>
        AcquisitionTesting
          .checks(ds)
          .record(
            checkItem(i),
            Instant.now().minusSeconds(3600),
            AcquireRecheck.LastCheck.Generation("g1"),
            AcquireRecheck.Outcome.NoCopy,
            domain.Problem("x")
          )
      )
    assertEquals(
      a.acquireRecheck.run(RecheckRequest(limit = Some(Acquire.Checks.RecheckRequest.Limit(5)))).toOption.get.allowance,
      0,
      "without --all the caps leave nothing (no share of the shared allowance)"
    )
    assertEquals(asked(up), Nil)
    assert(
      h.library.sources
        .put(
          Citekey("zzrecheck_full"),
          Source.Put(
            title = com.histmersion.api.library.Title("Full"),
            doi = (Some(doi("full"))).map(com.histmersion.api.library.Doi.apply),
            body = Some(Source.OcrMarkdown("The text."))
          )
        )
        .isRight
    )
    val full = item(h, "full")
    val ingestedBefore = a.autopilot.ingestedToday(Instant.now())
    up.answer = (d, into) =>
      if d.value == doi("a3") then found(d, Citekey("zzrecheck_a3"))
      else if d.value == doi("a4") then Left(Problem("HTTP 429 Too Many Requests from api.example.invalid"))
      else
        Left(
          Problem(
            s"${IngestBacklog.NoLegalCopy} $d: no legal open-access PDF found — unpaywall: no copy — none"
          )
        )
    val r = a.acquireRecheck.run(
      RecheckRequest(all = Acquire.Checks.RecheckRequest.All(true), by = Some(Acquire.Checks.RecheckRequest.By("test")))
    )
    val job = jobOf(r)
    assertEquals((r.toOption.get.withDoi, r.toOption.get.allowance), (7, 6))
    assertEquals(job.kind.value: String, RecheckJob.JobKind: String)
    assertEquals(job.detail.flatMap(RecheckJob.Detail.of).map(d => (d.all, d.by)), Some((true, "test")))
    val done = ended(a, job.id)
    assertEquals(done.status, JobState.Done.wire, done.toString)
    assertEquals(
      asked(up).map(_._1).toSet,
      ids.indices.map(k => doi(s"a${k + 1}")).toSet,
      "every item with a DOI, the closed one not"
    )
    assertEquals(
      asked(up).map(_._1).takeRight(2),
      List(doi("a1"), doi("a2")),
      "the never-checked first, the checked ones after"
    )
    assertEquals(
      done.progress,
      Some(
        "6/6 items · 1 closed · 1 fetched · 4 no legal copy · 1 failed (HTTP 429 Too Many Requests from api.example.invalid)"
      )
    )
    assertEquals(status(h, full), Queue.Done)
    val checks = AcquisitionTesting.checks(ds).of(ids.map(checkItem))
    assertEquals(
      checks.values.map(_.outcome.value).toList.sorted,
      List(Failed, Fetched, NoCopy, NoCopy, NoCopy, NoCopy)
    )
    assert(checks(checkItem(ids(3))).reason.value.contains("no answer (HTTP 429"), checks(checkItem(ids(3))).reason)
    assertEquals(a.acquireRecheck.fetchedToday(Instant.now()), 1)
    assertEquals(
      a.autopilot.ingestedToday(Instant.now()).value - ingestedBefore.value,
      1,
      "the job's find counts in the shared ingest counter"
    )
    val log =
      h.workListView
        .list(WorkListControllers.KindOfList("log"), all = WorkListView.IncludeAll(true))
        .toOption
        .get
        .items
        .map(_.text.value)
        .filter(_.startsWith("acquire re-check:"))
    assert(log.exists(l => l.contains("1 closed") && l.contains(s"(by test, job ${job.id})")), log.toString)
    // a rate-limited check stays due for the schedule: retry-hours later, not every-days
    val later = Instant.now().plusSeconds(7L * 3600L)
    assertEquals(
      a.acquireRecheck
        .run(RecheckRequest(dryRun = Acquire.Checks.RecheckRequest.DryRun(true)), later)
        .toOption
        .get
        .rows
        .filter(_.action.value != WouldClose)
        .map(_.item.value)
        .toSet
        .intersect(ids.map(_.value).toSet),
      Set(ids(3).value)
    )

  test("a person's capped pass of more than one item is a job too; one item answers at once"):
    val (a, h, up) = setup(
      baseCfg
        .modify(_.acquire)
        .setTo(
          baseCfg.acquire
            .modify(_.recheck)
            .setTo(baseCfg.acquire.recheck.modify(_.perPass).setTo(ItemCount(3)).modify(_.perDay).setTo(ItemCount(40)))
        )
    )
    (1 to 5).foreach(i => item(h, s"l$i"))
    val one = a.acquireRecheck
      .run(RecheckRequest(limit = Some(Acquire.Checks.RecheckRequest.Limit(1))))
      .fold(e => fail(e.message.value), identity)
    assertEquals((one.job, one.noCopy), (None, 1), "at most one: the pass at once")
    val r = a.acquireRecheck.run(RecheckRequest(limit = Some(Acquire.Checks.RecheckRequest.Limit(3))))
    val job = jobOf(r)
    assertEquals(r.toOption.get.allowance, 3)
    assertEquals(ended(a, job.id).progress, Some("3/3 items · 0 closed · 0 fetched · 3 no legal copy · 0 failed"))
    assertEquals(asked(up).size, 4)
    assertEquals(
      a.acquireRecheck.run(RecheckRequest(limit = Some(Acquire.Checks.RecheckRequest.Limit(3)))).toOption.get.job,
      None,
      "one due item left: at once"
    )
    assertEquals(asked(up).size, 5)

  test(
    "one job at a time: a second request names the running one; the scheduled passes wait; the status line shows it"
  ):
    val (a, h, up) = setup()
    val ids = (1 to 3).toList.map(i => item(h, s"o$i"))
    val gate = Gate()
    up.answer = (d, _) => gate.hold(d)
    val job = jobOf(a.acquireRecheck.run(RecheckRequest(all = Acquire.Checks.RecheckRequest.All(true))))
    assert(gate.started.await(30, java.util.concurrent.TimeUnit.SECONDS))
    val second = a.acquireRecheck.run(RecheckRequest(all = Acquire.Checks.RecheckRequest.All(true)))
    assert(
      second.left.exists(e =>
        e.message.value.contains(s"job ${job.id}") && e.code.exists(_.value == AgentsApi.CodeConflict)
      ),
      second.toString
    )
    assert(
      a.acquireRecheck
        .run(RecheckRequest(id = Some(RecheckRequest.Id(ids.last.value))))
        .left
        .exists(_.message.value.contains(job.id.value)),
      "nor one item"
    )
    assertEquals(a.acquireRecheck.tick(Instant.now()), Nil, "no scheduled pass while the job runs")
    val line = AcquireChecksMarkdown.line(a.acquireRecheck.status())
    assert(line.contains(s"job ${job.id} running: 0/3 items"), line)
    assert(a.autopilotPage(a.autopilot.status()).contains(s"job ${job.id} running"), "in the autopilot status")
    gate.release.countDown()
    assertEquals(ended(a, job.id).status, JobState.Done.wire)
    assertEquals(asked(up).size, 3, "the tick tried none of them")
    assert(!AcquireChecksMarkdown.line(a.acquireRecheck.status()).contains("job "), "a finished job is not named")

  test("draining: the job stops after the item in flight, the drain waits for it, and a tick resumes it once lifted"):
    val (a, h, up) = setup()
    val ids = (1 to 4).toList.map(i => item(h, s"d$i"))
    val gate = Gate()
    up.answer = (d, _) => gate.hold(d)
    val job = jobOf(
      a.acquireRecheck.run(
        RecheckRequest(
          all = Acquire.Checks.RecheckRequest.All(true),
          by = Some(Acquire.Checks.RecheckRequest.By("test"))
        )
      )
    )
    assert(gate.started.await(30, java.util.concurrent.TimeUnit.SECONDS))
    val _ = transact(ds)(AutopilotRepo.setDrain(on = true, AutopilotRepo.Row.ChangedBy("test")))
    assertEquals(a.drain.tick(Instant.now()), Nil, "not drained while the job's item is in flight")
    gate.release.countDown()
    val stopped = ended(a, job.id)
    assertEquals((stopped.status, stopped.error), (JobState.Failed.wire, Some(Stopped.DrainingText)))
    assertEquals(asked(up).size, 1, "the item in flight finished, nothing after it")
    assertEquals(a.drain.tick(Instant.now()), List("drained"))
    assertEquals(a.acquireRecheck.tick(Instant.now()), Nil, "no resume while draining")
    assert(
      AcquireChecksMarkdown.line(a.acquireRecheck.status()).contains(s"job ${job.id} stopped"),
      "the status names it"
    )
    val _ = transact(ds)(AutopilotRepo.setDrain(on = false, AutopilotRepo.Row.ChangedBy("test")))
    up.answer = (d, _) => Left(Problem(s"${IngestBacklog.NoLegalCopy} $d: no legal open-access PDF found"))
    val resumed = a.acquireRecheck.tick(Instant.now())
    assert(
      resumed.exists(_.value.contains(s"job ${job.id} (${Stopped.DrainingText}) resumed as job")),
      resumed.toString
    )
    val next = Transactor(ds)
      .read(JobRepo.page(JobQuery(kinds = List(JobQuery.Kinds(RecheckJob.JobKind))), PageSize(1), None, None))
      .toOption
      .get
      ._1
      .head
    assertEquals(next.detail.flatMap(RecheckJob.Detail.of).flatMap(_.resumes).map(_.value), Some(job.id.value))
    assertEquals(
      next.detail.flatMap(RecheckJob.Detail.of).map(_.since),
      job.detail.flatMap(RecheckJob.Detail.of).map(_.since),
      "the same start"
    )
    val end = ended(a, next.id)
    assertEquals(
      end.progress,
      Some("4/4 items · 0 closed · 0 fetched · 4 no legal copy · 0 failed"),
      "the counts carry over"
    )
    assertEquals(
      asked(up).map(_._1),
      ids.indices.toList.map(i => doi(s"d${i + 1}")),
      "each item once: the resumed job skipped the first"
    )
    assertEquals(
      a.acquireRecheck.tick(Instant.now()).filter(_.value.contains("resumed")),
      Nil,
      "a finished job is not resumed"
    )

  test("after a restart: the interrupted job is resumed — by the tick, or by --all again — skipping what it checked"):
    val (a, h, up) = setup()
    val ids = (1 to 4).toList.map(i => item(h, s"r$i"))
    val since = Instant.now().minusSeconds(600)
    // what a restart leaves: a job that ran, checked two items, and was failed as interrupted at startup
    val j = Transactor(ds).transact(
      JobRepo.create(
        Job.Kind.applyUnsafe(RecheckJob.JobKind),
        None,
        None,
        detail = Some(
          RecheckJob.Detail(
            Some(
              RecheckJob(
                all = Acquire.Checks.RecheckJob.All(true),
                since = RecheckJob.Since(since.toString),
                by = Acquire.Checks.RecheckJob.By("cli")
              )
            )
          )
        )
      )
    )
    val _ = Transactor(ds).transact(JobRepo.transition(jobKey(j.id), JobState.Queued, JobState.Running))
    ids
      .take(2)
      .foreach(i =>
        AcquisitionTesting
          .checks(ds)
          .record(
            checkItem(i),
            since.plusSeconds(60),
            AcquireRecheck.LastCheck.Generation("g1"),
            AcquireRecheck.Outcome.NoCopy,
            domain.Problem("x")
          )
      )
    val _ = Transactor(ds).transact(JobRepo.failInterrupted())
    assertEquals(Transactor(ds).read(JobRepo.get(jobKey(j.id))).flatMap(_.error), Some(JobState.Interrupted))
    val r = a.acquireRecheck.run(RecheckRequest(all = Acquire.Checks.RecheckRequest.All(true)))
    val job = jobOf(r)
    assert(r.toOption.get.note.exists(_.value.contains(s"resumes job ${j.id}")), r.toString)
    assertEquals(ended(a, job.id).progress, Some("4/4 items · 0 closed · 0 fetched · 4 no legal copy · 0 failed"))
    assertEquals(asked(up).map(_._1), List(doi("r3"), doi("r4")))
    // a fresh --all after a finished one starts over, every item again
    val again = jobOf(a.acquireRecheck.run(RecheckRequest(all = Acquire.Checks.RecheckRequest.All(true))))
    assertEquals(again.detail.flatMap(RecheckJob.Detail.of).flatMap(_.resumes), None)
    val _ = ended(a, again.id)
    assertEquals(asked(up).size, 6)
    // the tick resumes an interrupted one by itself
    Thread.sleep(5)
    val k = Transactor(ds).transact(
      JobRepo.create(
        Job.Kind.applyUnsafe(RecheckJob.JobKind),
        None,
        None,
        detail = Some(
          RecheckJob.Detail(
            Some(
              RecheckJob(
                all = Acquire.Checks.RecheckJob.All(true),
                since = RecheckJob.Since(Instant.now().toString),
                by = Acquire.Checks.RecheckJob.By("cli")
              )
            )
          )
        )
      )
    )
    val _ = Transactor(ds).transact(JobRepo.transition(jobKey(k.id), JobState.Queued, JobState.Running))
    val _ = Transactor(ds).transact(
      JobRepo.transition(
        jobKey(k.id),
        JobState.Running,
        JobState.Failed,
        None,
        Some(Problem(JobState.Interrupted))
      )
    )
    assert(a.acquireRecheck.tick(Instant.now()).exists(_.value.contains(s"job ${k.id} (interrupted) resumed")))
    awaitTrue("the resumed job")(!a.acquireRecheck.jobRunning)
    assertEquals(asked(up).size, 10)

  test("--all stays on the legal chain: a fetcher the config marks unauthorised is never asked"):
    val unauthorisedAsked = java.util.concurrent.atomic.AtomicInteger(0)
    val unauthorised = new SourceFetcher:
      val access = Access.Unauthorised
      def fetch(w: WorkKey, c: Fetch.Context) =
        unauthorisedAsked.incrementAndGet()
        Fetch.Found(
          FetchedFile(
            FetchedFile.Source("stub"),
            None,
            FetchedFile.Bytes(("%PDF-1.4\n" + "u" * 2000).getBytes("UTF-8")),
            FetchedFile.Extension("pdf")
          )
        )
    val open = new SourceFetcher:
      val access = Access.Open
      def fetch(w: WorkKey, c: Fetch.Context) = Fetch.Miss(plugin.Problem("no open-access location"))
    val chain = FetchChain(
      List(FetchChain.Step.Id("unpaywall") -> open, FetchChain.Step.Id("stub-unauthorised") -> unauthorised),
      allowUnauthorised = FetchChain.AllowUnauthorised(true)
    )
    val cfg = baseCfg
      .modify(_.acquire)
      .setTo(baseCfg.acquire.modify(_.allowUnauthorised).setTo(AcquireConfig.AllowUnauthorised(true)))
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    val a = AgentsControllers(ServerEngineSite(deps, h), (_, _) => Right(ScriptedClient(Model)))
    a.attach(
      AgentUpstream.of(
        AcquisitionTesting.controllers(
          deps,
          Discovery,
          NoCitations,
          chain,
          NoWiki,
          NoRw,
          AcquisitionTesting.skeleton(deps),
          generation = "real"
        )
      )
    )
    val ids = (1 to 3).toList.map(i => item(h, s"u$i"))
    val done = ended(a, jobOf(a.acquireRecheck.run(RecheckRequest(all = Acquire.Checks.RecheckRequest.All(true)))).id)
    assertEquals(done.progress, Some("3/3 items · 0 closed · 0 fetched · 3 no legal copy · 0 failed"), done.toString)
    assertEquals(unauthorisedAsked.get, 0, "a fetcher the config marks unauthorised is never asked")
    assertEquals(AcquisitionTesting.checks(ds).of(ids.map(checkItem)).size, 3)
