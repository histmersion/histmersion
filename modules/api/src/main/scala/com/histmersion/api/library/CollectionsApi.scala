package com.histmersion.api.library
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.Api

/** The library's source collections (the Calibre library, archive.org): what the server can run, and the operations
  * `histmersion calibre …` makes over HTTP. `{name}` is the collection's registered name.
  */
object CollectionsApi:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Query = Query.T
  object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  private lazy val base = endpoint.in("api" / "collections").errorOut(jsonBody[Api.Error])
  private def named = base.in(path[String]("name").description("the collection's name: calibre, archive-org, …"))

  lazy val collections: PublicEndpoint[Unit, Api.Error, List[CollectionInfo], Any] =
    base.get.out(jsonBody[List[CollectionInfo]])

  lazy val collection: PublicEndpoint[com.histmersion.api.library.Collection.Name, Api.Error, CollectionInfo, Any] =
    (named.get.out(jsonBody[CollectionInfo])).mapIn[_root_.com.histmersion.api.library.Collection.Name]((refactorDecoded12: _root_.java.lang.String) => _root_.com.histmersion.api.library.Collection.Name(refactorDecoded12))((refactorEncoded12: _root_.com.histmersion.api.library.Collection.Name) => (refactorEncoded12).value)

  lazy val search: PublicEndpoint[(com.histmersion.api.library.Collection.Name, Option[com.histmersion.api.library.CollectionsApi.Query], Option[com.histmersion.api.library.CollectionsApi.Limit], Boolean), Api.Error, CollectionSearch, Any] =
    (named.get
      .in("search")
      .in(query[Option[String]]("q").description("words to search for; empty lists everything"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("unlinked").default(false).description("only the items no source is linked to"))
      .out(jsonBody[CollectionSearch])).mapIn[(_root_.com.histmersion.api.library.Collection.Name, _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.Query], _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.Limit], Boolean)]((refactorDecoded2: (_root_.java.lang.String, _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.scala.Int], _root_.scala.Boolean)) => (_root_.com.histmersion.api.library.Collection.Name((refactorDecoded2)._1), ((refactorDecoded2)._2).map(refactorElement => _root_.com.histmersion.api.library.CollectionsApi.Query(refactorElement)), ((refactorDecoded2)._3).map(refactorElement2 => _root_.com.histmersion.api.library.CollectionsApi.Limit(refactorElement2)), ((refactorDecoded2)._4)))((refactorEncoded2: (_root_.com.histmersion.api.library.Collection.Name, _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.Query], _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.Limit], Boolean)) => (((refactorEncoded2)._1).value, ((refactorEncoded2)._2).map(refactorElement3 => (refactorElement3).value), ((refactorEncoded2)._3).map(refactorElement4 => (refactorElement4).value), ((refactorEncoded2)._4)))

  lazy val importItem: PublicEndpoint[(com.histmersion.api.library.Collection.Name, CollectionImportRequest), Api.Error, CollectionImported, Any] =
    (named.post.in("import").in(jsonBody[CollectionImportRequest]).out(jsonBody[CollectionImported])).mapIn[(_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionImportRequest)]((refactorDecoded14: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionImportRequest)) => (_root_.com.histmersion.api.library.Collection.Name((refactorDecoded14)._1), (refactorDecoded14)._2))((refactorEncoded14: (_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionImportRequest)) => (((refactorEncoded14)._1).value, (refactorEncoded14)._2))

  lazy val link: PublicEndpoint[(com.histmersion.api.library.Collection.Name, CollectionLinkRequest), Api.Error, CollectionLinked, Any] =
    (named.post.in("link").in(jsonBody[CollectionLinkRequest]).out(jsonBody[CollectionLinked])).mapIn[(_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionLinkRequest)]((refactorDecoded15: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionLinkRequest)) => (_root_.com.histmersion.api.library.Collection.Name((refactorDecoded15)._1), (refactorDecoded15)._2))((refactorEncoded15: (_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionLinkRequest)) => (((refactorEncoded15)._1).value, (refactorEncoded15)._2))

  lazy val push: PublicEndpoint[(com.histmersion.api.library.Collection.Name, CollectionPushRequest), Api.Error, CollectionPushed, Any] =
    (named.post.in("push").in(jsonBody[CollectionPushRequest]).out(jsonBody[CollectionPushed])).mapIn[(_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionPushRequest)]((refactorDecoded16: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionPushRequest)) => (_root_.com.histmersion.api.library.Collection.Name((refactorDecoded16)._1), (refactorDecoded16)._2))((refactorEncoded16: (_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionPushRequest)) => (((refactorEncoded16)._1).value, (refactorEncoded16)._2))

  lazy val updateOne: PublicEndpoint[(com.histmersion.api.library.Collection.Name, CollectionUpdateOne), Api.Error, CollectionUpdatedOne, Any] =
    (named.post.in("update").in(jsonBody[CollectionUpdateOne]).out(jsonBody[CollectionUpdatedOne])).mapIn[(_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionUpdateOne)]((refactorDecoded6: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionUpdateOne)) => (_root_.com.histmersion.api.library.Collection.Name((refactorDecoded6)._1), (refactorDecoded6)._2))((refactorEncoded6: (_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionUpdateOne)) => (((refactorEncoded6)._1).value, (refactorEncoded6)._2))

  lazy val updateAll: PublicEndpoint[(com.histmersion.api.library.Collection.Name, CollectionUpdateAll.Request), Api.Error, CollectionUpdateAll, Any] =
    (named.post.in("update-all").in(jsonBody[CollectionUpdateAll.Request]).out(jsonBody[CollectionUpdateAll])).mapIn[(_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionUpdateAll.Request)]((refactorDecoded7: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionUpdateAll.Request)) => (_root_.com.histmersion.api.library.Collection.Name((refactorDecoded7)._1), (refactorDecoded7)._2))((refactorEncoded7: (_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionUpdateAll.Request)) => (((refactorEncoded7)._1).value, (refactorEncoded7)._2))

  lazy val set: PublicEndpoint[(com.histmersion.api.library.Collection.Name, CollectionSetRequest), Api.Error, CollectionSetDone, Any] =
    (named.post.in("set").in(jsonBody[CollectionSetRequest]).out(jsonBody[CollectionSetDone])).mapIn[(_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionSetRequest)]((refactorDecoded8: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionSetRequest)) => (_root_.com.histmersion.api.library.Collection.Name((refactorDecoded8)._1), (refactorDecoded8)._2))((refactorEncoded8: (_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionSetRequest)) => (((refactorEncoded8)._1).value, (refactorEncoded8)._2))

  lazy val audit: PublicEndpoint[com.histmersion.api.library.Collection.Name, Api.Error, CollectionAudit, Any] =
    (named.get.in("audit").out(jsonBody[CollectionAudit])).mapIn[_root_.com.histmersion.api.library.Collection.Name]((refactorDecoded9: _root_.java.lang.String) => _root_.com.histmersion.api.library.Collection.Name(refactorDecoded9))((refactorEncoded9: _root_.com.histmersion.api.library.Collection.Name) => (refactorEncoded9).value)

  lazy val gaps: PublicEndpoint[(com.histmersion.api.library.Collection.Name, Option[com.histmersion.api.library.CollectionsApi.Limit]), Api.Error, CollectionGaps, Any] =
    (named.get.in("gaps").in(query[Option[Int]]("limit")).out(jsonBody[CollectionGaps])).mapIn[(_root_.com.histmersion.api.library.Collection.Name, _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.Limit])]((refactorDecoded10: (_root_.java.lang.String, _root_.scala.Option[_root_.scala.Int])) => (_root_.com.histmersion.api.library.Collection.Name((refactorDecoded10)._1), ((refactorDecoded10)._2).map(refactorElement5 => _root_.com.histmersion.api.library.CollectionsApi.Limit(refactorElement5))))((refactorEncoded10: (_root_.com.histmersion.api.library.Collection.Name, _root_.scala.Option[_root_.com.histmersion.api.library.CollectionsApi.Limit])) => (((refactorEncoded10)._1).value, ((refactorEncoded10)._2).map(refactorElement6 => (refactorElement6).value)))

  lazy val sync: PublicEndpoint[(com.histmersion.api.library.Collection.Name, CollectionSync.Request), Api.Error, CollectionSync, Any] =
    (named.post.in("sync").in(jsonBody[CollectionSync.Request]).out(jsonBody[CollectionSync])).mapIn[(_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionSync.Request)]((refactorDecoded11: (_root_.java.lang.String, _root_.com.histmersion.api.library.CollectionSync.Request)) => (_root_.com.histmersion.api.library.Collection.Name((refactorDecoded11)._1), (refactorDecoded11)._2))((refactorEncoded11: (_root_.com.histmersion.api.library.Collection.Name, _root_.com.histmersion.api.library.CollectionSync.Request)) => (((refactorEncoded11)._1).value, (refactorEncoded11)._2))

  lazy val all =
    List(collections, collection, search, importItem, link, push, updateOne, updateAll, set, audit, gaps, sync)

  /** The error code of an answer that refuses the request itself (a citekey collision, an unknown queue item): the CLI
    * exits 1 for it, as for a bad command line.
    */
  val Usage = "usage"

  /** The error code of a request for a collection the server does not have (its plugin off, or none registered). */
  val NoCollection = "no-collection"
