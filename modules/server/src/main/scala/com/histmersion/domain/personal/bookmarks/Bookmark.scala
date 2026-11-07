package com.histmersion.domain.personal.bookmarks

import com.histmersion.api.personal.Bookmark as WireBookmark
import com.histmersion.domain.personal.PersonalState.StoredBookmark
import com.histmersion.domain.personal.{MarkTarget, MarkTime, PersonalUuid, SearchWords}
import com.histmersion.domain.{Described, ItemRef}
import com.histmersion.domain.Title
import com.augustnagro.magnum.DbCodec
import com.histmersion.api.personal.{MapZoom, ViewState}
import com.histmersion.domain.personal.{MarkTarget, MarkTime, TargetLabel}
import com.histmersion.domain.{ItemKey, ItemUid}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.{Latitude, Longitude, UserId, Year}

/** A bookmark as the person sees it: its target as it shows now and what that is called. */
final case class Bookmark(
    id: com.histmersion.api.personal.Bookmark.Id,
    target: MarkTarget,
    title: Title,
    label: Option[com.histmersion.api.personal.Bookmark.Label],
    createdAt: MarkTime,
    updatedAt: MarkTime
):
  /** Its name, its item's title and its item's key hold every word. */
  def matches(words: SearchWords): Boolean =
    words.matches(label.fold("")(_.value), title.value, target.itemRef.fold("")(_.key.value))

  def wire: WireBookmark = WireBookmark(id, target.wire, com.histmersion.api.library.Title(title.value), label, WireBookmark.CreatedAt(createdAt.iso.value), WireBookmark.UpdatedAt(updatedAt.iso.value))

object Bookmark:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The id a client sent, checked here, once: a bookmark's id is a lower-case UUID. */

  def idOf(raw: WireBookmark.Id): Either[Problem, WireBookmark.Id] =
    val id = Option(raw).fold("")(_.value.trim.toLowerCase)
    Either.cond(PersonalUuid.matches(PersonalUuid.Text(id)), WireBookmark.Id(id), Problem(s"not a bookmark id: '$raw'"))

  def of(stored: StoredBookmark, described: Map[ItemRef, Described]): Bookmark =
    val (target, title) = stored.target.shown(described, stored.targetLabel)
    Bookmark(stored.id, target, title, stored.label, stored.createdAt, stored.updatedAt)

  /** A live bookmark as the table answers it. */
  final private[bookmarks] case class Row(
      id: com.histmersion.api.personal.Bookmark.Id,
      kind: com.histmersion.api.ItemKind.Wire,
      key: Option[ItemKey],
      uid: Option[ItemUid],
      lat: Option[Latitude],
      lon: Option[Longitude],
      zoom: Option[MapZoom],
      from: Option[Year],
      to: Option[Year],
      view: Option[ViewState],
      label: Option[com.histmersion.api.personal.Bookmark.Label],
      targetLabel: Option[TargetLabel],
      createdAt: MarkTime.Iso,
      updatedAt: MarkTime.Iso
  ) derives DbCodec:
    def stored(user: UserId): Option[StoredBookmark] =
      MarkTarget
        .ofColumns(kind, key, uid, lat, lon, zoom, from, to, view)
        .map(t => StoredBookmark(user, id, t, label, targetLabel, MarkTime.parse(createdAt), MarkTime.parse(updatedAt)))
