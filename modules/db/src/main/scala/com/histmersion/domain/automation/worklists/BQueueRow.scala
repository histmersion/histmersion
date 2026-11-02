package com.histmersion.domain.automation.worklists

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID

/** A work list's item as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BQueueRow(
    uid: UUID,
    origin: UUID,
    list: String,
    text: String,
    topic: Option[String],
    status: String,
    reason: Option[String],
    createdAt: String,
    doneAt: Option[String],
    updatedAt: String
) derives DbCodec
