package com.histmersion
package cli

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
import com.histmersion.api.Api
import com.histmersion.api.Paging

/** The typed client for the library's source collections (`CollectionsApi`), over [[Client]]'s transport. */
final class CollectionsClient(c: Client, name: Collection.Name):
  def info: Either[Cli.Error, CollectionInfo] = c.call(CollectionsApi.collection)(name)
  def search(
      q: Option[Api.Search.Text],
      limit: Option[Paging.Limit],
      unlinked: CalibreCmd.Unlinked
  ): Either[Cli.Error, CollectionSearch] =
    c.call(CollectionsApi.search)(
      (name, q.map(t => CollectionsApi.Query(t.value)), limit.map(l => CollectionsApi.Limit(l.value)), unlinked.value)
    )
  def importItem(req: CollectionImportRequest): Either[Cli.Error, CollectionImported] =
    c.call(CollectionsApi.importItem)((name, req))
  def link(req: CollectionLinkRequest): Either[Cli.Error, CollectionLinked] = c.call(CollectionsApi.link)((name, req))
  def push(req: CollectionPushRequest): Either[Cli.Error, CollectionPushed] = c.call(CollectionsApi.push)((name, req))
  def updateOne(citekey: Citekey): Either[Cli.Error, CollectionUpdatedOne] =
    c.call(CollectionsApi.updateOne)((name, CollectionUpdateOne(citekey)))
  def updateAll(apply: Api.Apply): Either[Cli.Error, CollectionUpdateAll] =
    c.call(CollectionsApi.updateAll)(
      (name, CollectionUpdateAll.Request(CollectionUpdateAll.Request.Apply(apply.value)))
    )
  def set(req: CollectionSetRequest): Either[Cli.Error, CollectionSetDone] = c.call(CollectionsApi.set)((name, req))
  def audit: Either[Cli.Error, CollectionAudit] = c.call(CollectionsApi.audit)(name)
  def gaps(limit: Option[Paging.Limit]): Either[Cli.Error, CollectionGaps] =
    c.call(CollectionsApi.gaps)((name, limit.map(l => CollectionsApi.Limit(l.value))))
  def sync(req: CollectionSync.Request): Either[Cli.Error, CollectionSync] = c.call(CollectionsApi.sync)((name, req))

  /** Whether the server runs this collection: it has the collection and reaches it now. Anything else — an older server
    * without collections, the plugin off, a library the server cannot read, no server at all — leaves the command to
    * this machine's own Calibre code.
    */
  def onServer: Boolean = info.exists(_.reachable.value)

object CollectionsClient:
  type Name = Collection.Name
  val Name: Collection.Name.type = Collection.Name
