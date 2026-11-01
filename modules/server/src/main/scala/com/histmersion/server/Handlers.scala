package com.histmersion.server

import com.histmersion.domain.catalogue.places.PlaceScope

import com.augustnagro.magnum.{connect, transact}

import com.histmersion.util.data.TablesAdapter.JobsOwner
import com.histmersion.util.data.diagnostics.{DiagnosticsService, StorageCache, StorageFigures}
import com.histmersion.util.data.backup.{BackupJobs, BackupService}
import com.histmersion.util.data.maintenance.{
  CacheEvent,
  CacheName,
  MaintenanceSite,
  MaintenanceSlice,
  MaintenanceTaskName,
  VacuumJobs,
  WriteGate
}
import com.histmersion.util.storage.MeasureBudget

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.util.jobs.BackgroundJob
import com.histmersion.util.identity.AliasKind
import com.histmersion.util.data.mirror.{Mirror, MirrorAdapter, TransitionNotes}

import com.histmersion.values.{BundleKind, Citekey, PlaceKind, UserId}
import com.histmersion.files.*

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{
  AgentStateRepo,
  AggregateRepo,
  ArticleRepo,
  ArticleReviewRepo,
  CoverageRepo,
  CritiqueRepo,
  GoalRepo,
  Importer,
  Migrate,
  ReviewLedgerRepo,
  SourceDeleteRepo,
  SourceRepo
}
import java.nio.file.{Files, Path}
import java.time.Instant

/** The ONE implementation of every operation in the contract. REST routes and MCP tools both call these; the CLI
  * reaches them over HTTP. Pure functions of (deps, input) except for the DB.
  */
final class Handlers(deps: Deps, converter: ConverterClient):

  /** The ordinary construction: the OCR sidecar client comes from config. The two-argument form is the seam an HTTP
    * test uses to substitute a fake converter (plan §1.8).
    */
  def this(deps: Deps) = this(deps, ConverterWiring.client(deps.cfg.converter))

  private val ds = deps.ds

  /** Read at each use: a config reload switches it (Deps). */
  private def cfg = deps.cfg

  /** Every source write re-materialises that citekey's `finding_places` (see `SourceRepo.write`), so the gazetteer
    * tolerance travels with the write rather than only with `places rebuild`.
    */
  private def tolKm = cfg.places.pointToleranceKm
  private val backups: BackupService = BackupWiring.service(deps)
  private val backupJobs = BackupJobs(backups, deps.cfg.backups.requestWaitSeconds, deps.pins)
  private val vacuumJobs = VacuumJobs(ds, deps.maintenance, Deps.data, () => cfg.maintenance.tables, deps.pins)

  /** The registry's figures on this server's database. */
  private val diagnostics: DiagnosticsService = DiagnosticsService(Deps.data, deps.transactor)

  /** One lock per server (plan §1.10): held by a restore, checked by EVERY write — here, in `OpsHandlers`, in
    * `IngestHandlers` and in the acquisition handlers, which is why it lives on `Deps` rather than in this class. A
    * restore drops and recreates every table; a write that slipped past the lock wrote into a schema that was about to
    * be replaced.
    */
  val maintenance: WriteGate = deps.maintenance

  /** Stream 2.8 — lint, the bookkeeping queues and the read-only query. Its own object (one file per stream), wired
    * into REST and MCP through this same `Handlers` value.
    */
  val ops: OpsHandlers = OpsHandlers(deps)

  /** The work lists' kernel. */
  val queues: com.histmersion.domain.automation.worklists.QueueService = deps.queues

  /** The first acquisition item (any status) whose text names `doi`. */
  def acquisitionItemNaming(doi: String): Option[Int] =
    queues.naming(com.histmersion.api.Queue.Kind.Acquire, doi).headOption.map(_.id)

  /** The ingest/jobs/assets surface (plan §1.8), in its own file but reached only through here — so "one implementation
    * per operation" still holds for REST, MCP and the CLI alike.
    */
  /** Media's pictures, their links, the imports from outside providers (#1446), the sources' covers and illustrations.
    * Thumbnails come from the converter.
    */
  val media: MediaWiring = MediaWiring(deps, converter)

  val ingest: IngestHandlers = IngestHandlers(
    deps,
    converter,
    media.assetStore,
    ck =>
      // a stored conversion: the source's illustration (and Open Library cover) first, then enrichment
      Option(
        List(
          media.covers.afterIngest(ck),
          com.histmersion.server.agents.TransitionWorkflows.enrich(agents).autoEnrich(ck)
        ).flatten
      ).filter(_.nonEmpty).map(_.mkString(" · "))
  )

  /** Named periods' write path and the first-run `init` job (#719, #720) — same arrangement. */
  val bootstrap: Bootstrap = Bootstrap(deps)

  /** The named periods' operations. */
  val periods: com.histmersion.domain.catalogue.periods.PeriodsControllers =
    com.histmersion.domain.catalogue.periods
      .PeriodsControllers(bootstrap.periods, bootstrap.periodImports, deps.maintenance)

  /** The terrain base map (docs/research-terrain-basemap.md): its extracts, their fetch job and the tiles the web page
    * draws — same arrangement.
    */
  val basemap: com.histmersion.util.basemap.BasemapService = BasemapWiring.service(deps)

  /** The portable bundle — instance identity, export, verify, import (docs/research-merge-import.md). */
  val bundles: com.histmersion.util.data.exchange.BundleControllers =
    import com.histmersion.util.data.exchange.{
      BundleControllers,
      BundleService,
      CarriedKinds,
      MergeApplyJobs,
      MergeRuleSet
    }
    val applyJobs = MergeApplyJobs(
      com.histmersion.util.jobs.MergeApplyRuns(deps.ds),
      deps.cfg.merge.applyWaitSeconds,
      deps.pins
    )
    BundleControllers(
      BundleService(
        BundleService.Wiring(
          deps.ds,
          () => deps.cfg,
          deps.storage,
          deps.clock,
          CarriedKinds(Deps.data.carried),
          MergeRuleSet(Deps.data.mergeRules),
          applyJobs,
          () => backups.create(Instant.now()).map(_.file)
        )
      ),
      deps.maintenance
    )

  /** Embedded research agents (docs/research-embedded-agents.md) — off unless `agents.enabled`. Constructing it starts
    * nothing; `Main` starts its run pool after the OCR pool.
    */
  /** The work lists' operations (the pipelines' worklog lines). */
  def workLists: com.histmersion.domain.automation.worklists.WorkListControllers = deps.workLists

  val agents: com.histmersion.domain.automation.agents.AgentsHandlers =
    com.histmersion.domain.automation.agents.AgentsHandlers(
      com.histmersion.server.agents.TransitionSite(deps, this)
    )

  /** Research › Activity: runs, jobs, worklog lines and a person's decisions, one list. */
  val activity: com.histmersion.domain.activity.ActivityControllers =
    com.histmersion.domain.activity
      .ActivityControllers(com.histmersion.domain.activity.ActivityService(ds), () => Instant.now())

  /** Pause, resume and cancel a job: a long background job cooperatively, an agent run by its own control. */
  lazy val jobControl: com.histmersion.util.jobs.JobControlControllers =
    import com.histmersion.util.jobs.{JobAction, JobControlControllers, JobControlService}
    def run(id: String, a: JobAction): Either[ApiError, Unit] = (a match
      case JobAction.Pause  => agents.engine.pause(id)
      case JobAction.Resume => agents.engine.resume(id, None, None)
      case JobAction.Cancel => agents.engine.cancel(id)
    ).map(_ => ())
    JobControlControllers(JobControlService(ds, deps.jobStops, run))

  /** Every domain's records as others point at them: what the person's marks show of what they point at. */
  lazy val referables: Referables =
    val db = deps.transactor
    Referables(
      List(
        com.histmersion.domain.library.sources.SourceReferable(db),
        com.histmersion.domain.research.articles.ArticleReferable(db),
        com.histmersion.domain.catalogue.CatalogueReferable(db),
        com.histmersion.domain.media.assets.PictureReferable(db),
        com.histmersion.domain.automation.goals.GoalReferable(db)
      )
    )

  /** A person's bookmarks, notes and read marks (the one local user's while the server runs for one person). */
  lazy val personal: com.histmersion.domain.personal.PersonalControllers =
    import com.histmersion.domain.personal.PersonalControllers
    import com.histmersion.domain.personal.bookmarks.BookmarkService
    import com.histmersion.domain.personal.notes.NoteService
    import com.histmersion.domain.personal.reading.ReadingService
    val db = deps.transactor
    val readables = List(
      com.histmersion.domain.library.sources.SourceReadable(db),
      com.histmersion.domain.research.articles.ArticleReadable(db)
    )
    PersonalControllers(
      BookmarkService(db, referables),
      NoteService(db, referables),
      ReadingService(db, referables, readables),
      deps.maintenance,
      deps.clock
    )

  /** How far the person has seen each window's news (the toolbar's notices). */
  lazy val seenMarks: com.histmersion.domain.personal.seen.SeenService =
    com.histmersion.domain.personal.seen.SeenService(deps.transactor)

  /** Settings: the settings file read and changed (never the database), reloaded into this server; the restart for what
    * only a restart applies.
    */
  lazy val settings: com.histmersion.util.settings.SettingsControllers =
    import com.histmersion.util.settings.{RestartService, SettingsControllers, SettingsService}
    SettingsControllers(
      SettingsService(() => deps.loaded, () => StorageLocations.settingsFile(deps.loaded, deps.storage)),
      RestartService(deps.restart),
      () => configReload(),
      () =>
        deps.plugins.report.plugins.flatMap(p =>
          s"histmersion.plugins.${p.id}.enabled" :: p.settings.map(st => s"histmersion.plugins.${p.id}.${st.key}")
        )
    )

  def version: VersionInfo = VersionInfo(Version.name, Version.version)

  /** The Storage part of About: measured in the background, at most once per `storage.ttl-seconds` ([[StorageCache]]),
    * so `about` itself never walks a disk.
    */
  private val storage = StorageCache(
    prepare = () => {
      val c = cfg
      val env = (k: String) => sys.env.get(k)
      val measured =
        StorageLocations.measured(c, env, System.getProperty("os.name", ""), Supervisor.currentExecutable)
      val run = deps.pins.carry(
        StorageFigures(Deps.data).measure(
          measured,
          StorageLocations.install,
          c,
          Some(diagnostics),
          backups.list.left.map(_.message),
          MeasureBudget.of(c.storage.maxEntries.toLong, c.storage.maxMillis.toLong),
          () => deps.clock.instant()
        )
      )
      () => run.run()
    },
    ttlMillis = () => cfg.storage.ttlSeconds * 1000L
  )

  /** `POST /api/config/reload` (MCP `config`, `histmersion config reload`): the config file read again and the server
    * switched to it in place — docs/research-plugins.md §3.5.
    */
  def configReload(): ReloadReport = deps.reloader()

  def health: Health =
    val conv = ingest.converterHealth
    scala.util.Try(Migrate.current(ds)) match
      case scala.util.Success(v) => Health(if conv.healthy then "ok" else "degraded", "ok", v, Some(conv.render))
      case scala.util.Failure(e) => Health("degraded", s"error: ${e.getMessage}", None, Some(conv.render))

  /** `GET /api/about` — the build, the health report, the job queue, the newest backup and the store's counts. Each
    * part that cannot be read becomes a `problems` line, never a failed request: this is the page someone opens BECAUSE
    * something looks wrong.
    */
  def about(now: Instant = Instant.now()): About =
    def attempt[A](what: String)(a: => A): Either[String, A] =
      scala.util.Try(a).toEither.left.map(e => s"$what: ${e.getMessage}")
    val jobs = attempt("job counts")(
      JobCounts.of(diagnostics.figures(JobsOwner).counts.map(c => c.noun -> c.count.toInt))
    )
    val counts = attempt("store counts")(corpusCounts)
    val listed = backups.list.left.map(e => s"backups: ${e.message}")
    val last = listed.toOption.flatMap(_.headOption)
    val age = last
      .flatMap(b => scala.util.Try(Instant.parse(b.createdAt)).toOption)
      .map(t => math.max(0L, now.getEpochSecond - t.getEpochSecond))
    About(
      name = Version.name,
      build = BuildStamp(
        Version.version,
        BuildMeta.gitCommit,
        BuildMeta.commitDate,
        BuildMeta.dirty,
        licence = Some(Version.licence),
        sourceUrl = Some(CorrespondingSource.url(cfg.server.sourceUrl, BuildMeta.gitCommit))
      ),
      health = health,
      jobs = jobs.getOrElse(JobCounts()),
      counts = counts.toOption,
      lastBackup = last,
      lastBackupAgeSeconds = age,
      problems = List(jobs, counts, listed).flatMap(_.left.toOption),
      configFile = deps.configFile,
      agentLease = agents.lease.status.map((o, u, expired, mine) => AgentLeaseInfo(o, u, expired, mine)),
      plugins = deps.plugins.report,
      storage = storageNow(now),
      upstreams = upstreamQuotas(now),
      acquireRecheck = scala.util
        .Try(com.histmersion.server.agents.TransitionWorkflows.work(agents).acquireRecheck.status(now))
        .toOption
    )

  /** The corpus About counts: what the owners count of their records. */
  private def corpusCounts: CorpusCounts =
    val figures = Deps.data.owners.filterNot(_ == JobsOwner).map(diagnostics.figures)
    def count(noun: String): Int = figures.flatMap(_.count(noun)).headOption.fold(0)(_.toInt)
    CorpusCounts(
      count("sources"),
      count("findings"),
      count("articles"),
      count("events"),
      count("places"),
      count("periods"),
      count("presets")
    )

  /** The metered upstreams' budgets as the database keeps them (CORE's: `upstream_quota`), under today's limits; none
    * before CORE was first asked, and none when the database cannot be read (`about` says that elsewhere).
    */
  def upstreamQuotas(now: Instant = Instant.now()): List[UpstreamQuota] =
    scala.util
      .Try(com.histmersion.db.UpstreamQuotaRepo.load(ds, com.histmersion.server.acquire.CoreBudget.Upstream))
      .toOption
      .flatten
      .map(s =>
        com.histmersion.domain.CoreQuota
          .view(s, com.histmersion.server.acquire.CoreBudget.limits(cfg.acquire.core), now)
      )
      .toList

  /** The last storage measurement, with its age as of `now`. */
  private def storageNow(now: Instant): StorageReport =
    val r = storage.get(cfg.storage.waitMillis.toLong)
    r.copy(ageSeconds =
      r.measuredAt
        .flatMap(t => scala.util.Try(Instant.parse(t)).toOption)
        .map(t => math.max(0L, now.getEpochSecond - t.getEpochSecond))
    )

  /** `GET /api/ui/counters` — the lists' counts and "something new" marks ([[UiNotices]]), and, where the server runs
    * agents, what they are doing (the top bar's status).
    */
  def uiCounters: UiNotices.Counters =
    val marks = seenMarks.marks(UserId.Local).map((b, at) => (b.value: String) -> at.iso)
    val counters = com.histmersion.db.UiRepo.counters(ds, marks)
    if cfg.agents.enabled then counters.copy(agents = Some(com.histmersion.db.UiRepo.agents(ds))) else counters

  /** `POST /api/ui/seen/{board}` — the reader opened that window, which showed items up to `mark.at`. Answers the
    * counters after the mark moved.
    */
  def markSeen(board: String, mark: UiNotices.SeenMark): Either[ApiError, UiNotices.Counters] =
    maintenance.guard("mark_seen"):
      seen(board, mark)

  private def seen(board: String, mark: UiNotices.SeenMark): Either[ApiError, UiNotices.Counters] =
    for
      b <- UiNotices.Board
        .parse(board)
        .toRight(ApiError(s"unknown board '$board' — one of: ${UiNotices.Board.wires.mkString(", ")}"))
      at <- mark.at.map(_.trim).filter(_.nonEmpty) match
        case None    => Right(None)
        case Some(s) =>
          scala.util
            .Try(Instant.parse(s))
            .toOption
            .map(Some(_))
            .toRight(ApiError(s"'at' is not an ISO-8601 instant: $s"))
    yield
      val _ = seenMarks.mark(UserId.Local, com.histmersion.domain.personal.seen.SeenBoard(b.wire), at)
      uiCounters

  /** The `ui` config block, plus whether writes need a key — never the key itself — and the plugins (their settings
    * with secrets as references, never values).
    */
  def uiConfig: UiConfig = UiConfigViews.page(
    cfg.ui,
    writeKeyRequired = cfg.auth.apiKey.nonEmpty,
    agentsEnabled = cfg.agents.enabled,
    plugins = deps.plugins.report,
    baseMapSource = baseMapSource,
    mapLanguage = cfg.basemap.labels.language,
    setupDue = scala.util.Try(settings.setupDue).getOrElse(false)
  )

  /** `ui.base-map-source` resolved ([[BaseMapSource]]): `auto` becomes `local` once the stored extract can draw one of
    * the vector styles whole — a `stat` and a cached header read.
    */
  def baseMapSource: String =
    cfg.ui.baseMapSource.trim.toLowerCase(java.util.Locale.ROOT) match
      case s @ (BaseMapSource.Local | BaseMapSource.Online) => s
      case _ => BaseMapSource.resolve(cfg.ui.baseMapSource, basemap.status)

  /** Legacy `GET /api/search` — kept working (the web page uses it) by delegating to the filtered search and dropping
    * the evidence, so there is ONE query path (FTS ranking and snippets included). New callers should use [[sources]].
    */
  def search(q: Option[String], limit: Option[Int]): List[SearchHit] =
    sources(Filter(q = q.map(_.trim).filter(_.nonEmpty), limit = limit.getOrElse(0))).hits.map(_.source)

  /** `limit <= 0` means "use the configured page size" — no literal page size exists in the code — and any limit is
    * CAPPED at `limits.max-page`. An uncapped `limit` is a denial of service by query parameter: `?limit=100000000`
    * used to build every row of it.
    */
  private def resolve(f: Filter): Filter =
    f.copy(limit = cfg.limits.page(Some(f.limit), cfg.limits.search), offset = f.offset.max(0))

  /** How the gazetteer reads this filter's `places` values (plan §1.6a): the ids among them expand to themselves plus
    * their descendants as of `f.period`; the rest fall back to the name ILIKE. Resolved ONCE per request and threaded
    * into every query, so the sidebar, the map, the facets and the histogram all agree. No `places` filter ⇒ no query
    * at all.
    */
  private def scopeOf(f: Filter): PlaceScope =
    if f.places.isEmpty then PlaceScope.empty else catalogue.placeStore.scopeFor(f.places, f.period)

  /** `GET /api/sources` — THE discovery operation: sources with their matched findings and reasons. */
  def sources(f: Filter): SearchResult =
    SourceRepo.sources(ds, resolve(f), cfg.limits.snippetWords, scopeOf(f))

  /** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
  def facets(field: String, prefix: Option[String], f: Filter): Either[ApiError, List[Facet]] =
    FacetField
      .parse(field)
      .toRight(ApiError(s"unknown facet field '$field' (one of: ${FacetField.all.mkString(", ")})"))
      .map(ff => SourceRepo.facets(ds, ff, prefix, resolve(f), resolve(f).limit, scopeOf(f)).toList)

  /** `GET /api/sources/{ck}/articles` — the compiled articles that cite this source (plan §5.2). 404 when the citekey
    * itself is unknown, so "no articles yet" and "no such source" are different answers.
    */
  def sourceArticles(citekey: String): Either[ApiError, List[Backlink]] =
    getSource(citekey).map(s => ArticleRepo.backlinksForSource(ds, s.source.citekey))

  /** `GET /api/resolve/{id}`: a stored source, else an article, else nothing — never an error. */
  def resolveId(id: String): Either[ApiError, ResolvedId] =
    val src = getSource(id, body = false).toOption.map(s =>
      ResolvedSource(s.source.citekey, s.source.doi, s.source.authors, s.source.year, s.source.title, s.source.status)
    )
    val art = if src.isDefined then None
    else getArticle(id, brief = true).toOption.map(a => ResolvedArticle(a.article.slug, a.article.title))
    Right(ResolvedId(id, src, art))

  /** THE citekey resolution (V027): the citekey a source has NOW, when `id` is an old one it was renamed from;
    * otherwise `id` unchanged (and a later lookup reports it unknown). Every operation that takes a citekey resolves it
    * here first, so an old id keeps working in every read — the REST routes, the MCP tools, the CLI and the
    * `/source/{id}` page (which redirects).
    */
  def canonical(id: String): String = SourceRepo.canonical(ds, id).getOrElse(id)

  /** THE identifier resolution of every renamed kind (#1799, [[Aliases]]): the identifier the record answers to now —
    * an old one resolves to where it went. Every read AND every write resolves here first, so a put, patch, delete or
    * rename naming an old id goes to the current record.
    */
  def current(k: AliasKind, id: String): String = Deps.aliasTables.current(ds, k, id)

  def getSource(citekey: String, body: Boolean = true): Either[ApiError, SourceWithFindings] =
    SourceRepo
      .get(ds, canonical(citekey))
      .map((s, fs) =>
        SourceWithFindings(
          if body then s else s.copy(ocrMarkdown = None),
          fs.toList,
          illustration = media.assets.sourceThumb(s.citekey)
        )
      )
      .toRight(ApiError(s"No source '$citekey'"))

  /** `GET /api/findings` — the MAP FEED (plan §1.0): the findings that themselves satisfy the filter's finding-level
    * criteria, of the sources that satisfy its source-level ones. Same query parameters as [[sources]]; `limit <= 0`
    * means the configured `limits.findings`.
    */
  def findings(q: Api.FindingsQuery): List[GeoHit] =
    val f = q.toFilter
    // the map feed's own cap is `limits.findings`, which is BOTH the default and the ceiling
    SourceRepo
      .findingsFor(
        ds,
        f.copy(limit = cfg.limits.page(Some(f.limit), cfg.limits.findings).min(cfg.limits.findings)),
        scopeOf(f)
      )
      .toList

  /** `GET /api/findings/page` — [[findings]] with its total, where the page starts and the next offset (`geo_when` used
    * to print `### 200 finding(s)` with no way to ask for the rest).
    */
  def findingsPage(q: Api.FindingsQuery): GeoHitPage =
    val f = q.toFilter
    val scope = scopeOf(f)
    GeoHitPage.of(SourceRepo.findingsCount(ds, f, scope), findings(q), f.offset.max(0))

  /** `/api/aggregate` — the timeline strip (§1.7) AND the nested tree (§1.6). Narrowed by the SAME filter as
    * `/api/sources` and `/api/findings`; the window defaults to the configured UI span.
    *
    * `by=time` alone keeps the wave-1 behaviour byte for byte: the flat bars, gaps included, which is what the strip
    * draws. Adding `depth`/`samples` nests the same numbers, and `by=region|time,region|region,time` fills `tree`
    * instead. SQL only ever SELECTS rows; [[Aggregate]] and [[Tree]] (both pure) do all the bucketing, so the strip,
    * the tabs, the CLI and MCP cannot drift apart.
    *
    * Deeper levels are LAZY by construction: there is no cursor — a client narrows `from`/`to` (or `place`) to one
    * bucket and asks again, which is the same query with a smaller window.
    */
  def aggregate(q: Api.AggregateQuery): Either[ApiError, TimeHistogram] =
    def norm(o: Option[String], dflt: String) = o.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(dflt)
    val unit = norm(q.unit, "sources")
    Tree
      .parseBy(norm(q.by, Tree.KindTime))
      .toRight(
        ApiError(
          s"unsupported by='${norm(q.by, Tree.KindTime)}' — use " +
            "time | region | time,region | region,time"
        )
      )
      .flatMap { axes =>
        if unit != "sources" && unit != "findings" && unit != Aggregate.UnitStarts then
          Left(ApiError(s"unsupported unit='$unit' — use sources, findings or starts"))
        else aggregateOn(axes, unit, q)
      }

  /** The aggregation itself, once `by`/`unit` have been validated.
    *
    * The window is CLAMPED to the configured deep-time span (`ui.time.min/max`) and the resulting bucket count is
    * capped (`ui.max-buckets`). Without either, `from=-1e15&to=1e15` asked for 400 000 000 000 buckets: the request
    * built them one `TimeBucket` at a time until the heap gave out, and `to = Long.MaxValue` overflowed the bucket
    * arithmetic on top of that.
    */
  private def aggregateOn(axes: List[String], unit: String, q: Api.AggregateQuery): Either[ApiError, TimeHistogram] =
    val t = cfg.ui.time
    // the window to bucket = the filter's deep-time range, defaulted (half-open bounds included)
    val a = Filter.lowerGiven(q.filter.period).getOrElse(t.defaultFrom)
    val b = Filter.upperGiven(q.filter.period).getOrElse(t.defaultTo)
    val (lo, hi) = if a <= b then (a, b) else (b, a)
    val (from, to) = (clampToSpan(lo, t), clampToSpan(hi, t))
    val size = q.size.filter(_ > 0).getOrElse(Aggregate.bucketSize(to - from))
    val wanted = Aggregate.bucketCount(from, to, size)
    if wanted > BigInt(math.max(1, cfg.ui.maxBuckets)) then
      Left(
        ApiError(
          s"that window needs $wanted buckets of $size year(s), over the " +
            s"${cfg.ui.maxBuckets} allowed (ui.max-buckets) — narrow `from`/`to`"
        )
      )
    else Right(aggregateWindow(axes, unit, q, from, to, size))

  /** A caller's year, forced inside the configured span. `ui.time.min/max` is what the web UI can draw and what the
    * corpus covers; nothing outside it can have a non-empty answer anyway.
    */
  private def clampToSpan(v: Long, t: UiTimeSettings): Long = math.max(t.min, math.min(t.max, v))

  private def aggregateWindow(
      axes: List[String],
      unit: String,
      q: Api.AggregateQuery,
      from: Long,
      to: Long,
      size: Long
  ): TimeHistogram =
    val window = q.filter.copy(period = Some(Period(from, to)))
    val scope = scopeOf(window)
    val depth = q.depth.filter(_ > 0).getOrElse(cfg.ui.treeDepth).max(1).min(Tree.MaxDepth)
    val samples = q.samples.getOrElse(cfg.ui.treeSamples).max(0).min(Tree.MaxSamples)
    // `unit=starts` is the SOURCE unit counted a different way (plan §1.7): same rows, one bar each,
    // by where the period begins. The rows query therefore still asks for sources.
    val starts = unit == Aggregate.UnitStarts
    val rowUnit = if starts then "sources" else unit
    val counting = if starts then Aggregate.Counting.Starts else Aggregate.Counting.Overlap
    val flat =
      if axes.headOption.contains(Tree.KindTime) then
        Aggregate.histogram(AggregateRepo.periods(ds, rowUnit, window, scope).toList, from, to, size, counting)
      else Nil
    val base = TimeHistogram(from, to, size, flat, by = axes.mkString(","), unit = unit, depth = depth)
    // The TIMELINE STRIP asks for `by=time` on every span change and only wants `buckets`; building
    // the tree as well would double its cost for nothing. A caller that wants the nested answer for
    // the same axis says so by asking for a `depth`/`samples` — which is exactly what the "by time"
    // tab does, and what the CLI's `--group time` sends.
    if axes == List(Tree.KindTime) && q.depth.isEmpty && q.samples.isEmpty then base
    else treeOn(axes, rowUnit, window, scope, from, to, depth, samples, q.asOf, base)

  /** The nested part of [[aggregateOn]] — split out so the flat strip never pays for it. */
  private def treeOn(
      axes: List[String],
      unit: String,
      window: Filter,
      scope: PlaceScope,
      from: Long,
      to: Long,
      depth: Int,
      samples: Int,
      askedAsOf: Option[Long],
      base: TimeHistogram
  ): TimeHistogram =
    val rows = AggregateRepo.treeRows(ds, unit, window, scope).toList
    // the gazetteer slice these rows hang off — one upward closure, not the whole 16 k-place table
    def gaz = catalogue.placeStore.gazetteer(rows.flatMap(_.placeIds))
    // `by=region` reads the hierarchy at ONE year: the caller's, else the middle of the window
    val asOf = askedAsOf.orElse(Some(Tree.midpoint(from, to)))
    // LAZY DEEPER LEVELS: `place=<one gazetteer id>` roots the region tree AT that place, so
    // expanding a node returns its subtree rather than the whole forest with the node buried in it.
    // (Two place chips, or a name that is not a gazetteer id, is an ordinary filter — no re-rooting.)
    val root = window.places.filter(p => scope.idsFor(p).isDefined) match
      case one :: Nil => Some(one)
      case _          => None
    val tree = axes match
      case Tree.KindTime :: Nil   => Tree.timeTree(rows, from, to, depth, samples)
      case Tree.KindRegion :: Nil =>
        val (ns, rs) = gaz; Tree.regionTree(rows, ns, rs, asOf, depth, samples, root)
      case Tree.KindTime :: Tree.KindRegion :: Nil =>
        val (ns, rs) = gaz; Tree.timeRegionTree(rows, from, to, ns, rs, depth, samples)
      case _ =>
        val (ns, rs) = gaz; Tree.regionTimeTree(rows, from, to, ns, rs, asOf, depth, samples, root)
    base.copy(
      total = rows.iterator.map(_.key).toSet.size,
      // `time,region` reads the gazetteer at each bucket's OWN midpoint, so no single asOf applies
      asOf = if axes == List(Tree.KindTime, Tree.KindRegion) || axes == List(Tree.KindTime) then None else asOf,
      tree = tree
    )

  /** Each row's far end arrives classified ([[NeighborTarget]]), so every renderer (MCP, CLI over HTTP, the HTML page)
    * links sources, articles and unresolved targets without a second lookup.
    */
  def neighbors(citekey: String, kinds: List[String] = Nil): List[NeighborRow] =
    SourceRepo.neighbors(ds, canonical(citekey), kinds).toList

  def graph(citekey: String, depth: Option[Int]): SourceGraph =
    SourceRepo.graph(ds, canonical(citekey), depth.getOrElse(2).max(0).min(4))

  def contradictions(citekey: Option[String], since: Option[Long]): ContradictionsResult =
    SourceRepo.contradictions(ds, citekey.map(canonical), since)

  def seed(req: SeedRequest): Either[ApiError, SeedResult] = maintenance.guard("seed"):
    val asked = Option(req.dir).map(_.trim).filter(_.nonEmpty).getOrElse(cfg.corpus.dir)
    if asked.isEmpty then Left(ApiError("no directory given and histmersion.corpus.dir is not configured"))
    else
      // `dir` is a SERVER-side path chosen by the caller, so it is confined to `corpus.dir` when
      // that is configured. When it is not, the old behaviour stands (the host-side workflow points
      // the server at the user's own tree) — but say so, once, rather than silently.
      if !SafePath.configured(corpusRoot) then
        scribe.warn(
          "histmersion.corpus.dir is not configured — `seed` will read any path the caller " +
            "names; set it (HISTMERSION_CORPUS_DIR) to confine server-side reads"
        )
      SafePath.confine(corpusRoot, asked).left.map(ApiError(_)).flatMap { dir =>
        if !Files.isDirectory(dir) then Left(ApiError(s"not a directory on the server: $dir"))
        else
          Right(
            Importer.importDir(
              ds,
              dir,
              cfg.limits.chunkChars,
              req.force,
              SeedFigureFiles(media.assetStore),
              cfg.limits.assetBytes,
              tolKm,
              corpusRoot
            )
          )
      }

  /** `corpus.dir` as an `Option`: empty string = not configured. */
  private def corpusRoot: Option[String] = Option(cfg.corpus.dir).map(_.trim).filter(_.nonEmpty)

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────

  /** Create or REPLACE everything under one citekey. */
  /** An OLD citekey follows the alias (#1799): the put replaces the source it was renamed to — it never creates a
    * second source under the old name, which the alias would then shadow.
    */
  def putSource(citekey: String, in: SourcePut, strict: Boolean = false): Either[ApiError, SourceWithFindings] =
    maintenance.guard("put_source"):
      Citekey.of(citekey).map(_ => current(AliasKindsAdapter.SourceRecords, citekey)).flatMap { ck =>
        Write
          .applyPut(ck, in, SourceRepo.aliasesOf(ds, ck))
          .left
          .map(m => ApiError(m, Some(Write.CodeInvalid)))
          .flatMap(s => strictGate(strict, sourceFormatting(s.summary, s.notes)).map(_ => s))
          .flatMap { s =>
            val _ = SourceRepo.put(ds, s, cfg.limits.chunkChars, tolKm); getSource(ck).map(withFormatting)
          }
      }

  /** An IMPORT of a note must not land under an OLD citekey — the one exception to following the alias: a note named by
    * an old citekey is a stale mirror file the export leaves behind (and prunes), and its findings are numbered under
    * the old name. A put, patch or delete follows.
    */
  private def notAnAlias(ck: String): Either[ApiError, String] =
    SourceRepo.aliasOf(ds, ck) match
      case Some(now) =>
        Left(ApiError(s"'$ck' was renamed to '$now' — write to '$now' instead", Some(Write.CodeInvalid)))
      case None => Right(ck)

  /** `POST /api/sources/{ck}/rename` — see `SourceRepo.rename` for everything it touches. */
  def renameSource(citekey: String, req: RenameRequest): Either[ApiError, SourceRenamed] =
    maintenance.guard("rename_source"):
      val from = current(AliasKindsAdapter.SourceRecords, citekey)
      SourceRepo.rename(ds, from, req.newCitekey).flatMap { counts =>
        val now = req.newCitekey.trim
        getSource(now, body = false)
          .map(sw => SourceRenamed(from, now, sw.source, counts, SourceRepo.calibreId(ds, now)))
      }

  /** Every old citekey and the source it names now. */
  def sourceAliases: List[SourceAlias] = SourceRepo.aliases(ds)

  /** `POST /api/sources/{ck}/delete` (#969) — a dry run unless `apply`. What it decides is `core.SourceDelete`; what it
    * touches, `SourceDeleteRepo`. Applied, the database half is one transaction (refused while anything cites the
    * source), and the stored originals ([[StoredOriginals]]) are removed after the commit, best effort. Not an MCP
    * tool, like deleting an article: a person's decision.
    */
  def deleteSource(citekey: String, req: SourceDeleteRequest): Either[ApiError, SourceDeletion] =
    val ck = current(AliasKindsAdapter.SourceRecords, citekey)
    def originals(p: SourceDeleteRepo.Plan): List[Path] =
      StoredOriginals.find(
        Path.of(cfg.converter.spoolDir),
        acquire.Spool.base(cfg.acquire.spoolDir),
        p.sha256,
        p.shaShared,
        p.keys
      )
    if !req.apply then SourceDeleteRepo.plan(ds, ck).map(p => p.deletion.copy(files = originals(p).map(_.toString)))
    else
      maintenance.guard("delete_source"):
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString
        // the files are named inside the transaction (they are in its log line) and go only after it committed
        SourceDeleteRepo
          .apply(ds, ck, today, p => originals(p).map(_.toString))
          .map { p =>
            scribe.info(SourceDelete.logText(p.deletion))
            p.deletion.copy(filesFailed = StoredOriginals.remove(p.deletion.files.map(Path.of(_))))
          }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). */
  def patchSource(citekey: String, p: SourcePatch, strict: Boolean = false): Either[ApiError, SourceWithFindings] =
    maintenance.guard("patch_source"):
      Citekey
        .of(citekey)
        // only what this patch writes is gated: a strict patch of the tags is not refused for old notes
        .flatMap(_ => strictGate(strict, sourceFormatting(p.summary, p.notes)))
        .flatMap(_ => SourceRepo.patch(ds, citekey, p, cfg.limits.chunkChars, tolKm))
        .flatMap(_ => getSource(citekey))
        .map(withFormatting)

  // ── formatting: what in the agent-written Markdown will not display as meant ─────────────────

  /** A source's formatting problems, its links resolved against the corpus. */
  def sourceFormatting(summary: Option[String], notes: Option[String]): List[FormatIssue] =
    MarkdownLint.ofSource(summary, notes, pageLinks(MarkdownLint.lookups(summary.toList ++ notes.toList)))

  private def articleFormatting(body: String, summary: Option[String]): List[FormatIssue] =
    MarkdownLint.ofArticle(body, summary, pageLinks(MarkdownLint.lookups(body :: summary.toList)))

  private def withFormatting(sw: SourceWithFindings): SourceWithFindings =
    sw.copy(formatting = sourceFormatting(sw.source.summary, sw.source.notes))

  /** `strict`: refuse a write whose text has a formatting ERROR; otherwise the problems ride along in the response, and
    * the write goes through.
    */
  private def strictGate(strict: Boolean, issues: => List[FormatIssue]): Either[ApiError, Unit] =
    if !strict then Right(())
    else MarkdownLint.refusal(issues).fold(Right(()))(m => Left(ApiError(m, Some(Write.CodeInvalid))))

  /** `GET /api/sources/{citekey}/lint`. */
  def lintSource(citekey: String): Either[ApiError, FormatReport] =
    getSource(citekey, body = false).map(sw =>
      FormatReport(sw.source.citekey, sourceFormatting(sw.source.summary, sw.source.notes))
    )

  /** `POST /api/sources/{citekey}/lint/fix` — the wording-safe repairs to summary and notes, written as one patch
    * guarded by the `updatedAt` just read, then the report of what is left.
    */
  def fixSource(citekey: String): Either[ApiError, FormatReport] =
    maintenance.guard("fix_source_format"):
      SourceRepo.load(ds, canonical(citekey)).toRight(Write.notFound(citekey)).flatMap { s =>
        val (summary, fs) =
          s.summary.map(t => MarkdownLint.fix(t, FormatField.Summary)).fold((None, Nil))((t, f) => (Some(t), f))
        val (notes, fn) =
          s.notes.map(t => MarkdownLint.fix(t, FormatField.Notes)).fold((None, Nil))((t, f) => (Some(t), f))
        val done = fs.map("summary: " + _) ++ fn.map("notes: " + _)
        val write =
          if done.isEmpty then Right(())
          else
            SourceRepo
              .patch(
                ds,
                s.citekey,
                SourcePatch(
                  summary = summary.filter(_ => fs.nonEmpty),
                  notes = notes.filter(_ => fn.nonEmpty),
                  ifUpdatedAt = s.updatedAt
                ),
                cfg.limits.chunkChars,
                tolKm
              )
              .map(_ => ())
        write.flatMap(_ => lintSource(s.citekey)).map(_.copy(fixed = done))
      }

  /** `GET /api/articles/{slug}/lint`. */
  def lintArticle(slug: String): Either[ApiError, FormatReport] =
    getArticle(slug).map(v => FormatReport(v.article.slug, v.formatting))

  /** `POST /api/articles/{slug}/lint/fix` — as [[fixSource]], for the body and summary. */
  def fixArticle(slug: String): Either[ApiError, FormatReport] =
    maintenance.guard("fix_article_format"):
      ArticleRepo.load(ds, ArticleRepo.canonical(ds, norm(slug))).toRight(Articles.notFound(slug)).flatMap { a =>
        val (body, fb) = MarkdownLint.fix(a.body, FormatField.Body, MarkdownLint.Context(article = true))
        val (summary, fs) =
          a.summary.map(t => MarkdownLint.fix(t, FormatField.Summary)).fold((None, Nil))((t, f) => (Some(t), f))
        val done = fb.map("body: " + _) ++ fs.map("summary: " + _)
        val write =
          if done.isEmpty then Right(())
          else
            ArticleRepo
              .patch(
                ds,
                a.slug,
                ArticlePatch(
                  body = Some(body).filter(_ => fb.nonEmpty),
                  summary = summary.filter(_ => fs.nonEmpty),
                  ifUpdatedAt = a.updatedAt
                )
              )
              .map(_ => ())
        write.flatMap(_ => lintArticle(a.slug)).map(_.copy(fixed = done))
      }

  /** `POST /api/lint/markdown` — Markdown that is not stored (yet): a file about to be put. With `fix`, the repaired
    * text comes back in `text`. A `body` is checked as an article body.
    */
  def lintText(in: FormatCheck): FormatReport =
    val (text, done) = if in.fix then
      MarkdownLint.fix(in.text, in.field, MarkdownLint.Context(article = in.field == FormatField.Body))
    else (in.text, Nil)
    val issues = in.field match
      case FormatField.Body    => articleFormatting(text, None)
      case FormatField.Summary => MarkdownLint.ofSource(Some(text), None, pageLinks(MarkdownLint.lookups(List(text))))
      case FormatField.Notes   => sourceFormatting(None, Some(text))
    FormatReport("text", issues, done, Option.when(in.fix)(text))

  // ── a draft of an article, checked as review-article's fixer validation checks it ──────────────

  /** What [[ReviewArticle.checkBody]] needs from the corpus, resolved once for every link in `texts` (the body and the
    * draft's texts): `[[x]]` as a source, as anything, and the formatting ERRORS of a text (the `--strict` gate's), one
    * position-free line each. `generated`: the article records a filter, so its Not used list and Method note are
    * generated (#958) and must not be written into the body.
    */
  def bodyCorpus(texts: List[String], generated: Boolean = false): ReviewArticle.BodyCorpus =
    val links = pageLinks(MarkdownLint.lookups(texts))
    ReviewArticle.BodyCorpus(
      t => links(t).exists(_.isInstanceOf[SourceRef]),
      t => links(t).isDefined,
      text =>
        MarkdownLint
          .ofArticle(text, None, links)
          .filter(_.severity == FormatSeverity.Error)
          .map { i =>
            s"${i.rule.wire} — ${i.message}" + (if i.excerpt.isEmpty then ""
                                                else s" · `${i.excerpt.replace("`", "'")}`")
          }
          .distinct,
      generated
    )

  /** `POST /api/articles/{slug}/check` (MCP and agent tool `check_edits`) — read only. Edits are applied to the STORED
    * body; a whole `body` is checked as it stands (the article need not exist yet: a compile writer's draft). The same
    * function the review pipeline validates its fixer with.
    */
  def checkArticle(slug: String, in: ArticleEdits.ArticleCheckRequest): Either[ApiError, ArticleEdits.ArticleCheck] =
    in.draft.left.map(m => ApiError(m, Some(Write.CodeInvalid))).flatMap { draft =>
      val stored = ArticleRepo.load(ds, ArticleRepo.canonical(ds, norm(slug)))
      (draft, stored) match
        case (ArticleEdits.Draft.Edits(_), None) => Left(Articles.notFound(slug))
        case _                                   =>
          val before = stored.fold("")(_.body)
          val kind = in.kind.map(_.trim).filter(_.nonEmpty).orElse(stored.map(_.kind)).getOrElse("concept")
          val texts = draft match
            case ArticleEdits.Draft.Edits(es) => before :: es.flatMap(_.text)
            case ArticleEdits.Draft.Body(t)   => List(before, t)
          // a new article is compiled with a filter (#958): its coverage sections will be generated
          val generated = stored.forall(_.filter.isDefined)
          Right(
            ReviewArticle.checkBody(stored.fold(norm(slug))(_.slug), before, kind, draft, bodyCorpus(texts, generated))
          )
    }

  /** The note as it would live on disk — frontmatter YAML + the `notes` body. */
  def exportSource(citekey: String): Either[ApiError, String] =
    mirror.note(MirrorAdapter.SourceNotes, citekey)(using transitionNotes)

  /** The Markdown mirror over the registered notes, rendered by today's per-kind code below. */
  private val mirror = Mirror(Deps.data.mirror)

  private val transitionNotes: TransitionNotes = new TransitionNotes:
    def sourceNote(citekey: String): Either[ApiError, String] =
      SourceRepo.load(ds, canonical(citekey)).map(Export.note).toRight(Write.notFound(citekey))

    def articleNote(slug: String): Either[ApiError, String] =
      ArticleRepo
        .load(ds, current(AliasKindsAdapter.ArticleRecords, norm(slug)))
        .map(a => ArticleNote.render(a, coverageOf(a).map(ArticleCoverage.exported)))
        .toRight(Articles.notFound(slug))

  def citekeys: List[String] = SourceRepo.citekeys(ds).toList

  def resolveEdges: Either[ApiError, EdgesResolved] =
    maintenance.guard("edges/resolve"):
      Right(EdgesResolved(SourceRepo.resolveEdges(ds)))

  /** Seed ONE note from raw text (the host-side path of `histmersion seed`, for a corpus dir the SERVER cannot see).
    * Applies the same "stored row is newer than the file" guard as [[seed]] whenever the client sends the file's mtime.
    */
  def importNote(req: NoteImport): Either[ApiError, SeedResult] = maintenance.guard("import"):
    Frontmatter.parseNote(req.text, req.citekey.getOrElse("")) match
      case None         => Left(ApiError("no YAML frontmatter in the note"))
      case Some(parsed) =>
        val ck = parsed.citekey
        if ck.isEmpty then Left(ApiError("note has no citekey (none in the frontmatter, none supplied)"))
        else if Citekey.parse(ck).isLeft then Citekey.of(ck).map(_ => SeedResult(0, Nil, 0, Nil))
        else if SourceRepo.aliasOf(ds, ck).isDefined then notAnAlias(ck).map(_ => SeedResult(0, Nil, 0, Nil))
        else if !req.force && req.mtime.exists(m => Importer.isStale(SourceRepo.updatedAtMillis(ds), ck, m))
        then Right(SeedResult(0, Nil, 0, List(ck)))
        else
          // `body` is the converted paper text the client read from `md_path`; NUL bytes occur in some
          // OCR output and Postgres `text` rejects them.
          val withBody = req.body.map(_.filterNot(_.toInt == 0)).filter(_.nonEmpty) match
            case Some(b) => parsed.copy(ocrMarkdown = Some(b))
            case None    => parsed
          // figures of the old file pipeline (plan §1.8): every `…/figures/<name>` spelling is
          // normalised to the canonical `assets/<name>` and the OCR's picture-text dumps folded
          // away, and any base64 payloads that travelled with the note stored
          val normalised =
            withBody.copy(ocrMarkdown = withBody.ocrMarkdown.map(AssetLinks.normalizeBody(_, withBody.title)))
          val _ = SourceRepo.put(ds, normalised, cfg.limits.chunkChars, tolKm)
          if req.assets.isEmpty then Right(SeedResult(1, Nil, 0, Nil))
          else media.assets.putFigures(ck, req.assets).map(_ => SeedResult(1, Nil, 0, Nil))

  /** `get_source` as Markdown, with image links made ABSOLUTE (plan §1.8) so an image-capable MCP client can fetch the
    * figures. The ONE renderer both MCP and the article page go through.
    *
    * Sectioning (A1/A7/A18): `section` selects what to return; `heading` filters to chunks under one heading;
    * `offset`/`chars` slice the body.
    */
  def sourceMarkdown(
      requested: String,
      fullText: Boolean,
      section: Option[String] = None,
      heading: Option[String] = None,
      offset: Option[Int] = None,
      chars: Option[Int] = None
  ): Either[ApiError, String] =
    val sec = section.flatMap(SourceSection.parse)
    val badSection = section.filter(_.nonEmpty).exists(_ => sec.isEmpty)
    if badSection then
      Left(ApiError(s"unknown section '${section.getOrElse("")}' — one of: ${SourceSection.all.mkString(", ")}"))
    else
      getSource(requested).map { sw =>
        val citekey = sw.source.citekey
        sec match
          case Some(SourceSection.Toc) =>
            val entries = SourceRepo.toc(ds, citekey)
            Markdown.toc(entries)

          case Some(SourceSection.Body) =>
            val bodyText = bodySlice(citekey, heading, offset, chars)
            AssetLinks.toAbsolute(bodyText, citekey, cfg.server.publicUrl)

          case Some(SourceSection.Summary) =>
            val s = sw.source
            val authStr = Markdown.chipLinks(s.authors, "author")
            val meta = List(authStr, Markdown.periodLabel(s.periodStart, s.periodEnd))
              .filter(_.nonEmpty)
              .mkString(" · ")
            s"# ${s.title}\n\n_${meta}_" +
              s.summary.map(x => s"\n\n${x.trim}").getOrElse("")

          case Some(SourceSection.Findings) =>
            Markdown.sourceFindingsOnly(sw.findings.toVector)

          case Some(SourceSection.Notes) =>
            sw.source.notes
              .map(_.trim)
              .filter(_.nonEmpty)
              .map(n => s"## Notes\n\n$n")
              .getOrElse("_(no notes)_")

          case Some(SourceSection.All) =>
            val ft = fullText || offset.isDefined || heading.isDefined
            val md = if ft && (offset.isDefined || chars.isDefined) then
              val bodyText = bodySlice(citekey, heading, offset, chars)
              Markdown.source(sw.source, sw.findings.toVector, false) + s"\n\n---\n\n$bodyText"
            else Markdown.source(sw.source, sw.findings.toVector, ft)
            AssetLinks.toAbsolute(md + mentionedIn(citekey), citekey, cfg.server.publicUrl)

          case Some(_) =>
            Markdown.source(sw.source, sw.findings.toVector, false)

          case None =>
            val ft = fullText
            val md =
              if ft && (offset.isDefined || chars.isDefined) then
                val bodyText = bodySlice(citekey, heading, offset, chars)
                Markdown.source(sw.source, sw.findings.toVector, false) + s"\n\n---\n\n$bodyText"
              else Markdown.source(sw.source, sw.findings.toVector, ft)
            AssetLinks.toAbsolute(md + mentionedIn(citekey), citekey, cfg.server.publicUrl)
      }

  /** `passages` (#954): find text inside ONE source's body — what `get --full | grep` is in a shell. The body is read
    * once (chunks, or the raw text of an unchunked source) and searched in memory by [[PassageFinder]], so the offsets
    * are those [[bodySlice]] uses. An old citekey resolves like every read; an unknown one is a 404, an empty query or
    * a bad regex a 400.
    */
  def passages(q: Api.PassagesQuery): Either[ApiError, PassageResult] =
    val lim = cfg.limits.passages
    SourceRepo
      .canonical(ds, q.citekey)
      .toRight(ApiError(s"No source '${q.citekey}'", Some(Write.CodeNotFound)))
      .flatMap { ck =>
        PassageFinder
          .find(
            ck,
            SourceRepo.bodyChunks(ds, ck),
            q.q,
            q.regex,
            lim.limitOf(q.limit),
            lim.contextOf(q.context),
            lim.maxMatches,
            lim.regexTimeoutMs.toLong
          )
          .left
          .map(m => ApiError(m, Some(Write.CodeInvalid)))
      }

  /** "Mentioned in articles" rides along with the default/all views rather than becoming a tool of its own: an LLM
    * asking about a source wants what the corpus has WRITTEN about it in the same answer, not after a second round
    * trip.
    */
  private def mentionedIn(citekey: String): String =
    Markdown.sourceArticles(ArticleRepo.backlinksForSource(ds, citekey).toVector)

  /** Read the body (from chunks or raw `ocr_markdown`), optionally filtered by heading and sliced. */
  private def bodySlice(citekey: String, heading: Option[String], offset: Option[Int], chars: Option[Int]): String =
    val raw = heading match
      case Some(h) => SourceRepo.bodyUnderHeading(ds, citekey, h)
      case None    => SourceRepo.bodyText(ds, citekey)
    val total = raw.length
    val off = offset.filter(_ > 0).getOrElse(0).min(total)
    val cap = chars.filter(_ > 0).getOrElse(cfg.limits.fullTextChars)
    val slice = raw.substring(off, math.min(total, off + cap))
    Markdown.bodySlice(slice, off, total)

  // ── events (plan §1.2) ──────────────────────────────────────────────────────────────────────

  /** The catalogue's events, their catalogues and links, and the refinement of site types. */
  val catalogue: CatalogueWiring =
    CatalogueWiring(
      deps,
      com.histmersion.pluginhost.JavaHttpTransport(cfg.events.timeoutSeconds),
      () => java.time.Year.now(deps.clock.withZone(java.time.ZoneOffset.UTC)).getValue.toLong,
      resolve,
      scopeOf
    )

  /** The events' store. */
  def eventStore: com.histmersion.domain.catalogue.events.EventService = catalogue.eventStore

  /** The event catalogues: the import, the re-derivation, the corrections by hand. */
  def eventImports: com.histmersion.domain.catalogue.events.EventImportsControllers = catalogue.eventImports

  /** The links of events to the corpus, the leads, an event's card and an article's catalogue leads. */
  def eventLinks: com.histmersion.domain.catalogue.evidence.EventLinksControllers = catalogue.eventLinks

  /** The refinement of the Wikidata sites' types. */
  def siteTypes: com.histmersion.domain.catalogue.places.SiteTypesControllers = catalogue.siteTypes

  /** The events' operations; an event's page joins its pictures and the articles citing its sources. */
  lazy val events: com.histmersion.domain.catalogue.events.EventsControllers =
    com.histmersion.domain.catalogue.events.EventsControllers(
      eventStore,
      com.histmersion.server.views
        .EventCardView(
          eventLinks.context,
          media.assets.eventThumbs,
          (citekeys, limit) => ArticleRepo.citing(ds, citekeys, limit)
        )
        .apply,
      current,
      resolve,
      scopeOf,
      () => (cfg.ui.events.mapLimit, cfg.ui.events.outlineToleranceDeg),
      maintenance
    )

  /** `POST /api/places/{id}/rename` — a new gazetteer id; the old one keeps resolving (V055), in
    * `GET /api/places/{old}` and in a `place=<old>` filter alike.
    */
  def renamePlace(id: String, req: LabelRename): Either[ApiError, LabelRenamed] =
    places
      .rename(Caller.Local, id, from => LabelRenames.validate(BundleKind.Place, from, req.to))
      .map((from, to) => LabelRenamed(BundleKind.Place, from, to))

  /** `POST /api/events/{id}/rename` — a new id; the old one keeps resolving (V053). */
  def renameEvent(id: String, req: LabelRename): Either[ApiError, LabelRenamed] =
    events
      .rename(Caller.Local, id, from => LabelRenames.validate(BundleKind.Event, from, req.to))
      .map((from, to) => LabelRenamed(BundleKind.Event, from, to))

  /** A REFERENCE to a record of a renamed kind (an asset link's target) names it by its current identifier (#1799):
    * `event`, `place`, `source` and `article` targets resolve an old one; other targets are kept as given.
    */
  private def refId(targetType: String, id: String): String =
    val kind = targetType.trim.toLowerCase(java.util.Locale.ROOT) match
      case "event"   => Some(AliasKindsAdapter.EventRecords)
      case "place"   => Some(AliasKindsAdapter.PlaceRecords)
      case "source"  => Some(AliasKindsAdapter.SourceRecords)
      case "article" => Some(AliasKindsAdapter.ArticleRecords)
      case _         => None
    kind.fold(id)(current(_, id))

  // ── attestations (plan §1.3) ──────────────────────────────────────────────────────────────

  /** What attests a data point: the attestations' operations. */
  lazy val attestations: com.histmersion.domain.catalogue.evidence.AttestationsControllers =
    com.histmersion.domain.catalogue.evidence.AttestationsControllers(
      com.histmersion.domain.catalogue.evidence.AttestationService(deps.transactor),
      deps.passages,
      current,
      limit => cfg.limits.page(limit, cfg.limits.search),
      maintenance
    )

  // ── goals (plan §1.5) ───────────────────────────────────────────────────────────────────────

  def listGoals(q: Api.GoalQuery): GoalList =
    GoalRepo.list(ds, q.status, cfg.limits.page(q.limit, cfg.limits.search), q.offset.getOrElse(0))

  /** An old goal slug (V053) answers as the goal it was renamed to. */
  def getGoal(slug: String): Either[ApiError, Goal] =
    connect(ds)(GoalRepo.get(current(AliasKindsAdapter.GoalRecords, slug)))
      .toRight(ApiError(s"No goal '$slug'", Some(Write.CodeNotFound)))

  /** A write to an OLD slug goes to the goal it was renamed to (#1799); a put replaces it. */
  def putGoal(slug: String, in: GoalPut): Either[ApiError, Goal] =
    maintenance.guard("put_goal"):
      val now = current(AliasKindsAdapter.GoalRecords, slug)
      transact(ds)(GoalRepo.put(now, in)).flatMap(_ => getGoal(now))

  def patchGoal(slug: String, p: GoalPatch): Either[ApiError, Goal] =
    maintenance.guard("patch_goal"):
      val now = current(AliasKindsAdapter.GoalRecords, slug)
      GoalRepo.patch(ds, now, p).flatMap(_ => getGoal(now))

  def deleteGoal(slug: String): Either[ApiError, GoalDeleted] =
    maintenance.guard("delete_goal"):
      val now = current(AliasKindsAdapter.GoalRecords, slug)
      if GoalRepo.delete(ds, now) then Right(GoalDeleted(now, true))
      else Left(ApiError(s"No goal '$slug'", Some(Write.CodeNotFound)))

  /** `POST /api/goals/{slug}/rename` — a new slug; the old one keeps resolving (V053). */
  def renameGoal(slug: String, req: LabelRename): Either[ApiError, LabelRenamed] =
    maintenance.guard("rename_goal"):
      val from = current(AliasKindsAdapter.GoalRecords, slug)
      for
        to <- LabelRenames.validate(BundleKind.Goal, from, req.to)
        _ <- GoalRepo.rename(ds, from, to)
      yield LabelRenamed(BundleKind.Goal, from, to)

  /** `POST /api/articles/{slug}/rename` — a new slug; the old one keeps resolving (V042) and every `[[old]]` in other
    * articles is rewritten (each a new version).
    */
  def renameArticle(slug: String, req: LabelRename): Either[ApiError, LabelRenamed] =
    maintenance.guard("rename_article"):
      val from = current(AliasKindsAdapter.ArticleRecords, norm(slug))
      for
        to <- LabelRenames.validate(BundleKind.Article, from, req.to)
        rewritten <- ArticleRepo.rename(ds, from, to)
      yield LabelRenamed(BundleKind.Article, from, to, rewritten)

  // ── agent state (plan §1.5) ────────────────────────────────────────────────────────────────

  def agentStateList(): List[AgentStateEntry] = AgentStateRepo.list(ds)

  def agentStateGet(key: String): Either[ApiError, AgentStateEntry] =
    AgentStateRepo.get(ds, key).toRight(ApiError(s"No agent state key '$key'", Some(Write.CodeNotFound)))

  def agentStateSet(key: String, in: AgentStatePut): Either[ApiError, AgentStateEntry] =
    maintenance.guard("agent_state_set"):
      AgentStateRepo.set(ds, key, in.value)
      agentStateGet(key)

  def agentStateDelete(key: String): Either[ApiError, AgentStateDeleted] =
    maintenance.guard("agent_state_delete"):
      if AgentStateRepo.delete(ds, key) then Right(AgentStateDeleted(key, true))
      else Left(ApiError(s"No agent state key '$key'", Some(Write.CodeNotFound)))

  // ── the gazetteer (plan §1.6a) ──────────────────────────────────────────────────────────────

  /** The gazetteer's operations. */
  def places: com.histmersion.domain.catalogue.places.PlacesControllers = catalogue.places

  /** The gazetteer's background work: a plugin's source or the land fetched, the hierarchy built. */
  def gazetteer: com.histmersion.domain.catalogue.places.GazetteerControllers = catalogue.gazetteer

  /** A place NAME → its gazetteer point (#981), for a finding about a place of kind `hint` dated `at`. `None` =
    * unknown.
    */
  def geocode(name: String, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution] =
    places.geocode(name, hint, at)

  /** The map's sites, events and pictures as vector tiles (`GET /api/tiles/…`). */
  val tiles: com.histmersion.domain.catalogue.maptiles.MapTileService =
    com.histmersion.domain.catalogue.maptiles.MapTileService(
      deps.transactor,
      () => cfg.ui.mapTiles,
      () => cfg.places.modernFrom,
      f => scopeOf(f),
      com.histmersion.domain.catalogue.maptiles.UndatedSites(
        deps.transactor,
        () => cfg.ui.mapTiles.undatedKeptZoom,
        run => { val _ = Thread.ofVirtual().name("histmersion-undated-sites").start(() => run()) }
      ),
      eventStore,
      deps.eventSources,
      media.mapLayer
    )

  def tile(q: TilesApi.TileQuery): Either[ApiError, TilesApi.TileAnswer] = tiles.tile(q)

  /** `GET /api/places/{id}` — the row, its GeoJSON geometry and the hierarchy around it at `asOf`; with `counts`, also
    * what the corpus holds for it and its pictures (the web UI's map hover card).
    */
  def place(id: String, asOf: Option[Long], counts: Boolean = false): Either[ApiError, PlaceDetail] =
    com.histmersion.server.views
      .PlaceCardView(places.get, places.placeId, placeCounts, media.assets.placeThumbs)
      .apply(id, asOf, counts)

  /** What `place=<id>` alone selects in each list — the same scope (the place and its descendants) and the same queries
    * as `/api/sources`, `/api/findings`, `/api/articles` and `/api/events`.
    */
  private def placeCounts(id: String): PlaceCounts =
    val f = Filter(places = List(id))
    SourceRepo.selectionCounts(ds, f, scopeOf(f))

  /** One resumable slice of the FTS chunk rebuild (plan §1.4); loop on `remaining` > 0. */
  def reindex(after: Option[String], limit: Option[Int]): Either[ApiError, ReindexResult] =
    maintenance.guard("reindex"):
      Deps.data
        .task(MaintenanceTaskName("reindex"))
        .toRight(ApiError("no reindex is registered"))
        .map(
          _.run(MaintenanceSlice(after.map(_.trim).filter(_.nonEmpty), limit.filter(_ > 0)), MaintenanceSite(ds, cfg))
        )

  // ── backup / restore (plan §1.10) ───────────────────────────────────────────────────────────

  /** `pg_dump -Fc` of the server's own database into `backups.dir`, as a job (see [[BackupJobs]]): the answer is the
    * finished backup, or `running` when it outlasts the request.
    */
  def backup(): Either[ApiError, BackupStatus] =
    maintenance.guard("backup")(backupJobs.start(Instant.now()))

  /** `POST /api/maintenance/vacuum` (#1445) — VACUUM [(ANALYZE)] of the named tables (default
    * `maintenance.vacuum-tables`) as a job (see [[VacuumJobs]]).
    */
  def vacuum(in: VacuumRequest): Either[ApiError, JobInfo] = vacuumJobs.start(in)

  /** Why a scheduled backup or repair must not start now (the autopilot's housekeeping): a restore holds the
    * maintenance lock, or a backup or a vacuum is still running. pg_dump reads one consistent snapshot, so ingests, OCR
    * and agent runs going on beside it are fine.
    */
  def housekeepingBlock: Option[String] =
    if maintenance.active then Some("a restore holds the maintenance lock")
    else if backupJobs.running then Some("a backup is still running")
    else if vacuumJobs.isRunning then Some("a vacuum is still running")
    else None

  def backupStatus: Either[ApiError, BackupStatus] =
    backupJobs.status.toRight(ApiError("no backup has run since the server started", Some(Write.CodeNotFound)))

  def backupList: Either[ApiError, List[BackupInfo]] = backups.list

  /** The archive itself, for `GET /api/backups/{file}` — validated against the listing first. */
  def backupFile(file: String): Either[ApiError, java.io.File] = backups.archive(file)

  /** Restore under the maintenance lock: every other write is answered 503 until it returns. Refuses a database that
    * still holds sources unless `force` (`pg_restore --clean` DROPS them), and re-validates the schema with Flyway
    * afterwards so a stale dump cannot pass silently.
    */
  def restore(req: RestoreRequest): Either[ApiError, RestoreResult] =
    if backupJobs.running then
      Left(
        ApiError("a backup is still running — wait for `histmersion backup` to finish", Some(Backup.CodeMaintenance))
      )
    else if vacuumJobs.isRunning then
      Left(
        ApiError(
          "a vacuum is still running — wait for its job (`histmersion jobs`) to finish",
          Some(Backup.CodeMaintenance)
        )
      )
    else
      maintenance.exclusively("restore"):
        // The OCR workers hold long transactions and write sources; a job landing mid-restore writes
        // into a schema that is being replaced. Paused for the duration, resumed whatever happens.
        ingest.pause()
        try restoreNow(req)
        finally ingest.resume()

  private def restoreNow(req: RestoreRequest): Either[ApiError, RestoreResult] =
    val before = backups.guardedRows
    for
      archive <- backups.resolve(req.file)
      _ <- if req.force || before == 0 then Right(()) else Left(backups.refusal(before))
      r <- backups.restore(archive)
      // Every pooled connection now holds a session cache describing types the restore dropped;
      // PostGIS in particular fails outright on the next spatial query. Throw them away here,
      // while the maintenance lock still keeps everyone else out.
      _ = { com.histmersion.db.Db.evict(ds); com.histmersion.db.Db.evict(deps.queryDs) }
      // An older dump comes up to this build's schema now rather than at the next restart; the
      // database settings and the read-only role's grants are not in a `--no-privileges` dump.
      _ <- scala.util
        .Try(Migrate.run(ds))
        .toEither
        .left
        .map(e => ApiError(s"restored ${req.file}, but migrating it failed: ${e.getMessage}"))
      _ = com.histmersion.db.Db.ensureDatabaseSettings(ds)
      role = com.histmersion.db.Db
        .provisionQueryRole(ds, cfg.query)
        .left
        .toOption
        .map(m => s"the read-only role '${cfg.query.user}' was not re-provisioned: $m")
        .toList
      _ = { com.histmersion.db.Db.evict(ds); com.histmersion.db.Db.evict(deps.queryDs) }
      // the restored versions of what the map's tiles read may be ones the kept tiles were cut under
      _ = clearOnRestore()
      v <- Migrate.validate(ds).left.map(m => ApiError(s"restored ${req.file}, but the schema does not validate: $m"))
    yield RestoreResult(req.file, v, before, backups.guardedRows, r._1, r._2 ++ role)

  /** The registered caches a restore makes stale, cleared: what this server holds of each. */
  private def clearOnRestore(): Unit =
    val held: Map[CacheName, () => Unit] = Map(CacheName("map-tiles") -> (() => tiles.cache.clear()))
    Deps.data.caches.filter(_.staleAfter.contains(CacheEvent.Restored)).foreach(c => held.get(c.name).foreach(_()))

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────

  /** `GET /api/articles` — the compiled articles citing the filter's sources (see [[Api.ArticleQuery]]), narrowed by
    * kind and searched with `text`.
    */
  def listArticles(q: Api.ArticleQuery): ArticleList =
    val f = resolve(q.filter)
    val list = ArticleRepo.list(ds, f, scopeOf(f), q.kind, q.text, f.limit, f.offset)
    val facts = ArticleReviewRepo.facts(ds, list.articles.map(_.slug))
    list.copy(articles =
      list.articles.map(a =>
        a.copy(review = Some(ArticleReviewStatus.badge(facts.get(a.slug).fold(ArticleReviewStatus.Facts())(_._1))))
      )
    )

  /** `GET /api/articles/{slug}/review` (MCP `article_review`, `histmersion article review`): the article's review
    * state, its review history from the ledger (the only read that decodes one), the goals linking it and the approvals
    * about it that wait for a person.
    */
  def articleReview(slug: String): Either[ApiError, ArticleReviewView] =
    val canon = ArticleRepo.canonical(ds, norm(slug))
    ArticleRepo.load(ds, canon).toRight(Articles.notFound(slug)).flatMap(_ => reviewOf(canon))

  /** The review view of an article known to exist (its canonical slug); a ledger that no longer decodes is an error
    * rather than an empty history.
    */
  def reviewOf(slug: String): Either[ApiError, ArticleReviewView] =
    ReviewLedgerRepo.get(ds, slug).left.map(e => ApiError(e)).map { ledger =>
      val (facts, updatedAt) =
        ArticleReviewRepo.facts(ds, List(slug)).getOrElse(slug, (ArticleReviewStatus.Facts(), None))
      val starts = ledger.fold(Map.empty[String, String])(l =>
        ArticleReviewRepo.runStarts(ds, ArticleReviewStatus.runOrder(l, Map.empty))
      )
      ArticleReviewView(
        slug,
        ArticleReviewStatus.badge(facts),
        facts.item.flatMap(_.outcome),
        updatedAt,
        ledger.fold(Nil)(ArticleReviewStatus.history(_, starts)),
        ArticleReviewRepo.goals(ds, slug),
        facts.waiting
      )
    }

  /** One article, with its backlinks, the sources it cites, and — when it records a filter — its coverage (#958), from
    * which every surface renders the generated Not used list and Method note.
    */
  def getArticle(slug: String, brief: Boolean = false): Either[ApiError, ArticleView] =
    val view = ArticleRepo.view(ds, norm(slug)).toRight(Articles.notFound(slug))
    // `brief`: the row and its links only — the lint and the coverage are most of a read's cost
    // (a hover card waited ~20 ms on the lint alone) and a preview shows neither
    if brief then view
    else
      view.map(v =>
        v.copy(
          formatting = articleFormatting(v.article.body, v.article.summary),
          coverage = ArticleRepo.load(ds, v.article.slug).flatMap(coverageOf)
        )
      )

  // ── coverage (#958): the article's filter against the live corpus ─────────────────────────────

  /** `GET /api/articles/{slug}/coverage` (MCP `article_coverage`, `histmersion article coverage`). An article that
    * records no filter has no coverage: 404 saying so and how to set one.
    */
  def articleCoverage(slug: String): Either[ApiError, Coverage] =
    ArticleRepo.load(ds, ArticleRepo.canonical(ds, norm(slug))).toRight(Articles.notFound(slug)).flatMap { a =>
      coverageOf(a).toRight(
        ApiError(
          s"article '${a.slug}' records no filter, so it has no generated coverage — " +
            s"set the filter it is compiled from: histmersion article patch ${a.slug} --filter-query … (or --filter-topics/--filter-tags), " +
            "or patch_article with `filter`",
          Some(Write.CodeNotFound)
        )
      )
    }

  /** The coverage of one stored article, `None` when it records no filter: the filter's source set (at most
    * `limits.coverage-sources` read; the counts are exact), which of them the body cites (its `[[links]]` resolved as
    * the page resolves them, a hand-written Not used section excluded), the disagreement edges touching the cited
    * sources, and the writer's further searches — handed to the pure [[ArticleCoverage.compute]].
    */
  def coverageOf(a: Article): Option[Coverage] =
    a.filter.map { af =>
      val f = af.toFilter
      val scope = scopeOf(f)
      val (total, hits) = CoverageRepo.sourceSet(ds, f, scope, cfg.limits.coverageSources)
      val links = bodyLinks(a.body)
      val cited = citedOf(a.body, links)
      val inFilter = CoverageRepo.members(ds, f, scope, cited)._2
      val edges = CoverageRepo.edgesTouching(ds, cited, ArticleCoverage.DisagreementKinds)
      val searches = af.extraQueries.map { q =>
        val (n, in) = CoverageRepo.members(ds, Filter(q = Some(q)), PlaceScope.empty, cited)
        CoverageSearch(q, n, cited.filter(in))
      }
      ArticleCoverage
        .compute(a.slug, af, total, hits, inFilter, cited, a.reasons, edges, searches)
        .copy(
          eventLeads = eventLinks.articleLeads(a.body, (cited ++ hits.map(_.citekey)).distinct, f.period),
          critiques = critiquesOf(a.body, links, edges, CritiqueRepo.list(ds, a.slug))
        )
    }

  /** The stored citekeys a body cites: its `[[links]]` outside a hand-written Not used section, resolved as the page
    * resolves them (an alias or a unique short form names its source).
    */
  def citedSources(body: String): List[String] = citedOf(body, bodyLinks(body))

  /** How the links of a body (outside a hand-written Not used section) resolve. */
  private def bodyLinks(body: String): String => Option[LinkTarget] =
    pageLinks(ArticleLinks.targets(ArticleCoverage.withoutNotUsed(body)))

  private def citedOf(body: String, links: String => Option[LinkTarget]): List[String] =
    ArticleLinks
      .targets(ArticleCoverage.withoutNotUsed(body))
      .flatMap(t => links(t).collect { case r: SourceRef => r.citekey })
      .distinct

  /** THE CRITIQUE CHECKLIST of an article (docs/research-review-convergence.md §6.8): every
    * challenges/refutes/updates/superseded_by edge with an end cited by `body`, addressed (both cited in one
    * paragraph), reasoned (a stored reason) or open — whether or not the article records a filter. `body` is the
    * article's own unless a caller reviews a revision held in memory; `extra` reasons count as stored (a dry run's,
    * which were never written).
    */
  def critiques(slug: String, body: String, extra: List[CritiqueReason] = Nil): List[CritiquePair] =
    val links = bodyLinks(body)
    val cited = citedOf(body, links)
    val edges = CoverageRepo.edgesTouching(ds, cited, ArticleCoverage.DisagreementKinds)
    val stored = CritiqueRepo.list(ds, ArticleRepo.canonical(ds, norm(slug)))
    critiquesOf(body, links, edges, extra ++ stored.filterNot(s => extra.exists(_.ref.same(s.ref))))

  /** THE PAIRING RULE of a body (docs/research-review-convergence.md §6.9, lint `article-critique-pairing`): each
    * paragraph that cites one side of a disagreement edge and names the other side's author without citing it, and the
    * body with the mechanical ones repaired (the missing citekey appended to the paragraph's citation). Read only.
    */
  def pairing(body: String): (List[CritiquePairing.Hit], String => Option[String]) =
    val links = bodyLinks(body)
    val cited = citedOf(body, links)
    val edges = CoverageRepo.edgesTouching(ds, cited, ArticleCoverage.DisagreementKinds)
    val works = SourceRepo
      .refs(ds, edges.flatMap(e => List(e.from, e.to)).distinct)
      .map(r => r.citekey -> CritiquePairing.Work(r.citekey, CritiquePairing.names(r.citekey, r.authors), r.year))
      .toMap
    val resolve: String => Option[String] = t => links(t).collect { case r: SourceRef => r.citekey }
    (CritiquePairing.hits(body, resolve, edges, works.get), resolve)

  /** [[pairing]]'s mechanical repair: the body with each named partner's citekey appended, and the hits applied.
    */
  def repairPairing(body: String): (String, List[CritiquePairing.Hit]) =
    CritiquePairing.repair(body, pairing(body)._1)

  /** What filter `af` returns from the live corpus: its source count, which of `citekeys` it returns, and up to `cap`
    * of its sources (by citekey) — what the managers' code checks measure (infer-filter, propose-articles;
    * docs/research-process-managers.md, phase 2).
    */
  def filterReach(af: ArticleFilter, citekeys: List[String], cap: Int): (Int, Set[String], List[String]) =
    val f = af.toFilter
    val scope = scopeOf(f)
    val (total, rows) = CoverageRepo.sourceSet(ds, f, scope, math.max(0, cap))
    val in = if citekeys.isEmpty then Set.empty[String] else CoverageRepo.members(ds, f, scope, citekeys)._2
    (total, in, rows.map(_.citekey))

  /** How many sources the filter article `slug` records returns now (None: no article, or no filter) — the review
    * manager's stale check.
    */
  /** Whether an article with this slug exists (as stored, no alias followed). */
  def articleExists(slug: String): Boolean = ArticleRepo.slugExists(ds, slug)

  /** Every goal's linked articles that exist: goal → slugs (research's measure of a goal's progress). */
  def articlesByGoal(): Map[String, List[String]] = ArticleRepo.articlesByGoal(ds)

  /** The articles that passed their review (no open MAJOR, no open item of review manager `reviewId`). */
  def passedReview(reviewId: String): Set[String] = ReviewLedgerRepo.passedReview(ds, reviewId)

  /** An article's review ledger (none: never reviewed, or one that no longer decodes) — the review manager kind's. */
  def reviewLedger(slug: String): Option[ReviewLedger.Ledger] = ReviewLedgerRepo.get(ds, slug).toOption.flatten

  /** The coverage filter an article records, as stored (no alias followed) — the review manager kind's. */
  def recordedFilter(slug: String): Option[ArticleFilter] = ArticleRepo.load(ds, slug).flatMap(_.filter)

  def articleFilterTotal(slug: String): Option[Int] =
    ArticleRepo.load(ds, ArticleRepo.canonical(ds, norm(slug))).flatMap(_.filter).map(af => filterReach(af, Nil, 0)._1)

  /** How many sources an article's filter returns (of the first `limits.coverage-sources`) that its body neither cites
    * nor gives a reason for — what an extension would add (the autopilot's article-extend threshold). None: no such
    * article, or it records no filter.
    */
  def newFilterSources(slug: String): Option[Int] =
    ArticleRepo
      .load(ds, ArticleRepo.canonical(ds, norm(slug)))
      .flatMap(a =>
        a.filter.map { af =>
          val f = af.toFilter
          val (_, hits) = CoverageRepo.sourceSet(ds, f, scopeOf(f), cfg.limits.coverageSources)
          val cited = citedOf(a.body, bodyLinks(a.body)).toSet
          hits.map(_.citekey).count(ck => !cited(ck) && !a.reasons.contains(ck))
        }
      )

  /** How many pairs of the article's critique checklist are open now and bear on a work it cites (None: no such
    * article) — what the review manager tracks per run (§6.8). An open pair on a work the article does not cite is
    * background (§6.9): no run owes it anything, so it is not counted.
    */
  def openCritiques(slug: String): Option[Int] =
    ArticleRepo
      .load(ds, ArticleRepo.canonical(ds, norm(slug)))
      .map(a => Critiques.toRule(critiques(a.slug, a.body)).size)

  private def critiquesOf(
      body: String,
      links: String => Option[LinkTarget],
      edges: List[CoverageEdge],
      reasons: List[CritiqueReason]
  ): List[CritiquePair] =
    if edges.isEmpty then Nil
    else
      val refs = SourceRepo
        .refs(ds, edges.flatMap(e => List(e.from, e.to)).distinct)
        .map(r => r.citekey -> (r.title, r.year))
        .toMap
      Critiques.build(body, t => links(t).collect { case r: SourceRef => r.citekey }, edges, reasons, refs.get)

  /** Reasons are stored under the citekey a source has NOW (an old one resolves). */
  private def canonicalReasons(rs: List[CoverageReason]): List[CoverageReason] =
    rs.map(r =>
      r.copy(citekey =
        canonical(Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse(""))
      )
    )

  /** How a page's `[[wikilinks]]` resolve (plan §5.2): the citekey aliases its targets expand to (exact first, else the
    * unique `<target>_%` prefix — the wiki writes `[[knapp2016]]` for `knapp2016_seven`), the stored sources behind
    * them, and which of the rest are compiled articles — three queries for the whole page, whatever its number of
    * links. A source wins over an article, as in the pure [[ArticleLinks.hrefFor]]; anything unresolvable is `None` and
    * renders as text rather than a link to a 404.
    */
  def pageLinks(targets: List[String]): String => Option[LinkTarget] =
    val aliases = SourceRepo.resolveAliases(ds, targets)
    val sources = SourceRepo.refs(ds, aliases.values.toList).map(r => r.citekey -> r).toMap
    val articles = ArticleRepo.refs(ds, targets).map(a => a.slug -> a).toMap
    t => aliases.get(t).flatMap(sources.get).orElse(articles.get(t))

  /** The citation-link view of stored sources (DOI, byline, status), for the HTML pages. */
  def sourceRefs(citekeys: List[String]): List[SourceRef] = SourceRepo.refs(ds, citekeys)

  /** The link view of compiled articles, for the HTML pages. */
  def articleRefs(slugs: List[String]): List[ArticleRef] = ArticleRepo.refs(ds, slugs)

  /** Create or REPLACE one article. */
  /** Create or REPLACE one article. The response carries its formatting problems; `strict` refuses the write when one
    * of them is an error.
    */
  def putArticle(slug: String, in: ArticlePut, strict: Boolean = false): Either[ApiError, ArticleView] =
    maintenance.guard("put_article"):
      // an OLD slug follows the alias (#1799): the put replaces the renamed article
      Articles
        .applyPut(
          Articles.slugOf(slug).fold(_ => slug, s => current(AliasKindsAdapter.ArticleRecords, s)),
          in.copy(reasons = canonicalReasons(in.reasons))
        )
        .left
        .map(m => ApiError(m, Some(Write.CodeInvalid)))
        .flatMap(a => strictGate(strict, articleFormatting(a.body, a.summary)).map(_ => a))
        .flatMap { a =>
          val _ = ArticleRepo.put(ds, a); getArticle(a.slug)
        }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). With `strict`, the merged
    * result is checked first — an append is judged in the article it lands in.
    */
  def patchArticle(slug: String, p: ArticlePatch, strict: Boolean = false): Either[ApiError, ArticleView] =
    maintenance.guard("patch_article"):
      Articles
        .slugOf(slug)
        .left
        .map(m => ApiError(m, Some(Write.CodeInvalid)))
        .map(current(AliasKindsAdapter.ArticleRecords, _))
        .flatMap { s =>
          val gate =
            if !strict then Right(())
            else
              ArticleRepo
                .load(ds, s)
                .fold(Right(()))(cur =>
                  Articles
                    .applyPatch(cur, p)
                    .fold(_ => Right(()), next => strictGate(true, articleFormatting(next.body, next.summary)))
                )
          gate.flatMap(_ => ArticleRepo.patch(ds, s, p.copy(reasons = canonicalReasons(p.reasons))))
        }
        .flatMap(_ => getArticle(slug))

  def deleteArticle(slug: String): Either[ApiError, ArticleDeleted] =
    maintenance.guard("delete_article"):
      val now = current(AliasKindsAdapter.ArticleRecords, norm(slug))
      if ArticleRepo.delete(ds, now) then Right(ArticleDeleted(now, true))
      else Left(Articles.notFound(slug))

  /** The article as it would live on disk — `wiki/<kind>/<slug>.md`, with its generated coverage sections after the
    * body (between markers an import drops) and its filter and reasons in the frontmatter (#958).
    */
  def exportArticle(slug: String): Either[ApiError, String] =
    mirror.note(MirrorAdapter.ArticleNotes, slug)(using transitionNotes)

  /** The edit history of one article. 404 when the slug itself is unknown (so "no versions yet" and "no such article"
    * are different answers).
    */
  def articleVersions(slug: String): Either[ApiError, List[ArticleVersion]] =
    getArticle(norm(slug)).map(v => ArticleRepo.listVersions(ds, v.article.slug))

  /** One historical version's full body. 404 when the slug or the version id is unknown. */
  def articleVersion(slug: String, versionId: Long): Either[ApiError, ArticleVersionFull] =
    getArticle(norm(slug)).flatMap(v =>
      ArticleRepo
        .getVersion(ds, v.article.slug, versionId)
        .toRight(ApiError(s"No version $versionId for article '$slug'", Some(Write.CodeNotFound)))
    )

  def articleSlugs: List[String] = ArticleRepo.slugs(ds).toList

  /** Import ONE article note from raw text (the host-side path of `histmersion seed`). Applies the same "stored row is
    * newer than the file" guard as the source seed whenever the client sends the file's mtime, so a reseed cannot wipe
    * an agent's compiled prose.
    */
  def importArticle(req: ArticleImport): Either[ApiError, SeedResult] =
    maintenance.guard("import_article")(importArticleNow(req))

  private def importArticleNow(req: ArticleImport): Either[ApiError, SeedResult] =
    val fallbackSlug = req.slug.map(_.trim).filter(_.nonEmpty).getOrElse("")
    val fallbackKind = req.kind.map(_.trim).filter(_.nonEmpty).getOrElse(Articles.Kinds.head)
    ArticleNote.parse(req.text, fallbackSlug, fallbackKind) match
      case None         => Left(ApiError("no YAML frontmatter in the article note"))
      case Some(parsed) =>
        Articles
          .slugOf(parsed.slug)
          .left
          .map(m => ApiError(m, Some(Write.CodeInvalid)))
          .flatMap { s =>
            // as for a source note: a note filed under an OLD slug is a stale mirror file — it would
            // otherwise create a second article that shadows the alias
            com.histmersion.util.identity.AliasTablesAdapter.ArticleAliases
              .renamedTo(ds, s)
              .fold(Right(s))(now =>
                Left(
                  ApiError(
                    s"'$s' was renamed to '$now' — import the note under '$now' instead",
                    Some(Write.CodeInvalid)
                  )
                )
              )
          }
          .flatMap { s =>
            if !req.force && req.mtime.exists(m => Importer.isStale(ArticleRepo.updatedAtMillis(ds), s, m))
            then Right(SeedResult(0, Nil, 0, List(s)))
            else
              Articles
                .kindOf(parsed.kind)
                .left
                .map(m => ApiError(m, Some(Write.CodeInvalid)))
                .flatMap(k => strictGate(req.strict, articleFormatting(parsed.body, parsed.summary)).map(_ => k))
                .map { k =>
                  val _ = ArticleRepo.put(ds, parsed.copy(slug = s, kind = k))
                  SeedResult(1, Nil, 0, Nil)
                }
          }

  /** Slugs are lower-case by construction; be forgiving about what a caller sends. */
  private def norm(slug: String): String = slug.trim.toLowerCase

object Handlers:
  /** The `jobs.kind` of a `places fetch` (a gazetteer, or the land) and of a hierarchy build (#1946). */
  val PlacesFetchJobKind = com.histmersion.api.JobKindsAdapter.PlacesFetch.value
  val PlacesHierarchyJobKind = com.histmersion.api.JobKindsAdapter.PlacesHierarchy.value
