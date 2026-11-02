package com.histmersion.domain.library.acquisition

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity

/** The REST wiring of [[AcquireApi]] — every route delegates to the [[AcquisitionControllers]] the MCP tools also call;
  * `archive search|info|import` to the library's collections, which run archive.org, and the Calibre gaps to theirs.
  */
final class AcquisitionRoutes(
    h: AcquisitionControllers,
    collections: com.histmersion.domain.library.collections.CollectionsControllers,
    gaps: com.histmersion.domain.library.collections.CalibreGapControllers
):

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    AcquireApi.discover.serverLogic[Identity]((q, since, sort, limit, notInDb) =>
      h.discover(q, since, sort, limit, notInDb)
    ),
    AcquireApi.meta.serverLogic[Identity](id => h.meta(id)),
    AcquireApi.wikirefs.serverLogic[Identity](url => h.wikirefs(url)),
    AcquireApi.refs.serverLogic[Identity](req => h.refs(req)),
    AcquireApi.assess.serverLogic[Identity](req => h.assess(req)),
    AcquireApi.updateRetractions.serverLogic[Identity](_ => h.updateRetractions()),
    AcquireApi.retractionsStatus.serverLogicSuccess[Identity](_ => h.retractionsStatus),
    AcquireApi.followups.serverLogic[Identity](req => h.followups(req)),
    AcquireApi.ingest.serverLogic[Identity](req => h.ingest(req)),
    AcquireApi.archiveSearch.serverLogic[Identity]((q, rows) => collections.archiveSearch(Caller.Local, q, rows)),
    AcquireApi.archiveInfo.serverLogic[Identity](id => collections.archiveInfo(Caller.Local, id)),
    AcquireApi.archiveImport.serverLogic[Identity](req => collections.archiveImport(Caller.Local, req)),
    AcquireApi.calibreGaps.serverLogicSuccess[Identity](limit => gaps.gaps(limit)),
    AcquireApi.calibreMeta.serverLogic[Identity](ck => gaps.pushMeta(ck)),
    AcquireApi.calibrePushed.serverLogic[Identity](req => gaps.pushed(req)),
    AcquireApi.candidates.serverLogic[Identity]((g, st, limit, offset) => h.candidates(g, st, limit, offset)),
    AcquireApi.sweepCandidates.serverLogic[Identity](req => h.sweepCandidates(req)),
    AcquireApi.markCandidates.serverLogic[Identity](req => h.markCandidates(req))
  )
