package com.histmersion
package web.marks

import com.histmersion.api.personal.{Note, Target}
import com.histmersion.web.Store
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A note not saved yet, kept by this browser until it is saved or cancelled — so a reload or a closed tab loses
  * nothing. Never sent anywhere.
  */
object NoteDrafts:
  type Key = Key.T
  object Key extends Newtype[String, Pure]

  private val Prefix = "histmersion.noteDraft."

  /** Which draft a note being written is: the note's own id, else what it is being left on. Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def key(target: Target, noteId: Option[Note.Id]): Key =
    Key(
      noteId
        .map(id => s"note:${id.value}")
        .orElse(target.identity.map(_.value))
        .getOrElse(
          s"${target.kind.wire}:${target.lat.fold("")(_.value.toString)},${target.lon.fold("")(_.value.toString)}"
        )
    )

  // `var`: tests swap the browser's storage for a Store.Memory
  private[web] var store: Store = Store.Browser

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def get(key: Key): Option[Note.Text] = store.get(Prefix + key.value).filter(_.nonEmpty).map(Note.Text(_))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def put(key: Key, text: Note.Text): Unit =
    if text.value.trim.isEmpty then store.remove(Prefix + key.value) else store.put(Prefix + key.value, text.value)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def drop(key: Key): Unit = store.remove(Prefix + key.value)
