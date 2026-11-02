package com.histmersion.domain.catalogue.periods

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** A named period of this collection's own, as its bundle line reads it. */
final private[periods] case class BPeriodRow(
    uid: UUID,
    origin: UUID,
    id: String,
    label: String,
    authority: Option[String],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    region: Option[String],
    parentId: Option[String],
    updatedAt: String
) derives DbCodec
