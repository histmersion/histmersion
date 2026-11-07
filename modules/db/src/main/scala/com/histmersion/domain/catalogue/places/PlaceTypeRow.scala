package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place

final private[places] case class PlaceTypeRow(
    placeId: com.histmersion.domain.catalogue.Place.Id,
    typeLabel: PlaceTypeRow.TypeLabel,
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    citekey: Option[com.histmersion.domain.library.Citekey]
) derives DbCodec:
  def toDto: Place.Type =
    Place.Type(
      com.histmersion.api.catalogue.Place.Id(placeId.value),
      Place.Type.TypeLabel(typeLabel.value),
      for s <- periodStart; e <- periodEnd yield
        Period(com.histmersion.api.catalogue.Year(s.value.toLong), com.histmersion.api.catalogue.Year(e.value.toLong)),
      citekey.map(c => com.histmersion.api.library.Citekey(c.value))
    )

object PlaceTypeRow:
  type TypeLabel = TypeLabel.T
  object TypeLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
