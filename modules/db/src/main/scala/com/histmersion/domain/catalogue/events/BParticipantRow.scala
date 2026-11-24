package com.histmersion
package domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One of an event's participants, as its bundle line reads it. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final private[events] case class BParticipantRow(
    eventUid: UUID,
    actor: BParticipantRow.Actor,
    role: BParticipantRow.Role,
    citekey: Option[Citekey],
    sourceUid: Option[UUID]
) derives DbCodec

object BParticipantRow:
  type Actor = Actor.T
  object Actor extends Newtype[String, Pure]
  type Role = Role.T
  object Role extends Newtype[String, Pure]
