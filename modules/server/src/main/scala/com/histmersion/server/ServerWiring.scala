package com.histmersion
package server
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.util.data.diagnostics.DiagnosticsService
import com.histmersion.util.data.backup.BackupService
import com.histmersion.util.data.maintenance.{CacheName, WriteGate}
import com.histmersion.util.jobs.Quiescent

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.api.aliases.AliasKind
import com.histmersion.util.data.mirror.Mirror

import com.histmersion.PageSize
import com.histmersion.domain.*
import java.time.Instant
import com.histmersion.api.automation.AgentLeaseInfo
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.api.checks.Lint
import com.histmersion.api.library.{Filter, ResolvedSource, UpstreamQuota}
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Place
import com.histmersion.domain.library.workflows.LibrarySite
import com.histmersion.domain.research.workflows.ResearchSite
import com.histmersion.domain.automation.worklists.QueueService
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import com.histmersion.domain.library.Doi
import com.histmersion.api.automation.Queue
import com.histmersion.server.agents.EngineContributions
import com.histmersion.domain.library.ingest.IngestQueue
import com.histmersion.domain.catalogue.periods.PeriodsControllers
import com.histmersion.util.basemap.BasemapService
import com.histmersion.util.data.exchange.BundleControllers
import com.histmersion.util.jobs.MergeApplyRuns
import com.histmersion.domain.automation.worklists.WorkListMergeFollowUps
import com.histmersion.util.data.exchange.BundleImporter
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.server.agents.ServerEngineSite
import com.histmersion.domain.activity.ActivityControllers
import com.histmersion.domain.activity.ActivityService
import com.histmersion.util.jobs.JobControlControllers
import com.histmersion.util.jobs.Job
import com.histmersion.domain.automation.Run
import com.histmersion.domain.library.sources.SourceReferable
import com.histmersion.domain.research.articles.ArticleReferable
import com.histmersion.domain.catalogue.CatalogueReferable
import com.histmersion.domain.media.assets.PictureReferable
import com.histmersion.domain.automation.goals.GoalReferable
import com.histmersion.domain.personal.PersonalControllers
import com.histmersion.domain.library.sources.SourceReadable
import com.histmersion.domain.research.articles.ArticleReadable
import com.histmersion.domain.personal.seen.SeenService
import com.histmersion.util.settings.SettingsControllers
import com.histmersion.server.views.ResolveView
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.util.identity.AliasTable
import com.histmersion.domain.library.sources.SourceMirrorNote
import com.histmersion.domain.research.articles.ArticleMirrorNote
import com.histmersion.domain.library.Citekey
import com.histmersion.pluginhost.JavaHttpTransport
import com.histmersion.pluginhost.HttpTransport
import com.histmersion.domain.research.articles.lint.SourceTextFormatting
import com.histmersion.domain.library.ingest.StoredOriginals
import com.histmersion.util.storage.Location
import com.histmersion.util.log.{LogControllers, LogService, LogWriter}
import com.histmersion.domain.library.collections.CalibreGapControllers
import com.histmersion.server.views.JobListView
import com.histmersion.util.jobs.JobListing
import com.histmersion.domain.catalogue.events.EventService
import com.histmersion.domain.catalogue.events.EventImportsControllers
import com.histmersion.domain.catalogue.evidence.EventLinksControllers
import com.histmersion.domain.catalogue.places.SiteTypesControllers
import com.histmersion.domain.catalogue.events.EventsControllers
import com.histmersion.server.views.EventCardView
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.events.GeoTolerance
import com.histmersion.domain.catalogue.evidence.AttestationsControllers
import com.histmersion.domain.catalogue.evidence.AttestationService
import com.histmersion.domain.catalogue.places.PlacesControllers
import com.histmersion.domain.catalogue.places.GazetteerControllers
import com.histmersion.domain.catalogue.maptiles.MapTileService
import com.histmersion.domain.catalogue.maptiles.UndatedSites
import com.histmersion.domain.catalogue.maptiles.UndatedBuild
import com.histmersion.server.views.PlaceCardView
import com.histmersion.domain.automation.goals.GoalsControllers
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.Goal
import com.histmersion.api.aliases.AliasKinds
import com.histmersion.domain.automation.agents.AgentStateControllers
import com.histmersion.domain.automation.agents.AgentStateService
import com.histmersion.server.views.WorkListView
import com.histmersion.domain.library.acquisition.AcquireCheckService
import com.histmersion.util.data.checks.{CheckControllers, GuardedWrite}
import com.histmersion.domain.research.Slug
import com.histmersion.api.research.Article
import com.histmersion.domain.research.articles.BArticleRow
import com.histmersion.server.views.FormatView
import com.histmersion.server.instance.MaintenanceControllers
import com.histmersion.server.instance.SeedControllers
import com.histmersion.server.instance.InstanceControllers
import com.histmersion.server.views.AutopilotView
import com.histmersion.server.views.OpenWorkView
import com.histmersion.domain.library.acquisition.UpstreamQuotaService
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.domain.research.ResearchData
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator

/** The composition root's object graph over one server's `Deps`: every domain's wiring and controllers, the views that
  * join several of them (`server.views`) and this instance's own (`server.instance`). REST routes ([[HttpApi]]) and MCP
  * tools ([[McpTools]]) bind the same controllers; the CLI reaches them over HTTP. Nothing here implements an
  * operation.
  */
final class ServerWiring(deps: Deps, converter: ConverterClient):

  /** The ordinary construction: the OCR sidecar client comes from config. The two-argument form is the seam an HTTP
    * test uses to substitute a fake converter (plan §1.8).
    */
  def this(deps: Deps) = this(deps, ConverterWiring.client(deps.cfg.converter))

  private val ds = deps.ds

  /** Read at each use: a config reload switches it (Deps). */
  private def cfg = deps.cfg

  private val backups: BackupService = BackupWiring.service(deps)

  /** The registry's figures on this server's database. */
  private val diagnostics: DiagnosticsService = DiagnosticsService(Deps.data, deps.transactor)

  /** One lock per server (plan §1.10): held by a restore, checked by EVERY write — in every controller that writes,
    * which is why it lives on `Deps` rather than in this class. A restore drops and recreates every table; a write that
    * slipped past the lock wrote into a schema that was about to be replaced.
    */
  val maintenance: WriteGate = deps.maintenance

  /** What the library's workflows are given. */
  lazy val librarySite: LibrarySite = LibraryWorkflowSite(this, deps)

  /** What research's workflows are given. */
  lazy val researchSite: ResearchSite = ResearchWorkflowSite(this, deps)

  /** The work lists' kernel. */
  val queues: QueueService = deps.queues

  /** The first acquisition item (any status) whose text names `doi`. */
  def acquisitionItemNaming(doi: Doi): Option[Queue.QueueItem.Id] =
    queues.naming(Queue.Kind.Acquire, doi).headOption.map(_.id)

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
          media.covers.afterIngest(ck).map(_.value),
          EngineContributions.enrich(agents).autoEnrich(ck).map(_.value)
        ).flatten
      ).filter(_.nonEmpty).map(notes => IngestQueue.Note(notes.mkString(" · ")))
  )

  /** Named periods' write path and the first-run `init` job (#719, #720) — same arrangement. */
  val bootstrap: Bootstrap = Bootstrap(deps)

  /** The named periods' operations. */
  val periods: PeriodsControllers =
    PeriodsControllers(bootstrap.periods, bootstrap.periodImports, deps.maintenance)

  /** The terrain base map (docs/research-terrain-basemap.md): its extracts, their fetch job and the tiles the web page
    * draws — same arrangement.
    */
  val basemap: BasemapService = BasemapWiring.service(deps)

  /** The portable bundle — instance identity, export, verify, import (docs/research-merge-import.md). */
  val bundles: BundleControllers =
    import com.histmersion.util.data.exchange.{
      BundleControllers,
      BundleService,
      CarriedKinds,
      MergeApplyJobs,
      MergeRuleSet
    }
    val applyJobs = MergeApplyJobs(
      MergeApplyRuns(deps.ds),
      deps.cfg.merge.applyWaitSeconds.value,
      deps.pins,
      deps.maintenance.workers
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
          WorkListMergeFollowUps,
          applyJobs,
          () =>
            backups
              .create(Instant.now(deps.clock))
              .map(r => BundleImporter.BackupFile(r.file.value))
        )
      ),
      deps.maintenance
    )

  /** Embedded research agents (docs/research-embedded-agents.md) — off unless `agents.enabled`. Constructing it starts
    * nothing; `Main` starts its run pool after the OCR pool.
    */
  /** The work lists' operations (the pipelines' worklog lines). */
  def workLists: WorkListControllers = deps.workLists

  val agents: AgentsControllers =
    AgentsControllers(
      ServerEngineSite(deps, this)
    )

  /** Research › Activity: runs, jobs, worklog lines and a person's decisions, one list. */
  val activity: ActivityControllers =
    ActivityControllers(ActivityService(ds), () => Instant.now(deps.clock))

  def logWriter: LogWriter = deps.logWriter

  val log: LogControllers =
    LogControllers(LogService(deps.transactor, deps.logFiles), () => Instant.now(deps.clock))

  /** Pause, resume and cancel a job: a long background job cooperatively, an agent run by its own control. */
  lazy val jobControl: JobControlControllers =
    import com.histmersion.util.jobs.{JobAction, JobControlControllers, JobControlService}
    def run(id: Job.Id, a: JobAction)(using WritePermit)(using Creator.New): Either[Api.Error, Unit] =
      Run.Id
        .either(id.value)
        .left
        .map(_ => Api.Error(Problem.Unclassified(Legacy.Text(s"'${id.value}' is not an agent run"))))
        .flatMap(run =>
          (a match
            case JobAction.Pause  => agents.engine.pause(run)
            case JobAction.Resume => agents.engine.resume(run, None, None)
            case JobAction.Cancel => agents.engine.cancel(run)
          ).map(_ => ())
        )
    JobControlControllers(JobControlService(ds, deps.jobStops, (id, a) => run(id, a)), maintenance)

  /** Every domain's records as others point at them: what the person's marks show of what they point at. */
  lazy val referables: Referables =
    val db = deps.transactor
    Referables(
      List(
        SourceReferable(db),
        ArticleReferable(db),
        CatalogueReferable(db),
        PictureReferable(db),
        GoalReferable(db)
      )
    )

  /** A person's bookmarks, notes and read marks (the one local user's while the server runs for one person). */
  lazy val personal: PersonalControllers =
    import com.histmersion.domain.personal.PersonalControllers
    import com.histmersion.domain.personal.bookmarks.BookmarkService
    import com.histmersion.domain.personal.notes.NoteService
    import com.histmersion.domain.personal.reading.ReadingService
    val db = deps.transactor
    val readables = List(
      SourceReadable(db),
      ArticleReadable(db)
    )
    PersonalControllers(
      BookmarkService(db, referables),
      NoteService(db, referables),
      ReadingService(db, referables, readables),
      deps.maintenance,
      deps.clock
    )

  /** How far the person has seen each window's news (the toolbar's notices). */
  lazy val seenMarks: SeenService =
    SeenService(deps.transactor)

  /** Settings: the settings file read and changed (never the database), reloaded into this server; the restart for what
    * only a restart applies.
    */
  lazy val settings: SettingsControllers =
    import com.histmersion.util.settings.{RestartService, SettingsControllers, SettingsService}
    SettingsControllers(
      SettingsService(
        () => deps.loaded,
        () => StorageLocations.settingsFile(deps.loaded, deps.storage),
        deps.memory
      ),
      RestartService(deps.restart),
      () => deps.reloader(),
      () =>
        deps.plugins.report.plugins.flatMap(p =>
          (s"histmersion.plugins.${p.id}.enabled" :: p.settings.map(st => s"histmersion.plugins.${p.id}.${st.key}"))
            .map(SettingsService.ConfigPath(_))
        )
    )

  /** A filter's page, as the library reads it. */
  private def resolve(f: Filter): Filter = library.filters.page(f)

  /** The places a filter's `places` values name, as the library reads them. */
  private def scopeOf(f: Filter): PlaceScope = library.filters.scope(f)

  /** `GET /api/resolve/{id}`: a source of the library, else an article of research. */
  lazy val resolveView = ResolveView(
    id =>
      library.sources
        .get(id, body = SourcesControllers.GetBody(false))
        .left
        .map(Api.Error(_))
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
  def current(k: AliasKind, id: AliasTable.Id): AliasTable.Id = Deps.aliasTables.current(ds, k, id)

  /** The Markdown mirror over the registered kinds, each note rendered by its owner. */
  lazy val mirror = Mirror(
    Deps.data.mirror,
    List(
      SourceMirrorNote(library.sources),
      ArticleMirrorNote(research.articles)
    )
  )

  /** "Mentioned in articles" rides along with the default/all views rather than becoming a tool of its own: an LLM
    * asking about a source wants what the corpus has WRITTEN about it in the same answer, not after a second round
    * trip.
    */
  private def mentionedIn(citekey: Citekey): SourcesControllers.Markdown =
    SourcesControllers.Markdown(research.articles.mentionedIn(citekey))

  // ── events (plan §1.2) ──────────────────────────────────────────────────────────────────────

  /** The catalogue's events, their catalogues and links, and the refinement of site types. */
  val catalogue: CatalogueWiring =
    CatalogueWiring(
      deps,
      JavaHttpTransport(
        HttpTransport.TimeoutSeconds(cfg.events.timeoutSeconds.value)
      ),
      () => DeepTime.SignedYear(java.time.Year.now(deps.clock.withZone(java.time.ZoneOffset.UTC)).getValue.toLong),
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
      SourceTextFormatting(targets => research.articles.pageLinks(targets)),
      mentionedIn,
      storedOriginals
    )

  /** The files the server keeps of the sources' originals: the uploads and the acquisition spool. */
  lazy val storedOriginals: StoredOriginals =
    StoredOriginals(
      () => deps.storage.store(Location.IngestOriginals),
      () => deps.storage.store(Location.Spool)
    )

  /** The sources not in Calibre yet, a push's metadata, the Calibre id written back. */
  lazy val calibreGaps: CalibreGapControllers =
    CalibreGapControllers(
      library.sourceStore,
      storedOriginals,
      () => deps.cfg,
      deps.maintenance
    )

  /** The jobs and the runs as `GET /api/jobs` filters them. */
  lazy val jobList: JobListView = JobListView(jobs)

  /** The time this server's decisions read (the composition's clock). */
  def clock: java.time.Clock = deps.clock

  /** The background jobs as they are listed. */
  val jobs: JobListing = JobListing(ds, () => deps.cfg)

  /** The events' store. */
  def eventStore: EventService = catalogue.eventStore

  /** The event catalogues: the import, the re-derivation, the corrections by hand. */
  def eventImports: EventImportsControllers = catalogue.eventImports

  /** The links of events to the corpus, the leads, an event's card and an article's catalogue leads. */
  def eventLinks: EventLinksControllers = catalogue.eventLinks

  /** The refinement of the Wikidata sites' types. */
  def siteTypes: SiteTypesControllers = catalogue.siteTypes

  /** The events' operations; an event's card joins the articles citing its sources. */
  lazy val events: EventsControllers =
    EventsControllers(
      eventStore,
      EventCardView.cited(
        eventLinks.context,
        (citekeys, limit) => research.articles.store.citing(citekeys, limit)
      ),
      (k, id) =>
        Event.Id
          .either(current(k, AliasTable.Id(id.value)).value)
          .getOrElse(id),
      resolve,
      scopeOf,
      () =>
        (
          PageSize.applyUnsafe(math.max(1, cfg.ui.events.mapLimit.value)),
          GeoTolerance(cfg.ui.events.outlineToleranceDeg.value)
        ),
      maintenance
    )

  // ── attestations (plan §1.3) ──────────────────────────────────────────────────────────────

  /** What attests a data point: the attestations' operations. */
  lazy val attestations: AttestationsControllers =
    new AttestationsControllers(
      AttestationService(deps.transactor),
      deps.passages,
      (k, id) =>
        AttestationsControllers.Alias(
          current(k, AliasTable.Id(id.value)).value
        ),
      limit => cfg.limits.page(limit, PageSize.applyUnsafe(math.max(1, cfg.limits.search.value))),
      maintenance
    )

  // ── the gazetteer (plan §1.6a) ──────────────────────────────────────────────────────────────

  /** The gazetteer's operations. */
  def places: PlacesControllers = catalogue.places

  /** The gazetteer's background work: a plugin's source or the land fetched, the hierarchy built. */
  def gazetteer: GazetteerControllers = catalogue.gazetteer

  /** A place NAME → its gazetteer point (#981), for a finding about a place of kind `hint` dated `at`. `None` =
    * unknown.
    */
  def geocode(name: Geocode.Attempt.Name, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution] =
    places.geocode(name, hint, at)

  /** The map's sites, events and pictures as vector tiles (`GET /api/tiles/…`). */
  val tiles: MapTileService =
    MapTileService(
      deps.transactor,
      () => cfg.ui.mapTiles,
      () => DeepTime.SignedYear(cfg.places.modernFrom.value),
      f => scopeOf(f),
      UndatedSites(
        deps.transactor,
        () => UndatedBuild.MaxZoom(cfg.ui.mapTiles.undatedKeptZoom.value),
        run => maintenance.workers.detached(Quiescent.Name("undated-sites"))(run)
      ),
      eventStore,
      deps.eventSources,
      media.mapLayer,
      maintenance.permits
    )

  /** `GET /api/places/{id}` — the row, its GeoJSON geometry and the hierarchy around it at `asOf`; with `counts`, also
    * what the corpus holds for it and its pictures (the web UI's map hover card).
    */
  /** An event's page: the catalogue's event with its pictures. */
  lazy val eventCard: EventCardView =
    EventCardView(events, media.assets.eventThumbs)

  lazy val placeCard: PlaceCardView =
    PlaceCardView(places.get, places.placeId, placeCounts, media.assets.placeThumbs)

  /** What `place=<id>` alone selects in each list — the same scope (the place and its descendants) and the same queries
    * as `/api/sources`, `/api/findings`, `/api/articles` and `/api/events`.
    */
  private def placeCounts(id: domain.catalogue.Place.Id): Place.Counts =
    val f = Filter(places = List(Filter.Places(id.value)))
    library.search.selectionCounts(f)

  // ── the goals and the agents' state (plan §1.5) ────────────────────────────────────────────

  /** The goals' operations; an old slug answers as the goal it was renamed to (#1799). */
  lazy val goals: GoalsControllers =
    GoalsControllers(
      GoalService(deps.transactor),
      deps.queues,
      slug =>
        Goal.Slug
          .either(
            current(
              AliasKinds.GoalRecords,
              AliasTable.Id(slug.value)
            ).value
          )
          .getOrElse(slug),
      limit => cfg.limits.page(limit, PageSize.applyUnsafe(math.max(1, cfg.limits.search.value))),
      maintenance
    )

  /** The agents' scratch state. */
  lazy val agentState: AgentStateControllers =
    AgentStateControllers(
      AgentStateService(deps.transactor),
      maintenance
    )

  /** A work list as it is listed, with its items' goals and the ACQUIRE list's checks. */
  lazy val workListView: WorkListView =
    WorkListView(
      deps.workLists,
      deps.queues,
      GoalService(deps.transactor),
      AcquireCheckService(deps.transactor)
    )

  // ── checks, upkeep and this instance ─────────────────────────────────────────────────────────

  /** The registered checks, reported in `Lint.names`' order and fixed by their ranks. */
  lazy val checks: CheckControllers =
    import com.histmersion.util.data.checks.{CheckControllers, CheckOrder, CheckRunner}
    CheckControllers(
      CheckRunner(Deps.data.checks, CheckOrder(Lint.names), deps.transactor),
      () => deps.cfg,
      ds,
      isCitekey =>
        (text, field, article) =>
          MarkdownLint.issues(
            MarkdownLint.Scan.Text(text),
            field,
            MarkdownLint.Context(
              article = MarkdownLint.Context.Article(article),
              isCitekey = Some(isCitekey)
            )
          ),
      new GuardedWrite((record, text, token) =>
        given Creator.New = InTreeWorkflow.CheckFix.creator
        Slug
          .either(record.value)
          .exists(slug =>
            research.articles.writeRepairedBody(
              slug,
              Article.Body(text.value),
              token.map(t => BArticleRow.UpdatedAt(t.value))
            )
          )
      ),
      maintenance
    )(using deps.timer)

  /** Markdown that is not stored yet, checked as the field it is about to be. */
  lazy val formats: FormatView =
    FormatView(research.articles, library.sources)

  /** The backups, the restore, the vacuum and the reindex of this server. */
  lazy val upkeep: MaintenanceControllers =
    MaintenanceControllers(
      deps,
      backups,
      Map(CacheName("map-tiles") -> (() => tiles.cache.clear()))
    )

  /** An exported tree of notes on this server's disk read back. */
  lazy val seeding: SeedControllers =
    SeedControllers(deps, ingest.seed)

  /** This server: its version, health, About, the web page's settings and counters, the config reload. */
  lazy val instance: InstanceControllers =
    InstanceControllers(
      deps,
      () => ingest.queue.converterHealth,
      diagnostics,
      backups,
      seenMarks,
      basemap,
      () =>
        agents.lease.status.map((o, u, expired, mine) =>
          AgentLeaseInfo(
            AgentLeaseInfo.Owner(o.value),
            AgentLeaseInfo.Until(u.value),
            AgentLeaseInfo.Expired(expired),
            AgentLeaseInfo.ThisServer(mine)
          )
        ),
      upstreamQuotas,
      now => Some(EngineContributions.work(agents).acquireRecheck.status(now)),
      () => settings.setupDue
    )

  /** The autopilot's page: the automation's status with the library's upstreams and its acquisition list's re-check. */
  lazy val autopilot: AutopilotView =
    EngineContributions.autopilot(agents)

  lazy val researchState: OpenWorkView =
    OpenWorkView(agents, EngineContributions.work(agents).acquireRecheck)

  /** Every long-running worker of this server, as a restore holds it ([[WriteGate.workers]]): the OCR pool, the paused
    * background jobs' ways to go on, the agent run pool, the managers' loop and the lease's renewal. The work started
    * once (background jobs, a merge apply, a backup, the housekeeping, the refreshes) registers itself as it starts.
    */
  private def registerWorkers(): Unit =
    List(
      ingest.queue,
      deps.jobStops.quiescent,
      agents.engine,
      agents.managers.quiescent,
      agents.lease.quiescent,
      logWriter.quiescent
    ).foreach(maintenance.workers.register)

  /** The metered upstreams' budgets as the database keeps them (CORE's: `upstream_quota`), under today's limits; none
    * before CORE was first asked, and none when the database cannot be read (`about` says that elsewhere).
    */
  def upstreamQuotas(now: Instant = Instant.now(deps.clock)): List[UpstreamQuota] =
    scala.util
      .Try(
        UpstreamQuotaService(deps.transactor)
          .load(CoreBudget.Upstream)
      )
      .toOption
      .flatten
      .map(s =>
        CoreQuota
          .view(s, CoreBudget.limits(cfg.acquire.core), now)
      )
      .toList

  // ── research ─────────────────────────────────────────────────────────────────────────────────

  /** Research's articles: their services, controllers and page, and its review ledgers. */
  lazy val research: ResearchWiring =
    ResearchWiring(
      deps,
      library,
      (body, citekeys, period) => eventLinks.articleLeads(body, citekeys, period),
      slug =>
        mirror
          .note(
            ResearchData.ArticleNotes,
            MirrorPlacement.RecordId(slug.value)
          )
          .map(n => domain.research.Article.Note(n.value))
    )

  // last, once every worker above exists
  registerWorkers()
