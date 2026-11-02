package com.histmersion.domain.library.labels

import com.histmersion.domain.catalogue.places.PlaceScope

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.library.{Facet, FacetField, Filter}

/** The db suites' reads of the labels' vocabulary on a database of their own. */
object LabelFixtures:
  def facets(
      ds: DataSource,
      field: FacetField,
      prefix: Option[String],
      filter: Filter,
      limit: Int,
      scope: PlaceScope = PlaceScope.empty
  ): Vector[Facet] =
    connect(ds)(LabelRepo.facets(field, prefix, filter, limit, scope))
