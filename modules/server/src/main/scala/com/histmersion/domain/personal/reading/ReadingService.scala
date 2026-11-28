package com.histmersion
package domain.personal.reading

import com.histmersion.domain.{ItemKey, ItemKind, ItemRef, Referables}
import com.histmersion.domain.personal.PersonalState.StoredMark
import com.histmersion.domain.personal.marks.{Readable, UnreadItems}
import com.histmersion.domain.personal.{MarkTime, PersonalState, TargetLabel}
import com.histmersion.util.database.Transactor
import com.histmersion.{Offset, PageSize, UserId}

import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** Whether a person has read a source or an article — only what they marked ("Mark as read") — and when they last
  * opened it, which marks nothing read. Marking unread keeps the row, with no read time. What is not read yet is the
  * owner's list ([[Readable]]) without what the person marked.
  */
trait ReadingService:
  def list(user: UserId, kind: Option[ItemKind], show: ReadingShow, size: PageSize): List[ReadMark]
  def setRead(
      user: UserId,
      kind: ItemKind,
      key: ItemKey,
      read: ReadingService.Read,
      title: Option[TargetLabel],
      at: MarkTime
  )(using WritePermit): ReadMark
  def opened(user: UserId, kind: ItemKind, key: ItemKey, title: Option[TargetLabel], at: MarkTime)(using
      WritePermit
  ): ReadMark

  /** A page of the records of `kind` not marked read, by title. */
  def unread(user: UserId, kind: ItemKind, size: PageSize, from: Offset): UnreadItems

object ReadingService:
  enum Read derives CanEqual:
    case Read, Unread
  object Read extends TwoCases[Read]:
    extension (read: Read) def read: Boolean = read == Read

  def apply(db: Transactor, items: Referables, readables: List[Readable]): ReadingService =
    Implementation(db, items, readables)

  private def shown(stored: List[StoredMark], items: Referables): List[ReadMark] =
    val described = items.describe(stored.map(_.ref).toSet)
    stored.map(ReadMark.of(_, described))

  /** The record `key` names, with its uid when its owner holds it. */
  private def ref(kind: ItemKind, key: ItemKey, items: Referables): ItemRef =
    ItemRef(kind, key, items.resolve(kind, key).toOption.flatMap(_.uid))

  private def unreadOf(readables: List[Readable], kind: ItemKind)(read: => Set[ItemRef], size: PageSize, from: Offset) =
    readables.find(_.kind == kind).fold(UnreadItems(Nil, UnreadItems.Total(0)))(_.unread(read, size, from))

  final private class Implementation(db: Transactor, items: Referables, readables: List[Readable])
      extends ReadingService:

    def list(user: UserId, kind: Option[ItemKind], show: ReadingShow, size: PageSize): List[ReadMark] =
      shown(db.read(ReadingRepo.list(user, kind, show, size)).map(_.stored(user)), items)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def readBack(user: UserId, r: ItemRef)(using com.augustnagro.magnum.DbCon): StoredMark =
      ReadingRepo
        .get(user, r)
        .map(_.stored(user))
        .getOrElse(throw IllegalStateException(s"read mark ${r.key.value} not read back"))

    def setRead(
        user: UserId,
        kind: ItemKind,
        key: ItemKey,
        read: Read,
        title: Option[TargetLabel],
        at: MarkTime
    )(using WritePermit): ReadMark =
      val r = ref(kind, key, items)
      val stored = db.write:
        ReadingRepo.setRead(user, r, title, read, at)
        readBack(user, r)
      shown(List(stored), items).head

    def opened(user: UserId, kind: ItemKind, key: ItemKey, title: Option[TargetLabel], at: MarkTime)(using
        WritePermit
    ): ReadMark =
      val r = ref(kind, key, items)
      val stored = db.write:
        ReadingRepo.setOpened(user, r, title, at)
        readBack(user, r)
      shown(List(stored), items).head

    def unread(user: UserId, kind: ItemKind, size: PageSize, from: Offset): UnreadItems =
      unreadOf(readables, kind)(db.read(ReadingRepo.read(user, kind)).toSet, size, from)

  /** The read marks held in `state`, beside the bookmarks and notes of the same person, by the rules the table keeps.
    */
  final class InMemory(state: AtomicReference[PersonalState], items: Referables, readables: List[Readable])
      extends ReadingService:

    private def mine(s: PersonalState, user: UserId): Vector[StoredMark] = s.marks.filter(_.user == user)

    private def find(s: PersonalState, user: UserId, kind: ItemKind, key: ItemKey): Option[StoredMark] =
      mine(s, user).find(m => m.ref.kind == kind && m.ref.key == key)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def list(user: UserId, kind: Option[ItemKind], show: ReadingShow, size: PageSize): List[ReadMark] =
      val all = mine(state.get, user).filter(m => kind.forall(_ == m.ref.kind))
      val ordered = show match
        case ReadingShow.Read =>
          PersonalState.newestFirst(all.filter(_.readAt.isDefined))(_.readAt.get, _.ref.key.value)
        case ReadingShow.Opened =>
          PersonalState.newestFirst(all.filter(_.openedAt.isDefined))(_.openedAt.get, _.ref.key.value)
        case ReadingShow.All => PersonalState.newestFirst(all)(_.updatedAt, _.ref.key.value)
      shown(ordered.take(size.value), items)

    @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
    private def upsert(user: UserId, kind: ItemKind, key: ItemKey, title: Option[TargetLabel], at: MarkTime)(
        change: StoredMark => StoredMark
    ): ReadMark =
      val r = ref(kind, key, items)
      val after = state.updateAndGet { s =>
        val m = find(s, user, kind, key).getOrElse(StoredMark(user, r, None, None, None, at, at))
        val kept = change(m).copy(
          ref = m.ref.modify(_.uid).setTo(r.uid.orElse(m.ref.uid)),
          targetLabel = title.orElse(m.targetLabel),
          updatedAt = at
        )
        s.modify(_.marks).using(_.filterNot(x => x.user == user && x.ref.kind == kind && x.ref.key == key) :+ kept)
      }
      shown(find(after, user, kind, key).toList, items).head

    def setRead(
        user: UserId,
        kind: ItemKind,
        key: ItemKey,
        read: Read,
        title: Option[TargetLabel],
        at: MarkTime
    )(using WritePermit): ReadMark =
      upsert(user, kind, key, title, at)(m =>
        m.modify(_.readAt).setTo(if read.read then m.readAt.orElse(Some(at)) else None)
      )

    def opened(user: UserId, kind: ItemKind, key: ItemKey, title: Option[TargetLabel], at: MarkTime)(using
        WritePermit
    ): ReadMark =
      upsert(user, kind, key, title, at)(_.modify(_.openedAt).setTo(Some(at)))

    def unread(user: UserId, kind: ItemKind, size: PageSize, from: Offset): UnreadItems =
      val read = mine(state.get, user).filter(m => m.ref.kind == kind && m.readAt.isDefined).map(_.ref).toSet
      unreadOf(readables, kind)(read, size, from)
