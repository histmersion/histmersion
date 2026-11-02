package com.histmersion.domain.automation.goals

import com.histmersion.db.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID

/** A goal's row as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BGoalRow(
    uid: UUID,
    origin: UUID,
    slug: String,
    title: String,
    status: String,
    notes: Option[String],
    tags: List[String],
    regions: List[String],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    parentUid: Option[UUID],
    createdAt: String,
    updatedAt: String,
    plan: Option[String],
    planApprovedAt: Option[String],
    budgetUsd: Option[BigDecimal]
) derives DbCodec
