package com.histmersion.domain.library.search

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.Filter
import com.histmersion.util.database.Sql
import com.histmersion.domain.catalogue.events.EventSourceFilter

/** The events related to what a filter selects, by the search's own reading of the filter's sources. */
final class FilterEventSources extends EventSourceFilter:
  def eventWhere(filter: Filter, scope: PlaceScope): Sql = FilterSql.eventWhere(filter, scope)
