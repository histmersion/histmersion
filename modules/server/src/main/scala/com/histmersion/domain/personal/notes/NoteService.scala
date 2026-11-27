package com.histmersion
package domain.personal.notes

import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.domain.{ItemKey, Referables}
import com.histmersion.domain.personal.PersonalState.StoredNote
import com.histmersion.domain.personal.{MarkTarget, MarkTime, PersonalState, SearchWords, TargetColumns, TargetLabel}
import com.histmersion.util.database.Transactor
import com.histmersion.{PageSize, UserId}

import hearth.kindlings.optics.syntax.*

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** A person's notes, each left on an item or a point on the map, which it opens back to. A target and a text reach here
  * checked; a removed note is kept as its tombstone. An item's key and title are its owner's as they are when the notes
  * are read.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
trait NoteService:
  /** Last changed first, `size` of them: of one kind, on one item (by its key now or the key it was left under), those
    * whose text, title or key hold every word.
    */
  def list(
      user: UserId,
      kind: Option[MarkKind],
      item: Option[ItemKey],
      words: SearchWords,
      size: PageSize
  ): List[Note]
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def get(user: UserId, id: com.histmersion.api.personal.Note.Id): Option[Note]
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def add(
      user: UserId,
      target: MarkTarget,
      text: com.histmersion.api.personal.Note.Text,
      title: Option[TargetLabel],
      at: MarkTime
  )(using WritePermit)(using Creator.New): Note

  /** The note's new text, unless it is gone or changed since `unchangedSince`. */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def edit(
      user: UserId,
      id: com.histmersion.api.personal.Note.Id,
      text: com.histmersion.api.personal.Note.Text,
      unchangedSince: Option[MarkTime],
      at: MarkTime
  )(using WritePermit): Either[Note.EditRefused, Note]

  /** The note as it was before it was removed. */
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def remove(user: UserId, id: com.histmersion.api.personal.Note.Id, at: MarkTime)(using WritePermit): Option[Note]

object NoteService:

  def apply(db: Transactor, items: Referables): NoteService = Implementation(db, items)

  private def shown(stored: List[StoredNote], items: Referables): List[Note] =
    val described = items.describe(stored.flatMap(_.target.itemRef).toSet)
    stored.map(Note.of(_, described))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def page(all: List[Note], words: SearchWords, size: PageSize): List[Note] =
    (if words.isEmpty then all else all.filter(_.matches(words))).take(size.value)

  /** The key, with the uids of the records of `kind` (of every kind when none) it names now. */
  private def noteItem(key: ItemKey, kind: Option[MarkKind], items: Referables): Note.Item =
    val kinds = kind.filter(_.isItem).fold(items.kinds)(k => Set(MarkTarget.itemKind(k)))
    Note.Item(key, kinds.flatMap(k => items.resolve(k, key).toOption.flatMap(_.uid)))

  private def resolved(target: MarkTarget, items: Referables): MarkTarget =
    target.itemRef.fold(target)(r => target.resolved(items.resolve(r.kind, r.key).toOption.flatMap(_.uid))).withoutZoom

  final private class Implementation(db: Transactor, items: Referables) extends NoteService:

    def list(
        user: UserId,
        kind: Option[MarkKind],
        item: Option[ItemKey],
        words: SearchWords,
        size: PageSize
    ): List[Note] =
      val about = item.map(noteItem(_, kind, items))
      val rows = db.read(NoteRepo.list(user, kind, about, Option.when(words.isEmpty)(size)))
      page(shown(rows.flatMap(_.stored(user)), items), words, size)

    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def get(user: UserId, id: com.histmersion.api.personal.Note.Id): Option[Note] =
      db.read(NoteRepo.live(user, id).flatMap(_.stored(user))).map(s => shown(List(s), items).head)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def add(
        user: UserId,
        target: MarkTarget,
        text: com.histmersion.api.personal.Note.Text,
        title: Option[TargetLabel],
        at: MarkTime
    )(using WritePermit)(using Creator.New): Note =
      val t = resolved(target, items)
      val stored = db.write:
        val id = NoteRepo.insert(user, TargetColumns.of(t), text, title, at)
        NoteRepo
          .live(user, id)
          .flatMap(_.stored(user))
          .getOrElse(throw IllegalStateException(s"note ${id.value} not read back"))
      shown(List(stored), items).head

    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def edit(
        user: UserId,
        id: com.histmersion.api.personal.Note.Id,
        text: com.histmersion.api.personal.Note.Text,
        unchangedSince: Option[MarkTime],
        at: MarkTime
    )(using WritePermit): Either[Note.EditRefused, Note] =
      db.write {
        NoteRepo.live(user, id).flatMap(_.stored(user)) match
          case None                                               => Left(Note.EditRefused.Missing)
          case Some(n) if unchangedSince.exists(_ != n.updatedAt) => Left(Note.EditRefused.Changed(n.updatedAt))
          case Some(_)                                            =>
            NoteRepo.rewrite(user, id, text, at): Unit
            NoteRepo.live(user, id).flatMap(_.stored(user)).toRight(Note.EditRefused.Missing)
      }.map(s => shown(List(s), items).head)

    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def remove(user: UserId, id: com.histmersion.api.personal.Note.Id, at: MarkTime)(using WritePermit): Option[Note] =
      db.write {
        val before = NoteRepo.live(user, id).flatMap(_.stored(user))
        before.foreach(_ => NoteRepo.tombstone(user, id, at))
        before
      }.map(s => shown(List(s), items).head)

  /** The notes held in `state`, beside the bookmarks and read marks of the same person, by the rules the table keeps.
    */
  final class InMemory(
      state: AtomicReference[PersonalState],
      items: Referables,
      newId: () => UUID = () => UUID.randomUUID()
  ) extends NoteService:

    private def live(s: PersonalState, user: UserId): Vector[StoredNote] =
      s.notes.filter(n => n.user == user && n.deletedAt.isEmpty)

    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    private def stored(user: UserId, id: com.histmersion.api.personal.Note.Id): Option[StoredNote] =
      live(state.get, user).find(_.id == id)

    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    private def changed(user: UserId, id: com.histmersion.api.personal.Note.Id)(
        change: StoredNote => StoredNote
    ): Unit =
      state.updateAndGet(
        _.modify(_.notes)
          .using(_.map(n => if n.id == id && n.user == user && n.deletedAt.isEmpty then change(n) else n))
      ): Unit

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def list(
        user: UserId,
        kind: Option[MarkKind],
        item: Option[ItemKey],
        words: SearchWords,
        size: PageSize
    ): List[Note] =
      val about = item.map(noteItem(_, kind, items))
      val mine = live(state.get, user).filter(n => kind.forall(_ == n.target.kind) && about.forall(_.holds(n)))
      page(shown(PersonalState.newestFirst(mine)(_.updatedAt, _.id.value), items), words, size)

    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def get(user: UserId, id: com.histmersion.api.personal.Note.Id): Option[Note] =
      stored(user, id).map(s => shown(List(s), items).head)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def add(
        user: UserId,
        target: MarkTarget,
        text: com.histmersion.api.personal.Note.Text,
        title: Option[TargetLabel],
        at: MarkTime
    )(using WritePermit)(using Creator.New): Note =
      val id =
        com.histmersion.api.personal.Note.Id(newId().toString)
      val note = StoredNote(user, id, resolved(target, items), text, title, at, at)
      state.updateAndGet(_.modify(_.notes).using(_ :+ note)): Unit
      shown(List(note), items).head

    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def edit(
        user: UserId,
        id: com.histmersion.api.personal.Note.Id,
        text: com.histmersion.api.personal.Note.Text,
        unchangedSince: Option[MarkTime],
        at: MarkTime
    )(using WritePermit): Either[Note.EditRefused, Note] =
      stored(user, id) match
        case None                                               => Left(Note.EditRefused.Missing)
        case Some(n) if unchangedSince.exists(_ != n.updatedAt) => Left(Note.EditRefused.Changed(n.updatedAt))
        case Some(_)                                            =>
          changed(user, id)(_.modify(_.text).setTo(text).modify(_.updatedAt).setTo(at))
          get(user, id).toRight(Note.EditRefused.Missing)

    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def remove(user: UserId, id: com.histmersion.api.personal.Note.Id, at: MarkTime)(using WritePermit): Option[Note] =
      val found = get(user, id)
      found.foreach(_ => changed(user, id)(_.modify(_.deletedAt).setTo(Some(at)).modify(_.updatedAt).setTo(at)))
      found
