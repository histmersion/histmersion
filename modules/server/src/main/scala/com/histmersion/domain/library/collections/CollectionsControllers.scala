package com.histmersion
package domain.library.collections

import com.histmersion.api.Write

import com.histmersion.api.library.*
import com.histmersion.domain.Caller
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.{
  ArchiveImportRequest,
  ArchiveItem,
  ArchiveSearchResult,
  CollectionAudit,
  CollectionGaps,
  CollectionImported,
  CollectionImportRequest,
  CollectionInfo,
  CollectionLinked,
  CollectionLinkRequest,
  CollectionPushed,
  CollectionPushRequest,
  CollectionSearch,
  CollectionSetDone,
  CollectionSetRequest,
  CollectionSync,
  CollectionUpdateAll,
  CollectionUpdatedOne,
  CollectionUpdateOne
}
import com.histmersion.api.Api

/** The collections' endpoints and tools over [[CollectionsService]]: the citekey checked where one is named, every
  * write behind the maintenance lock.
  */
final class CollectionsControllers(service: CollectionsService, maintenance: WriteGate):

  private def citekey(raw: com.histmersion.api.library.Citekey): Either[Api.Error, Citekey] =
    Citekey.parse(raw.value).left.map(p => Write.invalid(Write.InvalidMessage(p.value)))

  def collections(caller: Caller): Either[Api.Error, List[CollectionInfo]] = Right(service.collections())
  def collection(caller: Caller, name: CollectionsService.CollectionName): Either[Api.Error, CollectionInfo] = service.collection(name)

  def search(
      caller: Caller,
      name: CollectionsService.CollectionName,
      q: Option[com.histmersion.domain.library.search.Search.Query],
      limit: Option[CollectionsService.Limit],
      unlinked: CollectionsService.SearchUnlinked
  ): Either[Api.Error, CollectionSearch] = service.search(name, q, limit, unlinked)

  def importItem(caller: Caller, name: CollectionsService.CollectionName, req: CollectionImportRequest): Either[Api.Error, CollectionImported] =
    maintenance.guard(WriteGate.OperationName("collections/import"))(service.importItem(caller, name, req))

  def link(caller: Caller, name: CollectionsService.CollectionName, req: CollectionLinkRequest): Either[Api.Error, CollectionLinked] =
    maintenance.guard(WriteGate.OperationName("collections/link"))(service.link(caller, name, req))

  def push(caller: Caller, name: CollectionsService.CollectionName, req: CollectionPushRequest): Either[Api.Error, CollectionPushed] =
    maintenance.guard(WriteGate.OperationName("collections/push"))(citekey(req.citekey).flatMap(_ => service.push(caller, name, req)))

  def updateOne(caller: Caller, name: CollectionsService.CollectionName, req: CollectionUpdateOne): Either[Api.Error, CollectionUpdatedOne] =
    maintenance.guard(WriteGate.OperationName("collections/update"))(citekey(req.citekey).flatMap(service.updateOne(caller, name, _)))

  def updateAll(
      caller: Caller,
      name: CollectionsService.CollectionName,
      req: CollectionUpdateAll.Request
  ): Either[Api.Error, CollectionUpdateAll] =
    if req.apply.value then maintenance.guard(WriteGate.OperationName("collections/update"))(service.updateAll(caller, name, apply = CollectionsService.UpdateAllApply(true)))
    else service.updateAll(caller, name, apply = CollectionsService.UpdateAllApply(false))

  def set(caller: Caller, name: CollectionsService.CollectionName, req: CollectionSetRequest): Either[Api.Error, CollectionSetDone] =
    maintenance.guard(WriteGate.OperationName("collections/set"))(service.set(caller, name, req))

  def audit(caller: Caller, name: CollectionsService.CollectionName): Either[Api.Error, CollectionAudit] = service.audit(name)

  def gaps(caller: Caller, name: CollectionsService.CollectionName, limit: Option[CollectionsService.Limit]): Either[Api.Error, CollectionGaps] =
    service.gaps(name, limit)

  def sync(caller: Caller, name: CollectionsService.CollectionName, req: CollectionSync.Request): Either[Api.Error, CollectionSync] =
    if req.apply.value then maintenance.guard(WriteGate.OperationName("collections/sync"))(service.sync(caller, name, req))
    else service.sync(caller, name, req)

  def archiveSearch(caller: Caller, q: com.histmersion.domain.library.search.Search.Query, rows: Option[CollectionsService.Limit]): Either[Api.Error, ArchiveSearchResult] =
    service.archiveSearch(q, rows)

  def archiveInfo(caller: Caller, id: com.histmersion.domain.library.ArchiveId): Either[Api.Error, ArchiveItem] = service.archiveInfo(id)

  def archiveImport(caller: Caller, req: ArchiveImportRequest): Either[Api.Error, Ingest.Report] =
    service.archiveImport(caller, req)
