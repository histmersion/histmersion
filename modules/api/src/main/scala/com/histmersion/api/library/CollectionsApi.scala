package com.histmersion
package api.library
import com.histmersion.api.json.NewtypeCodecs.given
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The library's source collections (the Calibre library, archive.org): what the server can run, and the operations
  * `histmersion calibre …` makes over HTTP. `{name}` is the collection's registered name.
  */
@atBoundary(
  BoundaryKind.Http,
  "declares the Tapir endpoints of the collections routes with their request and response bodies"
)
object CollectionsApi:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Query = Query.T
  object Query extends Newtype[String, Pure]

  private lazy val base = endpoint.in("api" / "collections").errorOut(Api.problemOut)
  private def named = base.in(path[String]("name").description("the collection's name: calibre, archive-org, …"))

  lazy val collections: PublicEndpoint[Unit, Api.Error, List[CollectionInfo], Any] =
    base.get.out(jsonBody[List[CollectionInfo]])

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val collection: PublicEndpoint[Collection.Name, Api.Error, CollectionInfo, Any] =
    (named.get
      .out(jsonBody[CollectionInfo]))
      .mapIn[Collection.Name]((refactorDecoded12: String) => Collection.Name(refactorDecoded12))(
        (refactorEncoded12: Collection.Name) => (refactorEncoded12).value
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(
    LintRule.GetWrites,
    "pending S1 follow-up 5 (a GET or HEAD route writes nothing): GET: Process ProcessBuilder.start"
  )
  lazy val search
      : PublicEndpoint[(Collection.Name, Option[Query], Option[Limit], Boolean), Api.Error, CollectionSearch, Any] =
    (named.get
      .in("search")
      .in(query[Option[String]]("q").description("words to search for; empty lists everything"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("unlinked").default(false).description("only the items no source is linked to"))
      .out(jsonBody[CollectionSearch]))
      .mapIn[(Collection.Name, Option[Query], Option[Limit], Boolean)](
        (refactorDecoded2: (String, Option[String], Option[Int], Boolean)) =>
          (
            Collection.Name((refactorDecoded2)._1),
            ((refactorDecoded2)._2).map(refactorElement => Query(refactorElement)),
            ((refactorDecoded2)._3).map(refactorElement2 => Limit(refactorElement2)),
            ((refactorDecoded2)._4)
          )
      )((refactorEncoded2: (Collection.Name, Option[Query], Option[Limit], Boolean)) =>
        (
          ((refactorEncoded2)._1).value,
          ((refactorEncoded2)._2).map(refactorElement3 => (refactorElement3).value),
          ((refactorEncoded2)._3).map(refactorElement4 => (refactorElement4).value),
          ((refactorEncoded2)._4)
        )
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val importItem: PublicEndpoint[(Collection.Name, CollectionImportRequest), Api.Error, CollectionImported, Any] =
    (named.post
      .in("import")
      .in(jsonBody[CollectionImportRequest])
      .out(jsonBody[CollectionImported]))
      .mapIn[(Collection.Name, CollectionImportRequest)]((refactorDecoded14: (String, CollectionImportRequest)) =>
        (Collection.Name((refactorDecoded14)._1), (refactorDecoded14)._2)
      )((refactorEncoded14: (Collection.Name, CollectionImportRequest)) =>
        (((refactorEncoded14)._1).value, (refactorEncoded14)._2)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val link: PublicEndpoint[(Collection.Name, CollectionLinkRequest), Api.Error, CollectionLinked, Any] =
    (named.post
      .in("link")
      .in(jsonBody[CollectionLinkRequest])
      .out(jsonBody[CollectionLinked]))
      .mapIn[(Collection.Name, CollectionLinkRequest)]((refactorDecoded15: (String, CollectionLinkRequest)) =>
        (Collection.Name((refactorDecoded15)._1), (refactorDecoded15)._2)
      )((refactorEncoded15: (Collection.Name, CollectionLinkRequest)) =>
        (((refactorEncoded15)._1).value, (refactorEncoded15)._2)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val push: PublicEndpoint[(Collection.Name, CollectionPushRequest), Api.Error, CollectionPushed, Any] =
    (named.post
      .in("push")
      .in(jsonBody[CollectionPushRequest])
      .out(jsonBody[CollectionPushed]))
      .mapIn[(Collection.Name, CollectionPushRequest)]((refactorDecoded16: (String, CollectionPushRequest)) =>
        (Collection.Name((refactorDecoded16)._1), (refactorDecoded16)._2)
      )((refactorEncoded16: (Collection.Name, CollectionPushRequest)) =>
        (((refactorEncoded16)._1).value, (refactorEncoded16)._2)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val updateOne: PublicEndpoint[(Collection.Name, CollectionUpdateOne), Api.Error, CollectionUpdatedOne, Any] =
    (named.post
      .in("update")
      .in(jsonBody[CollectionUpdateOne])
      .out(jsonBody[CollectionUpdatedOne]))
      .mapIn[(Collection.Name, CollectionUpdateOne)]((refactorDecoded6: (String, CollectionUpdateOne)) =>
        (Collection.Name((refactorDecoded6)._1), (refactorDecoded6)._2)
      )((refactorEncoded6: (Collection.Name, CollectionUpdateOne)) =>
        (((refactorEncoded6)._1).value, (refactorEncoded6)._2)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val updateAll
      : PublicEndpoint[(Collection.Name, CollectionUpdateAll.Request), Api.Error, CollectionUpdateAll, Any] =
    (named.post
      .in("update-all")
      .in(jsonBody[CollectionUpdateAll.Request])
      .out(jsonBody[CollectionUpdateAll]))
      .mapIn[(Collection.Name, CollectionUpdateAll.Request)](
        (refactorDecoded7: (String, CollectionUpdateAll.Request)) =>
          (Collection.Name((refactorDecoded7)._1), (refactorDecoded7)._2)
      )((refactorEncoded7: (Collection.Name, CollectionUpdateAll.Request)) =>
        (((refactorEncoded7)._1).value, (refactorEncoded7)._2)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val set: PublicEndpoint[(Collection.Name, CollectionSetRequest), Api.Error, CollectionSetDone, Any] =
    (named.post
      .in("set")
      .in(jsonBody[CollectionSetRequest])
      .out(jsonBody[CollectionSetDone]))
      .mapIn[(Collection.Name, CollectionSetRequest)]((refactorDecoded8: (String, CollectionSetRequest)) =>
        (Collection.Name((refactorDecoded8)._1), (refactorDecoded8)._2)
      )((refactorEncoded8: (Collection.Name, CollectionSetRequest)) =>
        (((refactorEncoded8)._1).value, (refactorEncoded8)._2)
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(
    LintRule.GetWrites,
    "pending S1 follow-up 5 (a GET or HEAD route writes nothing): GET: Process ProcessBuilder.start"
  )
  lazy val audit: PublicEndpoint[Collection.Name, Api.Error, CollectionAudit, Any] =
    (named.get
      .in("audit")
      .out(jsonBody[CollectionAudit]))
      .mapIn[Collection.Name]((refactorDecoded9: String) => Collection.Name(refactorDecoded9))(
        (refactorEncoded9: Collection.Name) => (refactorEncoded9).value
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val gaps: PublicEndpoint[(Collection.Name, Option[Limit]), Api.Error, CollectionGaps, Any] =
    (named.get
      .in("gaps")
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[CollectionGaps]))
      .mapIn[(Collection.Name, Option[Limit])]((refactorDecoded10: (String, Option[Int])) =>
        (
          Collection.Name((refactorDecoded10)._1),
          ((refactorDecoded10)._2).map(refactorElement5 => Limit(refactorElement5))
        )
      )((refactorEncoded10: (Collection.Name, Option[Limit])) =>
        (((refactorEncoded10)._1).value, ((refactorEncoded10)._2).map(refactorElement6 => (refactorElement6).value))
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val sync: PublicEndpoint[(Collection.Name, CollectionSync.Request), Api.Error, CollectionSync, Any] =
    (named.post
      .in("sync")
      .in(jsonBody[CollectionSync.Request])
      .out(jsonBody[CollectionSync]))
      .mapIn[(Collection.Name, CollectionSync.Request)]((refactorDecoded11: (String, CollectionSync.Request)) =>
        (Collection.Name((refactorDecoded11)._1), (refactorDecoded11)._2)
      )((refactorEncoded11: (Collection.Name, CollectionSync.Request)) =>
        (((refactorEncoded11)._1).value, (refactorEncoded11)._2)
      )

  lazy val all =
    List(collections, collection, search, importItem, link, push, updateOne, updateAll, set, audit, gaps, sync)

  /** The error code of an answer that refuses the request itself (a citekey collision, an unknown queue item): the CLI
    * exits 1 for it, as for a bad command line.
    */
  val Usage = "usage"

  /** The error code of a request for a collection the server does not have (its plugin off, or none registered). */
  val NoCollection = "no-collection"
