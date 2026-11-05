package com.histmersion.domain.automation.goals
import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.api.automation.Goal

final private[histmersion] case class GoalRow(
    slug: String,
    title: String,
    status: String,
    notes: Option[String],
    tags: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    parentSlug: Option[String],
    updatedAt: Option[String]
) derives DbCodec:
  def toDto: Goal = Goal(slug, title, status, notes, tags, regions, periodStart, periodEnd, parentSlug, updatedAt)
