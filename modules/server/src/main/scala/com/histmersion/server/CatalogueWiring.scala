package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.catalogue.CatalogueJobs
import com.histmersion.domain.catalogue.events.{
  CorrectionService,
  EventImports,
  EventImportsControllers,
  EventImportService,
  EventService
}
import com.histmersion.domain.catalogue.evidence.{
  AttestationService,
  EventLinksControllers,
  EventLinkService,
  LeadService
}
import com.histmersion.domain.catalogue.places.{
  GazetteerControllers,
  GazetteerImports,
  GazetteerLoad,
  Geocoding,
  PlaceGeometry,
  PlaceMap,
  PlacesControllers,
  PlaceService,
  SiteTypesControllers,
  SiteTypeService
}
import com.histmersion.pluginhost.{HttpTransport, JavaHttpTransport, PluginGazetteers}
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.Place
import com.histmersion.util.identity.AliasTable
import com.histmersion.util.storage.Location
import com.histmersion.PageSize
import com.histmersion.api.catalogue.CatalogRecord

/** The catalogue's places, events, their catalogues and their links to the corpus, and the refinement of site types,
  * built over one server's `deps`: `http` fetches the event catalogues and Wikidata, `presentYear` is the year datings
  * are held inside, `resolve` gives a list's filter its page size, `scope` the gazetteer's reading of its places.
  */
final class CatalogueWiring(
    deps: Deps,
    http: HttpTransport,
    presentYear: () => DeepTime.SignedYear,
    resolve: Filter => Filter,
    scope: Filter => PlaceScope
):
  def this(deps: Deps, http: HttpTransport, presentYear: () => DeepTime.SignedYear) =
    this(
      deps,
      http,
      presentYear,
      f =>
        f.modify(_.limit)
          .setTo(
            Filter.Limit(
              deps.cfg.limits
                .page(PageSize.option(f.limit.value), PageSize.applyUnsafe(math.max(1, deps.cfg.limits.search.value)))
                .value
            )
          )
          .modify(_.offset)
          .setTo(Filter.Offset(f.offset.value.max(0))),
      _ => PlaceScope.empty
    )
  def this(deps: Deps, http: HttpTransport) =
    this(
      deps,
      http,
      () => DeepTime.SignedYear(java.time.Year.now(deps.clock.withZone(java.time.ZoneOffset.UTC)).getValue.toLong)
    )
  def this(deps: Deps) = this(deps, JavaHttpTransport(HttpTransport.TimeoutSeconds(deps.cfg.events.timeoutSeconds.value)))

  /** One catalogue run at a time; an event catalogue's import may be paused and cancelled. */
  val jobs: CatalogueJobs =
    CatalogueJobs(deps.ds, deps.maintenance, deps.pins, deps.jobStops, Set(EventImportsControllers.ImportJobKind))

  lazy val eventStore: EventService = EventService(deps.transactor, deps.eventSources)

  lazy val eventImporter: EventImports =
    EventImports(
      EventImportService(deps.transactor),
      () =>
        deps.plugins.registries.eventCatalogues.map((k, c) => CatalogRecord.Provider(k.value) -> c),
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

  /** The gazetteer's records. */
  lazy val placeStore: PlaceService = PlaceService(deps.transactor)

  /** The gazetteer's geometry: findings matched to places, the hierarchy, the land. */
  lazy val placeGeometry: PlaceGeometry = PlaceGeometry(deps.transactor)(using deps.timer)

  lazy val places: PlacesControllers =
    new PlacesControllers(
      placeStore,
      PlaceMap(deps.transactor),
      placeGeometry,
      Geocoding(deps.transactor),
      (k, id) =>
        Place.Id
          .either(Deps.aliasTables.current(deps.ds, k, AliasTable.Id(id.value)).value)
          .getOrElse(Place.Id.assume(id.value)),
      () => deps.cfg,
      (limit, default) => deps.cfg.limits.page(limit, default),
      deps.maintenance
    )

  lazy val gazetteer: GazetteerControllers =
    GazetteerControllers(
      CatalogueWiring.gazetteer(deps, placeStore, HttpDownload().file, HttpDownload().text),
      placeGeometry,
      deps.ds,
      deps.pins,
      deps.maintenance
    )

object CatalogueWiring:
  /** How the gazetteer comes in, over `deps`' stores and plugins: `download` fetches a pinned extract into its file,
    * `text` a url's text (the land).
    */
  def gazetteer(
      deps: Deps,
      places: PlaceService,
      download: GazetteerLoad.Download,
      text: HttpDownload.Url => Either[HttpDownload.Problem, HttpDownload.Text]
  ): GazetteerImports =
    GazetteerImports(
      deps.transactor,
      places,
      PlaceGeometry(deps.transactor)(using deps.timer),
      () =>
        val paths = GazetteerPaths.of(deps.cfg, deps.environment.lookup, deps.userPaths.osName)
        GazetteerLoad.Stores(
          deps.storage.store(Location.GazetteerExtracts),
          deps.storage.find(Location.GazetteerFolder),
          deps.storage.store(Location.GazetteerDrop),
          GazetteerLoad.Stores.ManifestAt(paths.manifest.toString),
          () => paths.manifestText()
        )
      ,
      () => deps.cfg,
      download,
      text,
      () => deps.plugins.registries.gazetteerSources,
      () => PluginGazetteers.http(PluginGazetteers.transport(deps.cfg.places))
    )
