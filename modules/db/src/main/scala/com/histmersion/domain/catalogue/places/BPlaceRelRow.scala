package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

/** One of a place's parents, as its bundle line reads it. */
final private[places] case class BPlaceRelRow(
    childId: BPlaceRelRow.ChildId,
    parentId: BPlaceRelRow.ParentId,
    kind: BPlaceRelRow.Kind,
    pNull: BPlaceRelRow.PNull,
    pLo: Option[BPlaceRelRow.PLo],
    pHi: Option[BPlaceRelRow.PHi]
) derives DbCodec

object BPlaceRelRow:
  type ChildId = ChildId.T
  object ChildId extends Newtype[String, Pure]
  type ParentId = ParentId.T
  object ParentId extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  enum PNull derives CanEqual:
    case Undated, Dated
  object PNull extends TwoCases[PNull]:
    extension (pNull: PNull) def pNull: Boolean = pNull == Undated
  type PLo = PLo.T
  object PLo extends Newtype[Long, Pure]
  type PHi = PHi.T
  object PHi extends Newtype[Long, Pure]
