package com.histmersion
package domain.library.search

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Filter
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.{PlaceScope, PlaceService}

import hearth.kindlings.optics.syntax.*

/** How the library reads a filter: its page, and the places its `places` values name. */
final class SearchFilters(places: PlaceService, cfg: () => AppConfig):

  /** `limit <= 0` means "use the configured page size" — no literal page size exists in the code — and any limit is
    * CAPPED at `limits.max-page`. An uncapped `limit` is a denial of service by query parameter: `?limit=100000000`
    * used to build every row of it.
    */
  def page(f: Filter): Filter =
    val c = cfg()
    f.modify(_.limit)
      .setTo(
        Filter.Limit(
          c.limits.page(PageSize.option(f.limit.value), PageSize.applyUnsafe(math.max(1, c.limits.search.value))).value
        )
      )
      .modify(_.offset)
      .setTo(Filter.Offset(f.offset.value.max(0)))

  /** How the gazetteer reads this filter's `places` values (plan §1.6a): the ids among them expand to themselves plus
    * their descendants as of `f.period`; the rest fall back to the name ILIKE. Resolved ONCE per request and threaded
    * into every query, so the sidebar, the map, the facets and the histogram all agree. No `places` filter ⇒ no query
    * at all.
    */
  def scope(f: Filter): PlaceScope =
    if f.places.isEmpty then PlaceScope.empty
    else places.scopeFor(f.places.map(p => PlaceService.ScopeForValues(p.value)), f.period)
