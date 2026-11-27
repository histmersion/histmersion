package com.histmersion
package domain.library.labels

import com.histmersion.util.database.given
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.library.search.FilterSql

import com.augustnagro.magnum.*
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The labels' vocabulary: the values of one dimension (tags, authors, places, topics, statuses, provenances) with the
  * sources each would leave inside a filter.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the label tables and their row codecs")
private[labels] object LabelRepo:
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[Facet] = DbCodec.derived

  /** Facet vocabulary of one dimension within the current filter; counts are DISTINCT sources. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def facets(
      field: Facet.Field,
      prefix: Option[FilterSql.FacetPrefix],
      filter: Filter,
      limit: PageSize,
      scope: PlaceScope = PlaceScope.empty
  )(using DbCon): Vector[Facet] =
    FilterSql.facetsQuery(field, prefix, filter, limit, scope).query[Facet].run()
