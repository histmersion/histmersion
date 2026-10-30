package com.histmersion.domain.personal

import com.histmersion.api.personal.*

import java.time.Instant

/** What the in-memory bookmarks, notes and read marks services hold together, as the database's tables hold
  * it: every row with its tombstone, and the items of the collection they can name (`catalog`, by
  * [[Target.identity]]: the item's title and what "Not read yet" lists of it). */
final case class PersonalState(
    bookmarks: Vector[PersonalState.StoredBookmark] = Vector.empty,
    notes: Vector[PersonalState.StoredNote] = Vector.empty,
    marks: Vector[PersonalState.StoredMark] = Vector.empty,
    catalog: Map[String, UnreadRow] = Map.empty):

  def titleOf(t: Target): Option[String] = t.identity.flatMap(catalog.get).map(_.title)

object PersonalState:

  final case class StoredBookmark(user: UserId, id: BookmarkId, target: Target, label: Option[BookmarkLabel],
      targetLabel: Option[String], createdAt: Instant, updatedAt: Instant, deletedAt: Option[Instant] = None)

  final case class StoredNote(user: UserId, id: NoteId, target: Target, text: NoteText, targetLabel: Option[String],
      createdAt: Instant, updatedAt: Instant, deletedAt: Option[Instant] = None)

  final case class StoredMark(user: UserId, kind: ItemKind, key: ItemKey, targetLabel: Option[String],
      readAt: Option[Instant], openedAt: Option[Instant], createdAt: Instant, updatedAt: Instant)
