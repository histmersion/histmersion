package com.histmersion
package domain.library.labels

import com.histmersion.api.Problem
import com.histmersion.domain.library.search.SearchFilters
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.api.Api
import com.histmersion.domain.library.search.FilterSql

/** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
final class LabelsControllers(labels: LabelService, filters: SearchFilters):
  def facets(
      field: Facet.Field.Wire,
      prefix: Option[FilterSql.FacetPrefix],
      f: Filter
  ): Either[Api.Error, List[Facet]] =
    Facet.Field
      .parse(field)
      .toRight(
        Api.Error(Problem(s"unknown facet field '$field' (one of: ${Facet.Field.all.mkString(", ")})"))
      )
      .map { ff =>
        val paged = filters.page(f)
        labels.facets(ff, prefix, paged, PageSize.assume(paged.limit.value), filters.scope(f))
      }
