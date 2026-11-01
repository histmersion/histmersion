package com.histmersion.domain.catalogue.events

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** One of an event's participants, as its bundle line reads it. */
final private[events] case class BParticipantRow(
    eventUid: UUID,
    actor: String,
    role: String,
    citekey: Option[String],
    sourceUid: Option[UUID]
) derives DbCodec
