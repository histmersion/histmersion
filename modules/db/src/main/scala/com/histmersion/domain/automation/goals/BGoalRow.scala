package com.histmersion
package domain.automation.goals

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A goal's row as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BGoalRow(
    uid: UUID,
    origin: UUID,
    slug: BGoalRow.Slug,
    title: BGoalRow.Title,
    status: BGoalRow.Status,
    notes: Option[BGoalRow.Notes],
    tags: List[BGoalRow.Tags],
    regions: List[BGoalRow.Regions],
    pNull: BGoalRow.PNull,
    pLo: Option[BGoalRow.PLo],
    pHi: Option[BGoalRow.PHi],
    parentUid: Option[UUID],
    createdAt: BGoalRow.CreatedAt,
    updatedAt: BGoalRow.UpdatedAt,
    plan: Option[BGoalRow.Plan],
    planApprovedAt: Option[BGoalRow.PlanApprovedAt],
    budgetUsd: Option[BigDecimal]
) derives DbCodec

object BGoalRow:
  type Slug = Slug.T
  object Slug extends RefinedType[String, Pure]
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  type Status = Status.T
  object Status extends RefinedType[String, Pure]
  type Notes = Notes.T
  object Notes extends RefinedType[String, Pure]
  type Tags = Tags.T
  object Tags extends RefinedType[String, Pure]
  type Regions = Regions.T
  object Regions extends RefinedType[String, Pure]
  type PNull = PNull.T
  object PNull extends RefinedType[Boolean, Pure]
  type PLo = PLo.T
  object PLo extends RefinedType[Long, Pure]
  type PHi = PHi.T
  object PHi extends RefinedType[Long, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends RefinedType[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends RefinedType[String, Pure]
  type Plan = Plan.T
  object Plan extends RefinedType[String, Pure]
  type PlanApprovedAt = PlanApprovedAt.T
  object PlanApprovedAt extends RefinedType[String, Pure]
