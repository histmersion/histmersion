package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place

final private[places] case class FootprintRow(
    id: FootprintRow.Id,
    placeId: com.histmersion.domain.catalogue.Place.Id,
    role: FootprintRow.Role,
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    certainty: Option[FootprintRow.Certainty],
    accuracyM: Option[FootprintRow.AccuracyM],
    citekey: Option[com.histmersion.domain.library.Citekey]
) derives DbCodec:
  def toDto: Place.Footprint =
    Place.Footprint(
      id,
      com.histmersion.api.catalogue.Place.Id(placeId),
      role,
      for s <- periodStart; e <- periodEnd yield Period(s, e),
      certainty,
      accuracyM,
      citekey
    )

object FootprintRow:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Certainty = Certainty.T
  object Certainty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AccuracyM = AccuracyM.T
  object AccuracyM extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Float, _root_.io.github.iltotore.iron.constraint.any.Pure]
