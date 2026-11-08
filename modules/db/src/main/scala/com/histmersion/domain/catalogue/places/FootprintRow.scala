package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final private[places] case class FootprintRow(
    id: FootprintRow.Id,
    placeId: com.histmersion.domain.catalogue.Place.Id,
    role: FootprintRow.Role,
    periodStart: Option[Year],
    periodEnd: Option[Year],
    certainty: Option[FootprintRow.Certainty],
    accuracyM: Option[FootprintRow.AccuracyM],
    citekey: Option[FootprintRow.Citekey]
) derives DbCodec:
  def toDto: Place.Footprint =
    Place.Footprint(
      Place.Footprint.Id(id.value),
      Place.Id(placeId.value),
      Place.Footprint.Role(role.value),
      for s <- periodStart; e <- periodEnd yield
        Period(com.histmersion.api.catalogue.Year(s.value.toLong), com.histmersion.api.catalogue.Year(e.value.toLong)),
      certainty.map(c => Place.Footprint.Certainty(c.value)),
      accuracyM.map(a => Place.Footprint.AccuracyM(a.value)),
      citekey.map(c => com.histmersion.api.library.Citekey(c.value))
    )

object FootprintRow:
  type Citekey = Citekey.T
  object Citekey extends RefinedType[String, Pure]
  type Id = Id.T
  object Id extends RefinedType[Int, Pure]
  type Role = Role.T
  object Role extends RefinedType[String, Pure]
  type Certainty = Certainty.T
  object Certainty extends RefinedType[String, Pure]
  type AccuracyM = AccuracyM.T
  object AccuracyM extends RefinedType[Float, Pure]
