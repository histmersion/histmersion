package com.histmersion.server

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.util.data.diagnostics.DiagnosticsService
import com.histmersion.util.data.backup.BackupService
import com.histmersion.util.data.maintenance.{CacheName, WriteGate}

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.api.aliases.AliasKind
import com.histmersion.util.data.mirror.Mirror

import com.histmersion.domain.*
import java.time.Instant
import com.histmersion.api.automation.AgentLeaseInfo
import com.histmersion.api.catalogue.{Geocode, Period}
import com.histmersion.api.checks.Lint
import com.histmersion.api.library.{Filter, ResolvedSource, UpstreamQuota}
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Place

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
  lazy val librarySite: com.histmersion.domain.library.workflows.LibrarySite = LibraryWorkflowSite(this, deps)

  /** What research's workflows are given. */
  lazy val researchSite: com.histmersion.domain.research.workflows.ResearchSite = ResearchWorkflowSite(this, deps)

  /** The work lists' kernel. */
  val queues: com.histmersion.domain.automation.worklists.QueueService = deps.queues

  /** The first acquisition item (any status) whose text names `doi`. */
  def acquisitionItemNaming(doi: String): Option[Int] =
    queues.naming(com.histmersion.api.automation.Queue.Kind.Acquire, doi).headOption.map(_.id)

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
          com.histmersion.server.agents.EngineContributions.enrich(agents).autoEnrich(ck)
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

  val agents: com.histmersion.domain.automation.agents.AgentsControllers =
    com.histmersion.domain.automation.agents.AgentsControllers(
      com.histmersion.server.agents.ServerEngineSite(deps, this)
    )

  /** Research › Activity: runs, jobs, worklog lines and a person's decisions, one list. */
  val activity: com.histmersion.domain.activity.ActivityControllers =
    com.histmersion.domain.activity
      .ActivityControllers(com.histmersion.domain.activity.ActivityService(ds), () => Instant.now())

  /** Pause, resume and cancel a job: a long background job cooperatively, an agent run by its own control. */
  lazy val jobControl: com.histmersion.util.jobs.JobControlControllers =
    import com.histmersion.util.jobs.{JobAction, JobControlControllers, JobControlService}
    def run(id: String, a: JobAction): Either[Api.Error, Unit] = (a match
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
      () => deps.reloader(),
      () =>
        deps.plugins.report.plugins.flatMap(p =>
          s"histmersion.plugins.${p.id}.enabled" :: p.settings.map(st => s"histmersion.plugins.${p.id}.${st.key}")
        )
    )

  /** A filter's page, as the library reads it. */
  private def resolve(f: Filter): Filter = library.filters.page(f)

  /** The places a filter's `places` values name, as the library reads them. */
  private def scopeOf(f: Filter): PlaceScope = library.filters.scope(f)

  /** `GET /api/resolve/{id}`: a source of the library, else an article of research. */
  lazy val resolveView = com.histmersion.server.views.ResolveView(
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

  /** The Markdown mirror over the registered kinds, each note rendered by its owner. */
  lazy val mirror = Mirror(
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

  /** The jobs and the runs as `GET /api/jobs` filters them. */
  lazy val jobList: com.histmersion.server.views.JobListView = com.histmersion.server.views.JobListView(jobs)

  /** The time this server's decisions read (the composition's clock). */
  def clock: java.time.Clock = deps.clock

  /** The background jobs as they are listed. */
  val jobs: com.histmersion.util.jobs.JobListing = com.histmersion.util.jobs.JobListing(ds, () => deps.cfg)

  /** The events' store. */
  def eventStore: com.histmersion.domain.catalogue.events.EventService = catalogue.eventStore

  /** The event catalogues: the import, the re-derivation, the corrections by hand. */
  def eventImports: com.histmersion.domain.catalogue.events.EventImportsControllers = catalogue.eventImports

  /** The links of events to the corpus, the leads, an event's card and an article's catalogue leads. */
  def eventLinks: com.histmersion.domain.catalogue.evidence.EventLinksControllers = catalogue.eventLinks

  /** The refinement of the Wikidata sites' types. */
  def siteTypes: com.histmersion.domain.catalogue.places.SiteTypesControllers = catalogue.siteTypes

  /** The events' operations; an event's card joins the articles citing its sources. */
  lazy val events: com.histmersion.domain.catalogue.events.EventsControllers =
    com.histmersion.domain.catalogue.events.EventsControllers(
      eventStore,
      com.histmersion.server.views.EventCardView.cited(
        eventLinks.context,
        (citekeys, limit) => research.articles.store.citing(citekeys, limit)
      ),
      current,
      resolve,
      scopeOf,
      () => (cfg.ui.events.mapLimit, cfg.ui.events.outlineToleranceDeg),
      maintenance
    )

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

  /** `GET /api/places/{id}` — the row, its GeoJSON geometry and the hierarchy around it at `asOf`; with `counts`, also
    * what the corpus holds for it and its pictures (the web UI's map hover card).
    */
  /** An event's page: the catalogue's event with its pictures. */
  lazy val eventCard: com.histmersion.server.views.EventCardView =
    com.histmersion.server.views.EventCardView(events, media.assets.eventThumbs)

  lazy val placeCard: com.histmersion.server.views.PlaceCardView =
    com.histmersion.server.views.PlaceCardView(places.get, places.placeId, placeCounts, media.assets.placeThumbs)

  /** What `place=<id>` alone selects in each list — the same scope (the place and its descendants) and the same queries
    * as `/api/sources`, `/api/findings`, `/api/articles` and `/api/events`.
    */
  private def placeCounts(id: String): Place.Counts =
    val f = Filter(places = List(id))
    library.search.selectionCounts(f)

  // ── the goals and the agents' state (plan §1.5) ────────────────────────────────────────────

  /** The goals' operations; an old slug answers as the goal it was renamed to (#1799). */
  lazy val goals: com.histmersion.domain.automation.goals.GoalsControllers =
    com.histmersion.domain.automation.goals.GoalsControllers(
      com.histmersion.domain.automation.goals.GoalService(deps.transactor),
      deps.queues,
      current(com.histmersion.api.aliases.AliasKinds.GoalRecords, _),
      limit => cfg.limits.page(limit, cfg.limits.search),
      maintenance
    )

  /** The agents' scratch state. */
  lazy val agentState: com.histmersion.domain.automation.agents.AgentStateControllers =
    com.histmersion.domain.automation.agents.AgentStateControllers(
      com.histmersion.domain.automation.agents.AgentStateService(deps.transactor),
      maintenance
    )

  /** A work list as it is listed, with its items' goals and the ACQUIRE list's checks. */
  lazy val workListView: com.histmersion.server.views.WorkListView =
    com.histmersion.server.views.WorkListView(
      deps.workLists,
      deps.queues,
      com.histmersion.domain.automation.goals.GoalService(deps.transactor),
      com.histmersion.domain.library.acquisition.AcquireCheckService(deps.transactor)
    )

  // ── checks, upkeep and this instance ─────────────────────────────────────────────────────────

  /** The registered checks, reported in `Lint.names`' order and fixed by their ranks. */
  lazy val checks: com.histmersion.util.data.checks.CheckControllers =
    import com.histmersion.util.data.checks.{CheckControllers, CheckOrder, CheckRunner}
    CheckControllers(
      CheckRunner(Deps.data.checks, CheckOrder(Lint.names), deps.transactor),
      () => deps.cfg,
      ds,
      isCitekey =>
        (text, field, article) =>
          MarkdownLint.issues(text, field, MarkdownLint.Context(article = article, isCitekey = Some(isCitekey))),
      (slug, body, token) => research.articles.writeRepairedBody(slug, body, token),
      maintenance
    )(using deps.timer)

  /** Markdown that is not stored yet, checked as the field it is about to be. */
  lazy val formats: com.histmersion.server.views.FormatView =
    com.histmersion.server.views.FormatView(research.articles, library.sources)

  /** The backups, the restore, the vacuum and the reindex of this server. */
  lazy val upkeep: com.histmersion.server.instance.MaintenanceControllers =
    com.histmersion.server.instance.MaintenanceControllers(
      deps,
      backups,
      () => ingest.queue.pause(),
      () => ingest.queue.resume(),
      Map(CacheName("map-tiles") -> (() => tiles.cache.clear()))
    )

  /** An exported tree of notes on this server's disk read back. */
  lazy val seeding: com.histmersion.server.instance.SeedControllers =
    com.histmersion.server.instance.SeedControllers(deps, ingest.seed)

  /** This server: its version, health, About, the web page's settings and counters, the config reload. */
  lazy val instance: com.histmersion.server.instance.InstanceControllers =
    com.histmersion.server.instance.InstanceControllers(
      deps,
      () => ingest.queue.converterHealth,
      diagnostics,
      backups,
      seenMarks,
      basemap,
      () => agents.lease.status.map((o, u, expired, mine) => AgentLeaseInfo(o, u, expired, mine)),
      upstreamQuotas,
      now => Some(com.histmersion.server.agents.EngineContributions.work(agents).acquireRecheck.status(now)),
      () => settings.setupDue
    )

  /** The autopilot's page: the automation's status with the library's upstreams and its acquisition list's re-check. */
  lazy val autopilot: com.histmersion.server.views.AutopilotView =
    com.histmersion.server.agents.EngineContributions.autopilot(agents)

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

  // ── research ─────────────────────────────────────────────────────────────────────────────────

  /** Research's articles: their services, controllers and page, and its review ledgers. */
  lazy val research: ResearchWiring =
    ResearchWiring(
      deps,
      library,
      (body, citekeys, period) => eventLinks.articleLeads(body, citekeys, period),
      slug => mirror.note(com.histmersion.domain.research.ResearchData.ArticleNotes, slug)
    )
