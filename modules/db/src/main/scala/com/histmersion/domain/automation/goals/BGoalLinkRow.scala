package com.histmersion.domain.automation.goals

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID

/** A goal's link as a bundle reads it, the linked record by its uid (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BGoalLinkRow(
    goalUid: UUID,
    kind: String,
    ref: String,
    refUid: Option[UUID],
    note: Option[String],
    createdAt: String
) derives DbCodec
