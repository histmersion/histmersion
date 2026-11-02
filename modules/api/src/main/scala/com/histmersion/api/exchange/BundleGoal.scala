package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Json

import java.util.UUID
import com.histmersion.api.given

final case class BundleGoal(
    uid: UUID,
    origin: UUID,
    slug: String,
    title: String,
    status: String,
    notes: Option[String],
    tags: List[String],
    regions: List[String],
    period: Option[BundleRange],
    parentUid: Option[UUID],
    createdAt: String,
    updatedAt: String,
    rev: String = "",
    /** The four fields below are absent when empty, so a line written before they existed keeps its `rev`. */
    plan: Option[Json] = None,
    planApprovedAt: Option[String] = None,
    budgetUsd: Option[Double] = None,
    links: Option[List[BundleGoalLink]] = None,
    schedules: Option[List[BundleGoalSchedule]] = None
) derives KindlingsCodecAsObject
