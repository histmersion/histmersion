package com.histmersion.domain.personal

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.personal.*
import com.histmersion.domain.personal.PersonalState.StoredMark

import com.augustnagro.magnum.{connect, transact}

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** Whether a person has read a source or an article — only what they marked ("Mark as read") — and when they last
  * opened it, which marks nothing read. Marking unread keeps the row, with no read time.
  */
trait ReadingService:
  def list(user: UserId, kind: Option[ItemKind], show: ReadingShow, limit: Int): List[ReadMark]
  def setRead(user: UserId, kind: ItemKind, key: ItemKey, read: Boolean, title: Option[String], at: Instant): ReadMark
  def opened(user: UserId, kind: ItemKind, key: ItemKey, title: Option[String], at: Instant): ReadMark

  /** A page of the sources (or articles) not marked read, by title. */
  def unread(user: UserId, kind: ItemKind, limit: Int, offset: Int): UnreadPage

object ReadingService:

  def apply(ds: DataSource): ReadingService = Implementation(ds)

  final private class Implementation(ds: DataSource) extends ReadingService:

    def list(user: UserId, kind: Option[ItemKind], show: ReadingShow, limit: Int): List[ReadMark] =
      connect(ds)(ReadingRepo.list(user, kind, show, limit).flatMap(_.mark))

    private def readBack(user: UserId, kind: ItemKind, key: ItemKey)(using com.augustnagro.magnum.DbCon): ReadMark =
      ReadingRepo
        .get(user, kind, key.value)
        .flatMap(_.mark)
        .getOrElse(throw IllegalStateException(s"read mark ${key.value} not read back"))

    def setRead(
        user: UserId,
        kind: ItemKind,
        key: ItemKey,
        read: Boolean,
        title: Option[String],
        at: Instant
    ): ReadMark =
      transact(ds):
        ReadingRepo.setRead(user, kind, key.value, ItemTitles.uidOf(kind, Some(key.value)), title, read, at)
        readBack(user, kind, key)

    def opened(user: UserId, kind: ItemKind, key: ItemKey, title: Option[String], at: Instant): ReadMark =
      transact(ds):
        ReadingRepo.setOpened(user, kind, key.value, ItemTitles.uidOf(kind, Some(key.value)), title, at)
        readBack(user, kind, key)

    def unread(user: UserId, kind: ItemKind, limit: Int, offset: Int): UnreadPage =
      connect(ds):
        val (rows, total) = ReadingRepo.unread(user, kind, limit, offset)
        UnreadPage(
          rows.map(r =>
            UnreadRow(kind, ItemKey(r.key), r.title, r.authors, r.year, r.status, r.periodStart, r.periodEnd)
          ),
          total,
          offset
        )

  /** The read marks held in `state`, beside the bookmarks and notes of the same person; "Not read yet" lists the items
    * of `state`'s catalog, which a test fills with [[addItem]].
    */
  final class InMemory(state: AtomicReference[PersonalState] = AtomicReference(PersonalState())) extends ReadingService:

    /** An item of the collection: what it is called and what "Not read yet" shows of it. */
    def addItem(row: UnreadRow): Unit =
      state.updateAndGet(s => s.modify(_.catalog).using(_ + (s"${row.kind.wire}:${row.id.value}" -> row))): Unit

    private def view(s: PersonalState, m: StoredMark): ReadMark =
      val t = Target(m.kind, Some(m.key))
      ReadMark(
        m.kind,
        m.key,
        PersonalRules.title(t, s.titleOf(t), m.targetLabel),
        m.readAt.isDefined,
        m.readAt.map(PersonalRules.iso),
        m.openedAt.map(PersonalRules.iso)
      )

    private def find(s: PersonalState, user: UserId, kind: ItemKind, key: ItemKey): Option[StoredMark] =
      s.marks.find(m => m.user == user && m.kind == kind && m.key == key)

    def list(user: UserId, kind: Option[ItemKind], show: ReadingShow, limit: Int): List[ReadMark] =
      val s = state.get
      val mine = s.marks.filter(m => m.user == user && kind.forall(_ == m.kind))
      val ordered = show match
        case ReadingShow.Read   => PersonalRules.newestFirst(mine.filter(_.readAt.isDefined))(_.readAt.get, _.key.value)
        case ReadingShow.Opened =>
          PersonalRules.newestFirst(mine.filter(_.openedAt.isDefined))(_.openedAt.get, _.key.value)
        case ReadingShow.All => PersonalRules.newestFirst(mine)(_.updatedAt, _.key.value)
      ordered.take(limit).map(view(s, _))

    private def upsert(user: UserId, kind: ItemKind, key: ItemKey, title: Option[String], at: Instant)(
        change: StoredMark => StoredMark
    ): ReadMark =
      val t = PersonalRules.kept(at)
      val after = state.updateAndGet { s =>
        val m = find(s, user, kind, key).getOrElse(StoredMark(user, kind, key, None, None, None, t, t))
        val changed = change(m).copy(targetLabel = title.orElse(m.targetLabel), updatedAt = t)
        s.modify(_.marks).using(_.filterNot(x => x.user == user && x.kind == kind && x.key == key) :+ changed)
      }
      view(after, find(after, user, kind, key).get)

    def setRead(
        user: UserId,
        kind: ItemKind,
        key: ItemKey,
        read: Boolean,
        title: Option[String],
        at: Instant
    ): ReadMark =
      upsert(user, kind, key, title, at)(m =>
        m.copy(readAt = if read then m.readAt.orElse(Some(PersonalRules.kept(at))) else None)
      )

    def opened(user: UserId, kind: ItemKind, key: ItemKey, title: Option[String], at: Instant): ReadMark =
      upsert(user, kind, key, title, at)(_.copy(openedAt = Some(PersonalRules.kept(at))))

    def unread(user: UserId, kind: ItemKind, limit: Int, offset: Int): UnreadPage =
      val s = state.get
      val all = s.catalog.values
        .filter(r => r.kind == kind && !find(s, user, kind, r.id).exists(_.readAt.isDefined))
        .toList
        .sortBy(r => (r.title.toLowerCase, r.id.value))
      UnreadPage(all.slice(offset, offset + limit), all.size, offset)
