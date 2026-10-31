package com.histmersion.domain.personal.bookmarks

import com.histmersion.api.personal.{BookmarkId, BookmarkLabel, ItemKind as MarkKind}
import com.histmersion.domain.Referables
import com.histmersion.domain.personal.PersonalState.StoredBookmark
import com.histmersion.domain.personal.{MarkTarget, MarkTime, PersonalState, SearchWords, TargetColumns, TargetLabel}
import com.histmersion.util.database.Transactor
import com.histmersion.values.{PageSize, UserId}

import hearth.kindlings.optics.syntax.*

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** A person's bookmarks. A target reaches here checked; an item is bookmarked once, so bookmarking it again answers its
  * bookmark (renamed when a label is given). A removed bookmark is kept as its tombstone. An item's key and title are
  * its owner's as they are when the bookmarks are read.
  */
trait BookmarkService:
  /** Newest first, `size` of them, those whose name, title or key hold every word. */
  def list(user: UserId, kind: Option[MarkKind], words: SearchWords, size: PageSize): List[Bookmark]
  def add(
      user: UserId,
      target: MarkTarget,
      label: Option[BookmarkLabel],
      title: Option[TargetLabel],
      at: MarkTime
  ): Bookmark
  def rename(user: UserId, id: BookmarkId, label: Option[BookmarkLabel], at: MarkTime): Option[Bookmark]

  /** The bookmark as it was before it was removed. */
  def remove(user: UserId, id: BookmarkId, at: MarkTime): Option[Bookmark]

object BookmarkService:

  def apply(db: Transactor, items: Referables): BookmarkService = Implementation(db, items)

  private def shown(stored: List[StoredBookmark], items: Referables): List[Bookmark] =
    val described = items.describe(stored.flatMap(_.target.itemRef).toSet)
    stored.map(Bookmark.of(_, described))

  private def page(all: List[Bookmark], words: SearchWords, size: PageSize): List[Bookmark] =
    (if words.isEmpty then all else all.filter(_.matches(words))).take(size.value)

  /** The target with its item's uid, when its owner holds the item. */
  private def resolved(target: MarkTarget, items: Referables): MarkTarget =
    target.itemRef.fold(target)(r => target.resolved(items.resolve(r.kind, r.key).toOption.flatMap(_.uid)))

  final private class Implementation(db: Transactor, items: Referables) extends BookmarkService:

    def list(user: UserId, kind: Option[MarkKind], words: SearchWords, size: PageSize): List[Bookmark] =
      val rows = db.read(BookmarkRepo.list(user, kind, Option.when(words.isEmpty)(size)))
      page(shown(rows.flatMap(_.stored(user)), items), words, size)

    def add(
        user: UserId,
        target: MarkTarget,
        label: Option[BookmarkLabel],
        title: Option[TargetLabel],
        at: MarkTime
    ): Bookmark =
      val t = resolved(target, items)
      val stored = db.transact:
        val id = t.itemRef.flatMap(BookmarkRepo.ofItem(user, _)) match
          case Some(existing) =>
            if label.isDefined then BookmarkRepo.relabel(user, existing, label, at): Unit
            existing
          case None => BookmarkRepo.insert(user, TargetColumns.of(t), label, title, at)
        BookmarkRepo
          .live(user, id)
          .flatMap(_.stored(user))
          .getOrElse(throw IllegalStateException(s"bookmark ${id.value} not read back"))
      shown(List(stored), items).head

    def rename(user: UserId, id: BookmarkId, label: Option[BookmarkLabel], at: MarkTime): Option[Bookmark] =
      db.transact(
        if BookmarkRepo.relabel(user, id, label, at) == 0 then None
        else BookmarkRepo.live(user, id).flatMap(_.stored(user))
      ).map(s => shown(List(s), items).head)

    def remove(user: UserId, id: BookmarkId, at: MarkTime): Option[Bookmark] =
      db.transact {
        val before = BookmarkRepo.live(user, id).flatMap(_.stored(user))
        before.foreach(_ => BookmarkRepo.tombstone(user, id, at))
        before
      }.map(s => shown(List(s), items).head)

  /** The bookmarks held in `state`, beside the notes and read marks of the same person, by the rules the table keeps.
    */
  final class InMemory(
      state: AtomicReference[PersonalState],
      items: Referables,
      newId: () => UUID = () => UUID.randomUUID()
  ) extends BookmarkService:

    private def live(s: PersonalState, user: UserId): Vector[StoredBookmark] =
      s.bookmarks.filter(b => b.user == user && b.deletedAt.isEmpty)

    private def changed(id: BookmarkId, user: UserId)(change: StoredBookmark => StoredBookmark): PersonalState =
      state.updateAndGet(
        _.modify(_.bookmarks)
          .using(_.map(b => if b.id == id && b.user == user && b.deletedAt.isEmpty then change(b) else b))
      )

    def list(user: UserId, kind: Option[MarkKind], words: SearchWords, size: PageSize): List[Bookmark] =
      val mine = live(state.get, user).filter(b => kind.forall(_ == b.target.kind))
      page(shown(PersonalState.newestFirst(mine)(_.createdAt, _.id.value), items), words, size)

    def add(
        user: UserId,
        target: MarkTarget,
        label: Option[BookmarkLabel],
        title: Option[TargetLabel],
        at: MarkTime
    ): Bookmark =
      val t = resolved(target, items)
      val id = BookmarkId.parse(newId().toString).fold(e => throw IllegalStateException(e), identity)
      val after = state.updateAndGet { s =>
        live(s, user).find(b =>
          t.itemRef.isDefined && b.target.itemRef.map(r => (r.kind, r.key)) == t.itemRef.map(r => (r.kind, r.key))
        ) match
          case Some(existing) if label.isDefined =>
            s.modify(_.bookmarks)
              .using(_.map(b => if b.id == existing.id then b.copy(label = label, updatedAt = at) else b))
          case Some(_) => s
          case None    => s.modify(_.bookmarks).using(_ :+ StoredBookmark(user, id, t, label, title, at, at))
      }
      val kept = live(after, user)
        .find(b => t.itemRef.fold(b.id == id)(r => b.target.itemRef.exists(x => x.kind == r.kind && x.key == r.key)))
        .getOrElse(throw IllegalStateException("bookmark not kept"))
      shown(List(kept), items).head

    def rename(user: UserId, id: BookmarkId, label: Option[BookmarkLabel], at: MarkTime): Option[Bookmark] =
      val after = changed(id, user)(_.copy(label = label, updatedAt = at))
      live(after, user).find(_.id == id).map(s => shown(List(s), items).head)

    def remove(user: UserId, id: BookmarkId, at: MarkTime): Option[Bookmark] =
      val found = live(state.get, user).find(_.id == id)
      found.foreach(_ => changed(id, user)(_.copy(deletedAt = Some(at), updatedAt = at)): Unit)
      found.map(s => shown(List(s), items).head)
