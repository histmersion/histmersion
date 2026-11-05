package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

/** One of a place's names, as its bundle line reads it. */
final private[places] case class BPlaceNameRow(
    placeId: com.histmersion.domain.catalogue.Place.Id,
    name: BPlaceNameRow.Name,
    lang: BPlaceNameRow.Lang,
    pNull: BPlaceNameRow.PNull,
    pLo: Option[BPlaceNameRow.PLo],
    pHi: Option[BPlaceNameRow.PHi]
) derives DbCodec

object BPlaceNameRow:
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lang = Lang.T
  object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PNull = PNull.T
  object PNull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PLo = PLo.T
  object PLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PHi = PHi.T
  object PHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
