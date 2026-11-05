package com.histmersion.domain.personal.reading

import com.histmersion.domain.personal.PersonalState.StoredMark
import com.histmersion.domain.personal.{MarkTime, TargetLabel}
import com.histmersion.domain.{ItemKey, ItemKind, ItemRef, ItemUid}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.UserId

import com.augustnagro.magnum.DbCodec

/** A read mark as the table answers it. */
final private[reading] case class MarkRow(
    kind: ItemKind,
    key: ItemKey,
    uid: Option[ItemUid],
    targetLabel: Option[TargetLabel],
    readAt: Option[MarkRow.ReadAt],
    openedAt: Option[MarkRow.OpenedAt],
    createdAt: MarkRow.CreatedAt,
    updatedAt: MarkRow.UpdatedAt
) derives DbCodec:
  def stored(user: UserId): StoredMark =
    StoredMark(
      user,
      ItemRef(kind, key, uid),
      targetLabel,
      readAt.map(MarkTime.parse),
      openedAt.map(MarkTime.parse),
      MarkTime.parse(createdAt),
      MarkTime.parse(updatedAt)
    )

object MarkRow:
  type ReadAt = ReadAt.T
  object ReadAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenedAt = OpenedAt.T
  object OpenedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CreatedAt = CreatedAt.T
  object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
