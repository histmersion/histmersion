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
    readAt: Option[String],
    openedAt: Option[String],
    createdAt: String,
    updatedAt: String
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
