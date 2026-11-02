package com.histmersion.cli

import com.histmersion.api.library.*
import com.histmersion.api.library.{
  CollectionAudit,
  CollectionGaps,
  CollectionImported,
  CollectionImportRequest,
  CollectionInfo,
  CollectionLinked,
  CollectionLinkRequest,
  CollectionPushed,
  CollectionPushRequest,
  CollectionsApi,
  CollectionSearch,
  CollectionSetDone,
  CollectionSetRequest,
  CollectionSync,
  CollectionUpdateAll,
  CollectionUpdatedOne,
  CollectionUpdateOne
}

/** The typed client for the library's source collections (`CollectionsApi`), over [[Client]]'s transport. */
final class CollectionsClient(c: Client, name: String):
  def info: Either[CliError, CollectionInfo] = c.call(CollectionsApi.collection)(name)
  def search(q: Option[String], limit: Option[Int], unlinked: Boolean): Either[CliError, CollectionSearch] =
    c.call(CollectionsApi.search)((name, q, limit, unlinked))
  def importItem(req: CollectionImportRequest): Either[CliError, CollectionImported] =
    c.call(CollectionsApi.importItem)((name, req))
  def link(req: CollectionLinkRequest): Either[CliError, CollectionLinked] = c.call(CollectionsApi.link)((name, req))
  def push(req: CollectionPushRequest): Either[CliError, CollectionPushed] = c.call(CollectionsApi.push)((name, req))
  def updateOne(citekey: String): Either[CliError, CollectionUpdatedOne] =
    c.call(CollectionsApi.updateOne)((name, CollectionUpdateOne(citekey)))
  def updateAll(apply: Boolean): Either[CliError, CollectionUpdateAll] =
    c.call(CollectionsApi.updateAll)((name, CollectionUpdateAll.Request(apply)))
  def set(req: CollectionSetRequest): Either[CliError, CollectionSetDone] = c.call(CollectionsApi.set)((name, req))
  def audit: Either[CliError, CollectionAudit] = c.call(CollectionsApi.audit)(name)
  def gaps(limit: Option[Int]): Either[CliError, CollectionGaps] = c.call(CollectionsApi.gaps)((name, limit))
  def sync(req: CollectionSync.Request): Either[CliError, CollectionSync] = c.call(CollectionsApi.sync)((name, req))

  /** Whether the server runs this collection: it has the collection and reaches it now. Anything else — an older server
    * without collections, the plugin off, a library the server cannot read, no server at all — leaves the command to
    * this machine's own Calibre code.
    */
  def onServer: Boolean = info.exists(_.reachable)
