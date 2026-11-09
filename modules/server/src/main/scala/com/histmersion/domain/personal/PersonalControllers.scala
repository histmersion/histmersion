package com.histmersion
package domain.personal

import com.histmersion.util.problem.{Legacy, Problem}
import io.scalaland.chimney.dsl.*
import com.histmersion.api.{Paging, Write}
import com.histmersion.api.automation.Activity
import com.histmersion.api.personal.{
  Bookmark as WireBookmark,
  ItemKey as WireKey,
  Note as WireNote,
  PersonalApi,
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
import com.histmersion.api.library.Title
import com.histmersion.api.personal.Bookmark
import com.histmersion.domain.personal.notes.Note
import com.histmersion.api.personal.ReadMark
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Year

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

  private def bad(why: Problem): Api.Error = Api.Error(why)
  private def missing(what: String): Api.Error =
    Api.Error(Problem.Unclassified(Legacy.Text(s"No $what")), Some(Api.Error.Code(Write.CodeNotFound)))
  private def now(): MarkTime = MarkTime.at(Instant.now(clock))

  private def person(caller: Caller): Either[Api.Error, UserId] =
    caller.person.toRight(
      bad(
        Problem.Unclassified(
          Legacy.Text("bookmarks, notes and read marks are a person's own; an automated run has none")
        )
      )
    )

  private def kindOf(raw: Option[MarkKind.Wire], allowed: Set[MarkKind]): Either[Api.Error, Option[MarkKind]] =
    raw.map(_.value.trim).filter(_.nonEmpty) match
      case None    => Right(None)
      case Some(k) =>
        MarkKind
          .parse(MarkKind.Wire(k))
          .filter(allowed)
          .map(Some(_))
          .toRight(
            bad(
              Problem.Unclassified(
                Legacy.Text(s"kind '$k': one of ${MarkKind.values.filter(allowed).map(_.wire.value).mkString(", ")}")
              )
            )
          )

  private def size(raw: Option[Paging.Limit]): PageSize = PageSize.applyUnsafe(PersonalIds.limit(raw).value)

  private def search(q: Option[PersonalApi.Search]): SearchWords =
    SearchWords.of(q.map(w => SearchWords.Query(w.value)))

  private def label(title: Option[Title]): Option[TargetLabel] =
    TargetLabel.tidy(title.map(t => TargetLabel.Label(t.value)))

  private def target(t: Target, allowed: Set[MarkKind], what: String): Either[Api.Error, MarkTarget] =
    Target
      .check(t, allowed, what)
      .flatMap(MarkTarget.zoomChecked(_))
      .flatMap(
        MarkTarget
          .fromWire(_)
          .toRight(Problem.Unclassified(Legacy.Text(s"a $what on a ${t.kind.label.value.toLowerCase} names it")))
      )
      .left
      .map(bad)

  // ── bookmarks ───────────────────────────────────────────────────────────────────────────────────

  def listBookmarks(
      caller: Caller,
      kind: Option[MarkKind.Wire],
      q: Option[PersonalApi.Search],
      max: Option[Paging.Limit]
  ): Either[Api.Error, List[WireBookmark]] =
    for
      user <- person(caller)
      k <- kindOf(kind, MarkKind.Bookmarkable)
    yield bookmarks.list(user, k, search(q), size(max)).map(_.wire)

  def addBookmark(caller: Caller, in: Bookmark.New): Either[Api.Error, WireBookmark] =
    maintenance.guard(WriteGate.OperationName("add_bookmark")):
      for
        user <- person(caller)
        t <- target(in.target, MarkKind.Bookmarkable, "bookmark")
        label <- Bookmark.Label.tidy(in.label).left.map(p => bad(p))
      yield bookmarks.add(user, t, label, this.label(in.title), now()).wire

  def editBookmark(
      caller: Caller,
      id: WireBookmark.Id,
      in: Bookmark.Edit
  ): Either[Api.Error, WireBookmark] =
    maintenance.guard(WriteGate.OperationName("edit_bookmark")):
      for
        user <- person(caller)
        bid <- com.histmersion.domain.personal.bookmarks.Bookmark.idOf(id).left.map(p => bad(p))
        label <- Bookmark.Label.tidy(in.label).left.map(p => bad(p))
        b <- bookmarks.rename(user, bid, label, now()).toRight(missing(s"bookmark $id"))
      yield b.wire

  def removeBookmark(caller: Caller, id: WireBookmark.Id): Either[Api.Error, WireBookmark] =
    maintenance.guard(WriteGate.OperationName("remove_bookmark")):
      for
        user <- person(caller)
        bid <- com.histmersion.domain.personal.bookmarks.Bookmark.idOf(id).left.map(p => bad(p))
        b <- bookmarks.remove(user, bid, now()).toRight(missing(s"bookmark $id"))
      yield b.wire

  // ── notes ───────────────────────────────────────────────────────────────────────────────────────

  def listNotes(
      caller: Caller,
      kind: Option[MarkKind.Wire],
      itemKey: Option[WireKey],
      q: Option[PersonalApi.Search],
      max: Option[Paging.Limit]
  ): Either[Api.Error, List[WireNote]] =
    for
      user <- person(caller)
      k <- kindOf(kind, MarkKind.Notable)
    yield notes
      .list(user, k, itemKey.map(_.value.trim).filter(_.nonEmpty).map(ItemKey(_)), search(q), size(max))
      .map(_.wire)

  def note(caller: Caller, id: WireNote.Id): Either[Api.Error, WireNote] =
    for
      user <- person(caller)
      nid <- Note.idOf(id).left.map(p => bad(p))
      n <- notes.get(user, nid).toRight(missing(s"note $id"))
    yield n.wire

  def addNote(caller: Caller, in: com.histmersion.api.personal.Note.New): Either[Api.Error, WireNote] =
    maintenance.guard(WriteGate.OperationName("add_note")):
      for
        user <- person(caller)
        t <- target(in.target, MarkKind.Notable, "note")
        text <- com.histmersion.api.personal.Note.Text.tidy(in.text).left.map(p => bad(p))
      yield notes.add(user, t, text, label(in.title), now()).wire

  def editNote(
      caller: Caller,
      id: WireNote.Id,
      in: com.histmersion.api.personal.Note.Edit
  ): Either[Api.Error, WireNote] =
    maintenance.guard(WriteGate.OperationName("edit_note")):
      for
        user <- person(caller)
        nid <- Note.idOf(id).left.map(p => bad(p))
        text <- com.histmersion.api.personal.Note.Text.tidy(in.text).left.map(p => bad(p))
        n <- edited(user, nid, text, in.ifUpdatedAt.map(_.value.trim).filter(_.nonEmpty).map(MarkTime.Iso(_))).left
          .map {
            case Note.EditRefused.Missing      => missing(s"note $id")
            case Note.EditRefused.Changed(was) =>
              Api.Error(
                Problem.Unclassified(
                  Legacy.Text(s"the note changed since it was opened (now ${was.iso}); open it again")
                ),
                Some(Api.Error.Code(Write.CodePrecondition))
              )
          }
      yield n.wire

  /** A time last seen that is no time at all never matches the note's: the edit is refused as a stale one. */
  private def edited(
      user: UserId,
      id: com.histmersion.api.personal.Note.Id,
      text: com.histmersion.api.personal.Note.Text,
      seen: Option[MarkTime.Iso]
  ) =
    seen.map(s => Try(MarkTime.parse(s)).toOption) match
      case Some(None) =>
        notes
          .get(user, id)
          .toRight(Note.EditRefused.Missing)
          .flatMap(n => Left(Note.EditRefused.Changed(n.updatedAt)))
      case asked => notes.edit(user, id, text, asked.flatten, now())

  def removeNote(caller: Caller, id: WireNote.Id): Either[Api.Error, WireNote] =
    maintenance.guard(WriteGate.OperationName("remove_note")):
      for
        user <- person(caller)
        nid <- Note.idOf(id).left.map(p => bad(p))
        n <- notes.remove(user, nid, now()).toRight(missing(s"note $id"))
      yield n.wire

  // ── read marks ──────────────────────────────────────────────────────────────────────────────────

  def readMarks(
      caller: Caller,
      kind: Option[MarkKind.Wire],
      show: Option[Activity.Show.Wire],
      max: Option[Paging.Limit]
  ): Either[Api.Error, List[WireReadMark]] =
    for
      user <- person(caller)
      k <- kindOf(kind, MarkKind.Readable)
      s <- ReadingShow.parse(show.map(w => ReadingShow.Wire(w.value))).left.map(p => bad(p))
    yield reading.list(user, k.map(MarkTarget.itemKind), s, size(max)).map(_.wire)

  private def readable(kind: MarkKind, key: WireKey): Either[Api.Error, ItemKey] =
    if !MarkKind.Readable.contains(kind) then
      Left(
        bad(
          Problem.Unclassified(
            Legacy.Text(s"only sources and articles are marked read, not a ${kind.label.value.toLowerCase}")
          )
        )
      )
    else
      Option(key.value.trim)
        .filter(_.nonEmpty)
        .map(ItemKey(_))
        .toRight(bad(Problem.Unclassified(Legacy.Text("which one? (its id)"))))

  def setRead(caller: Caller, in: ReadMark.Set): Either[Api.Error, WireReadMark] =
    maintenance.guard(WriteGate.OperationName("set_read")):
      for
        user <- person(caller)
        key <- readable(in.kind, in.id)
      yield reading
        .setRead(user, MarkTarget.itemKind(in.kind), key, ReadingService.Read(in.read.value), label(in.title), now())
        .wire

  def opened(caller: Caller, in: ReadOpened): Either[Api.Error, WireReadMark] =
    maintenance.guard(WriteGate.OperationName("opened")):
      for
        user <- person(caller)
        key <- readable(in.kind, in.id)
      yield reading.opened(user, MarkTarget.itemKind(in.kind), key, label(in.title), now()).wire

  def unread(
      caller: Caller,
      kind: Option[MarkKind.Wire],
      max: Option[Paging.Limit],
      offset: Option[UnreadPage.Offset]
  ): Either[Api.Error, UnreadPage] =
    for
      user <- person(caller)
      k <- kindOf(kind, MarkKind.Readable)
    yield
      val shown = k.getOrElse(MarkKind.Source)
      val from = Offset.applyUnsafe(offset.map(_.value).filter(_ > 0).getOrElse(0))
      val page = reading.unread(user, MarkTarget.itemKind(shown), size(max), from)
      UnreadPage(
        page.items.map(i =>
          UnreadRow(
            shown,
            WireKey(i.key.value),
            Title(i.title.value),
            i.authors.map(a => AuthorName(a.value)),
            i.year.map(y => Year(y.value.toLong)),
            i.status.map(st => UnreadRow.Status(st.value)),
            i.periodStart.map(y => Year(y.value.toLong)),
            i.periodEnd.map(y => Year(y.value.toLong))
          )
        ),
        UnreadPage.Total(page.total.value),
        UnreadPage.Offset(from.value)
      )
