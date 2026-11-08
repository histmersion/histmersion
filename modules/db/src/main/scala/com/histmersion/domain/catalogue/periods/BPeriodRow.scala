package com.histmersion
package domain.catalogue.periods

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A named period of this collection's own, as its bundle line reads it. */
final private[periods] case class BPeriodRow(
    uid: UUID,
    origin: UUID,
    id: BPeriodRow.Id,
    label: BPeriodRow.Label,
    authority: Option[BPeriodRow.Authority],
    pNull: BPeriodRow.PNull,
    pLo: Option[BPeriodRow.PLo],
    pHi: Option[BPeriodRow.PHi],
    region: Option[BPeriodRow.Region],
    parentId: Option[BPeriodRow.ParentId],
    updatedAt: BPeriodRow.UpdatedAt
) derives DbCodec

object BPeriodRow:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Authority = Authority.T
  object Authority extends RefinedType[String, Pure]
  type PNull = PNull.T
  object PNull extends RefinedType[Boolean, Pure]
  type PLo = PLo.T
  object PLo extends RefinedType[Long, Pure]
  type PHi = PHi.T
  object PHi extends RefinedType[Long, Pure]
  type Region = Region.T
  object Region extends RefinedType[String, Pure]
  type ParentId = ParentId.T
  object ParentId extends RefinedType[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends RefinedType[String, Pure]
