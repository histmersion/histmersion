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
  def info: Either[Cli.Error, CollectionInfo] = c.call(CollectionsApi.collection)(name)
  def search(q: Option[com.histmersion.cli.CollectionsClient.SearchQ], limit: Option[com.histmersion.cli.CollectionsClient.SearchLimit], unlinked: com.histmersion.cli.CollectionsClient.SearchUnlinked): Either[Cli.Error, CollectionSearch] =
    c.call(CollectionsApi.search)((name, q, limit, unlinked))
  def importItem(req: CollectionImportRequest): Either[Cli.Error, CollectionImported] =
    c.call(CollectionsApi.importItem)((name, req))
  def link(req: CollectionLinkRequest): Either[Cli.Error, CollectionLinked] = c.call(CollectionsApi.link)((name, req))
  def push(req: CollectionPushRequest): Either[Cli.Error, CollectionPushed] = c.call(CollectionsApi.push)((name, req))
  def updateOne(citekey: com.histmersion.cli.CollectionsClient.UpdateOneCitekey): Either[Cli.Error, CollectionUpdatedOne] =
    c.call(CollectionsApi.updateOne)((name, CollectionUpdateOne(citekey)))
  def updateAll(apply: com.histmersion.cli.CollectionsClient.UpdateAllApply): Either[Cli.Error, CollectionUpdateAll] =
    c.call(CollectionsApi.updateAll)((name, CollectionUpdateAll.Request(apply)))
  def set(req: CollectionSetRequest): Either[Cli.Error, CollectionSetDone] = c.call(CollectionsApi.set)((name, req))
  def audit: Either[Cli.Error, CollectionAudit] = c.call(CollectionsApi.audit)(name)
  def gaps(limit: Option[com.histmersion.cli.CollectionsClient.GapsLimit]): Either[Cli.Error, CollectionGaps] = c.call(CollectionsApi.gaps)((name, limit))
  def sync(req: CollectionSync.Request): Either[Cli.Error, CollectionSync] = c.call(CollectionsApi.sync)((name, req))

  /** Whether the server runs this collection: it has the collection and reaches it now. Anything else — an older server
    * without collections, the plugin off, a library the server cannot read, no server at all — leaves the command to
    * this machine's own Calibre code.
    */
  def onServer: com.histmersion.cli.CollectionsClient.OnServerResult = info.exists(_.reachable)

object CollectionsClient:
  type SearchQ = SearchQ.T
  object SearchQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUnlinked = SearchUnlinked.T
  object SearchUnlinked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateOneCitekey = UpdateOneCitekey.T
  object UpdateOneCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllApply = UpdateAllApply.T
  object UpdateAllApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GapsLimit = GapsLimit.T
  object GapsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnServerResult = OnServerResult.T
  object OnServerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
