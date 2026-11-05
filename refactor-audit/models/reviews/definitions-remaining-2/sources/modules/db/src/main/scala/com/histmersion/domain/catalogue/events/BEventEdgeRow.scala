package com.histmersion.domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** One of an event's edges to another event, as its bundle line reads it. */
final private[events] case class BEventEdgeRow(
    eventUid: UUID,
    dstUid: UUID,
    kind: BEventEdgeRow.Kind,
    citekey: com.histmersion.domain.library.Citekey,
    sourceUid: Option[UUID],
    note: Option[BEventEdgeRow.Note]
) derives DbCodec

object BEventEdgeRow:
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
