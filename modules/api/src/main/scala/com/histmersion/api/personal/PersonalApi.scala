package com.histmersion.api.personal

import com.histmersion.api.Api
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.ItemKind

object PersonalApi:
  type Search = Search.T
  object Search extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private def errors = Api.writeErrors
  private def kindQuery =
    query[Option[String]]("kind").description("only these: " + ItemKind.values.map(_.wire).mkString(", "))
  private def wordsQuery = query[Option[String]]("q").description("words its name, its label or its text holds")
  private def limitQuery = query[Option[Int]]("limit")
    .description(s"how many (default ${PersonalIds.DefaultLimit}, at most ${PersonalIds.MaxLimit})")

  lazy val bookmarks: PublicEndpoint[(Option[com.histmersion.api.ItemKind.Wire], Option[com.histmersion.api.personal.PersonalApi.Search], Option[Int]), Api.Error, List[Bookmark], Any] =
    (endpoint.get
      .in("api" / "bookmarks")
      .in(kindQuery)
      .in(wordsQuery)
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[Bookmark]])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.ItemKind.Wire], _root_.scala.Option[_root_.com.histmersion.api.personal.PersonalApi.Search], _root_.scala.Option[Int])]((refactorDecoded10: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded10)._1).map(refactorElement27 => _root_.com.histmersion.api.ItemKind.Wire(refactorElement27)), ((refactorDecoded10)._2).map(refactorElement28 => _root_.com.histmersion.api.personal.PersonalApi.Search(refactorElement28)), ((refactorDecoded10)._3).map(refactorElement29 => (refactorElement29))))((refactorEncoded10: (_root_.scala.Option[_root_.com.histmersion.api.ItemKind.Wire], _root_.scala.Option[_root_.com.histmersion.api.personal.PersonalApi.Search], _root_.scala.Option[Int])) => (((refactorEncoded10)._1).map(refactorElement30 => (refactorElement30).value), ((refactorEncoded10)._2).map(refactorElement31 => (refactorElement31).value), ((refactorEncoded10)._3).map(refactorElement32 => (refactorElement32))))

  lazy val addBookmark: PublicEndpoint[Bookmark.New, Api.Error, Bookmark, Any] =
    endpoint.post.in("api" / "bookmarks").in(jsonBody[Bookmark.New]).errorOut(errors).out(jsonBody[Bookmark])

  lazy val editBookmark: PublicEndpoint[(com.histmersion.api.personal.Bookmark.Id, Bookmark.Edit), Api.Error, Bookmark, Any] =
    (endpoint.patch
      .in("api" / "bookmarks" / path[String]("id"))
      .in(jsonBody[Bookmark.Edit])
      .errorOut(errors)
      .out(jsonBody[Bookmark])).mapIn[(_root_.com.histmersion.api.personal.Bookmark.Id, _root_.com.histmersion.api.personal.Bookmark.Edit)]((refactorDecoded11: (_root_.java.lang.String, _root_.com.histmersion.api.personal.Bookmark.Edit)) => (_root_.com.histmersion.api.personal.Bookmark.Id((refactorDecoded11)._1), (refactorDecoded11)._2))((refactorEncoded11: (_root_.com.histmersion.api.personal.Bookmark.Id, _root_.com.histmersion.api.personal.Bookmark.Edit)) => (((refactorEncoded11)._1).value, (refactorEncoded11)._2))

  lazy val removeBookmark: PublicEndpoint[com.histmersion.api.personal.Bookmark.Id, Api.Error, Bookmark, Any] =
    (endpoint.delete.in("api" / "bookmarks" / path[String]("id")).errorOut(errors).out(jsonBody[Bookmark])).mapIn[_root_.com.histmersion.api.personal.Bookmark.Id]((refactorDecoded12: _root_.java.lang.String) => _root_.com.histmersion.api.personal.Bookmark.Id(refactorDecoded12))((refactorEncoded12: _root_.com.histmersion.api.personal.Bookmark.Id) => (refactorEncoded12).value)

  lazy val notes
      : PublicEndpoint[(Option[com.histmersion.api.ItemKind.Wire], Option[com.histmersion.api.personal.ItemKey], Option[com.histmersion.api.personal.PersonalApi.Search], Option[Int]), Api.Error, List[Note], Any] =
    (endpoint.get
      .in("api" / "notes")
      .in(kindQuery)
      .in(query[Option[String]]("id").description("the item's key"))
      .in(wordsQuery)
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[Note]])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.ItemKind.Wire], _root_.scala.Option[_root_.com.histmersion.api.personal.ItemKey], _root_.scala.Option[_root_.com.histmersion.api.personal.PersonalApi.Search], _root_.scala.Option[Int])]((refactorDecoded4: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded4)._1).map(refactorElement7 => _root_.com.histmersion.api.ItemKind.Wire(refactorElement7)), ((refactorDecoded4)._2).map(refactorElement8 => _root_.com.histmersion.api.personal.ItemKey(refactorElement8)), ((refactorDecoded4)._3).map(refactorElement9 => _root_.com.histmersion.api.personal.PersonalApi.Search(refactorElement9)), ((refactorDecoded4)._4).map(refactorElement10 => (refactorElement10))))((refactorEncoded4: (_root_.scala.Option[_root_.com.histmersion.api.ItemKind.Wire], _root_.scala.Option[_root_.com.histmersion.api.personal.ItemKey], _root_.scala.Option[_root_.com.histmersion.api.personal.PersonalApi.Search], _root_.scala.Option[Int])) => (((refactorEncoded4)._1).map(refactorElement11 => (refactorElement11).value), ((refactorEncoded4)._2).map(refactorElement12 => (refactorElement12).value), ((refactorEncoded4)._3).map(refactorElement13 => (refactorElement13).value), ((refactorEncoded4)._4).map(refactorElement14 => (refactorElement14))))

  lazy val note: PublicEndpoint[com.histmersion.api.personal.Note.Id, Api.Error, Note, Any] =
    (endpoint.get.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])).mapIn[_root_.com.histmersion.api.personal.Note.Id]((refactorDecoded5: _root_.java.lang.String) => _root_.com.histmersion.api.personal.Note.Id(refactorDecoded5))((refactorEncoded5: _root_.com.histmersion.api.personal.Note.Id) => (refactorEncoded5).value)

  lazy val addNote: PublicEndpoint[Note.New, Api.Error, Note, Any] =
    endpoint.post.in("api" / "notes").in(jsonBody[Note.New]).errorOut(errors).out(jsonBody[Note])

  lazy val editNote: PublicEndpoint[(com.histmersion.api.personal.Note.Id, Note.Edit), Api.Error, Note, Any] =
    (endpoint.patch.in("api" / "notes" / path[String]("id")).in(jsonBody[Note.Edit]).errorOut(errors).out(jsonBody[Note])).mapIn[(_root_.com.histmersion.api.personal.Note.Id, _root_.com.histmersion.api.personal.Note.Edit)]((refactorDecoded6: (_root_.java.lang.String, _root_.com.histmersion.api.personal.Note.Edit)) => (_root_.com.histmersion.api.personal.Note.Id((refactorDecoded6)._1), (refactorDecoded6)._2))((refactorEncoded6: (_root_.com.histmersion.api.personal.Note.Id, _root_.com.histmersion.api.personal.Note.Edit)) => (((refactorEncoded6)._1).value, (refactorEncoded6)._2))

  lazy val removeNote: PublicEndpoint[com.histmersion.api.personal.Note.Id, Api.Error, Note, Any] =
    (endpoint.delete.in("api" / "notes" / path[String]("id")).errorOut(errors).out(jsonBody[Note])).mapIn[_root_.com.histmersion.api.personal.Note.Id]((refactorDecoded7: _root_.java.lang.String) => _root_.com.histmersion.api.personal.Note.Id(refactorDecoded7))((refactorEncoded7: _root_.com.histmersion.api.personal.Note.Id) => (refactorEncoded7).value)

  lazy val reading: PublicEndpoint[(Option[com.histmersion.api.ItemKind.Wire], Option[com.histmersion.api.automation.Activity.Show.Wire], Option[Int]), Api.Error, List[ReadMark], Any] =
    (endpoint.get
      .in("api" / "reading")
      .in(kindQuery)
      .in(query[Option[String]]("show").description("read (default), opened or all"))
      .in(limitQuery)
      .errorOut(errors)
      .out(jsonBody[List[ReadMark]])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.ItemKind.Wire], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Show.Wire], _root_.scala.Option[Int])]((refactorDecoded17: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded17)._1).map(refactorElement41 => _root_.com.histmersion.api.ItemKind.Wire(refactorElement41)), ((refactorDecoded17)._2).map(refactorElement42 => _root_.com.histmersion.api.automation.Activity.Show.Wire(refactorElement42)), ((refactorDecoded17)._3).map(refactorElement43 => (refactorElement43))))((refactorEncoded17: (_root_.scala.Option[_root_.com.histmersion.api.ItemKind.Wire], _root_.scala.Option[_root_.com.histmersion.api.automation.Activity.Show.Wire], _root_.scala.Option[Int])) => (((refactorEncoded17)._1).map(refactorElement44 => (refactorElement44).value), ((refactorEncoded17)._2).map(refactorElement45 => (refactorElement45).value), ((refactorEncoded17)._3).map(refactorElement46 => (refactorElement46))))

  lazy val setRead: PublicEndpoint[ReadMark.Set, Api.Error, ReadMark, Any] =
    endpoint.put.in("api" / "reading").in(jsonBody[ReadMark.Set]).errorOut(errors).out(jsonBody[ReadMark])

  lazy val opened: PublicEndpoint[ReadOpened, Api.Error, ReadMark, Any] =
    endpoint.post.in("api" / "reading" / "opened").in(jsonBody[ReadOpened]).errorOut(errors).out(jsonBody[ReadMark])

  lazy val unread: PublicEndpoint[(Option[com.histmersion.api.ItemKind.Wire], Option[Int], Option[com.histmersion.api.personal.UnreadPage.Offset]), Api.Error, UnreadPage, Any] =
    (endpoint.get
      .in("api" / "reading" / "unread")
      .in(kindQuery)
      .in(limitQuery)
      .in(query[Option[Int]]("offset").description("the first row of the page"))
      .errorOut(errors)
      .out(jsonBody[UnreadPage])).mapIn[(_root_.scala.Option[_root_.com.histmersion.api.ItemKind.Wire], _root_.scala.Option[Int], _root_.scala.Option[_root_.com.histmersion.api.personal.UnreadPage.Offset])]((refactorDecoded18: (_root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Option[_root_.scala.Int])) => (((refactorDecoded18)._1).map(refactorElement47 => _root_.com.histmersion.api.ItemKind.Wire(refactorElement47)), ((refactorDecoded18)._2).map(refactorElement48 => (refactorElement48)), ((refactorDecoded18)._3).map(refactorElement49 => _root_.com.histmersion.api.personal.UnreadPage.Offset(refactorElement49))))((refactorEncoded18: (_root_.scala.Option[_root_.com.histmersion.api.ItemKind.Wire], _root_.scala.Option[Int], _root_.scala.Option[_root_.com.histmersion.api.personal.UnreadPage.Offset])) => (((refactorEncoded18)._1).map(refactorElement50 => (refactorElement50).value), ((refactorEncoded18)._2).map(refactorElement51 => (refactorElement51)), ((refactorEncoded18)._3).map(refactorElement52 => (refactorElement52).value)))

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
