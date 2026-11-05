package com.histmersion.api.personal

import com.histmersion.api.Api
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.ItemKind

object PersonalApi:

  type BookmarksPart1Part1 = BookmarksPart1Part1.T
  object BookmarksPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BookmarksPart1Part2 = BookmarksPart1Part2.T
  object BookmarksPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BookmarksPart1Part3 = BookmarksPart1Part3.T
  object BookmarksPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditBookmarkPart1Part1 = EditBookmarkPart1Part1.T
  object EditBookmarkPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveBookmarkPart1 = RemoveBookmarkPart1.T
  object RemoveBookmarkPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesPart1Part1 = NotesPart1Part1.T
  object NotesPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesPart1Part2 = NotesPart1Part2.T
  object NotesPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesPart1Part3 = NotesPart1Part3.T
  object NotesPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotesPart1Part4 = NotesPart1Part4.T
  object NotesPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NotePart1 = NotePart1.T
  object NotePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EditNotePart1Part1 = EditNotePart1Part1.T
  object EditNotePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveNotePart1 = RemoveNotePart1.T
  object RemoveNotePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadingPart1Part1 = ReadingPart1Part1.T
  object ReadingPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadingPart1Part2 = ReadingPart1Part2.T
  object ReadingPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadingPart1Part3 = ReadingPart1Part3.T
  object ReadingPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreadPart1Part1 = UnreadPart1Part1.T
  object UnreadPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreadPart1Part2 = UnreadPart1Part2.T
  object UnreadPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnreadPart1Part3 = UnreadPart1Part3.T
  object UnreadPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def errors = Api.writeErrors
  private def kindQuery =
    query[Option[String]]("kind").description("only these: " + ItemKind.values.map(_.wire).mkString(", "))
  private def wordsQuery = query[Option[String]]("q").description("words its name, its label or its text holds")
  private def limitQuery = query[Option[Int]]("limit")
    .description(s"how many (default ${PersonalIds.DefaultLimit}, at most ${PersonalIds.MaxLimit})")

  lazy val bookmarks: PublicEndpoint[(Option[com.histmersion.api.personal.PersonalApi.BookmarksPart1Part1], Option[com.histmersion.api.personal.PersonalApi.BookmarksPart1Part2], Option[com.histmersion.api.personal.PersonalApi.BookmarksPart1Part3]), Api.Error, List[Bookmark], Any] =
    endpoint.get
      .in("api" / "bookmarks")
      .in(kindQuery)
      .in(wordsQuery)
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[Bookmark]])

  lazy val addBookmark: PublicEndpoint[Bookmark.New, Api.Error, Bookmark, Any] =
    endpoint.post.in("api" / "bookmarks").in(jsonBody[Bookmark.New]).errorOut(errors).out(jsonBody[Bookmark])

  lazy val editBookmark: PublicEndpoint[(com.histmersion.api.personal.PersonalApi.EditBookmarkPart1Part1, Bookmark.Edit), Api.Error, Bookmark, Any] =
    endpoint.patch
      .in("api" / "bookmarks" / path[String]("id"))
      .in(jsonBody[Bookmark.Edit])
      .errorOut(errors)
      .out(jsonBody[Bookmark])

  lazy val removeBookmark: PublicEndpoint[com.histmersion.api.personal.PersonalApi.RemoveBookmarkPart1, Api.Error, Bookmark, Any] =
    endpoint.delete.in("api" / "bookmarks" / path[String]("id")).errorOut(errors).out(jsonBody[Bookmark])

  lazy val notes
      : PublicEndpoint[(Option[com.histmersion.api.personal.PersonalApi.NotesPart1Part1], Option[com.histmersion.api.personal.PersonalApi.NotesPart1Part2], Option[com.histmersion.api.personal.PersonalApi.NotesPart1Part3], Option[com.histmersion.api.personal.PersonalApi.NotesPart1Part4]), Api.Error, List[Note], Any] =
    endpoint.get
      .in("api" / "notes")
      .in(kindQuery)
      .in(query[Option[String]]("id").description("the item's key"))
      .in(wordsQuery)
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[Note]])

  lazy val note: PublicEndpoint[com.histmersion.api.personal.PersonalApi.NotePart1, Api.Error, Note, Any] =
    endpoint.get.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])

  lazy val addNote: PublicEndpoint[Note.New, Api.Error, Note, Any] =
    endpoint.post.in("api" / "notes").in(jsonBody[Note.New]).errorOut(errors).out(jsonBody[Note])

  lazy val editNote: PublicEndpoint[(com.histmersion.api.personal.PersonalApi.EditNotePart1Part1, Note.Edit), Api.Error, Note, Any] =
    endpoint.patch.in("api" / "notes" / path[String]("id")).in(jsonBody[Note.Edit]).errorOut(errors).out(jsonBody[Note])

  lazy val removeNote: PublicEndpoint[com.histmersion.api.personal.PersonalApi.RemoveNotePart1, Api.Error, Note, Any] =
    endpoint.delete.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])

  lazy val reading: PublicEndpoint[(Option[com.histmersion.api.personal.PersonalApi.ReadingPart1Part1], Option[com.histmersion.api.personal.PersonalApi.ReadingPart1Part2], Option[com.histmersion.api.personal.PersonalApi.ReadingPart1Part3]), Api.Error, List[ReadMark], Any] =
    endpoint.get
      .in("api" / "reading")
      .in(kindQuery)
      .in(query[Option[String]]("show").description("read (default), opened or all"))
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[ReadMark]])

  lazy val setRead: PublicEndpoint[ReadMark.Set, Api.Error, ReadMark, Any] =
    endpoint.put.in("api" / "reading").in(jsonBody[ReadMark.Set]).errorOut(errors).out(jsonBody[ReadMark])

  lazy val opened: PublicEndpoint[ReadOpened, Api.Error, ReadMark, Any] =
    endpoint.post.in("api" / "reading" / "opened").in(jsonBody[ReadOpened]).errorOut(errors).out(jsonBody[ReadMark])

  lazy val unread: PublicEndpoint[(Option[com.histmersion.api.personal.PersonalApi.UnreadPart1Part1], Option[com.histmersion.api.personal.PersonalApi.UnreadPart1Part2], Option[com.histmersion.api.personal.PersonalApi.UnreadPart1Part3]), Api.Error, UnreadPage, Any] =
    endpoint.get
      .in("api" / "reading" / "unread")
      .in(kindQuery)
      .in(limitQuery)
      .in(query[Option[Int]]("offset").description("the first row of the page"))
      .errorOut(errors)
      .out(jsonBody[UnreadPage])

  lazy val all: List[AnyEndpoint] =
    List(
      bookmarks,
      addBookmark,
      editBookmark,
      removeBookmark,
      notes,
      note,
      addNote,
      editNote,
      removeNote,
      reading,
      setRead,
      opened,
      unread
    )
