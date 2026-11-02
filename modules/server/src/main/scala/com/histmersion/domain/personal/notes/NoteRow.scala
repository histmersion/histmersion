package com.histmersion.domain.personal.notes

import com.histmersion.api.personal.{NoteId, NoteText}
import com.histmersion.domain.personal.PersonalState.StoredNote
import com.histmersion.domain.personal.{MarkTarget, MarkTime, TargetLabel}
import com.histmersion.domain.{ItemKey, ItemUid}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.{Latitude, Longitude, UserId, Year}

import com.augustnagro.magnum.DbCodec

/** A live note as the table answers it. */
final private[notes] case class NoteRow(
    id: NoteId,
    kind: String,
    key: Option[ItemKey],
    uid: Option[ItemUid],
    lat: Option[Latitude],
    lon: Option[Longitude],
    from: Option[Year],
    to: Option[Year],
    body: NoteText,
    targetLabel: Option[TargetLabel],
    createdAt: String,
    updatedAt: String
) derives DbCodec:
  def stored(user: UserId): Option[StoredNote] =
    MarkTarget
      .ofColumns(kind, key, uid, lat, lon, None, from, to, None)
      .map(t => StoredNote(user, id, t, body, targetLabel, MarkTime.parse(createdAt), MarkTime.parse(updatedAt)))
