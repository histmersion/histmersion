package com.histmersion
package domain.personal

import com.histmersion.domain.ItemRef
import com.histmersion.domain.personal.seen.SeenBoard
import com.histmersion.UserId
import com.histmersion.api.personal.Bookmark
import com.histmersion.api.personal.Note
import com.histmersion.lint.{tolerated, LintRule}

/** What the in-memory bookmarks, notes, read marks and seen marks hold together, as the database's tables hold it:
  * every row with its tombstone.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class PersonalState(
    bookmarks: Vector[PersonalState.StoredBookmark] = Vector.empty,
    notes: Vector[PersonalState.StoredNote] = Vector.empty,
    marks: Vector[PersonalState.StoredMark] = Vector.empty,
    seen: Map[SeenBoard, MarkTime] = Map.empty
)

object PersonalState:

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class StoredBookmark(
      user: UserId,
      id: Bookmark.Id,
      target: MarkTarget,
      label: Option[Bookmark.Label],
      targetLabel: Option[TargetLabel],
      createdAt: MarkTime,
      updatedAt: MarkTime,
      deletedAt: Option[MarkTime] = None
  )

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class StoredNote(
      user: UserId,
      id: Note.Id,
      target: MarkTarget,
      text: Note.Text,
      targetLabel: Option[TargetLabel],
      createdAt: MarkTime,
      updatedAt: MarkTime,
      deletedAt: Option[MarkTime] = None
  )

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class StoredMark(
      user: UserId,
      ref: ItemRef,
      targetLabel: Option[TargetLabel],
      readAt: Option[MarkTime],
      openedAt: Option[MarkTime],
      createdAt: MarkTime,
      updatedAt: MarkTime
  )

  /** Newest first by `at`, then by id from the highest — the order the tables' `ORDER BY … DESC, id DESC` keep. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[personal] def newestFirst[A](as: Seq[A])(at: A => MarkTime, id: A => String): List[A] =
    as.sortWith((a, b) => at(a).value.isAfter(at(b).value) || (at(a) == at(b) && id(a) > id(b))).toList
