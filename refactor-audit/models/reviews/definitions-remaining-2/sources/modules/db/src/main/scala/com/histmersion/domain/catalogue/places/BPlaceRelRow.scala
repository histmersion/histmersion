package com.histmersion.domain.catalogue.places

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

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
  object ChildId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentId = ParentId.T
  object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PNull = PNull.T
  object PNull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PLo = PLo.T
  object PLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PHi = PHi.T
  object PHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
