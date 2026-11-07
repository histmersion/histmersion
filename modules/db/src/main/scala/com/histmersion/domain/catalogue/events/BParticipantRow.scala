package com.histmersion.domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given

/** One of an event's participants, as its bundle line reads it. */
final private[events] case class BParticipantRow(
    eventUid: UUID,
    actor: BParticipantRow.Actor,
    role: BParticipantRow.Role,
    citekey: Option[com.histmersion.domain.library.Citekey],
    sourceUid: Option[UUID]
) derives DbCodec

object BParticipantRow:
  type Actor = Actor.T
  object Actor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
