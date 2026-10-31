package com.histmersion.domain.personal.notes

import com.histmersion.api.personal.{Note as WireNote, NoteId, NoteText}
import com.histmersion.domain.personal.PersonalState.StoredNote
import com.histmersion.domain.personal.{MarkTarget, MarkTime, SearchWords}
import com.histmersion.domain.{Described, ItemRef}
import com.histmersion.values.Title

/** A note as the person sees it: what it is left on as that shows now, and what that is called. */
final case class Note(
    id: NoteId,
    target: MarkTarget,
    text: NoteText,
    title: Title,
    createdAt: MarkTime,
    updatedAt: MarkTime
):
  /** Its text, its item's title and its item's key hold every word. */
  def matches(words: SearchWords): Boolean =
    words.matches(text.value, title.value, target.itemRef.fold("")(_.key.value))

  def wire: WireNote = WireNote(id, target.wire, text, title.value, createdAt.iso, updatedAt.iso)

object Note:
  def of(stored: StoredNote, described: Map[ItemRef, Described]): Note =
    val (target, title) = stored.target.shown(described, stored.targetLabel)
    Note(stored.id, target, stored.text, title, stored.createdAt, stored.updatedAt)
