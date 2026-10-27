package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.api.AcquireChecks.*
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.db.{AcquireCheckRepo, AutopilotRepo, Migrate, SourceRepo}
import com.histmersion.plugin.{Access, Fetch, FetchContext, FetchedFile, SourceFetcher, WorkKey}
import com.histmersion.pluginhost.FetchChain
import com.histmersion.server.{Deps, Handlers}
import com.histmersion.server.acquire.*

import java.time.Instant

/** The acquisition list's legal re-check against a `*_test` database (docs/research-process-managers.md "As
  * built: the acquisition list's re-check"): an item the corpus holds with its text is closed, a
  * metadata-only source is fetched into, the others are tried through the legal chain only — a fetcher the
  * config marks unauthorised is never asked, even with acquire.allow-unauthorised = true — within the caps
  * and the shared allowance; what each check found is kept with the item and shown; an item is due again
  * after every-days or a new fetcher generation; nothing runs while the autopilot drains. The upstreams are
  * fixtures: nothing reaches the network. Skipped without HISTMERSION_TEST_DB_URL. */
class AcquireRecheckDbSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout         = scala.concurrent.duration.Duration(120, "s")

  private val Model = ModelRef(ProviderKind.Local, "scripted")

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(db = b.db.copy(url = testDb.get), agents = b.agents.copy(enabled = true,
      autopilot = b.agents.autopilot.copy(backupHours = 0, lintHours = 0, ingestPerDay = 0)))

  private lazy val ds = testPool(baseCfg.db)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzrecheck%'".update.run()
      sql"DELETE FROM queue_items WHERE text ILIKE '%zzrecheck%' OR text LIKE 'acquire re-check:%'".update.run()
      sql"UPDATE agent_autopilot SET enabled = false, paused = '{}', changed_by = '', last_ensure_at = NULL".update.run()
    val _ = AutopilotRepo.setDrain(ds, on = false, "test")

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit   = clean()

  /** The fixture's legal ingest: what `answer` says for each DOI; records the DOI and the source it was asked into. */
  private final class RecheckUpstream extends AgentUpstream:
    @volatile var generation = "g1"
    @volatile var answer: (String, Option[String]) => Either[String, IngestReport] = (d, _) =>
      Left(s"${IngestBacklog.NoLegalCopy} $d: no legal open-access PDF found — unpaywall: no copy — none")
    val asked = java.util.concurrent.ConcurrentLinkedQueue[(String, Option[String])]()
    def meta(doi: String) = Left("not stubbed")
    def refs(citekey: String, apply: Boolean) = Left("not stubbed")
    def assess(citekey: String, apply: Boolean) = Left("not stubbed")
    def followups(id: String, since: Option[Int], limit: Option[Int]) = Left("not stubbed")
    def discover(query: String, limit: Int) = Left("not stubbed")
    override def ingestLegalInto(doi: String, into: Option[String]) = { asked.add(doi -> into); answer(doi, into) }
    override def fetcherGeneration = generation

  private def setup(cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers, RecheckUpstream) =
    val deps = Deps(cfg, ds)
    val h    = Handlers(deps)
    val a    = AgentsHandlers(deps, h, (_, _) => Right(ScriptedClient(Model)))
    val up   = RecheckUpstream()
    a.attach(up)
    (a, h, up)

  private def doi(n: String) = s"10.5555/zzrecheck.$n"
  private def item(h: Handlers, n: String): Int =
    h.ops.queueAdd("acquire", QueueAdd(s"Zzrecheck $n, A work — https://doi.org/${doi(n)}", topic = Some("zzrecheck"))).toOption.get.id
  private def asked(up: RecheckUpstream): List[(String, Option[String])] =
    import scala.jdk.CollectionConverters.*
    up.asked.asScala.toList
  private def found(d: String, ck: String) =
    Right(IngestReport(ck, Some(d), Some("found"), created = true, Some(PdfInfo("unpaywall", None, 2048, None)), 0, None, Nil))
  private def status(h: Handlers, id: Int): String = h.ops.queueList("acquire", all = true).toOption.get.items.find(_.id == id).get.status

  test("a pass: the item the corpus holds with its text closes, a metadata-only source is fetched into, the rest is tried and keeps why"):
    val (a, h, up) = setup()
    assert(h.putSource("zzrecheck_full", SourcePut(title = "Full", doi = Some(doi("full")), body = Some("The text."))).isRight)
    assert(h.putSource("zzrecheck_meta", SourcePut(title = "Meta", doi = Some(doi("meta")), notes = Some("Compiled."))).isRight)
    val full = item(h, "full"); val meta = item(h, "meta"); val none = item(h, "none")
    val noDoi = h.ops.queueAdd("acquire", QueueAdd("Zzrecheck, a book with no DOI")).toOption.get.id
    up.answer = (d, into) => if d == doi("meta") then found(d, into.get) else Left(s"${IngestBacklog.NoLegalCopy} $d: no legal open-access PDF found — unpaywall: no copy — none; landing: refused — bot-protection 403")
    val r = a.acquireRecheck.run(RecheckRequest(limit = Some(10))).fold(e => fail(e.message), identity)
    assertEquals((r.closed, r.fetched, r.noCopy, r.failed), (1, 1, 1, 0), r.toString)
    assertEquals(asked(up).toSet, Set[(String, Option[String])](doi("meta") -> Some("zzrecheck_meta"), doi("none") -> None), "the closed one is not fetched")
    assertEquals(status(h, full), Queue.Done)
    assert(h.ops.queueList("acquire", all = true).toOption.get.items.find(_.id == full).get.reason.exists(_.contains("[[zzrecheck_full]]")))
    assertEquals((status(h, meta), status(h, none), status(h, noDoi)), (Queue.Open, Queue.Open, Queue.Open),
      "a found copy is closed by its conversion, not here")
    assertEquals(SourceRepo.load(ds, "zzrecheck_meta").flatMap(_.notes), Some("Compiled."))
    // what each check found is kept, and shown with the item
    val listing = h.ops.queueList("acquire", all = false).toOption.get
    val day = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
    assertEquals(listing.checks.find(_.item == none).map(_.reason),
      Some(s"checked $day: no legal copy (unpaywall: no copy — none; landing: refused — bot-protection 403)"))
    assertEquals(listing.checks.find(_.item == meta).map(_.outcome), Some(Fetched))
    assert(Queue.markdown(Queue.Kind.Acquire, listing.items, listing.checks).contains(s"_checked $day: no legal copy"))
    assert(h.ops.queueList("log", all = true).toOption.get.items.exists(_.text.startsWith("acquire re-check: 1 closed")))
    // again at once: nothing is due (checked within every-days), nothing is asked
    val again = a.acquireRecheck.run(RecheckRequest(limit = Some(10))).fold(e => fail(e.message), identity)
    assertEquals((again.due, again.closed), (0, 0))
    assertEquals(asked(up).size, 2)

  test("due again after every-days, or at once under a new fetcher generation; one item by id, due or not"):
    val (a, h, up) = setup()
    val one = item(h, "one"); val two = item(h, "two")
    val _ = a.acquireRecheck.run(RecheckRequest(limit = Some(10)))
    assertEquals(asked(up).size, 2)
    val later = Instant.now().plusSeconds(8L * 86400L)
    assertEquals(a.acquireRecheck.run(RecheckRequest(dryRun = true), later).toOption.get.due, 2, "a week later both are due")
    assertEquals(a.acquireRecheck.run(RecheckRequest(dryRun = true)).toOption.get.due, 0)
    up.generation = "g2" // a new fetcher, a new key
    val dry = a.acquireRecheck.run(RecheckRequest(dryRun = true)).fold(e => fail(e.message), identity)
    assertEquals(dry.due, 2)
    assertEquals(asked(up).size, 2, "a dry run asks no fetcher")
    assertEquals(dry.rows.map(_.action).distinct, List(WouldTry))
    val byId = a.acquireRecheck.run(RecheckRequest(id = Some(one))).fold(e => fail(e.message), identity)
    assertEquals(byId.rows.map(_.item), List(one))
    assertEquals(asked(up).size, 3)
    assertEquals(AcquireCheckRepo.of(ds, List(one)).get(one).map(_.checks), Some(2))
    assert(a.acquireRecheck.run(RecheckRequest(id = Some(999999))).isLeft, "no such open item")
    val _ = two

  test("the caps: per pass, per day; a status line with what is due"):
    val (a, h, up) = setup(baseCfg.copy(acquire = baseCfg.acquire.copy(recheck = baseCfg.acquire.recheck.copy(perPass = 2, perDay = 3))))
    (1 to 5).foreach(i => item(h, s"c$i"))
    val first = a.acquireRecheck.run(RecheckRequest()).toOption.get
    assertEquals((first.due, first.allowance, asked(up).size), (5, 2, 2))
    assert(first.note.exists(_.contains("3 due item(s) wait for the next pass")), first.note.toString)
    val second = a.acquireRecheck.run(RecheckRequest()).toOption.get
    assertEquals((second.allowance, asked(up).size), (1, 3), "the day's cap: 3")
    val st = a.acquireRecheck.status()
    assertEquals((st.due, st.triedToday, st.perDay), (2, 3, 3))
    assert(st.nextPass.exists(_.startsWith("tomorrow")), st.toString)
    assert(AutopilotMarkdown.status(a.autopilot.status()).contains("- acquire re-check: 2 of"), "the autopilot status says it")

  test("under the autopilot's shared allowance: the watchers' backlog first, then at most share-percent of it"):
    val cfg = baseCfg.copy(agents = baseCfg.agents.copy(autopilot = baseCfg.agents.autopilot.copy(ingestPerDay = 8)),
      acquire = baseCfg.acquire.copy(recheck = baseCfg.acquire.recheck.copy(perPass = 10, sharePercent = 25)))
    val (a, h, up) = setup(cfg)
    connect(ds)(sql"UPDATE agent_autopilot SET enabled = true".update.run())
    (1 to 4).foreach(i => item(h, s"s$i"))
    up.answer = (d, _) => found(d, "zzrecheck" + d.replaceAll("[^a-z0-9]", ""))
    val r = a.acquireRecheck.run(RecheckRequest()).toOption.get
    assertEquals((r.allowance, r.fetched), (2, 2), "25 % of 8")
    assertEquals(a.autopilot.sharedIngestLeft(Instant.now()), Some(6), "the re-check's finds count against the shared allowance")
    assertEquals(a.acquireRecheck.run(RecheckRequest()).toOption.get.allowance, 0)

  test("draining: the managers' pass does nothing and a pass by hand is refused, a dry run still lists"):
    val (a, h, up) = setup()
    val _ = item(h, "d1")
    val _ = AutopilotRepo.setDrain(ds, on = true, "test")
    assertEquals(a.acquireRecheck.tick(Instant.now()), Nil)
    val r = a.acquireRecheck.run(RecheckRequest()).toOption.get
    assert(r.note.exists(_.contains("draining")), r.toString)
    assertEquals(a.acquireRecheck.run(RecheckRequest(dryRun = true)).toOption.get.due, 1)
    assertEquals(asked(up), Nil)
    val _ = AutopilotRepo.setDrain(ds, on = false, "test")
    assert(a.acquireRecheck.tick(Instant.now()).exists(_.startsWith("acquire re-check:")))
    assertEquals(asked(up).size, 1)
    assertEquals(a.acquireRecheck.tick(Instant.now()), Nil, "the next pass waits pass-minutes")

  // ── through the real acquisition handlers ──────────────────────────────────────────────────────

  private object Discovery extends DiscoveryClient:
    def search(q: String, since: Option[Int], sort: Option[String], limit: Int) = Right(Nil)
    def meta(d: String) = Right(WorkRef(doi = Some(d), title = Some(s"Work $d"), year = Some(2020), authors = List("Zz Zzrecheck"),
      citekey = Some("zzrecheck2020_" + d.replaceAll("[^a-z0-9]", "").takeRight(6))))
    def references(d: String) = Right(("crossref", Nil))
    def updates(d: String) = Right(Nil)
  private object NoCitations extends CitationsClient:
    def citing(work: WorkRef, since: Option[Int], limit: Int) = Right(("stub", Nil))
  private object NoWiki extends WikiClient:
    def refs(url: String) = Left("not used here")
  private object NoArchive extends ArchiveClient:
    def search(q: String, rows: Int) = Right(Nil)
    def info(id: String) = Left("not used here")
    def download(id: String, file: String) = Left("not used here")
  private object NoRw extends RetractionWatchClient:
    def csv() = Left("not used here")

  test("through the real chain: the legal fetchers only, even with acquire.allow-unauthorised = true; a copy found goes into the metadata-only source"):
    val unauthorisedAsked = java.util.concurrent.atomic.AtomicInteger(0)
    val unauthorised = new SourceFetcher:
      val access = Access.Unauthorised
      def fetch(w: WorkKey, c: FetchContext) =
        unauthorisedAsked.incrementAndGet()
        Fetch.Found(FetchedFile("stub", None, ("%PDF-1.4\n" + "u" * 2000).getBytes("UTF-8"), "pdf"))
    val open = new SourceFetcher:
      val access = Access.Open
      def fetch(w: WorkKey, c: FetchContext) =
        if w.doi.exists(_.value == doi("oa")) then
          Fetch.Found(FetchedFile("unpaywall", Some("https://example.invalid/oa.pdf"), ("%PDF-1.4\n" + "o" * 2000).getBytes("UTF-8"), "pdf"))
        else Fetch.Miss("no open-access location")
    val chain = FetchChain(List("unpaywall" -> open, "stub-unauthorised" -> unauthorised), allowUnauthorised = true)
    val cfg   = baseCfg.copy(acquire = baseCfg.acquire.copy(allowUnauthorised = true))
    val deps  = Deps(cfg, ds)
    val h     = Handlers(deps)
    val a     = AgentsHandlers(deps, h, (_, _) => Right(ScriptedClient(Model)))
    val acq   = AcquireHandlers(deps, Discovery, NoCitations, chain, NoWiki, NoArchive, NoRw,
      SkeletonIngest(ds, cfg.limits.chunkChars), generation = "real")
    a.attach(AgentUpstream.of(acq))
    assert(h.putSource("zzrecheck_kept", SourcePut(title = "Kept", doi = Some(doi("oa")), notes = Some("Compiled."))).isRight)
    val oa = item(h, "oa"); val closed = item(h, "closed")
    val r = a.acquireRecheck.run(RecheckRequest(limit = Some(10))).fold(e => fail(e.message), identity)
    assertEquals(unauthorisedAsked.get, 0, "a fetcher the config marks unauthorised is never asked")
    assertEquals((r.fetched, r.noCopy), (1, 1), r.toString)
    assertEquals(r.rows.find(_.item == oa).flatMap(_.citekey), Some("zzrecheck_kept"), "fetched into the metadata-only source")
    assertEquals(SourceRepo.load(ds, "zzrecheck_kept").flatMap(_.notes), Some("Compiled."), "its enrichment stays")
    assert(SourceRepo.load(ds, "zzrecheck2020_" + doi("closed").replaceAll("[^a-z0-9]", "").takeRight(6)).isEmpty,
      "no legal copy: no metadata-only source is created")
    val why = AcquireCheckRepo.of(ds, List(closed)).get(closed).map(_.reason).getOrElse("")
    assert(why.contains("no legal copy (unpaywall: no copy — no open-access location)"), why)
    assertEquals(AcquireCheckRepo.of(ds, List(oa)).get(oa).map(_.generation), Some("real"))
