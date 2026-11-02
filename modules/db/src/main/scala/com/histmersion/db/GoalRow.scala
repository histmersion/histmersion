package com.histmersion.db

import com.histmersion.util.database.RangeSql

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*

final private[db] case class GoalRow(
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
