package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One of a place's types, as its bundle line reads it. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final private[places] case class BPlaceTypeRow(
    placeId: Place.Id,
    typeLabel: BPlaceTypeRow.TypeLabel,
    pNull: BPlaceTypeRow.PNull,
    pLo: Option[BPlaceTypeRow.PLo],
    pHi: Option[BPlaceTypeRow.PHi],
    citekey: Option[Citekey]
) derives DbCodec

object BPlaceTypeRow:
  type TypeLabel = TypeLabel.T
  object TypeLabel extends Newtype[String, Pure]
  type PNull = PNull.T
  object PNull extends Newtype[Boolean, Pure]
  type PLo = PLo.T
  object PLo extends Newtype[Long, Pure]
  type PHi = PHi.T
  object PHi extends Newtype[Long, Pure]
