package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given

/** One of a place's types, as its bundle line reads it. */
final private[places] case class BPlaceTypeRow(
    placeId: com.histmersion.domain.catalogue.Place.Id,
    typeLabel: BPlaceTypeRow.TypeLabel,
    pNull: BPlaceTypeRow.PNull,
    pLo: Option[BPlaceTypeRow.PLo],
    pHi: Option[BPlaceTypeRow.PHi],
    citekey: Option[com.histmersion.domain.library.Citekey]
) derives DbCodec

object BPlaceTypeRow:
  type TypeLabel = TypeLabel.T
  object TypeLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PNull = PNull.T
  object PNull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PLo = PLo.T
  object PLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PHi = PHi.T
  object PHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
