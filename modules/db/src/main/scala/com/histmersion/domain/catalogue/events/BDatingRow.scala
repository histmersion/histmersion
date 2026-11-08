package com.histmersion
package domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

/** One of an event's datings, as its bundle line reads it. */
final private[events] case class BDatingRow(
    eventUid: UUID,
    citekey: Citekey,
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
  object NotBefore extends Newtype[Long, Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends Newtype[Long, Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends Newtype[Long, Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends Newtype[Long, Pure]
  type Method = Method.T
  object Method extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
