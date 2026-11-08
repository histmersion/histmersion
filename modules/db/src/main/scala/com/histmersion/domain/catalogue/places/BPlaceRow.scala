package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A place of this collection's own, as its bundle line reads it. */
final private[places] case class BPlaceRow(
    uid: UUID,
    origin: UUID,
    id: BPlaceRow.Id,
    label: BPlaceRow.Label,
    kind: BPlaceRow.Kind,
    geom: Option[BPlaceRow.Geom],
    pNull: BPlaceRow.PNull,
    pLo: Option[BPlaceRow.PLo],
    pHi: Option[BPlaceRow.PHi],
    source: Option[BPlaceRow.Source],
    properties: Option[BPlaceRow.Properties],
    updatedAt: BPlaceRow.UpdatedAt
) derives DbCodec

object BPlaceRow:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Geom = Geom.T
  object Geom extends RefinedType[String, Pure]
  type PNull = PNull.T
  object PNull extends RefinedType[Boolean, Pure]
  type PLo = PLo.T
  object PLo extends RefinedType[Long, Pure]
  type PHi = PHi.T
  object PHi extends RefinedType[Long, Pure]
  type Source = Source.T
  object Source extends RefinedType[String, Pure]
  type Properties = Properties.T
  object Properties extends RefinedType[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends RefinedType[String, Pure]
