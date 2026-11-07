package com.histmersion.web.marks

import com.histmersion.api.personal.Target
import com.histmersion.web.Store

/** A note not saved yet, kept by this browser until it is saved or cancelled — so a reload or a closed tab loses
  * nothing. Never sent anywhere.
  */
object NoteDrafts:
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type KeyNoteId = KeyNoteId.T
  object KeyNoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Prefix = "histmersion.noteDraft."

  /** Which draft a note being written is: the note's own id, else what it is being left on. Pure. */
  def key(target: Target, noteId: Option[com.histmersion.web.marks.NoteDrafts.KeyNoteId]): com.histmersion.web.marks.NoteDrafts.Key =
    noteId
      .map(id => s"note:$id")
      .orElse(target.identity)
      .getOrElse(
        s"${target.kind.wire}:${target.lat.fold("")(_.value.toString)},${target.lon.fold("")(_.value.toString)}"
      )

  // `var`: tests swap the browser's storage for a Store.Memory
  private[web] var store: Store = Store.Browser

  def get(key: com.histmersion.web.marks.NoteDrafts.Key): Option[com.histmersion.web.marks.NoteDrafts.Text] = store.get(Prefix + key).filter(_.nonEmpty)
  def put(key: com.histmersion.web.marks.NoteDrafts.Key, text: com.histmersion.web.marks.NoteDrafts.Text): Unit =
    if text.trim.isEmpty then store.remove(Prefix + key) else store.put(Prefix + key, text)
  def drop(key: com.histmersion.web.marks.NoteDrafts.Key): Unit = store.remove(Prefix + key)
