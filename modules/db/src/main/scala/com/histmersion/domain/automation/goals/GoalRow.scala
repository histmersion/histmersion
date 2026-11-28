package com.histmersion
package domain.automation.goals
import com.histmersion.util.database.RangeSql

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.automation.{Creation, Goal}
import com.histmersion.util.database.Jsonb
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
private given DbCodec[Creation] = Jsonb.codec(Creation(Creation.Unreadable))

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
    updatedAt: Option[GoalRow.UpdatedAt],
    creator: Option[Creation] = None,
    createdAt: Option[Creation.At] = None
) derives DbCodec:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def toDto: Goal =
    Goal(
      Goal.Slug(slug.value),
      Goal.Title(title.value),
      Goal.StatusValue(status.value),
      notes.map(n => Goal.Notes(n.value)),
      tags.map(t => Goal.Tags(t.value)),
      regions.map(r => Goal.Regions(r.value)),
      periodStart.map(y => y),
      periodEnd.map(y => y),
      parentSlug.map(p => Goal.Slug(p.value)),
      updatedAt.map(u => Goal.UpdatedAt(u.value)),
      creator,
      createdAt
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
  object Tags extends Newtype[String, Pure]:
    extension (tags: Tags) def asTag: EnrichSource.Tag = EnrichSource.Tag(tags.value)
  type Regions = Regions.T
  object Regions extends Newtype[String, Pure]
  type ParentSlug = ParentSlug.T
  object ParentSlug extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
