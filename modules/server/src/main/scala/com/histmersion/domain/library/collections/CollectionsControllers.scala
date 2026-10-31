package com.histmersion.domain.library.collections

import com.histmersion.api.*
import com.histmersion.api.library.collections.*
import com.histmersion.domain.Caller
import com.histmersion.server.Maintenance
import com.histmersion.values.Citekey

/** The collections' endpoints and tools over [[CollectionsService]]: the citekey checked where one is named, every
  * write behind the maintenance lock.
  */
final class CollectionsControllers(service: CollectionsService, maintenance: Maintenance):

  private def citekey(raw: String): Either[ApiError, String] = Citekey.of(raw).map(_.value)

  def collections(caller: Caller): Either[ApiError, List[CollectionInfo]] = Right(service.collections())
  def collection(caller: Caller, name: String): Either[ApiError, CollectionInfo] = service.collection(name)

  def search(
      caller: Caller,
      name: String,
      q: Option[String],
      limit: Option[Int],
      unlinked: Boolean
  ): Either[ApiError, CollectionSearch] = service.search(name, q, limit, unlinked)

  def importItem(caller: Caller, name: String, req: CollectionImportRequest): Either[ApiError, CollectionImported] =
    maintenance.guard("collections/import")(service.importItem(caller, name, req))

  def link(caller: Caller, name: String, req: CollectionLinkRequest): Either[ApiError, CollectionLinked] =
    maintenance.guard("collections/link")(service.link(caller, name, req))

  def push(caller: Caller, name: String, req: CollectionPushRequest): Either[ApiError, CollectionPushed] =
    maintenance.guard("collections/push")(citekey(req.citekey).flatMap(_ => service.push(caller, name, req)))

  def updateOne(caller: Caller, name: String, req: CollectionUpdateOne): Either[ApiError, CollectionUpdatedOne] =
    maintenance.guard("collections/update")(citekey(req.citekey).flatMap(service.updateOne(caller, name, _)))

  def updateAll(caller: Caller, name: String, req: CollectionUpdateAllRequest): Either[ApiError, CollectionUpdateAll] =
    if req.apply then maintenance.guard("collections/update")(service.updateAll(caller, name, apply = true))
    else service.updateAll(caller, name, apply = false)

  def set(caller: Caller, name: String, req: CollectionSetRequest): Either[ApiError, CollectionSetDone] =
    maintenance.guard("collections/set")(service.set(caller, name, req))

  def audit(caller: Caller, name: String): Either[ApiError, CollectionAudit] = service.audit(name)

  def gaps(caller: Caller, name: String, limit: Option[Int]): Either[ApiError, CollectionGaps] =
    service.gaps(name, limit)

  def sync(caller: Caller, name: String, req: CollectionSyncRequest): Either[ApiError, CollectionSync] =
    if req.apply then maintenance.guard("collections/sync")(service.sync(caller, name, req))
    else service.sync(caller, name, req)

  def archiveSearch(caller: Caller, q: String, rows: Option[Int]): Either[ApiError, ArchiveSearchResult] =
    service.archiveSearch(q, rows)

  def archiveInfo(caller: Caller, id: String): Either[ApiError, ArchiveItem] = service.archiveInfo(id)

  def archiveImport(caller: Caller, req: ArchiveImportRequest): Either[ApiError, IngestReport] =
    service.archiveImport(caller, req)
