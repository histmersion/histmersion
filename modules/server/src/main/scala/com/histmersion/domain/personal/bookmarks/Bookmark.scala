package com.histmersion.domain.personal.bookmarks

import com.histmersion.api.personal.{Bookmark as WireBookmark, BookmarkId, BookmarkLabel}
import com.histmersion.domain.personal.PersonalState.StoredBookmark
import com.histmersion.domain.personal.{MarkTarget, MarkTime, SearchWords}
import com.histmersion.domain.{Described, ItemRef}
import com.histmersion.domain.library.Title

/** A bookmark as the person sees it: its target as it shows now and what that is called. */
final case class Bookmark(
    id: BookmarkId,
    target: MarkTarget,
    title: Title,
    label: Option[BookmarkLabel],
    createdAt: MarkTime,
    updatedAt: MarkTime
):
  /** Its name, its item's title and its item's key hold every word. */
  def matches(words: SearchWords): Boolean =
    words.matches(label.fold("")(_.value), title.value, target.itemRef.fold("")(_.key.value))

  def wire: WireBookmark = WireBookmark(id, target.wire, title.value, label, createdAt.iso, updatedAt.iso)

object Bookmark:
  def of(stored: StoredBookmark, described: Map[ItemRef, Described]): Bookmark =
    val (target, title) = stored.target.shown(described, stored.targetLabel)
    Bookmark(stored.id, target, title, stored.label, stored.createdAt, stored.updatedAt)
