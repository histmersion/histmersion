package com.histmersion.api.personal

import com.histmersion.api.Api
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.ItemKind

object PersonalApi:

  private def errors = Api.writeErrors
  private def kindQuery =
    query[Option[String]]("kind").description("only these: " + ItemKind.values.map(_.wire).mkString(", "))
  private def wordsQuery = query[Option[String]]("q").description("words its name, its label or its text holds")
  private def limitQuery = query[Option[Int]]("limit")
    .description(s"how many (default ${PersonalIds.DefaultLimit}, at most ${PersonalIds.MaxLimit})")

  lazy val bookmarks: PublicEndpoint[(Option[String], Option[String], Option[Int]), Api.Error, List[Bookmark], Any] =
    endpoint.get
      .in("api" / "bookmarks")
      .in(kindQuery)
      .in(wordsQuery)
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[Bookmark]])

  lazy val addBookmark: PublicEndpoint[Bookmark.New, Api.Error, Bookmark, Any] =
    endpoint.post.in("api" / "bookmarks").in(jsonBody[Bookmark.New]).errorOut(errors).out(jsonBody[Bookmark])

  lazy val editBookmark: PublicEndpoint[(String, Bookmark.Edit), Api.Error, Bookmark, Any] =
    endpoint.patch
      .in("api" / "bookmarks" / path[String]("id"))
      .in(jsonBody[Bookmark.Edit])
      .errorOut(errors)
      .out(jsonBody[Bookmark])

  lazy val removeBookmark: PublicEndpoint[String, Api.Error, Bookmark, Any] =
    endpoint.delete.in("api" / "bookmarks" / path[String]("id")).errorOut(errors).out(jsonBody[Bookmark])

  lazy val notes
      : PublicEndpoint[(Option[String], Option[String], Option[String], Option[Int]), Api.Error, List[Note], Any] =
    endpoint.get
      .in("api" / "notes")
      .in(kindQuery)
      .in(query[Option[String]]("id").description("the item's key"))
      .in(wordsQuery)
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[Note]])

  lazy val note: PublicEndpoint[String, Api.Error, Note, Any] =
    endpoint.get.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])

  lazy val addNote: PublicEndpoint[Note.New, Api.Error, Note, Any] =
    endpoint.post.in("api" / "notes").in(jsonBody[Note.New]).errorOut(errors).out(jsonBody[Note])

  lazy val editNote: PublicEndpoint[(String, Note.Edit), Api.Error, Note, Any] =
    endpoint.patch.in("api" / "notes" / path[String]("id")).in(jsonBody[Note.Edit]).errorOut(errors).out(jsonBody[Note])

  lazy val removeNote: PublicEndpoint[String, Api.Error, Note, Any] =
    endpoint.delete.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])

  lazy val reading: PublicEndpoint[(Option[String], Option[String], Option[Int]), Api.Error, List[ReadMark], Any] =
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

  lazy val unread: PublicEndpoint[(Option[String], Option[Int], Option[Int]), Api.Error, UnreadPage, Any] =
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
