package com.histmersion
package domain.catalogue.events

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.library.Filter
import com.augustnagro.magnum.Frag
import com.histmersion.lint.{tolerated, LintRule}

/** How an event relates to what a filter selects, as the library answers it — the WHERE over `events e`: its own
  * source, or a source attesting it, among the sources the filter selects; its outer span in the filter's period; its
  * label containing the filter's title. `TRUE` when the filter sets none of these.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait EventSourceFilter:
  def eventWhere(filter: Filter, scope: PlaceScope): Frag
