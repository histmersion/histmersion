package com.histmersion.domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** One of an event's datings, as its bundle line reads it. */
final private[events] case class BDatingRow(
    eventUid: UUID,
    citekey: com.histmersion.domain.library.Citekey,
    sourceUid: Option[UUID],
    notBefore: Option[BDatingRow.NotBefore],
    beginBy: Option[BDatingRow.BeginBy],
    endFrom: Option[BDatingRow.EndFrom],
    notAfter: Option[BDatingRow.NotAfter],
    method: Option[BDatingRow.Method],
    note: Option[BDatingRow.Note]
) derives DbCodec

object BDatingRow:
  type NotBefore = NotBefore.T
  object NotBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Method = Method.T
  object Method extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
