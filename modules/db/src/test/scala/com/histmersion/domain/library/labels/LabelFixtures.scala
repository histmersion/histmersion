package com.histmersion.domain.library.labels

import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.library.search.FilterSql

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.library.{Facet, Filter}

/** The db suites' reads of the labels' vocabulary on a database of their own. */
object LabelFixtures:
  def facets(
      ds: DataSource,
      field: Facet.Field,
      prefix: Option[FilterSql.FacetPrefix],
      filter: Filter,
      limit: com.histmersion.PageSize,
      scope: PlaceScope = PlaceScope.empty
  ): Vector[Facet] =
    connect(ds)(LabelRepo.facets(field, prefix, filter, limit, scope))
