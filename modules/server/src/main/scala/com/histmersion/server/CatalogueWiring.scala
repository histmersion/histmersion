package com.histmersion.server

import com.histmersion.api.Filter
import com.histmersion.db.PlaceScope
import com.histmersion.domain.catalogue.CatalogueJobs
import com.histmersion.domain.catalogue.events.{
  CorrectionService,
  EventImportService,
  EventImports,
  EventImportsControllers,
  EventService
}
import com.histmersion.domain.catalogue.evidence.{
  AttestationService,
  EventLinkService,
  EventLinksControllers,
  LeadService
}
import com.histmersion.domain.catalogue.places.{SiteTypeService, SiteTypesControllers}
import com.histmersion.pluginhost.{HttpTransport, JavaHttpTransport}

/** The catalogue's events, their catalogues and their links to the corpus, and the refinement of site types, built over
  * one server's `deps`: `http` fetches the event catalogues and Wikidata, `presentYear` is the year datings are held
  * inside, `resolve` gives a list's filter its page size, `scope` the gazetteer's reading of its places.
  */
final class CatalogueWiring(
    deps: Deps,
    http: HttpTransport,
    presentYear: () => Long,
    resolve: Filter => Filter,
    scope: Filter => PlaceScope
):
  def this(deps: Deps, http: HttpTransport, presentYear: () => Long) =
    this(
      deps,
      http,
      presentYear,
      f => f.copy(limit = deps.cfg.limits.page(Some(f.limit), deps.cfg.limits.search), offset = f.offset.max(0)),
      _ => PlaceScope.empty
    )
  def this(deps: Deps, http: HttpTransport) =
    this(deps, http, () => java.time.Year.now(deps.clock.withZone(java.time.ZoneOffset.UTC)).getValue.toLong)
  def this(deps: Deps) = this(deps, JavaHttpTransport(deps.cfg.events.timeoutSeconds))

  /** One catalogue run at a time; an event catalogue's import may be paused and cancelled. */
  val jobs: CatalogueJobs =
    CatalogueJobs(deps.ds, deps.maintenance, deps.pins, deps.jobStops, Set(EventImportsControllers.ImportJobKind))

  lazy val eventStore: EventService = EventService(deps.transactor, deps.eventSources)

  lazy val eventImporter: EventImports =
    EventImports(
      EventImportService(deps.transactor),
      () => deps.plugins.registries.eventCatalogues,
      () => deps.cfg,
      http,
      presentYear,
      () => deps.maintenance.active
    )

  lazy val eventImports: EventImportsControllers =
    EventImportsControllers(
      eventImporter,
      CorrectionService(deps.transactor),
      eventStore,
      jobs,
      (limit, default) => deps.cfg.limits.page(limit, default)
    )

  lazy val eventLinks: EventLinksControllers =
    EventLinksControllers(
      EventLinkService(deps.transactor),
      LeadService(deps.transactor, deps.eventSources),
      eventStore,
      AttestationService(deps.transactor),
      deps.passages,
      jobs,
      eventImporter.infoOf,
      resolve,
      scope,
      () => deps.cfg,
      deps.maintenance
    )

  lazy val siteTypes: SiteTypesControllers =
    SiteTypesControllers(SiteTypeService(deps.transactor), jobs, () => deps.cfg, http, deps.clock)
