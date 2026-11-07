package com.histmersion.domain.automation.worklists

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given

/** A work list's item as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BQueueRow(
    uid: UUID,
    origin: UUID,
    list: BQueueRow.List,
    text: BQueueRow.Text,
    topic: Option[BQueueRow.Topic],
    status: BQueueRow.Status,
    reason: Option[BQueueRow.Reason],
    createdAt: BQueueRow.CreatedAt,
    doneAt: Option[BQueueRow.DoneAt],
    updatedAt: BQueueRow.UpdatedAt
) derives DbCodec

object BQueueRow:
  type List = List.T
  object List extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topic = Topic.T
  object Topic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoneAt = DoneAt.T
  object DoneAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
