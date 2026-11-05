package com.histmersion.api.library
import sttp.tapir.*
import sttp.tapir.json.circe.*
import com.histmersion.api.Api

/** The library's source collections (the Calibre library, archive.org): what the server can run, and the operations
  * `histmersion calibre …` makes over HTTP. `{name}` is the collection's registered name.
  */
object CollectionsApi:

  private lazy val base = endpoint.in("api" / "collections").errorOut(jsonBody[Api.Error])
  private def named = base.in(path[String]("name").description("the collection's name: calibre, archive-org, …"))

  lazy val collections: PublicEndpoint[Unit, Api.Error, List[CollectionInfo], Any] =
    base.get.out(jsonBody[List[CollectionInfo]])

  lazy val collection: PublicEndpoint[String, Api.Error, CollectionInfo, Any] =
    named.get.out(jsonBody[CollectionInfo])

  lazy val search: PublicEndpoint[(String, Option[String], Option[Int], Boolean), Api.Error, CollectionSearch, Any] =
    named.get
      .in("search")
      .in(query[Option[String]]("q").description("words to search for; empty lists everything"))
      .in(query[Option[Int]]("limit"))
      .in(query[Boolean]("unlinked").default(false).description("only the items no source is linked to"))
      .out(jsonBody[CollectionSearch])

  lazy val importItem: PublicEndpoint[(String, CollectionImportRequest), Api.Error, CollectionImported, Any] =
    named.post.in("import").in(jsonBody[CollectionImportRequest]).out(jsonBody[CollectionImported])

  lazy val link: PublicEndpoint[(String, CollectionLinkRequest), Api.Error, CollectionLinked, Any] =
    named.post.in("link").in(jsonBody[CollectionLinkRequest]).out(jsonBody[CollectionLinked])

  lazy val push: PublicEndpoint[(String, CollectionPushRequest), Api.Error, CollectionPushed, Any] =
    named.post.in("push").in(jsonBody[CollectionPushRequest]).out(jsonBody[CollectionPushed])

  lazy val updateOne: PublicEndpoint[(String, CollectionUpdateOne), Api.Error, CollectionUpdatedOne, Any] =
    named.post.in("update").in(jsonBody[CollectionUpdateOne]).out(jsonBody[CollectionUpdatedOne])

  lazy val updateAll: PublicEndpoint[(String, CollectionUpdateAll.Request), Api.Error, CollectionUpdateAll, Any] =
    named.post.in("update-all").in(jsonBody[CollectionUpdateAll.Request]).out(jsonBody[CollectionUpdateAll])

  lazy val set: PublicEndpoint[(String, CollectionSetRequest), Api.Error, CollectionSetDone, Any] =
    named.post.in("set").in(jsonBody[CollectionSetRequest]).out(jsonBody[CollectionSetDone])

  lazy val audit: PublicEndpoint[String, Api.Error, CollectionAudit, Any] =
    named.get.in("audit").out(jsonBody[CollectionAudit])

  lazy val gaps: PublicEndpoint[(String, Option[Int]), Api.Error, CollectionGaps, Any] =
    named.get.in("gaps").in(query[Option[Int]]("limit")).out(jsonBody[CollectionGaps])

  lazy val sync: PublicEndpoint[(String, CollectionSync.Request), Api.Error, CollectionSync, Any] =
    named.post.in("sync").in(jsonBody[CollectionSync.Request]).out(jsonBody[CollectionSync])

  lazy val all =
    List(collections, collection, search, importItem, link, push, updateOne, updateAll, set, audit, gaps, sync)

  /** The error code of an answer that refuses the request itself (a citekey collision, an unknown queue item): the CLI
    * exits 1 for it, as for a bad command line.
    */
  val Usage = "usage"

  /** The error code of a request for a collection the server does not have (its plugin off, or none registered). */
  val NoCollection = "no-collection"
