package com.histmersion
package domain.personal.notes

import com.histmersion.api.Problem
import com.histmersion.api.personal.Note as WireNote
import com.histmersion.domain.personal.PersonalState.StoredNote
import com.histmersion.domain.personal.{MarkTarget, MarkTime, PersonalUuid, SearchWords}
import com.histmersion.domain.{Described, ItemRef}
import com.histmersion.domain.Title
import com.augustnagro.magnum.DbCodec
import com.histmersion.domain.personal.MarkTime
import com.histmersion.domain.personal.{MarkTarget, MarkTime, TargetLabel}
import com.histmersion.domain.{ItemKey, ItemUid}
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.{Latitude, Longitude, UserId, Year}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ItemKind

/** A note as the person sees it: what it is left on as that shows now, and what that is called. */
final case class Note(
    id: com.histmersion.api.personal.Note.Id,
    target: MarkTarget,
    text: com.histmersion.api.personal.Note.Text,
    title: Title,
    createdAt: MarkTime,
    updatedAt: MarkTime
):
  /** Its text, its item's title and its item's key hold every word. */
  def matches(words: SearchWords): Boolean =
    words.matches(text.value, title.value, target.itemRef.fold("")(_.key.value))

  def wire: WireNote = WireNote(
    id,
    target.wire,
    text,
    com.histmersion.api.library.Title(title.value),
    WireNote.CreatedAt(createdAt.iso.value),
    WireNote.UpdatedAt(updatedAt.iso.value)
  )

object Note:

  /** The id a client sent, checked here, once: a note's id is a lower-case UUID. */

  def idOf(raw: WireNote.Id): Either[Problem, WireNote.Id] =
    val id = Option(raw).fold("")(_.value.trim.toLowerCase)
    Either.cond(PersonalUuid.matches(PersonalUuid.Text(id)), WireNote.Id(id), Problem(s"not a note id: '$raw'"))

  def of(stored: StoredNote, described: Map[ItemRef, Described]): Note =
    val (target, title) = stored.target.shown(described, stored.targetLabel)
    Note(stored.id, target, stored.text, title, stored.createdAt, stored.updatedAt)

  /** Why a note's text was not changed: there is no such note, or it changed since the person read it. */
  enum EditRefused derives CanEqual:
    case Missing
    case Changed(now: MarkTime)

  /** The item a list of notes is narrowed to: the key it was asked by, and the uids of the records that key names now —
    * so a note left on an item before its rename is found by the new key.
    */
  final case class Item(key: ItemKey, uids: Set[ItemUid]):
    def holds(note: StoredNote): Boolean = note.target.itemRef.exists(r => r.key == key || r.uid.exists(uids))

  /** A live note as the table answers it. */
  final private[notes] case class Row(
      id: com.histmersion.api.personal.Note.Id,
      kind: ItemKind.Wire,
      key: Option[ItemKey],
      uid: Option[ItemUid],
      lat: Option[Latitude],
      lon: Option[Longitude],
      from: Option[Year],
      to: Option[Year],
      body: com.histmersion.api.personal.Note.Text,
      targetLabel: Option[TargetLabel],
      createdAt: MarkTime.Iso,
      updatedAt: MarkTime.Iso
  ) derives DbCodec:
    def stored(user: UserId): Option[StoredNote] =
      MarkTarget
        .ofColumns(kind, key, uid, lat, lon, None, from, to, None)
        .map(t => StoredNote(user, id, t, body, targetLabel, MarkTime.parse(createdAt), MarkTime.parse(updatedAt)))
