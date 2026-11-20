package com.histmersion
package cli

import com.histmersion.config.*
import com.histmersion.api.library.{
  AcquireApi,
  ArchiveImportRequest,
  ArchiveItem,
  ArchiveSearchResult,
  Assessment,
  AssessRequest,
  CalibreGap,
  CalibreGaps,
  CalibrePushRequest,
  CalibrePushResult,
  DiscoverResult,
  FollowupsRequest,
  FollowupsResult,
  RefsRequest,
  RefsResult,
  RetractionsUpdate,
  WikirefsResult,
  WorkRef
}
import com.histmersion.api.library.Ingest
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Title
import com.histmersion.api.library.ArchiveId
import com.histmersion.api.library.Citekey

/** The typed client for the acquisition endpoints — the same `AcquireApi` values the server interprets, so the CLI
  * cannot drift from the REST/MCP surface. It reuses [[Client]]'s transport rather than opening a second one.
  */
final class AcquireClient(c: Client):
  def discover(
      q: AcquireApi.Query,
      since: Option[Year],
      sort: Option[AcquireApi.Filter],
      limit: Option[AcquireApi.Limit],
      notInDb: Boolean
  ): Either[Cli.Error, DiscoverResult] = c.call(AcquireApi.discover)((q, since, sort, limit, notInDb))
  def meta(id: AcquireApi.Identifier): Either[Cli.Error, WorkRef] = c.call(AcquireApi.meta)(id)
  def wikirefs(url: Title): Either[Cli.Error, WikirefsResult] = c.call(AcquireApi.wikirefs)(url)
  def refs(req: RefsRequest): Either[Cli.Error, RefsResult] = c.call(AcquireApi.refs)(req)
  def assess(req: AssessRequest): Either[Cli.Error, Assessment] = c.call(AcquireApi.assess)(req)
  def updateRetractions: Either[Cli.Error, RetractionsUpdate] = c.call(AcquireApi.updateRetractions)(())
  def retractionsStatus: Either[Cli.Error, RetractionsUpdate] = c.call(AcquireApi.retractionsStatus)(())

  /** No final answer came for work a request started here ([[Client.unanswered]]). */
  def unanswered(what: String): com.histmersion.util.problem.Problem = c.unanswered(what)
  def followups(req: FollowupsRequest): Either[Cli.Error, FollowupsResult] = c.call(AcquireApi.followups)(req)
  def ingest(req: Ingest.Request): Either[Cli.Error, Ingest.Report] = c.call(AcquireApi.ingest)(req)
  def archiveSearch(q: AcquireApi.Query, rows: Option[AcquireApi.Limit]): Either[Cli.Error, ArchiveSearchResult] =
    c.call(AcquireApi.archiveSearch)((q, rows))
  def archiveInfo(id: ArchiveId): Either[Cli.Error, ArchiveItem] = c.call(AcquireApi.archiveInfo)(id)
  def archiveImport(req: ArchiveImportRequest): Either[Cli.Error, Ingest.Report] =
    c.call(AcquireApi.archiveImport)(req)
  def calibreGaps(limit: Option[AcquireApi.Limit]): Either[Cli.Error, CalibreGaps] =
    c.call(AcquireApi.calibreGaps)(limit)
  def calibreMeta(citekey: Citekey): Either[Cli.Error, CalibreGap] = c.call(AcquireApi.calibreMeta)(citekey)
  def calibrePushed(req: CalibrePushRequest): Either[Cli.Error, CalibrePushResult] =
    c.call(AcquireApi.calibrePushed)(req)
