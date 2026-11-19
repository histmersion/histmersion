package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final private[places] case class PlaceTypeRow(
    placeId: domain.catalogue.Place.Id,
    typeLabel: PlaceTypeRow.TypeLabel,
    periodStart: Option[Year],
    periodEnd: Option[Year],
    citekey: Option[Citekey]
) derives DbCodec:
  def toDto: Place.Type =
    Place.Type(
      Place.Id(placeId.value),
      Place.Type.TypeLabel(typeLabel.value),
      for s <- periodStart; e <- periodEnd
      yield Period(
        com.histmersion.api.catalogue.Year(s.value.toLong),
        com.histmersion.api.catalogue.Year(e.value.toLong)
      ),
      citekey.map(c => com.histmersion.api.library.Citekey(c.value))
    )

object PlaceTypeRow:
  type TypeLabel = TypeLabel.T
  object TypeLabel extends Newtype[String, Pure]
