package com.histmersion
package domain.automation.worklists
import com.histmersion.util.problem.Problem

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec

import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Queue

/** A work list's item as a bundle reads it (Magnum maps a SELECT's columns positionally). */
final private[histmersion] case class BQueueRow(
    uid: UUID,
    origin: UUID,
    list: BQueueRow.List,
    text: BQueueRow.Text,
    topic: Option[BQueueRow.Topic],
    status: BQueueRow.Status,
    reason: Option[Queue.Rationale],
    createdAt: BQueueRow.CreatedAt,
    doneAt: Option[BQueueRow.DoneAt],
    updatedAt: BQueueRow.UpdatedAt
) derives DbCodec

object BQueueRow:
  type List = List.T
  object List extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type DoneAt = DoneAt.T
  object DoneAt extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]
