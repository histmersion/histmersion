package com.histmersion
package domain.library.findings

import com.histmersion.util.database.given
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.library.search.FilterSql

import com.augustnagro.magnum.*
import com.histmersion.api.library.{Filter, GeoHit}

/** The map feed (`geo-when`): the findings a filter selects, and how many. */
private[findings] object FindingRepo:

  /** `GET /api/findings` — the MAP FEED, through the ONE filter contract (plan §1.0): the findings that THEMSELVES
    * satisfy the finding-level criteria (period overlap, viewport incl. the antimeridian split, place on `region_name`,
    * tags over `findings.tags ∪ sources.tags`) of the sources that satisfy the source-level ones (`q`, authors, status,
    * publication years, topics). `filter.limit` must already be resolved against the configured default (the handler
    * does that).
    */

  def findingsFor(filter: Filter, scope: PlaceScope = PlaceScope.empty)(using DbCon): Vector[GeoHit] =
    (FilterSql.findingsFeedQuery(filter, scope).query[GeoFeedRow].run().map(_.toHit))

  def findingsCount(filter: Filter, scope: PlaceScope = PlaceScope.empty)(using DbCon): RowCount =
    RowCount(FilterSql.findingsCountQuery(filter, scope).query[Int].run().headOption.getOrElse(0))
