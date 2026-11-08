package com.histmersion
package domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity
import com.histmersion.api.library.AcquireApi
import com.histmersion.domain.library.collections.CollectionsControllers
import com.histmersion.domain.library.collections.CalibreGapControllers
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.collections.CollectionsService
import com.histmersion.domain.library.ArchiveId
import com.histmersion.domain.library.Citekey
import com.histmersion.api.Write
import com.histmersion.api.Api

/** The REST wiring of [[AcquireApi]] — every route delegates to the [[AcquisitionControllers]] the MCP tools also call;
  * `archive search|info|import` to the library's collections, which run archive.org, and the Calibre gaps to theirs.
  */
final class AcquisitionRoutes(
    h: AcquisitionControllers,
    collections: CollectionsControllers,
    gaps: CalibreGapControllers
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
    AcquireApi.archiveSearch.serverLogic[Identity]((q, rows) => 
      collections.archiveSearch(
        Caller.Local,
        Search.Query(q.value),
        rows.map(r => CollectionsService.Limit(r.value))
      )
    ),
    AcquireApi.archiveInfo.serverLogic[Identity](id => collections.archiveInfo(Caller.Local, ArchiveId(id.value))),
    AcquireApi.archiveImport.serverLogic[Identity](req => collections.archiveImport(Caller.Local, req)),
    AcquireApi.calibreGaps.serverLogicSuccess[Identity](limit => gaps.gaps(limit.map(l => CalibreGapControllers.Limit(l.value)))),
    AcquireApi.calibreMeta.serverLogic[Identity](ck =>
      Citekey
        .parse(ck.value)
        .left
        .map(p => Write.invalid(Api.Error.Message(p.value)))
        .flatMap(gaps.pushMeta)
    ),
    AcquireApi.calibrePushed.serverLogic[Identity](req => gaps.pushed(req)),
    AcquireApi.candidates.serverLogic[Identity]((g, st, limit, offset) => h.candidates(g, st, limit, offset)),
    AcquireApi.sweepCandidates.serverLogic[Identity](req => h.sweepCandidates(req)),
    AcquireApi.markCandidates.serverLogic[Identity](req => h.markCandidates(req))
  )
