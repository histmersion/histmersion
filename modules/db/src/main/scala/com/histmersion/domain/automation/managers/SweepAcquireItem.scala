package com.histmersion
package domain.automation.managers

import com.histmersion.util.database.given
import SweepAcquireItem.Rationale
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Doi
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** An open acquisition item a watcher's sweep created, found by structure: the sweep run that picked the DOI, the
  * watcher, the goal it linked the item to, and the pick as the ranking saw it (`isOa` = it marked the work open
  * access).
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class SweepAcquireItem(
    item: SweepAcquireItem.Item,
    text: SweepAcquireItem.Text,
    topic: Option[SweepAcquireItem.Topic],
    goal: SweepAcquireItem.Goal,
    managerId: SweepAcquireItem.ManagerId,
    job: SweepAcquireItem.Job,
    doi: Doi,
    isOa: SweepAcquireItem.IsOa,
    title: Option[SweepAcquireItem.Title],
    year: Option[Year],
    authors: List[SweepAcquireItem.Authors],
    venue: Option[SweepAcquireItem.Venue],
    why: Option[Rationale]
) derives DbCodec

object SweepAcquireItem:
  /** Why the ranking picked the DOI, as it said it: narration, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
  type Item = Item.T
  object Item extends Newtype[Int, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends Newtype[String, Pure]
  type Job = Job.T
  object Job extends Newtype[String, Pure]
  type IsOa = IsOa.T
  object IsOa extends Newtype[Boolean, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  type Venue = Venue.T
  object Venue extends Newtype[String, Pure]
