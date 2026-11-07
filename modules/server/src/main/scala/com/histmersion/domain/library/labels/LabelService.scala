package com.histmersion.domain.library.labels

import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Transactor
import com.histmersion.api.library.{Facet, Filter}

/** The labels' vocabulary inside a filter: the values of one dimension (tags, authors, places, topics, statuses,
  * provenances), each with the number of distinct sources it would leave. A mechanism over the filter's SQL, so it has
  * no in-memory twin.
  */
trait LabelService:
  def facets(field: Facet.Field, prefix: Option[com.histmersion.domain.library.search.FilterSql.FacetPrefix], filter: Filter, limit: com.histmersion.domain.library.labels.LabelService.Limit, scope: PlaceScope): List[Facet]

object LabelService:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): LabelService = Implementation(db)

  final private class Implementation(db: Transactor) extends LabelService:
    def facets(field: Facet.Field, prefix: Option[com.histmersion.domain.library.search.FilterSql.FacetPrefix], filter: Filter, limit: com.histmersion.domain.library.labels.LabelService.Limit, scope: PlaceScope): List[Facet] =
      db.read(LabelRepo.facets(field, prefix, filter, limit, scope)).toList

  object Implementation:
