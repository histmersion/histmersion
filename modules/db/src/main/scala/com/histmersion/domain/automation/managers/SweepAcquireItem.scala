package com.histmersion
package domain.automation.managers

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Doi
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** An open acquisition item a watcher's sweep created, found by structure: the sweep run that picked the DOI, the
  * watcher, the goal it linked the item to, and the pick as the ranking saw it (`isOa` = it marked the work open
  * access).
  */
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
    why: Option[SweepAcquireItem.Why]
) derives DbCodec

object SweepAcquireItem:
  type Item = Item.T
  object Item extends RefinedType[Int, Pure]
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  type Topic = Topic.T
  object Topic extends RefinedType[String, Pure]
  type Goal = Goal.T
  object Goal extends RefinedType[String, Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends RefinedType[String, Pure]
  type Job = Job.T
  object Job extends RefinedType[String, Pure]
  type IsOa = IsOa.T
  object IsOa extends RefinedType[Boolean, Pure]
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  type Authors = Authors.T
  object Authors extends RefinedType[String, Pure]
  type Venue = Venue.T
  object Venue extends RefinedType[String, Pure]
  type Why = Why.T
  object Why extends RefinedType[String, Pure]
