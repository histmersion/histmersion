package com.histmersion.api.personal

import com.histmersion.api.{Api, ApiError}
import sttp.tapir.*
import sttp.tapir.json.circe.*

/** A person's bookmarks, notes and read marks: the endpoints (definitions only). Reads are also the read-only
  * `bookmarks` and `notes` tools, so agents can read what the person kept; only the web UI and
  * `histmersion bookmarks|notes` write them.
  */
object PersonalApi:

  private def errors = Api.writeErrors
  private def kindQuery =
    query[Option[String]]("kind").description("only these: " + ItemKind.values.map(_.wire).mkString(", "))
  private def wordsQuery = query[Option[String]]("q").description("words its name, its label or its text holds")
  private def limitQuery = query[Option[Int]]("limit")
    .description(s"how many (default ${PersonalIds.DefaultLimit}, at most ${PersonalIds.MaxLimit})")

  /** `GET /api/bookmarks?kind=&q=&limit=` — the person's bookmarks, newest first. */
  lazy val bookmarks: PublicEndpoint[(Option[String], Option[String], Option[Int]), ApiError, List[Bookmark], Any] =
    endpoint.get
      .in("api" / "bookmarks")
      .in(kindQuery)
      .in(wordsQuery)
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[Bookmark]])

  /** `POST /api/bookmarks` — bookmark an item, a point on the map, a view of Explore or a period. */
  lazy val addBookmark: PublicEndpoint[BookmarkNew, ApiError, Bookmark, Any] =
    endpoint.post.in("api" / "bookmarks").in(jsonBody[BookmarkNew]).errorOut(errors).out(jsonBody[Bookmark])

  /** `PATCH /api/bookmarks/{id}` — rename a bookmark. */
  lazy val editBookmark: PublicEndpoint[(String, BookmarkEdit), ApiError, Bookmark, Any] =
    endpoint.patch
      .in("api" / "bookmarks" / path[String]("id"))
      .in(jsonBody[BookmarkEdit])
      .errorOut(errors)
      .out(jsonBody[Bookmark])

  /** `DELETE /api/bookmarks/{id}` — remove a bookmark; answers it as it was. */
  lazy val removeBookmark: PublicEndpoint[String, ApiError, Bookmark, Any] =
    endpoint.delete.in("api" / "bookmarks" / path[String]("id")).errorOut(errors).out(jsonBody[Bookmark])

  /** `GET /api/notes?kind=&id=&q=&limit=` — the person's notes, last changed first; `kind` and `id` the ones left on
    * one item.
    */
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

  /** `GET /api/notes/{id}` — one note. */
  lazy val note: PublicEndpoint[String, ApiError, Note, Any] =
    endpoint.get.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])

  /** `POST /api/notes` — leave a note on an item or a point on the map. */
  lazy val addNote: PublicEndpoint[NoteNew, ApiError, Note, Any] =
    endpoint.post.in("api" / "notes").in(jsonBody[NoteNew]).errorOut(errors).out(jsonBody[Note])

  /** `PATCH /api/notes/{id}` — change a note's text (412 when it changed since `ifUpdatedAt`). */
  lazy val editNote: PublicEndpoint[(String, NoteEdit), ApiError, Note, Any] =
    endpoint.patch.in("api" / "notes" / path[String]("id")).in(jsonBody[NoteEdit]).errorOut(errors).out(jsonBody[Note])

  /** `DELETE /api/notes/{id}` — remove a note; answers it as it was. */
  lazy val removeNote: PublicEndpoint[String, ApiError, Note, Any] =
    endpoint.delete.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])

  /** `GET /api/reading?kind=&show=read|opened|all&limit=` — the person's read marks: the items marked read (newest
    * first), those opened (last opened first), or all of them.
    */
  lazy val reading: PublicEndpoint[(Option[String], Option[String], Option[Int]), ApiError, List[ReadMark], Any] =
    endpoint.get
      .in("api" / "reading")
      .in(kindQuery)
      .in(query[Option[String]]("show").description("read (default), opened or all"))
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[ReadMark]])

  /** `PUT /api/reading` — mark a source or an article read, or not read yet. */
  lazy val setRead: PublicEndpoint[ReadMarkSet, ApiError, ReadMark, Any] =
    endpoint.put.in("api" / "reading").in(jsonBody[ReadMarkSet]).errorOut(errors).out(jsonBody[ReadMark])

  /** `POST /api/reading/opened` — the person opened a source or an article (it is not marked read). */
  lazy val opened: PublicEndpoint[ReadOpened, ApiError, ReadMark, Any] =
    endpoint.post.in("api" / "reading" / "opened").in(jsonBody[ReadOpened]).errorOut(errors).out(jsonBody[ReadMark])

  /** `GET /api/reading/unread?kind=source|article&limit=&offset=` — the sources (or articles) not marked read, by
    * title, a page at a time.
    */
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
