package com.histmersion.domain.library.labels

import com.histmersion.util.database.given
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.library.search.FilterSql

import com.augustnagro.magnum.*
import com.histmersion.api.library.{Facet, Filter}

/** The labels' vocabulary: the values of one dimension (tags, authors, places, topics, statuses, provenances) with the
  * sources each would leave inside a filter.
  */
private[labels] object LabelRepo:

  type FacetsPrefix = FacetsPrefix.T
  object FacetsPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsLimit = FacetsLimit.T
  object FacetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private given DbCodec[Facet] = DbCodec.derived

  /** Facet vocabulary of one dimension within the current filter; counts are DISTINCT sources. */
  def facets(
      field: Facet.Field,
      prefix: Option[com.histmersion.domain.library.labels.LabelRepo.FacetsPrefix],
      filter: Filter,
      limit: com.histmersion.domain.library.labels.LabelRepo.FacetsLimit,
      scope: PlaceScope = PlaceScope.empty
  )(using DbCon): Vector[Facet] =
    FilterSql.facetsQuery(field, prefix, filter, limit, scope).frag.query[Facet].run()
