package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure

/** One of a place's names, as its bundle line reads it. */
final private[places] case class BPlaceNameRow(
    placeId: Place.Id,
    name: BPlaceNameRow.Name,
    lang: BPlaceNameRow.Lang,
    pNull: BPlaceNameRow.PNull,
    pLo: Option[BPlaceNameRow.PLo],
    pHi: Option[BPlaceNameRow.PHi]
) derives DbCodec

object BPlaceNameRow:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Lang = Lang.T
  object Lang extends Newtype[String, Pure]
  type PNull = PNull.T
  object PNull extends Newtype[Boolean, Pure]
  type PLo = PLo.T
  object PLo extends Newtype[Long, Pure]
  type PHi = PHi.T
  object PHi extends Newtype[Long, Pure]
