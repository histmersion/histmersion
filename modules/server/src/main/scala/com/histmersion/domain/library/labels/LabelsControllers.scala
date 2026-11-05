package com.histmersion.domain.library.labels

import com.histmersion.domain.library.search.SearchFilters
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.api.Api

/** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
final class LabelsControllers(labels: LabelService, filters: SearchFilters):
  def facets(field: com.histmersion.domain.library.labels.LabelsControllers.FacetsField, prefix: Option[com.histmersion.domain.library.labels.LabelsControllers.FacetsPrefix], f: Filter): Either[Api.Error, List[Facet]] =
    Facet.Field
      .parse(field)
      .toRight(Api.Error(s"unknown facet field '$field' (one of: ${Facet.Field.all.mkString(", ")})"))
      .map { ff =>
        val paged = filters.page(f)
        labels.facets(ff, prefix, paged, paged.limit, filters.scope(f))
      }

object LabelsControllers:
  type FacetsField = FacetsField.T
  object FacetsField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetsPrefix = FacetsPrefix.T
  object FacetsPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
