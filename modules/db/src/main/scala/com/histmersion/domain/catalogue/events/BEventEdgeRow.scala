package com.histmersion
package domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

/** One of an event's edges to another event, as its bundle line reads it. */
final private[events] case class BEventEdgeRow(
    eventUid: UUID,
    dstUid: UUID,
    kind: BEventEdgeRow.Kind,
    citekey: Citekey,
    sourceUid: Option[UUID],
    note: Option[BEventEdgeRow.Note]
) derives DbCodec

object BEventEdgeRow:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
