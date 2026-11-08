package com.histmersion
package domain.automation.goals
import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

final private[histmersion] case class GoalRow(
    slug: GoalRow.Slug,
    title: GoalRow.Title,
    status: GoalRow.Status,
    notes: Option[GoalRow.Notes],
    tags: List[GoalRow.Tags],
    regions: List[GoalRow.Regions],
    periodStart: Option[DeepTime.SignedYear],
    periodEnd: Option[DeepTime.SignedYear],
    parentSlug: Option[GoalRow.ParentSlug],
    updatedAt: Option[GoalRow.UpdatedAt]
) derives DbCodec:
  def toDto: Goal =
    Goal(
      Goal.Slug(slug.value),
      Goal.Title(title.value),
      Goal.StatusValue(status.value),
      notes.map(n => Goal.Notes(n.value)),
      tags.map(t => Goal.Tags(t.value)),
      regions.map(r => Goal.Regions(r.value)),
      periodStart.map(y => Year(y.value)),
      periodEnd.map(y => Year(y.value)),
      parentSlug.map(p => Goal.Slug(p.value)),
      updatedAt.map(u => Goal.UpdatedAt(u.value))
    )

object GoalRow:
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
  type ParentSlug = ParentSlug.T
  object ParentSlug extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
