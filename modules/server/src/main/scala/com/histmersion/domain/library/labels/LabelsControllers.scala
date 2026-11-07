package com.histmersion.domain.library.labels

import com.histmersion.domain.library.search.SearchFilters
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.api.Api

/** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
final class LabelsControllers(labels: LabelService, filters: SearchFilters):
  def facets(field: Facet.Field.Wire, prefix: Option[com.histmersion.domain.library.search.FilterSql.FacetPrefix], f: Filter): Either[Api.Error, List[Facet]] =
    Facet.Field
      .parse(field)
      .toRight(Api.Error(Api.Error.Message(s"unknown facet field '$field' (one of: ${Facet.Field.all.mkString(", ")})")))
      .map { ff =>
        val paged = filters.page(f)
        labels.facets(ff, prefix, paged, com.histmersion.PageSize(paged.limit.value), filters.scope(f))
      }
