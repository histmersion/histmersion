package com.histmersion.domain.catalogue.events

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** One of an event's datings, as its bundle line reads it. */
final private[events] case class BDatingRow(
    eventUid: UUID,
    citekey: String,
    sourceUid: Option[UUID],
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    method: Option[String],
    note: Option[String]
) derives DbCodec
