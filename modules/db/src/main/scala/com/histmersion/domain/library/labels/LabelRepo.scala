package com.histmersion
package domain.library.labels

import com.histmersion.util.database.given
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.library.search.FilterSql

import com.augustnagro.magnum.*
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.util.database.NewtypeColumns.given

/** The labels' vocabulary: the values of one dimension (tags, authors, places, topics, statuses, provenances) with the
  * sources each would leave inside a filter.
  */
private[labels] object LabelRepo:
  private given DbCodec[Facet] = DbCodec.derived

  /** Facet vocabulary of one dimension within the current filter; counts are DISTINCT sources. */
  def facets(
      field: Facet.Field,
      prefix: Option[FilterSql.FacetPrefix],
      filter: Filter,
      limit: PageSize,
      scope: PlaceScope = PlaceScope.empty
  )(using DbCon): Vector[Facet] =
    FilterSql.facetsQuery(field, prefix, filter, limit, scope).query[Facet].run()
