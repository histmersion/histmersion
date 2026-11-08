package com.histmersion
package domain.library.labels

import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Transactor
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.domain.library.search.FilterSql

/** The labels' vocabulary inside a filter: the values of one dimension (tags, authors, places, topics, statuses,
  * provenances), each with the number of distinct sources it would leave. A mechanism over the filter's SQL, so it has
  * no in-memory twin.
  */
trait LabelService:
  def facets(field: Facet.Field, prefix: Option[FilterSql.FacetPrefix], filter: Filter, limit: PageSize, scope: PlaceScope): List[Facet]

object LabelService:
  def apply(db: Transactor): LabelService = Implementation(db)

  final private class Implementation(db: Transactor) extends LabelService:
    def facets(field: Facet.Field, prefix: Option[FilterSql.FacetPrefix], filter: Filter, limit: PageSize, scope: PlaceScope): List[Facet] =
      db.read(LabelRepo.facets(field, prefix, filter, limit, scope)).toList

