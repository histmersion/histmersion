package com.histmersion
package domain.library.acquisition

import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*
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
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{tolerated, LintRule}

/** The REST wiring of [[AcquireApi]] — every route delegates to the [[AcquisitionControllers]] the MCP tools also call;
  * `archive search|info|import` to the library's collections, which run archive.org, and the Calibre gaps to theirs.
  */
final class AcquisitionRoutes(
    h: AcquisitionControllers,
    collections: CollectionsControllers,
    gaps: CalibreGapControllers
):

  private given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  val endpoints: List[ServerEndpoint[Any, Identity]] = List(
    AcquireApi.discover.serverLogic[Identity]((q, since, sort, limit, notInDb) =>
      h.discover(q, since, sort, limit, notInDb)
    ),
    AcquireApi.meta.serverLogic[Identity](id => h.meta(id)),
    AcquireApi.wikirefs.serverLogic[Identity](url => h.wikirefs(url)),
    AcquireApi.refs.serverLogic[Identity](req => CreatorControllers.rest(req)(h.refs(req))),
    AcquireApi.assess.serverLogic[Identity](req => CreatorControllers.rest(req)(h.assess(req))),
    AcquireApi.updateRetractions.serverLogic[Identity](_ => h.updateRetractions()),
    AcquireApi.retractionsStatus.serverLogicSuccess[Identity](_ => h.retractionsStatus),
    AcquireApi.followups.serverLogic[Identity](req => CreatorControllers.rest(req)(h.followups(req))),
    AcquireApi.ingest.serverLogic[Identity](req => h.ingest(req)),
    AcquireApi.archiveSearch.serverLogic[Identity]((q, rows) =>
      collections.archiveSearch(
        Caller.Local,
        Search.Query(q.value),
        rows.map(r => CollectionsService.Limit(r.value))
      )
    ),
    AcquireApi.archiveInfo.serverLogic[Identity](id => collections.archiveInfo(Caller.Local, ArchiveId(id.value))),
    AcquireApi.archiveImport.serverLogic[Identity](req =>
      CreatorControllers.rest(req)(collections.archiveImport(Caller.Local, req))
    ),
    AcquireApi.calibreGaps.serverLogicSuccess[Identity](limit =>
      gaps.gaps(limit.map(l => CalibreGapControllers.Limit(l.value)))
    ),
    AcquireApi.calibreMeta.serverLogic[Identity](ck =>
      Citekey
        .parse(ck.value)
        .left
        .map(p => Write.invalid(p))
        .flatMap(gaps.pushMeta)
    ),
    AcquireApi.calibrePushed.serverLogic[Identity](req => gaps.pushed(req)),
    AcquireApi.candidates.serverLogic[Identity]((g, st, limit, offset) => h.candidates(g, st, limit, offset)),
    AcquireApi.sweepCandidates.serverLogic[Identity](req => CreatorControllers.rest(req)(h.sweepCandidates(req))),
    AcquireApi.markCandidates.serverLogic[Identity](req => h.markCandidates(req))
  )
