package com.histmersion
package domain.automation.goals

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.Jsonb
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

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
    budgetUsd: Option[BigDecimal],
    creator: Option[Jsonb.JsonText] = None
) derives DbCodec

object BGoalRow:
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Regions = Regions.T
  object Regions extends Newtype[String, Pure]
  enum PNull derives CanEqual:
    case Undated, Dated
  object PNull extends TwoCases[PNull]:
    extension (pNull: PNull) def pNull: Boolean = pNull == Undated
  type PLo = PLo.T
  object PLo extends Newtype[Long, Pure]
  type PHi = PHi.T
  object PHi extends Newtype[Long, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
  type Plan = Plan.T
  object Plan extends Newtype[String, Pure]
  type PlanApprovedAt = PlanApprovedAt.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PlanApprovedAt extends Newtype[String, Pure]
