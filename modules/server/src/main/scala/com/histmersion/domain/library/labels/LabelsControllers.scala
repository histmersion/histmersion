package com.histmersion.domain.library.labels

import com.histmersion.api.*
import com.histmersion.domain.library.search.SearchFilters

/** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
final class LabelsControllers(labels: LabelService, filters: SearchFilters):
  def facets(field: String, prefix: Option[String], f: Filter): Either[ApiError, List[Facet]] =
    FacetField
      .parse(field)
      .toRight(ApiError(s"unknown facet field '$field' (one of: ${FacetField.all.mkString(", ")})"))
      .map { ff =>
        val paged = filters.page(f)
        labels.facets(ff, prefix, paged, paged.limit, filters.scope(f))
      }
