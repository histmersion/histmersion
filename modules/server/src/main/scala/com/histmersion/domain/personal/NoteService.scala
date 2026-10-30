package com.histmersion.domain.personal

import com.histmersion.values.UserId

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.personal.*
import com.histmersion.domain.personal.PersonalState.StoredNote

import com.augustnagro.magnum.{connect, transact}

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/** A person's notes, each left on an item or a point on the map, which it opens back to. A target and a text reach here
  * checked; a removed note is kept as its tombstone.
  */
trait NoteService:
  def list(user: UserId, kind: Option[ItemKind], itemKey: Option[String], words: List[String], limit: Int): List[Note]
  def get(user: UserId, id: NoteId): Option[Note]
  def add(user: UserId, target: Target, text: NoteText, title: Option[String], at: Instant): Note

  /** The note's new text, unless it is gone or changed since `ifUpdatedAt`. */
  def edit(
      user: UserId,
      id: NoteId,
      text: NoteText,
      ifUpdatedAt: Option[String],
      at: Instant
  ): Either[NoteEditRefused, Note]

  /** The note as it was before it was removed. */
  def remove(user: UserId, id: NoteId, at: Instant): Option[Note]

object NoteService:

  def apply(ds: DataSource): NoteService = Implementation(ds)

  final private class Implementation(ds: DataSource) extends NoteService:

    def list(
        user: UserId,
        kind: Option[ItemKind],
        itemKey: Option[String],
        words: List[String],
        limit: Int
    ): List[Note] =
      connect(ds)(NoteRepo.list(user, kind, itemKey, words, limit).flatMap(_.note))

    def get(user: UserId, id: NoteId): Option[Note] = connect(ds)(NoteRepo.live(user, id).flatMap(_.note))

    def add(user: UserId, target: Target, text: NoteText, title: Option[String], at: Instant): Note =
      transact(ds):
        val id = NoteRepo.insert(user, target, text, title, ItemTitles.uidOf(target.kind, target.itemKey), at)
        NoteRepo
          .live(user, id)
          .flatMap(_.note)
          .getOrElse(throw IllegalStateException(s"note ${id.value} not read back"))

    def edit(
        user: UserId,
        id: NoteId,
        text: NoteText,
        ifUpdatedAt: Option[String],
        at: Instant
    ): Either[NoteEditRefused, Note] =
      transact(ds):
        NoteRepo.live(user, id).flatMap(_.note) match
          case None                                            => Left(NoteEditRefused.Missing)
          case Some(n) if ifUpdatedAt.exists(_ != n.updatedAt) => Left(NoteEditRefused.Changed(n.updatedAt))
          case Some(_)                                         =>
            NoteRepo.rewrite(user, id, text, at): Unit
            NoteRepo.live(user, id).flatMap(_.note).toRight(NoteEditRefused.Missing)

    def remove(user: UserId, id: NoteId, at: Instant): Option[Note] =
      transact(ds):
        val before = NoteRepo.live(user, id).flatMap(_.note)
        before.foreach(_ => NoteRepo.tombstone(user, id, at))
        before

  /** The notes held in `state`, beside the bookmarks and read marks of the same person, by the rules the table keeps.
    */
  final class InMemory(
      state: AtomicReference[PersonalState] = AtomicReference(PersonalState()),
      newId: () => UUID = () => UUID.randomUUID()
  ) extends NoteService:

    private def view(s: PersonalState, n: StoredNote): Note =
      Note(
        n.id,
        n.target,
        n.text,
        PersonalRules.title(n.target, s.titleOf(n.target), n.targetLabel),
        PersonalRules.iso(n.createdAt),
        PersonalRules.iso(n.updatedAt)
      )

    private def live(s: PersonalState, user: UserId): Vector[StoredNote] =
      s.notes.filter(n => n.user == user && n.deletedAt.isEmpty)

    def list(
        user: UserId,
        kind: Option[ItemKind],
        itemKey: Option[String],
        words: List[String],
        limit: Int
    ): List[Note] =
      val s = state.get
      val all = live(s, user)
        .filter(n => kind.forall(_ == n.target.kind) && itemKey.forall(k => n.target.itemKey.contains(k)))
        .map(view(s, _))
        .filter(n => PersonalRules.matches(words, n.text.value, n.title, n.target.itemKey.getOrElse("")))
      PersonalRules.newestFirst(all)(n => Instant.parse(n.updatedAt), _.id.value).take(limit)

    def get(user: UserId, id: NoteId): Option[Note] =
      val s = state.get
      live(s, user).find(_.id == id).map(view(s, _))

    def add(user: UserId, target: Target, text: NoteText, title: Option[String], at: Instant): Note =
      val t = PersonalRules.kept(at)
      val id = NoteId.parse(newId().toString).fold(e => throw IllegalStateException(e), identity)
      val after = state.updateAndGet(s => s.modify(_.notes).using(_ :+ StoredNote(user, id, target, text, title, t, t)))
      view(after, after.notes.last)

    def edit(
        user: UserId,
        id: NoteId,
        text: NoteText,
        ifUpdatedAt: Option[String],
        at: Instant
    ): Either[NoteEditRefused, Note] =
      get(user, id) match
        case None                                            => Left(NoteEditRefused.Missing)
        case Some(n) if ifUpdatedAt.exists(_ != n.updatedAt) => Left(NoteEditRefused.Changed(n.updatedAt))
        case Some(_)                                         =>
          state.updateAndGet(s =>
            s.copy(notes =
              s.notes.map(n =>
                if n.id == id && n.user == user && n.deletedAt.isEmpty then
                  n.copy(text = text, updatedAt = PersonalRules.kept(at))
                else n
              )
            )
          )
          get(user, id).toRight(NoteEditRefused.Missing)

    def remove(user: UserId, id: NoteId, at: Instant): Option[Note] =
      val found = get(user, id)
      found.foreach(_ =>
        state.updateAndGet(s =>
          s.copy(notes =
            s.notes.map(n =>
              if n.id == id && n.user == user then
                n.copy(deletedAt = Some(PersonalRules.kept(at)), updatedAt = PersonalRules.kept(at))
              else n
            )
          )
        )
      )
      found
