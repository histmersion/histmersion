package com.histmersion.domain.catalogue.events

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** One of an event's edges to another event, as its bundle line reads it. */
final private[events] case class BEventEdgeRow(
    eventUid: UUID,
    dstUid: UUID,
    kind: String,
    citekey: String,
    sourceUid: Option[UUID],
    note: Option[String]
) derives DbCodec
