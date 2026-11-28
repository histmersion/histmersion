package com.histmersion
package domain.catalogue.periods

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.Jsonb
import com.histmersion.TwoCases

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
    updatedAt: BPeriodRow.UpdatedAt,
    creator: Option[Jsonb.JsonText] = None
) derives DbCodec

object BPeriodRow:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Authority = Authority.T
  object Authority extends Newtype[String, Pure]
  enum PNull derives CanEqual:
    case Undated, Dated
  object PNull extends TwoCases[PNull]:
    extension (pNull: PNull) def pNull: Boolean = pNull == Undated
  type PLo = PLo.T
  object PLo extends Newtype[Long, Pure]
  type PHi = PHi.T
  object PHi extends Newtype[Long, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  type ParentId = ParentId.T
  object ParentId extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
