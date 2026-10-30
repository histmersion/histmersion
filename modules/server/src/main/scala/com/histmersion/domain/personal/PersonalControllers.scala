package com.histmersion.domain.personal

import com.histmersion.api.{ApiError, Write}
import com.histmersion.api.personal.*
import com.histmersion.server.Maintenance

import java.time.Instant

/** The bookmarks', notes' and read marks' endpoints, the `bookmarks` and `notes` tools (reads only) and
  * `histmersion bookmarks|notes`: what was asked checked, then the person's own (`user`). Writes wait while a restore
  * holds the maintenance lock.
  */
final class PersonalControllers(
    bookmarks: BookmarkService,
    notes: NoteService,
    reading: ReadingService,
    maintenance: Maintenance,
    now: () => Instant,
    user: UserId = UserId.Local
):

  private def bad(why: String): ApiError = ApiError(why)
  private def missing(what: String): ApiError = ApiError(s"No $what", Some(Write.CodeNotFound))

  private def kindOf(raw: Option[String], allowed: Set[ItemKind]): Either[ApiError, Option[ItemKind]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(k) =>
        ItemKind
          .parse(k)
          .filter(allowed)
          .map(Some(_))
          .toRight(bad(s"kind '$k': one of ${ItemKind.values.filter(allowed).map(_.wire).mkString(", ")}"))

  private def limit(raw: Option[Int]): Int = PersonalIds.limit(raw)

  // ── bookmarks ───────────────────────────────────────────────────────────────────────────────────

  def listBookmarks(kind: Option[String], q: Option[String], max: Option[Int]): Either[ApiError, List[Bookmark]] =
    kindOf(kind, ItemKind.Bookmarkable).map(k => bookmarks.list(user, k, PersonalRules.words(q), limit(max)))

  def addBookmark(in: BookmarkNew): Either[ApiError, Bookmark] =
    maintenance.guard("add_bookmark"):
      for
        t <- Target.check(in.target, ItemKind.Bookmarkable, "bookmark").left.map(bad)
        label <- PersonalRules.label(in.label).left.map(bad)
      yield bookmarks.add(user, t, label, PersonalRules.storedTitle(in.title), now())

  def editBookmark(id: String, in: BookmarkEdit): Either[ApiError, Bookmark] =
    maintenance.guard("edit_bookmark"):
      for
        bid <- BookmarkId.parse(id).left.map(bad)
        label <- PersonalRules.label(in.label).left.map(bad)
        b <- bookmarks.rename(user, bid, label, now()).toRight(missing(s"bookmark $id"))
      yield b

  def removeBookmark(id: String): Either[ApiError, Bookmark] =
    maintenance.guard("remove_bookmark"):
      BookmarkId
        .parse(id)
        .left
        .map(bad)
        .flatMap(bid => bookmarks.remove(user, bid, now()).toRight(missing(s"bookmark $id")))

  // ── notes ───────────────────────────────────────────────────────────────────────────────────────

  def listNotes(
      kind: Option[String],
      itemKey: Option[String],
      q: Option[String],
      max: Option[Int]
  ): Either[ApiError, List[Note]] =
    kindOf(kind, ItemKind.Notable).map(k =>
      notes.list(user, k, itemKey.map(_.trim).filter(_.nonEmpty), PersonalRules.words(q), limit(max))
    )

  def note(id: String): Either[ApiError, Note] =
    NoteId.parse(id).left.map(bad).flatMap(nid => notes.get(user, nid).toRight(missing(s"note $id")))

  def addNote(in: NoteNew): Either[ApiError, Note] =
    maintenance.guard("add_note"):
      for
        t <- Target.check(in.target, ItemKind.Notable, "note").left.map(bad)
        text <- PersonalRules.text(in.text).left.map(bad)
      yield notes.add(user, t, text, PersonalRules.storedTitle(in.title), now())

  def editNote(id: String, in: NoteEdit): Either[ApiError, Note] =
    maintenance.guard("edit_note"):
      for
        nid <- NoteId.parse(id).left.map(bad)
        text <- PersonalRules.text(in.text).left.map(bad)
        n <- notes.edit(user, nid, text, in.ifUpdatedAt.map(_.trim).filter(_.nonEmpty), now()).left.map {
          case NoteEditRefused.Missing      => missing(s"note $id")
          case NoteEditRefused.Changed(was) =>
            ApiError(s"the note changed since it was opened (now $was); open it again", Some(Write.CodePrecondition))
        }
      yield n

  def removeNote(id: String): Either[ApiError, Note] =
    maintenance.guard("remove_note"):
      NoteId.parse(id).left.map(bad).flatMap(nid => notes.remove(user, nid, now()).toRight(missing(s"note $id")))

  // ── read marks ──────────────────────────────────────────────────────────────────────────────────

  def readMarks(kind: Option[String], show: Option[String], max: Option[Int]): Either[ApiError, List[ReadMark]] =
    for
      k <- kindOf(kind, ItemKind.Readable)
      s <- ReadingShow.parse(show).left.map(bad)
    yield reading.list(user, k, s, limit(max))

  private def readable(kind: ItemKind, key: ItemKey): Either[ApiError, ItemKey] =
    if !ItemKind.Readable.contains(kind) then
      Left(bad(s"only sources and articles are marked read, not a ${kind.label.toLowerCase}"))
    else Option(key.value.trim).filter(_.nonEmpty).map(ItemKey(_)).toRight(bad("which one? (its id)"))

  def setRead(in: ReadMarkSet): Either[ApiError, ReadMark] =
    maintenance.guard("set_read"):
      readable(in.kind, in.id).map(k =>
        reading.setRead(user, in.kind, k, in.read, PersonalRules.storedTitle(in.title), now())
      )

  def opened(in: ReadOpened): Either[ApiError, ReadMark] =
    maintenance.guard("opened"):
      readable(in.kind, in.id).map(k => reading.opened(user, in.kind, k, PersonalRules.storedTitle(in.title), now()))

  def unread(kind: Option[String], max: Option[Int], offset: Option[Int]): Either[ApiError, UnreadPage] =
    kindOf(kind, ItemKind.Readable).map(k =>
      reading.unread(user, k.getOrElse(ItemKind.Source), limit(max), offset.filter(_ > 0).getOrElse(0))
    )
