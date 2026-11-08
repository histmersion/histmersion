package com.histmersion
package domain.automation.managers

import java.time.Instant
import com.histmersion.domain.automation.Run
import io.github.iltotore.iron.constraint.any.Pure

/** A manager's event as [[ManagerService.InMemory]] holds it. */
final case class ManagerEventRecord(
    id: ManagerEventRecord.Id,
    managerId: ManagerEventRecord.ManagerId,
    itemId: Option[ManagerEventRecord.ItemId],
    runId: Option[Run.Id],
    at: Instant,
    kind: ManagerEventRecord.Kind,
    text: ManagerEventRecord.Text
)

object ManagerEventRecord:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends Newtype[String, Pure]
  type ItemId = ItemId.T
  object ItemId extends Newtype[Long, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
