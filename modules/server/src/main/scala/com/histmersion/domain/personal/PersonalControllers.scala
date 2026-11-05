package com.histmersion.domain.personal

import com.histmersion.api.Write
import com.histmersion.api.personal.{
  Bookmark as WireBookmark,
  ItemKey as WireKey,
  Note as WireNote,
  PersonalIds,
  ReadMark as WireReadMark,
  ReadOpened,
  Target,
  UnreadPage,
  UnreadRow
}
import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.domain.{Caller, ItemKey}
import com.histmersion.domain.personal.bookmarks.BookmarkService
import com.histmersion.domain.personal.notes.NoteService
import com.histmersion.domain.personal.reading.{ReadingService, ReadingShow}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.{Offset, PageSize, UserId}

import java.time.{Clock, Instant}
import scala.util.Try
import com.histmersion.api.Api

/** The bookmarks', notes' and read marks' endpoints, the `bookmarks` and `notes` tools (reads only) and
  * `histmersion bookmarks|notes`: what was asked checked, then the caller's own. Writes wait while a restore holds the
  * maintenance lock.
  */
final class PersonalControllers(
    bookmarks: BookmarkService,
    notes: NoteService,
    reading: ReadingService,
    maintenance: WriteGate,
    clock: Clock
):

  private def bad(why: com.histmersion.domain.personal.PersonalControllers.BadWhy): Api.Error = Api.Error(why)
  private def missing(what: com.histmersion.domain.personal.PersonalControllers.MissingWhat): Api.Error = Api.Error(s"No $what", Some(Write.CodeNotFound))
  private def now(): MarkTime = MarkTime.at(Instant.now(clock))

  private def person(caller: Caller): Either[Api.Error, UserId] =
    caller.person.toRight(bad("bookmarks, notes and read marks are a person's own; an automated run has none"))

  private def kindOf(raw: Option[com.histmersion.domain.personal.PersonalControllers.KindOfRaw], allowed: Set[MarkKind]): Either[Api.Error, Option[MarkKind]] =
    raw.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(k) =>
        MarkKind
          .parse(k)
          .filter(allowed)
          .map(Some(_))
          .toRight(bad(s"kind '$k': one of ${MarkKind.values.filter(allowed).map(_.wire).mkString(", ")}"))

  private def size(raw: Option[com.histmersion.domain.personal.PersonalControllers.SizeRaw]): PageSize = PageSize.applyUnsafe(PersonalIds.limit(raw))

  private def target(t: Target, allowed: Set[MarkKind], what: com.histmersion.domain.personal.PersonalControllers.TargetWhat): Either[Api.Error, MarkTarget] =
    Target
      .check(t, allowed, what)
      .flatMap(MarkTarget.zoomChecked)
      .flatMap(MarkTarget.fromWire(_).toRight(s"a $what on a ${t.kind.label.toLowerCase} names it"))
      .left
      .map(bad)

  // ── bookmarks ───────────────────────────────────────────────────────────────────────────────────

  def listBookmarks(
      caller: Caller,
      kind: Option[com.histmersion.domain.personal.PersonalControllers.ListBookmarksKind],
      q: Option[com.histmersion.domain.personal.PersonalControllers.ListBookmarksQ],
      max: Option[com.histmersion.domain.personal.PersonalControllers.ListBookmarksMax]
  ): Either[Api.Error, List[WireBookmark]] =
    for
      user <- person(caller)
      k <- kindOf(kind, MarkKind.Bookmarkable)
    yield bookmarks.list(user, k, SearchWords.of(q), size(max)).map(_.wire)

  def addBookmark(caller: Caller, in: com.histmersion.api.personal.Bookmark.New): Either[Api.Error, WireBookmark] =
    maintenance.guard("add_bookmark"):
      for
        user <- person(caller)
        t <- target(in.target, MarkKind.Bookmarkable, "bookmark")
        label <- com.histmersion.api.personal.Bookmark.Label.tidy(in.label).left.map(bad)
      yield bookmarks.add(user, t, label, TargetLabel.tidy(in.title), now()).wire

  def editBookmark(
      caller: Caller,
      id: com.histmersion.domain.personal.PersonalControllers.EditBookmarkId,
      in: com.histmersion.api.personal.Bookmark.Edit
  ): Either[Api.Error, WireBookmark] =
    maintenance.guard("edit_bookmark"):
      for
        user <- person(caller)
        bid <- com.histmersion.domain.personal.bookmarks.Bookmark.idOf(id).left.map(bad)
        label <- com.histmersion.api.personal.Bookmark.Label.tidy(in.label).left.map(bad)
        b <- bookmarks.rename(user, bid, label, now()).toRight(missing(s"bookmark $id"))
      yield b.wire

  def removeBookmark(caller: Caller, id: com.histmersion.domain.personal.PersonalControllers.RemoveBookmarkId): Either[Api.Error, WireBookmark] =
    maintenance.guard("remove_bookmark"):
      for
        user <- person(caller)
        bid <- com.histmersion.domain.personal.bookmarks.Bookmark.idOf(id).left.map(bad)
        b <- bookmarks.remove(user, bid, now()).toRight(missing(s"bookmark $id"))
      yield b.wire

  // ── notes ───────────────────────────────────────────────────────────────────────────────────────

  def listNotes(
      caller: Caller,
      kind: Option[com.histmersion.domain.personal.PersonalControllers.ListNotesKind],
      itemKey: Option[com.histmersion.domain.personal.PersonalControllers.ListNotesItemKey],
      q: Option[com.histmersion.domain.personal.PersonalControllers.ListNotesQ],
      max: Option[com.histmersion.domain.personal.PersonalControllers.ListNotesMax]
  ): Either[Api.Error, List[WireNote]] =
    for
      user <- person(caller)
      k <- kindOf(kind, MarkKind.Notable)
    yield notes
      .list(user, k, itemKey.map(_.trim).filter(_.nonEmpty).map(ItemKey(_)), SearchWords.of(q), size(max))
      .map(_.wire)

  def note(caller: Caller, id: com.histmersion.domain.personal.PersonalControllers.NoteId): Either[Api.Error, WireNote] =
    for
      user <- person(caller)
      nid <- com.histmersion.domain.personal.notes.Note.idOf(id).left.map(bad)
      n <- notes.get(user, nid).toRight(missing(s"note $id"))
    yield n.wire

  def addNote(caller: Caller, in: com.histmersion.api.personal.Note.New): Either[Api.Error, WireNote] =
    maintenance.guard("add_note"):
      for
        user <- person(caller)
        t <- target(in.target, MarkKind.Notable, "note")
        text <- com.histmersion.api.personal.Note.Text.tidy(in.text).left.map(bad)
      yield notes.add(user, t, text, TargetLabel.tidy(in.title), now()).wire

  def editNote(caller: Caller, id: com.histmersion.domain.personal.PersonalControllers.EditNoteId, in: com.histmersion.api.personal.Note.Edit): Either[Api.Error, WireNote] =
    maintenance.guard("edit_note"):
      for
        user <- person(caller)
        nid <- com.histmersion.domain.personal.notes.Note.idOf(id).left.map(bad)
        text <- com.histmersion.api.personal.Note.Text.tidy(in.text).left.map(bad)
        n <- edited(user, nid, text, in.ifUpdatedAt.map(_.trim).filter(_.nonEmpty)).left.map {
          case com.histmersion.domain.personal.notes.Note.EditRefused.Missing      => missing(s"note $id")
          case com.histmersion.domain.personal.notes.Note.EditRefused.Changed(was) =>
            Api.Error(
              s"the note changed since it was opened (now ${was.iso}); open it again",
              Some(Write.CodePrecondition)
            )
        }
      yield n.wire

  /** A time last seen that is no time at all never matches the note's: the edit is refused as a stale one. */
  private def edited(
      user: UserId,
      id: com.histmersion.api.personal.Note.Id,
      text: com.histmersion.api.personal.Note.Text,
      seen: Option[com.histmersion.domain.personal.PersonalControllers.EditedSeen]
  ) =
    seen.map(s => Try(MarkTime.parse(s)).toOption) match
      case Some(None) =>
        notes
          .get(user, id)
          .toRight(com.histmersion.domain.personal.notes.Note.EditRefused.Missing)
          .flatMap(n => Left(com.histmersion.domain.personal.notes.Note.EditRefused.Changed(n.updatedAt)))
      case asked => notes.edit(user, id, text, asked.flatten, now())

  def removeNote(caller: Caller, id: com.histmersion.domain.personal.PersonalControllers.RemoveNoteId): Either[Api.Error, WireNote] =
    maintenance.guard("remove_note"):
      for
        user <- person(caller)
        nid <- com.histmersion.domain.personal.notes.Note.idOf(id).left.map(bad)
        n <- notes.remove(user, nid, now()).toRight(missing(s"note $id"))
      yield n.wire

  // ── read marks ──────────────────────────────────────────────────────────────────────────────────

  def readMarks(
      caller: Caller,
      kind: Option[com.histmersion.domain.personal.PersonalControllers.ReadMarksKind],
      show: Option[com.histmersion.domain.personal.PersonalControllers.ReadMarksShow],
      max: Option[com.histmersion.domain.personal.PersonalControllers.ReadMarksMax]
  ): Either[Api.Error, List[WireReadMark]] =
    for
      user <- person(caller)
      k <- kindOf(kind, MarkKind.Readable)
      s <- ReadingShow.parse(show).left.map(bad)
    yield reading.list(user, k.map(MarkTarget.itemKind), s, size(max)).map(_.wire)

  private def readable(kind: MarkKind, key: WireKey): Either[Api.Error, ItemKey] =
    if !MarkKind.Readable.contains(kind) then
      Left(bad(s"only sources and articles are marked read, not a ${kind.label.toLowerCase}"))
    else Option(key.value.trim).filter(_.nonEmpty).map(ItemKey(_)).toRight(bad("which one? (its id)"))

  def setRead(caller: Caller, in: com.histmersion.api.personal.ReadMark.Set): Either[Api.Error, WireReadMark] =
    maintenance.guard("set_read"):
      for
        user <- person(caller)
        key <- readable(in.kind, in.id)
      yield reading.setRead(user, MarkTarget.itemKind(in.kind), key, in.read, TargetLabel.tidy(in.title), now()).wire

  def opened(caller: Caller, in: ReadOpened): Either[Api.Error, WireReadMark] =
    maintenance.guard("opened"):
      for
        user <- person(caller)
        key <- readable(in.kind, in.id)
      yield reading.opened(user, MarkTarget.itemKind(in.kind), key, TargetLabel.tidy(in.title), now()).wire

  def unread(
      caller: Caller,
      kind: Option[com.histmersion.domain.personal.PersonalControllers.UnreadKind],
      max: Option[com.histmersion.domain.personal.PersonalControllers.UnreadMax],
      offset: Option[com.histmersion.domain.personal.PersonalControllers.UnreadOffset]
  ): Either[Api.Error, UnreadPage] =
    for
      user <- person(caller)
      k <- kindOf(kind, MarkKind.Readable)
    yield
      val shown = k.getOrElse(MarkKind.Source)
      val from = Offset.applyUnsafe(offset.filter(_ > 0).getOrElse(0))
      val page = reading.unread(user, MarkTarget.itemKind(shown), size(max), from)
      UnreadPage(
        page.items.map(i =>
          UnreadRow(
            shown,
            WireKey(i.key.value),
            i.title.value,
            i.authors.map(_.value),
            i.year.map(_.value),
            i.status.map(_.value),
            i.periodStart.map(_.value.toLong),
            i.periodEnd.map(_.value.toLong)
          )
        ),
        page.total,
        from.value
      )

object PersonalControllers:
  type BadWhy = BadWhy.T
  object BadWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MissingWhat = MissingWhat.T
  object MissingWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindOfRaw = KindOfRaw.T
  object KindOfRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SizeRaw = SizeRaw.T
  object SizeRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetWhat = TargetWhat.T
  object TargetWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListBookmarksKind = ListBookmarksKind.T
  object ListBookmarksKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListBookmarksQ = ListBookmarksQ.T
  object ListBookmarksQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListBookmarksMax = ListBookmarksMax.T
  object ListBookmarksMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditBookmarkId = EditBookmarkId.T
  object EditBookmarkId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveBookmarkId = RemoveBookmarkId.T
  object RemoveBookmarkId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListNotesKind = ListNotesKind.T
  object ListNotesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListNotesItemKey = ListNotesItemKey.T
  object ListNotesItemKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListNotesQ = ListNotesQ.T
  object ListNotesQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListNotesMax = ListNotesMax.T
  object ListNotesMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteId = NoteId.T
  object NoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditNoteId = EditNoteId.T
  object EditNoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditedSeen = EditedSeen.T
  object EditedSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveNoteId = RemoveNoteId.T
  object RemoveNoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadMarksKind = ReadMarksKind.T
  object ReadMarksKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadMarksShow = ReadMarksShow.T
  object ReadMarksShow extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadMarksMax = ReadMarksMax.T
  object ReadMarksMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreadKind = UnreadKind.T
  object UnreadKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreadMax = UnreadMax.T
  object UnreadMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreadOffset = UnreadOffset.T
  object UnreadOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
