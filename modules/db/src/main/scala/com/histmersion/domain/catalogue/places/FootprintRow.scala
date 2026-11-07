package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.util.database.NewtypeColumns.given

final private[places] case class FootprintRow(
    id: FootprintRow.Id,
    placeId: com.histmersion.domain.catalogue.Place.Id,
    role: FootprintRow.Role,
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    certainty: Option[FootprintRow.Certainty],
    accuracyM: Option[FootprintRow.AccuracyM],
    citekey: Option[FootprintRow.Citekey]
) derives DbCodec:
  def toDto: Place.Footprint =
    Place.Footprint(
      Place.Footprint.Id(id.value),
      com.histmersion.api.catalogue.Place.Id(placeId.value),
      Place.Footprint.Role(role.value),
      for s <- periodStart; e <- periodEnd yield
        Period(com.histmersion.api.catalogue.Year(s.value.toLong), com.histmersion.api.catalogue.Year(e.value.toLong)),
      certainty.map(c => Place.Footprint.Certainty(c.value)),
      accuracyM.map(a => Place.Footprint.AccuracyM(a.value)),
      citekey.map(c => com.histmersion.api.library.Citekey(c.value))
    )

object FootprintRow:
  type Citekey = Citekey.T
  object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Certainty = Certainty.T
  object Certainty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AccuracyM = AccuracyM.T
  object AccuracyM extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Float, _root_.io.github.iltotore.iron.constraint.any.Pure]
