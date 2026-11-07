package com.histmersion.domain.automation.managers

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given

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
    doi: com.histmersion.domain.library.Doi,
    isOa: SweepAcquireItem.IsOa,
    title: Option[SweepAcquireItem.Title],
    year: Option[com.histmersion.Year],
    authors: List[SweepAcquireItem.Authors],
    venue: Option[SweepAcquireItem.Venue],
    why: Option[SweepAcquireItem.Why]
) derives DbCodec

object SweepAcquireItem:
  type Item = Item.T
  object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topic = Topic.T
  object Topic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Job = Job.T
  object Job extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsOa = IsOa.T
  object IsOa extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Venue = Venue.T
  object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Why = Why.T
  object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
