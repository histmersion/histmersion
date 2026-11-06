package com.histmersion.api.library
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.Api

/** The library's source collections (the Calibre library, archive.org): what the server can run, and the operations
  * `histmersion calibre …` makes over HTTP. `{name}` is the collection's registered name.
  */
object CollectionsApi:

  type CollectionPart1 = CollectionPart1.T
  object CollectionPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchPart1Part1 = SearchPart1Part1.T
  object SearchPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchPart1Part2 = SearchPart1Part2.T
  object SearchPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchPart1Part3 = SearchPart1Part3.T
  object SearchPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchPart1Part4 = SearchPart1Part4.T
  object SearchPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportItemPart1Part1 = ImportItemPart1Part1.T
  object ImportItemPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkPart1Part1 = LinkPart1Part1.T
  object LinkPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PushPart1Part1 = PushPart1Part1.T
  object PushPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateOnePart1Part1 = UpdateOnePart1Part1.T
  object UpdateOnePart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllPart1Part1 = UpdateAllPart1Part1.T
  object UpdateAllPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetPart1Part1 = SetPart1Part1.T
  object SetPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuditPart1 = AuditPart1.T
  object AuditPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GapsPart1Part1 = GapsPart1Part1.T
  object GapsPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GapsPart1Part2 = GapsPart1Part2.T
  object GapsPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SyncPart1Part1 = SyncPart1Part1.T
  object SyncPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private lazy val base = endpoint.in("api" / "collections").errorOut(jsonBody[Api.Error])
  private def named = base.in(path[String]("name").description("the collection's name: calibre, archive-org, …"))

  lazy val collections: PublicEndpoint[Unit, Api.Error, List[CollectionInfo], Any] =
    base.get.out(jsonBody[List[CollectionInfo]])

  lazy val collection: PublicEndpoint[com.histmersion.api.library.CollectionsApi.CollectionPart1, Api.Error, CollectionInfo, Any] =
    (named.get.out(jsonBody[CollectionInfo])).mapIn[_root_.com.histmersion.api.library.CollectionsApi.CollectionPart1]((refactorDecoded12: _root_.java.lang.String) => _root_.com.histmersion.api.library.CollectionsApi.CollectionPart1(refactorDecoded12))((refactorEncoded12: _root_.com.histmersion.api.library.CollectionsApi.CollectionPart1) => (refactorEncoded12).value)

  lazy val search: PublicEndpoint[(com.histmersion.api.library.CollectionsApi.SearchPart1Part1, Option[com.histmersion.api.library.CollectionsApi.SearchPart1Part2], Option[com.histmersion.api.library.CollectionsApi.SearchPart1Part3], com.histmersion.api.library.CollectionsApi.SearchPart1Part4), Api.Error, CollectionSearch, Any] =
    (named.get
      .in("search")
      .in(query[Option[String]]("q").description("words to search for; empty lists everything"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("unlinked").default(false).description("only the items no source is linked to"))
      .out(jsonBody[CollectionSearch])).mapIn[(_root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part1, _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part3], _root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part4)]((refactorDecoded2: (_root_.java.lang.String, _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Boolean)) => (_root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part1((refactorDecoded2)._1), ((refactorDecoded2)._2).map(refactorElement => _root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part2(refactorElement)), ((refactorDecoded2)._3).map(refactorElement2 => _root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part3(refactorElement2)), _root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part4((refactorDecoded2)._4)))((refactorEncoded2: (_root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part1, _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part2], _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part3], _root_.com.histmersion.api.library.CollectionsApi.SearchPart1Part4)) => (((refactorEncoded2)._1).value, ((refactorEncoded2)._2).map(refactorElement3 => (refactorElement3).value), ((refactorEncoded2)._3).map(refactorElement4 => (refactorElement4).value), ((refactorEncoded2)._4).value))

  lazy val importItem: PublicEndpoint[(com.histmersion.api.library.CollectionsApi.ImportItemPart1Part1, CollectionImportRequest), Api.Error, CollectionImported, Any] =
    (named.post.in("import").in(jsonBody[CollectionImportRequest]).out(jsonBody[CollectionImported])).mapIn[(_root_.com.histmersion.api.library.CollectionsApi.ImportItemPart1Part1, _root_.com.histmersion.api.library.CollectionImportRequest)]((refactorDecoded14: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionImportRequest)) => (_root_.com.histmersion.api.library.CollectionsApi.ImportItemPart1Part1((refactorDecoded14)._1), (refactorDecoded14)._2))((refactorEncoded14: (_root_.com.histmersion.api.library.CollectionsApi.ImportItemPart1Part1, _root_.com.histmersion.api.library.CollectionImportRequest)) => (((refactorEncoded14)._1).value, (refactorEncoded14)._2))

  lazy val link: PublicEndpoint[(com.histmersion.api.library.CollectionsApi.LinkPart1Part1, CollectionLinkRequest), Api.Error, CollectionLinked, Any] =
    (named.post.in("link").in(jsonBody[CollectionLinkRequest]).out(jsonBody[CollectionLinked])).mapIn[(_root_.com.histmersion.api.library.CollectionsApi.LinkPart1Part1, _root_.com.histmersion.api.library.CollectionLinkRequest)]((refactorDecoded15: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionLinkRequest)) => (_root_.com.histmersion.api.library.CollectionsApi.LinkPart1Part1((refactorDecoded15)._1), (refactorDecoded15)._2))((refactorEncoded15: (_root_.com.histmersion.api.library.CollectionsApi.LinkPart1Part1, _root_.com.histmersion.api.library.CollectionLinkRequest)) => (((refactorEncoded15)._1).value, (refactorEncoded15)._2))

  lazy val push: PublicEndpoint[(com.histmersion.api.library.CollectionsApi.PushPart1Part1, CollectionPushRequest), Api.Error, CollectionPushed, Any] =
    (named.post.in("push").in(jsonBody[CollectionPushRequest]).out(jsonBody[CollectionPushed])).mapIn[(_root_.com.histmersion.api.library.CollectionsApi.PushPart1Part1, _root_.com.histmersion.api.library.CollectionPushRequest)]((refactorDecoded16: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionPushRequest)) => (_root_.com.histmersion.api.library.CollectionsApi.PushPart1Part1((refactorDecoded16)._1), (refactorDecoded16)._2))((refactorEncoded16: (_root_.com.histmersion.api.library.CollectionsApi.PushPart1Part1, _root_.com.histmersion.api.library.CollectionPushRequest)) => (((refactorEncoded16)._1).value, (refactorEncoded16)._2))

  lazy val updateOne: PublicEndpoint[(com.histmersion.api.library.CollectionsApi.UpdateOnePart1Part1, CollectionUpdateOne), Api.Error, CollectionUpdatedOne, Any] =
    (named.post.in("update").in(jsonBody[CollectionUpdateOne]).out(jsonBody[CollectionUpdatedOne])).mapIn[(_root_.com.histmersion.api.library.CollectionsApi.UpdateOnePart1Part1, _root_.com.histmersion.api.library.CollectionUpdateOne)]((refactorDecoded6: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionUpdateOne)) => (_root_.com.histmersion.api.library.CollectionsApi.UpdateOnePart1Part1((refactorDecoded6)._1), (refactorDecoded6)._2))((refactorEncoded6: (_root_.com.histmersion.api.library.CollectionsApi.UpdateOnePart1Part1, _root_.com.histmersion.api.library.CollectionUpdateOne)) => (((refactorEncoded6)._1).value, (refactorEncoded6)._2))

  lazy val updateAll: PublicEndpoint[(com.histmersion.api.library.CollectionsApi.UpdateAllPart1Part1, CollectionUpdateAll.Request), Api.Error, CollectionUpdateAll, Any] =
    (named.post.in("update-all").in(jsonBody[CollectionUpdateAll.Request]).out(jsonBody[CollectionUpdateAll])).mapIn[(_root_.com.histmersion.api.library.CollectionsApi.UpdateAllPart1Part1, _root_.com.histmersion.api.library.CollectionUpdateAll.Request)]((refactorDecoded7: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionUpdateAll.Request)) => (_root_.com.histmersion.api.library.CollectionsApi.UpdateAllPart1Part1((refactorDecoded7)._1), (refactorDecoded7)._2))((refactorEncoded7: (_root_.com.histmersion.api.library.CollectionsApi.UpdateAllPart1Part1, _root_.com.histmersion.api.library.CollectionUpdateAll.Request)) => (((refactorEncoded7)._1).value, (refactorEncoded7)._2))

  lazy val set: PublicEndpoint[(com.histmersion.api.library.CollectionsApi.SetPart1Part1, CollectionSetRequest), Api.Error, CollectionSetDone, Any] =
    (named.post.in("set").in(jsonBody[CollectionSetRequest]).out(jsonBody[CollectionSetDone])).mapIn[(_root_.com.histmersion.api.library.CollectionsApi.SetPart1Part1, _root_.com.histmersion.api.library.CollectionSetRequest)]((refactorDecoded8: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionSetRequest)) => (_root_.com.histmersion.api.library.CollectionsApi.SetPart1Part1((refactorDecoded8)._1), (refactorDecoded8)._2))((refactorEncoded8: (_root_.com.histmersion.api.library.CollectionsApi.SetPart1Part1, _root_.com.histmersion.api.library.CollectionSetRequest)) => (((refactorEncoded8)._1).value, (refactorEncoded8)._2))

  lazy val audit: PublicEndpoint[com.histmersion.api.library.CollectionsApi.AuditPart1, Api.Error, CollectionAudit, Any] =
    (named.get.in("audit").out(jsonBody[CollectionAudit])).mapIn[_root_.com.histmersion.api.library.CollectionsApi.AuditPart1]((refactorDecoded9: _root_.java.lang.String) => _root_.com.histmersion.api.library.CollectionsApi.AuditPart1(refactorDecoded9))((refactorEncoded9: _root_.com.histmersion.api.library.CollectionsApi.AuditPart1) => (refactorEncoded9).value)

  lazy val gaps: PublicEndpoint[(com.histmersion.api.library.CollectionsApi.GapsPart1Part1, Option[com.histmersion.api.library.CollectionsApi.GapsPart1Part2]), Api.Error, CollectionGaps, Any] =
    (named.get.in("gaps").in(query[Option[Int]]("limit")).out(jsonBody[CollectionGaps])).mapIn[(_root_.com.histmersion.api.library.CollectionsApi.GapsPart1Part1, _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.GapsPart1Part2])]((refactorDecoded10: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Int])) => (_root_.com.histmersion.api.library.CollectionsApi.GapsPart1Part1((refactorDecoded10)._1), ((refactorDecoded10)._2).map(refactorElement5 => _root_.com.histmersion.api.library.CollectionsApi.GapsPart1Part2(refactorElement5))))((refactorEncoded10: (_root_.com.histmersion.api.library.CollectionsApi.GapsPart1Part1, _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.GapsPart1Part2])) => (((refactorEncoded10)._1).value, ((refactorEncoded10)._2).map(refactorElement6 => (refactorElement6).value)))

  lazy val sync: PublicEndpoint[(com.histmersion.api.library.CollectionsApi.SyncPart1Part1, CollectionSync.Request), Api.Error, CollectionSync, Any] =
    (named.post.in("sync").in(jsonBody[CollectionSync.Request]).out(jsonBody[CollectionSync])).mapIn[(_root_.com.histmersion.api.library.CollectionsApi.SyncPart1Part1, _root_.com.histmersion.api.library.CollectionSync.Request)]((refactorDecoded11: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionSync.Request)) => (_root_.com.histmersion.api.library.CollectionsApi.SyncPart1Part1((refactorDecoded11)._1), (refactorDecoded11)._2))((refactorEncoded11: (_root_.com.histmersion.api.library.CollectionsApi.SyncPart1Part1, _root_.com.histmersion.api.library.CollectionSync.Request)) => (((refactorEncoded11)._1).value, (refactorEncoded11)._2))

  lazy val all =
    List(collections, collection, search, importItem, link, push, updateOne, updateAll, set, audit, gaps, sync)

  /** The error code of an answer that refuses the request itself (a citekey collision, an unknown queue item): the CLI
    * exits 1 for it, as for a bad command line.
    */
  val Usage = "usage"

  /** The error code of a request for a collection the server does not have (its plugin off, or none registered). */
  val NoCollection = "no-collection"
