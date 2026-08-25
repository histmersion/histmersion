package histdb.server.acquire

import histdb.core.*

import sttp.tapir.server.ServerEndpoint
import sttp.shared.Identity

/** The REST wiring of [[AcquireApi]] — every route delegates to the single [[AcquireHandlers]]
  * implementation the MCP tools also call. */
final class AcquireRoutes(h: AcquireHandlers):

  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    AcquireApi.discover.serverLogic[Identity]((q, since, sort, limit, notInDb) =>
      h.discover(q, since, sort, limit, notInDb)),
    AcquireApi.meta.serverLogic[Identity](id => h.meta(id)),
    AcquireApi.wikirefs.serverLogic[Identity](url => h.wikirefs(url)),
    AcquireApi.refs.serverLogic[Identity](req => h.refs(req)),
    AcquireApi.assess.serverLogic[Identity](req => h.assess(req)),
    AcquireApi.updateRetractions.serverLogic[Identity](_ => h.updateRetractions()),
    AcquireApi.retractionsStatus.serverLogicSuccess[Identity](_ => h.retractionsStatus),
    AcquireApi.followups.serverLogic[Identity](req => h.followups(req)),
    AcquireApi.ingest.serverLogic[Identity](req => h.ingest(req)),
    AcquireApi.archiveSearch.serverLogic[Identity]((q, rows) => h.archiveSearch(q, rows)),
    AcquireApi.archiveInfo.serverLogic[Identity](id => h.archiveInfo(id)),
    AcquireApi.archiveImport.serverLogic[Identity](req => h.archiveImport(req)),
    AcquireApi.calibreGaps.serverLogicSuccess[Identity](limit => h.calibreGaps(limit)),
    AcquireApi.calibreMeta.serverLogic[Identity](ck => h.calibreMeta(ck)),
    AcquireApi.calibrePushed.serverLogic[Identity](req => h.calibrePushed(req))
  )
