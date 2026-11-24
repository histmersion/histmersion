package com.histmersion
package domain.library.search

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.events.EventSourceFilter
import com.augustnagro.magnum.Frag
import com.histmersion.lint.{LintRule, tolerated}

/** The events related to what a filter selects, by the search's own reading of the filter's sources. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class FilterEventSources extends EventSourceFilter:
  def eventWhere(filter: Filter, scope: PlaceScope): Frag = FilterSql.eventWhere(filter, scope)
