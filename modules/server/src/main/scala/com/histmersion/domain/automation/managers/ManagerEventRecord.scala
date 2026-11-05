package com.histmersion.domain.automation.managers

import java.time.Instant

/** A manager's event as [[ManagerService.InMemory]] holds it. */
final case class ManagerEventRecord(
    id: ManagerEventRecord.Id,
    managerId: ManagerEventRecord.ManagerId,
    itemId: Option[ManagerEventRecord.ItemId],
    runId: Option[com.histmersion.domain.automation.Run.Id],
    at: Instant,
    kind: ManagerEventRecord.Kind,
    text: ManagerEventRecord.Text
)

object ManagerEventRecord:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManagerId = ManagerId.T
  object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemId = ItemId.T
  object ItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
