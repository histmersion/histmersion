package com.histmersion.domain.personal

import com.histmersion.values.UserId

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.personal.*
import com.histmersion.domain.personal.PersonalState.StoredBookmark

import com.augustnagro.magnum.{connect, transact}

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** A person's bookmarks. A target reaches here checked ([[Target.check]]); an item is bookmarked once, so bookmarking
  * it again answers its bookmark (renamed when a label is given). A removed bookmark is kept as its tombstone.
  */
trait BookmarkService:
  def list(user: UserId, kind: Option[ItemKind], words: List[String], limit: Int): List[Bookmark]
  def add(user: UserId, target: Target, label: Option[BookmarkLabel], title: Option[String], at: Instant): Bookmark
  def rename(user: UserId, id: BookmarkId, label: Option[BookmarkLabel], at: Instant): Option[Bookmark]

  /** The bookmark as it was before it was removed. */
  def remove(user: UserId, id: BookmarkId, at: Instant): Option[Bookmark]

object BookmarkService:

  def apply(ds: DataSource): BookmarkService = Implementation(ds)

  final private class Implementation(ds: DataSource) extends BookmarkService:

    def list(user: UserId, kind: Option[ItemKind], words: List[String], limit: Int): List[Bookmark] =
      connect(ds)(BookmarkRepo.list(user, kind, words, limit).flatMap(_.bookmark))

    def add(user: UserId, target: Target, label: Option[BookmarkLabel], title: Option[String], at: Instant): Bookmark =
      transact(ds):
        val id = target.itemKey.flatMap(k => BookmarkRepo.ofItem(user, target.kind, k)) match
          case Some(existing) =>
            if label.isDefined then BookmarkRepo.relabel(user, existing, label, at): Unit
            existing
          case None =>
            BookmarkRepo.insert(user, target, label, title, ItemTitles.uidOf(target.kind, target.itemKey), at)
        BookmarkRepo
          .live(user, id)
          .flatMap(_.bookmark)
          .getOrElse(throw IllegalStateException(s"bookmark ${id.value} not read back"))

    def rename(user: UserId, id: BookmarkId, label: Option[BookmarkLabel], at: Instant): Option[Bookmark] =
      transact(ds):
        if BookmarkRepo.relabel(user, id, label, at) == 0 then None else BookmarkRepo.live(user, id).flatMap(_.bookmark)

    def remove(user: UserId, id: BookmarkId, at: Instant): Option[Bookmark] =
      transact(ds):
        val before = BookmarkRepo.live(user, id).flatMap(_.bookmark)
        before.foreach(_ => BookmarkRepo.tombstone(user, id, at))
        before

  /** The bookmarks held in `state`, beside the notes and read marks of the same person, by the rules the table keeps.
    */
  final class InMemory(
      state: AtomicReference[PersonalState] = AtomicReference(PersonalState()),
      newId: () => UUID = () => UUID.randomUUID()
  ) extends BookmarkService:

    private def view(s: PersonalState, b: StoredBookmark): Bookmark =
      Bookmark(
        b.id,
        b.target,
        PersonalRules.title(b.target, s.titleOf(b.target), b.targetLabel),
        b.label,
        PersonalRules.iso(b.createdAt),
        PersonalRules.iso(b.updatedAt)
      )

    private def live(s: PersonalState, user: UserId): Vector[StoredBookmark] =
      s.bookmarks.filter(b => b.user == user && b.deletedAt.isEmpty)

    def list(user: UserId, kind: Option[ItemKind], words: List[String], limit: Int): List[Bookmark] =
      val s = state.get
      val all = live(s, user)
        .filter(b => kind.forall(_ == b.target.kind))
        .map(view(s, _))
        .filter(b => PersonalRules.matches(words, b.label.fold("")(_.value), b.title, b.target.itemKey.getOrElse("")))
      PersonalRules.newestFirst(all)(b => Instant.parse(b.createdAt), _.id.value).take(limit)

    def add(user: UserId, target: Target, label: Option[BookmarkLabel], title: Option[String], at: Instant): Bookmark =
      val t = PersonalRules.kept(at)
      val after = state.updateAndGet { s =>
        live(s, user).find(b => target.identity.isDefined && b.target.identity == target.identity) match
          case Some(existing) if label.isDefined =>
            s.copy(bookmarks =
              s.bookmarks.map(b => if b.id == existing.id then b.copy(label = label, updatedAt = t) else b)
            )
          case Some(_) => s
          case None    =>
            val id = BookmarkId.parse(newId().toString).fold(e => throw IllegalStateException(e), identity)
            s.modify(_.bookmarks).using(_ :+ StoredBookmark(user, id, target, label, title, t, t))
      }
      live(after, user)
        .filter(b => target.identity.fold(b.createdAt == t)(i => b.target.identity.contains(i)))
        .lastOption
        .map(view(after, _))
        .getOrElse(throw IllegalStateException("bookmark not kept"))

    def rename(user: UserId, id: BookmarkId, label: Option[BookmarkLabel], at: Instant): Option[Bookmark] =
      val after = state.updateAndGet(s =>
        s.copy(bookmarks =
          s.bookmarks.map(b =>
            if b.id == id && b.user == user && b.deletedAt.isEmpty then
              b.copy(label = label, updatedAt = PersonalRules.kept(at))
            else b
          )
        )
      )
      live(after, user).find(_.id == id).map(view(after, _))

    def remove(user: UserId, id: BookmarkId, at: Instant): Option[Bookmark] =
      val before = state.get
      val found = live(before, user).find(_.id == id).map(view(before, _))
      found.foreach(_ =>
        state.updateAndGet(s =>
          s.copy(bookmarks =
            s.bookmarks.map(b =>
              if b.id == id && b.user == user then
                b.copy(deletedAt = Some(PersonalRules.kept(at)), updatedAt = PersonalRules.kept(at))
              else b
            )
          )
        )
      )
      found
