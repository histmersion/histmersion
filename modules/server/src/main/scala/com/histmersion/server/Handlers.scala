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
  ArticleRepo,
  ArticleReviewRepo,
  CoverageRepo,
  CritiqueRepo,
  GoalRepo,
  Migrate,
  ReviewLedgerRepo
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

  /** Every source write re-materialises that citekey's `finding_places` (the library's sources), so the gazetteer
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

  /** `GET /api/sources/{ck}/articles` — the compiled articles that cite this source (plan §5.2). 404 when the citekey
    * itself is unknown, so "no articles yet" and "no such source" are different answers.
    */
  def sourceArticles(citekey: String): Either[ApiError, List[Backlink]] =
    library.sources.get(citekey).map(s => ArticleRepo.backlinksForSource(ds, s.source.citekey))

  /** `GET /api/resolve/{id}`: a stored source, else an article, else nothing — never an error. */
  def resolveId(id: String): Either[ApiError, ResolvedId] =
    val src = library.sources
      .get(id, body = false)
      .toOption
      .map(s =>
        ResolvedSource(s.source.citekey, s.source.doi, s.source.authors, s.source.year, s.source.title, s.source.status)
      )
    val art = if src.isDefined then None
    else getArticle(id, brief = true).toOption.map(a => ResolvedArticle(a.article.slug, a.article.title))
    Right(ResolvedId(id, src, art))

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

  private def articleFormatting(body: String, summary: Option[String]): List[FormatIssue] =
    MarkdownLint.ofArticle(body, summary, pageLinks(MarkdownLint.lookups(body :: summary.toList)))

  /** `strict`: refuse a write whose text has a formatting ERROR; otherwise the problems ride along in the response, and
    * the write goes through.
    */
  private def strictGate(strict: Boolean, issues: => List[FormatIssue]): Either[ApiError, Unit] =
    if !strict then Right(())
    else MarkdownLint.refusal(issues).fold(Right(()))(m => Left(ApiError(m, Some(Write.CodeInvalid))))

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
      case FormatField.Notes   => library.sources.formatIssues(None, Some(text))
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
      library.sources.note(citekey)

    def articleNote(slug: String): Either[ApiError, String] =
      ArticleRepo
        .load(ds, current(AliasKindsAdapter.ArticleRecords, norm(slug)))
        .map(a => ArticleNote.render(a, coverageOf(a).map(ArticleCoverage.exported)))
        .toRight(Articles.notFound(slug))

  /** "Mentioned in articles" rides along with the default/all views rather than becoming a tool of its own: an LLM
    * asking about a source wants what the corpus has WRITTEN about it in the same answer, not after a second round
    * trip.
    */
  private def mentionedIn(citekey: String): String =
    Markdown.sourceArticles(ArticleRepo.backlinksForSource(ds, citekey).toVector)

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
      com.histmersion.domain.research.articles.lint.SourceTextFormatting(pageLinks),
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
    val works = library.sourceStore
      .refs(edges.flatMap(e => List(e.from, e.to)).distinct)
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
      val refs = library.sourceStore
        .refs(edges.flatMap(e => List(e.from, e.to)).distinct)
        .map(r => r.citekey -> (r.title, r.year))
        .toMap
      Critiques.build(body, t => links(t).collect { case r: SourceRef => r.citekey }, edges, reasons, refs.get)

  /** Reasons are stored under the citekey a source has NOW (an old one resolves). */
  private def canonicalReasons(rs: List[CoverageReason]): List[CoverageReason] =
    rs.map(r =>
      r.copy(citekey =
        library.sources
          .canonical(Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse(""))
      )
    )

  /** How a page's `[[wikilinks]]` resolve (plan §5.2): the citekey aliases its targets expand to (exact first, else the
    * unique `<target>_%` prefix — the wiki writes `[[knapp2016]]` for `knapp2016_seven`), the stored sources behind
    * them, and which of the rest are compiled articles — three queries for the whole page, whatever its number of
    * links. A source wins over an article, as in the pure [[ArticleLinks.hrefFor]]; anything unresolvable is `None` and
    * renders as text rather than a link to a 404.
    */
  def pageLinks(targets: List[String]): String => Option[LinkTarget] =
    val aliases = library.sourceStore.resolveLinks(targets)
    val sources = library.sourceStore.refs(aliases.values.toList).map(r => r.citekey -> r).toMap
    val articles = ArticleRepo.refs(ds, targets).map(a => a.slug -> a).toMap
    t => aliases.get(t).flatMap(sources.get).orElse(articles.get(t))

  /** The citation-link view of stored sources (DOI, byline, status), for the HTML pages. */
  def sourceRefs(citekeys: List[String]): List[SourceRef] = library.sourceStore.refs(citekeys)

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
            if !req.force && req.mtime.exists(m =>
                com.histmersion.domain.library.ingest.SeedImport.isStale(ArticleRepo.updatedAtMillis(ds), s, m)
              )
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
