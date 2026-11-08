package com.histmersion
package domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object ChildId extends RefinedType[String, Pure]
  type ParentId = ParentId.T
  object ParentId extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type PNull = PNull.T
  object PNull extends RefinedType[Boolean, Pure]
  type PLo = PLo.T
  object PLo extends RefinedType[Long, Pure]
  type PHi = PHi.T
  object PHi extends RefinedType[Long, Pure]
