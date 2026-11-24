package com.histmersion
package domain.library.findings

import com.histmersion.util.database.given
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.library.search.FilterSql

import com.augustnagro.magnum.*
import com.histmersion.api.library.{Filter, GeoHit}
import com.histmersion.lint.{LintRule, tolerated}

/** The map feed (`geo-when`): the findings a filter selects, and how many. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
private[findings] object FindingRepo:

  /** `GET /api/findings` — the MAP FEED, through the ONE filter contract (plan §1.0): the findings that THEMSELVES
    * satisfy the finding-level criteria (period overlap, viewport incl. the antimeridian split, place on `region_name`,
    * tags over `findings.tags ∪ sources.tags`) of the sources that satisfy the source-level ones (`q`, authors, status,
    * publication years, topics). `filter.limit` must already be resolved against the configured default (the handler
    * does that).
    */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def findingsFor(filter: Filter, scope: PlaceScope = PlaceScope.empty)(using DbCon): Vector[GeoHit] =
    (FilterSql.findingsFeedQuery(filter, scope).query[GeoFeedRow].run().map(_.toHit))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def findingsCount(filter: Filter, scope: PlaceScope = PlaceScope.empty)(using DbCon): RowCount =
    RowCount(FilterSql.findingsCountQuery(filter, scope).query[Int].run().headOption.getOrElse(0))
