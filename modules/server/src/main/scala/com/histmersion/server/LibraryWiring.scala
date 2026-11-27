package com.histmersion
package server

import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.PlaceService
import com.histmersion.domain.library.citations.{CitationsControllers, CitationService}
import com.histmersion.domain.library.findings.{FindingsControllers, FindingService}
import com.histmersion.domain.library.labels.{LabelsControllers, LabelService}
import com.histmersion.domain.library.search.{
  QueryControllers,
  QueryService,
  SearchControllers,
  SearchFilters,
  SearchService
}
import com.histmersion.domain.library.sources.{
  SourceFormatting,
  SourceOriginals,
  SourcesControllers,
  SourceService,
  SourceSettings
}
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.media.assets.AssetsControllers
import com.histmersion.util.identity.AliasTable

/** The library's services and controllers, built over one server's `deps`: the figures are media's, the gazetteer the
  * catalogue's, the formatting check and the "Mentioned in articles" part research's, the stored originals the
  * ingest's; what every domain keeps of a source follows a rename or a delete through `Deps.sourceReferrers`.
  */
final class LibraryWiring(
    deps: Deps,
    figures: AssetsControllers,
    places: PlaceService,
    formatting: SourceFormatting,
    mentions: Citekey => SourcesControllers.Markdown,
    originals: SourceOriginals
):
  private val cfg: () => AppConfig = () => deps.cfg

  /** The sources, their findings and edges, as the library stores them. */
  val sourceStore: SourceService =
    SourceService(deps.transactor, Deps.sourceReferrers, () => LibraryWiring.settings(deps.cfg), deps.clock)

  /** How a filter's page and places are read. */
  val filters: SearchFilters = SearchFilters(places, cfg)

  val sources: SourcesControllers = new SourcesControllers(
    sourceStore,
    figures,
    formatting,
    originals,
    mentions,
    (k, id) => Citekey.either(Deps.aliasTables.current(deps.ds, k, AliasTable.Id(id.value)).value).getOrElse(id),
    cfg,
    deps.maintenance,
    deps.clock
  )(using deps.timer)

  val citations: CitationsControllers =
    CitationsControllers(CitationService(deps.transactor), sourceStore, deps.maintenance)

  val findings: FindingsControllers = FindingsControllers(FindingService(deps.transactor), filters, cfg)

  val labels: LabelsControllers = LabelsControllers(LabelService(deps.transactor), filters)

  val searchStore: SearchService = SearchService(deps.transactor)

  val search: SearchControllers = SearchControllers(searchStore, filters, places, cfg)

  val query: QueryControllers = QueryControllers(QueryService(deps.queryDs, deps.ds)(using deps.timer), cfg)

object LibraryWiring:

  /** What a source's write follows, read from the settings at each write (a reload changes them). */

  def settings(cfg: AppConfig): SourceSettings = SourceSettings(
    SourceSettings.ChunkChars(cfg.limits.chunkChars.value),
    SourceSettings.ToleranceKm(cfg.places.pointToleranceKm.value)
  )
