package com.histmersion
package domain.personal.bookmarks
import com.histmersion.util.problem.Problem

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
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ItemKind
import com.histmersion.lint.{tolerated, LintRule}

/** A bookmark as the person sees it: its target as it shows now and what that is called. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class Bookmark(
    id: com.histmersion.api.personal.Bookmark.Id,
    target: MarkTarget,
    title: Title,
    label: Option[com.histmersion.api.personal.Bookmark.Label],
    createdAt: MarkTime,
    updatedAt: MarkTime
):
  /** Its name, its item's title and its item's key hold every word. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def matches(words: SearchWords): Boolean =
    words.matches(label.fold("")(_.value), title.value, target.itemRef.fold("")(_.key.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def wire: WireBookmark = WireBookmark(
    id,
    target.wire,
    com.histmersion.api.library.Title(title.value),
    label,
    WireBookmark.CreatedAt(createdAt.iso.value),
    WireBookmark.UpdatedAt(updatedAt.iso.value)
  )

object Bookmark:

  /** The id a client sent, checked here, once: a bookmark's id is a lower-case UUID. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def idOf(raw: WireBookmark.Id): Either[Problem, WireBookmark.Id] =
    val id = Option(raw).fold("")(_.value.trim.toLowerCase)
    Either.cond(
      PersonalUuid.matches(PersonalUuid.Text(id)),
      WireBookmark.Id(id),
      Problem.Invalid.Unreadable.here(
        Problem.TypeName.of(WireBookmark.Id),
        Problem.Raw.of(raw),
        Vector(Problem.Raw("0193f1c4-7b2a-7c3e-9d41-5e6f7a8b9c0d"))
      )
    )

  def of(stored: StoredBookmark, described: Map[ItemRef, Described]): Bookmark =
    val (target, title) = stored.target.shown(described, stored.targetLabel)
    Bookmark(stored.id, target, title, stored.label, stored.createdAt, stored.updatedAt)

  /** A live bookmark as the table answers it. */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final private[bookmarks] case class Row(
      id: com.histmersion.api.personal.Bookmark.Id,
      kind: ItemKind.Wire,
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
