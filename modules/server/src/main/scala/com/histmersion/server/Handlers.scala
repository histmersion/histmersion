package com.histmersion.server

import com.histmersion.util.identity.AliasKinds

import com.histmersion.domain.catalogue.places.PlaceScope

import com.augustnagro.magnum.{connect, transact}

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
import com.histmersion.util.data.mirror.Mirror

import com.histmersion.values.{BundleKind, Citekey, PlaceKind, UserId}
import com.histmersion.files.*

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{AgentStateRepo, GoalRepo, Migrate}
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

  /** Every source write re-materialises that citekey's `finding_places` (the library's sources), so the gazetteer
    * tolerance travels with the write rather than only with `places rebuild`.
    */
  private def tolKm = cfg.places.pointToleranceKm
  private val backups: BackupService = BackupWiring.service(deps)
  private val backupJobs = BackupJobs(backups, deps.cfg.backups.requestWaitSeconds, deps.pins)
  private val vacuumJobs = VacuumJobs(ds, deps.maintenance, Deps.data, () => cfg.maintenance.tables, deps.pins)

  /** The registry's figures on this server's database. */
  private val diagnostics: DiagnosticsService = DiagnosticsService(Deps.data, deps.transactor)

  /** One lock per server (plan §1.10): held by a restore, checked by EVERY write — here, in `OpsHandlers`, in the
    * ingest and the acquisition controllers, which is why it lives on `Deps` rather than in this class. A restore drops
    * and recreates every table; a write that slipped past the lock wrote into a schema that was about to be replaced.
    */
  val maintenance: WriteGate = deps.maintenance

  /** What the library's workflows are given. */
  lazy val librarySite: com.histmersion.domain.library.workflows.LibrarySite = LibraryWorkflowSite(this, deps)

  /** What research's workflows are given. */
  lazy val researchSite: com.histmersion.domain.research.workflows.ResearchSite = ResearchWorkflowSite(this, deps)

  /** Stream 2.8 — lint, the bookkeeping queues and the read-only query. Its own object (one file per stream), wired
    * into REST and MCP through this same `Handlers` value.
    */
  val ops: OpsHandlers =
    OpsHandlers(deps, (slug, body, token) => research.articles.writeRepairedBody(slug, body, token))

  /** The work lists' kernel. */
  val queues: com.histmersion.domain.automation.worklists.QueueService = deps.queues

  /** The first acquisition item (any status) whose text names `doi`. */
  def acquisitionItemNaming(doi: String): Option[Int] =
    queues.naming(com.histmersion.api.Queue.Kind.Acquire, doi).headOption.map(_.id)

  /** Media's pictures, their links, the imports from outside providers (#1446), the sources' covers and illustrations.
    * Thumbnails come from the converter.
    */
  val media: MediaWiring = MediaWiring(deps, converter)

  /** The library's ingest: the OCR job queue, the ingest surface, the hand-over of a fetched copy, the seed. */
  lazy val ingest: IngestWiring = IngestWiring(
    deps,
    converter,
    library.sourceStore,
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
          com.histmersion.domain.automation.worklists.WorkListMergeFollowUps,
          applyJobs,
          () => backups.create(Instant.now()).map(_.file),
          Deps.sourceReferrers
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
      val env = deps.environment.lookup
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
    val conv = ingest.queue.converterHealth
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
      JobCounts.of(
        diagnostics.figures(com.histmersion.util.jobs.JobsData.owner).counts.map(c => c.noun -> c.count.toInt)
      )
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
    val figures = Deps.data.owners.filterNot(_ == com.histmersion.util.jobs.JobsData.owner).map(diagnostics.figures)
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
      .Try(
        com.histmersion.domain.library.acquisition
          .UpstreamQuotaService(deps.transactor)
          .load(com.histmersion.domain.library.acquisition.CoreBudget.Upstream)
      )
      .toOption
      .flatten
      .map(s =>
        com.histmersion.domain.CoreQuota
          .view(s, com.histmersion.domain.library.acquisition.CoreBudget.limits(cfg.acquire.core), now)
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

  /** A filter's page, as the library reads it. */
  private def resolve(f: Filter): Filter = library.filters.page(f)

  /** The places a filter's `places` values name, as the library reads them. */
  private def scopeOf(f: Filter): PlaceScope = library.filters.scope(f)

  /** `GET /api/sources/{ck}/articles`: research's. */
  def sourceArticles(citekey: String): Either[ApiError, List[Backlink]] = research.articles.sourceArticles(citekey)

  /** `GET /api/resolve/{id}`: research's (a source, else an article). */
  def resolveId(id: String): Either[ApiError, ResolvedId] = resolveView(id)

  private lazy val resolveView = com.histmersion.server.views.ResolveView(
    id =>
      library.sources
        .get(id, body = false)
        .toOption
        .map(s =>
          ResolvedSource(
            s.source.citekey,
            s.source.doi,
            s.source.authors,
            s.source.year,
            s.source.title,
            s.source.status
          )
        ),
    research.articles.resolved
  )

  /** THE identifier resolution of every renamed kind (#1799, [[Aliases]]): the identifier the record answers to now —
    * an old one resolves to where it went. Every read AND every write resolves here first, so a put, patch, delete or
    * rename naming an old id goes to the current record.
    */
  def current(k: AliasKind, id: String): String = Deps.aliasTables.current(ds, k, id)

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
        else Right(ingest.seed.run(SeedNoteFiles(dir, corpusRoot), req.force, cfg.limits.assetBytes))
      }

  /** `corpus.dir` as an `Option`: empty string = not configured. */
  private def corpusRoot: Option[String] = Option(cfg.corpus.dir).map(_.trim).filter(_.nonEmpty)

  // ── formatting: what in the agent-written Markdown will not display as meant ─────────────────

  /** `GET /api/articles/{slug}/lint`. */
  def lintArticle(slug: String): Either[ApiError, FormatReport] = research.articles.lint(slug)

  /** `POST /api/articles/{slug}/lint/fix`. */
  def fixArticle(slug: String): Either[ApiError, FormatReport] = research.articles.fix(slug)

  /** `POST /api/lint/markdown` — Markdown that is not stored (yet): a file about to be put. With `fix`, the repaired
    * text comes back in `text`. A `body` is checked as an article body.
    */
  def lintText(in: FormatCheck): FormatReport =
    val (text, done) = if in.fix then
      MarkdownLint.fix(in.text, in.field, MarkdownLint.Context(article = in.field == FormatField.Body))
    else (in.text, Nil)
    val issues = in.field match
      case FormatField.Body    => research.articles.formatting(text, None)
      case FormatField.Summary =>
        MarkdownLint.ofSource(Some(text), None, research.articles.pageLinks(MarkdownLint.lookups(List(text))))
      case FormatField.Notes => library.sources.formatIssues(None, Some(text))
    FormatReport("text", issues, done, Option.when(in.fix)(text))

  /** The note as it would live on disk — frontmatter YAML + the `notes` body. */
  def exportSource(citekey: String): Either[ApiError, String] =
    mirror.note(com.histmersion.domain.library.LibraryData.SourceNotes, citekey)

  /** The Markdown mirror over the registered kinds, each note rendered by its owner. */
  private lazy val mirror = Mirror(
    Deps.data.mirror,
    List(
      com.histmersion.domain.library.sources.SourceMirrorNote(library.sources),
      com.histmersion.domain.research.articles.ArticleMirrorNote(research.articles)
    )
  )

  /** "Mentioned in articles" rides along with the default/all views rather than becoming a tool of its own: an LLM
    * asking about a source wants what the corpus has WRITTEN about it in the same answer, not after a second round
    * trip.
    */
  private def mentionedIn(citekey: String): String = research.articles.mentionedIn(citekey)

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

  /** The library's sources, findings, citations, labels and search; research's prose lint checks a source's text and
    * writes the "Mentioned in articles" part, the ingest's stores hold its originals.
    */
  lazy val library: LibraryWiring =
    LibraryWiring(
      deps,
      media.assets,
      catalogue.placeStore,
      com.histmersion.domain.research.articles.lint.SourceTextFormatting(targets =>
        research.articles.pageLinks(targets)
      ),
      mentionedIn,
      storedOriginals
    )

  /** The files the server keeps of the sources' originals: the uploads and the acquisition spool. */
  lazy val storedOriginals: com.histmersion.domain.library.ingest.StoredOriginals =
    com.histmersion.domain.library.ingest.StoredOriginals(
      () => deps.storage.store(com.histmersion.util.storage.Location.IngestOriginals),
      () => deps.storage.store(com.histmersion.util.storage.Location.Spool)
    )

  /** The sources not in Calibre yet, a push's metadata, the Calibre id written back. */
  lazy val calibreGaps: com.histmersion.domain.library.collections.CalibreGapControllers =
    com.histmersion.domain.library.collections.CalibreGapControllers(
      library.sourceStore,
      storedOriginals,
      () => deps.cfg,
      deps.maintenance
    )

  /** The background jobs as they are listed. */
  val jobs: com.histmersion.util.jobs.JobListing = com.histmersion.util.jobs.JobListing(ds, () => deps.cfg)

  /** `GET /api/jobs` filtered ([[ListFilters.JobFilter]]): one page, newest first, with the per-state and the kind
    * counts under the same filter.
    */
  def jobsPage(
      f: ListFilters.JobFilter,
      limit: Option[Int],
      before: Option[String],
      upTo: Option[String]
  ): Either[ApiError, (List[JobInfo], Paging.Page)] =
    ListQueries.jobs(f, Instant.now(deps.clock)).flatMap(q => jobs.page(q, limit, before, upTo))

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
          (citekeys, limit) => research.articles.store.citing(citekeys, limit)
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
    connect(ds)(GoalRepo.get(current(AliasKinds.GoalRecords, slug)))
      .toRight(ApiError(s"No goal '$slug'", Some(Write.CodeNotFound)))

  /** A write to an OLD slug goes to the goal it was renamed to (#1799); a put replaces it. */
  def putGoal(slug: String, in: GoalPut): Either[ApiError, Goal] =
    maintenance.guard("put_goal"):
      val now = current(AliasKinds.GoalRecords, slug)
      transact(ds)(GoalRepo.put(now, in)).flatMap(_ => getGoal(now))

  def patchGoal(slug: String, p: GoalPatch): Either[ApiError, Goal] =
    maintenance.guard("patch_goal"):
      val now = current(AliasKinds.GoalRecords, slug)
      GoalRepo.patch(ds, now, p).flatMap(_ => getGoal(now))

  def deleteGoal(slug: String): Either[ApiError, GoalDeleted] =
    maintenance.guard("delete_goal"):
      val now = current(AliasKinds.GoalRecords, slug)
      if GoalRepo.delete(ds, now) then Right(GoalDeleted(now, true))
      else Left(ApiError(s"No goal '$slug'", Some(Write.CodeNotFound)))

  /** `POST /api/goals/{slug}/rename` — a new slug; the old one keeps resolving (V053). */
  def renameGoal(slug: String, req: LabelRename): Either[ApiError, LabelRenamed] =
    maintenance.guard("rename_goal"):
      val from = current(AliasKinds.GoalRecords, slug)
      for
        to <- LabelRenames.validate(BundleKind.Goal, from, req.to)
        _ <- GoalRepo.rename(ds, from, to)
      yield LabelRenamed(BundleKind.Goal, from, to)

  /** `POST /api/articles/{slug}/rename`: research's. */
  def renameArticle(slug: String, req: LabelRename): Either[ApiError, LabelRenamed] =
    research.articles.rename(slug, req)

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
    library.search.selectionCounts(f)

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
        ingest.queue.pause()
        try restoreNow(req)
        finally ingest.queue.resume()

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
      role = com.histmersion.domain.library.search.QueryAccess
        .provision(ds, cfg.query)
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

  // ── research ─────────────────────────────────────────────────────────────────────────────────

  /** Research's articles: their services, controllers and page, and its review ledgers. */
  lazy val research: ResearchWiring =
    ResearchWiring(
      deps,
      library,
      (body, citekeys, period) => eventLinks.articleLeads(body, citekeys, period),
      slug => mirror.note(com.histmersion.domain.research.ResearchData.ArticleNotes, slug)
    )

object Handlers:
  /** The `jobs.kind` of a `places fetch` (a gazetteer, or the land) and of a hierarchy build (#1946). */
  val PlacesFetchJobKind = com.histmersion.api.catalogue.places.PlacesJobKinds.PlacesFetch.kind.value
  val PlacesHierarchyJobKind = com.histmersion.api.catalogue.places.PlacesJobKinds.PlacesHierarchy.kind.value
