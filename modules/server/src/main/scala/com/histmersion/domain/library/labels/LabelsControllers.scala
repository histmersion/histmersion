package com.histmersion
package domain.library.labels
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.domain.library.search.SearchFilters
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.api.Api
import com.histmersion.domain.library.search.FilterSql
import com.histmersion.util.problem.Problems

/** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
final class LabelsControllers(labels: LabelService, filters: SearchFilters):
  def facets(
      field: Facet.Field.Wire,
      prefix: Option[FilterSql.FacetPrefix],
      f: Filter
  ): Either[Problems, List[Facet]] =
    Facet.Field
      .parse(field)
      .toRight(
        Problem.Unclassified(Legacy.Text(s"unknown facet field '$field' (one of: ${Facet.Field.all.mkString(", ")})"))
      )
      .map { ff =>
        val paged = filters.page(f)
        labels.facets(ff, prefix, paged, PageSize.assume(paged.limit.value), filters.scope(f))
      }
