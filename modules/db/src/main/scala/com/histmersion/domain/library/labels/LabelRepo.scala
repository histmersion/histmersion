package com.histmersion.domain.library.labels

import com.histmersion.util.database.given
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.library.search.FilterSql

import com.augustnagro.magnum.*
import com.histmersion.api.library.{Facet, FacetField, Filter}

/** The labels' vocabulary: the values of one dimension (tags, authors, places, topics, statuses, provenances) with the
  * sources each would leave inside a filter.
  */
private[labels] object LabelRepo:

  private given DbCodec[Facet] = DbCodec.derived

  /** Facet vocabulary of one dimension within the current filter; counts are DISTINCT sources. */
  def facets(
      field: FacetField,
      prefix: Option[String],
      filter: Filter,
      limit: Int,
      scope: PlaceScope = PlaceScope.empty
  )(using DbCon): Vector[Facet] =
    FilterSql.facetsQuery(field, prefix, filter, limit, scope).frag.query[Facet].run()
