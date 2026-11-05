package com.histmersion.domain.automation.goals

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID

/** A goal's schedule as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BGoalScheduleRow(
    goalUid: UUID,
    kind: String,
    cron: String,
    enabled: Boolean,
    budgetUsd: Option[BigDecimal]
) derives DbCodec
