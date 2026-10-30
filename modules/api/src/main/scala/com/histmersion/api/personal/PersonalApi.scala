package com.histmersion.api.personal

import com.histmersion.api.{Api, ApiError}
import sttp.tapir.*
import sttp.tapir.json.circe.*

object PersonalApi:

  private def errors = Api.writeErrors
  private def kindQuery =
    query[Option[String]]("kind").description("only these: " + ItemKind.values.map(_.wire).mkString(", "))
  private def wordsQuery = query[Option[String]]("q").description("words its name, its label or its text holds")
  private def limitQuery = query[Option[Int]]("limit")
    .description(s"how many (default ${PersonalIds.DefaultLimit}, at most ${PersonalIds.MaxLimit})")

  lazy val bookmarks: PublicEndpoint[(Option[String], Option[String], Option[Int]), ApiError, List[Bookmark], Any] =
    endpoint.get
      .in("api" / "bookmarks")
      .in(kindQuery)
      .in(wordsQuery)
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[Bookmark]])

  lazy val addBookmark: PublicEndpoint[BookmarkNew, ApiError, Bookmark, Any] =
    endpoint.post.in("api" / "bookmarks").in(jsonBody[BookmarkNew]).errorOut(errors).out(jsonBody[Bookmark])

  lazy val editBookmark: PublicEndpoint[(String, BookmarkEdit), ApiError, Bookmark, Any] =
    endpoint.patch
      .in("api" / "bookmarks" / path[String]("id"))
      .in(jsonBody[BookmarkEdit])
      .errorOut(errors)
      .out(jsonBody[Bookmark])

  lazy val removeBookmark: PublicEndpoint[String, ApiError, Bookmark, Any] =
    endpoint.delete.in("api" / "bookmarks" / path[String]("id")).errorOut(errors).out(jsonBody[Bookmark])

  lazy val notes
      : PublicEndpoint[(Option[String], Option[String], Option[String], Option[Int]), ApiError, List[Note], Any] =
    endpoint.get
      .in("api" / "notes")
      .in(kindQuery)
      .in(query[Option[String]]("id").description("the item's key"))
      .in(wordsQuery)
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[Note]])

  lazy val note: PublicEndpoint[String, ApiError, Note, Any] =
    endpoint.get.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])

  lazy val addNote: PublicEndpoint[NoteNew, ApiError, Note, Any] =
    endpoint.post.in("api" / "notes").in(jsonBody[NoteNew]).errorOut(errors).out(jsonBody[Note])

  lazy val editNote: PublicEndpoint[(String, NoteEdit), ApiError, Note, Any] =
    endpoint.patch.in("api" / "notes" / path[String]("id")).in(jsonBody[NoteEdit]).errorOut(errors).out(jsonBody[Note])

  lazy val removeNote: PublicEndpoint[String, ApiError, Note, Any] =
    endpoint.delete.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])

  lazy val reading: PublicEndpoint[(Option[String], Option[String], Option[Int]), ApiError, List[ReadMark], Any] =
    endpoint.get
      .in("api" / "reading")
      .in(kindQuery)
      .in(query[Option[String]]("show").description("read (default), opened or all"))
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[ReadMark]])

  lazy val setRead: PublicEndpoint[ReadMarkSet, ApiError, ReadMark, Any] =
    endpoint.put.in("api" / "reading").in(jsonBody[ReadMarkSet]).errorOut(errors).out(jsonBody[ReadMark])

  lazy val opened: PublicEndpoint[ReadOpened, ApiError, ReadMark, Any] =
    endpoint.post.in("api" / "reading" / "opened").in(jsonBody[ReadOpened]).errorOut(errors).out(jsonBody[ReadMark])

  lazy val unread: PublicEndpoint[(Option[String], Option[Int], Option[Int]), ApiError, UnreadPage, Any] =
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
