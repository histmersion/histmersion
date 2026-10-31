package com.histmersion.domain.personal.bookmarks

import com.histmersion.api.personal.{BookmarkId, BookmarkLabel, MapZoom, ViewState}
import com.histmersion.domain.personal.PersonalState.StoredBookmark
import com.histmersion.domain.personal.{MarkTarget, MarkTime, TargetLabel}
import com.histmersion.domain.{ItemKey, ItemUid}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.values.{Latitude, Longitude, UserId, Year}

import com.augustnagro.magnum.DbCodec

/** A live bookmark as the table answers it. */
final private[bookmarks] case class BookmarkRow(
    id: BookmarkId,
    kind: String,
    key: Option[ItemKey],
    uid: Option[ItemUid],
    lat: Option[Latitude],
    lon: Option[Longitude],
    zoom: Option[MapZoom],
    from: Option[Year],
    to: Option[Year],
    view: Option[ViewState],
    label: Option[BookmarkLabel],
    targetLabel: Option[TargetLabel],
    createdAt: String,
    updatedAt: String
) derives DbCodec:
  def stored(user: UserId): Option[StoredBookmark] =
    MarkTarget
      .ofColumns(kind, key, uid, lat, lon, zoom, from, to, view)
      .map(t => StoredBookmark(user, id, t, label, targetLabel, MarkTime.parse(createdAt), MarkTime.parse(updatedAt)))
