package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
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

/** The typed client for the acquisition endpoints — the same `AcquireApi` values the server interprets, so the CLI
  * cannot drift from the REST/MCP surface. It reuses [[Client]]'s transport rather than opening a second one.
  */
final class AcquireClient(c: Client):
  def discover(
      q: com.histmersion.cli.AcquireClient.DiscoverQ,
      since: Option[com.histmersion.cli.AcquireClient.DiscoverSince],
      sort: Option[com.histmersion.cli.AcquireClient.DiscoverSort],
      limit: Option[com.histmersion.cli.AcquireClient.DiscoverLimit],
      notInDb: com.histmersion.cli.AcquireClient.DiscoverNotInDb
  ): Either[Cli.Error, DiscoverResult] = c.call(AcquireApi.discover)((q, since, sort, limit, notInDb))
  def meta(id: com.histmersion.cli.AcquireClient.MetaId): Either[Cli.Error, WorkRef] = c.call(AcquireApi.meta)(id)
  def wikirefs(url: com.histmersion.cli.AcquireClient.WikirefsUrl): Either[Cli.Error, WikirefsResult] = c.call(AcquireApi.wikirefs)(url)
  def refs(req: RefsRequest): Either[Cli.Error, RefsResult] = c.call(AcquireApi.refs)(req)
  def assess(req: AssessRequest): Either[Cli.Error, Assessment] = c.call(AcquireApi.assess)(req)
  def updateRetractions: Either[Cli.Error, RetractionsUpdate] = c.call(AcquireApi.updateRetractions)(())
  def retractionsStatus: Either[Cli.Error, RetractionsUpdate] = c.call(AcquireApi.retractionsStatus)(())
  def followups(req: FollowupsRequest): Either[Cli.Error, FollowupsResult] = c.call(AcquireApi.followups)(req)
  def ingest(req: Ingest.Request): Either[Cli.Error, Ingest.Report] = c.call(AcquireApi.ingest)(req)
  def archiveSearch(q: com.histmersion.cli.AcquireClient.ArchiveSearchQ, rows: Option[com.histmersion.cli.AcquireClient.ArchiveSearchRows]): Either[Cli.Error, ArchiveSearchResult] =
    c.call(AcquireApi.archiveSearch)((q, rows))
  def archiveInfo(id: com.histmersion.cli.AcquireClient.ArchiveInfoId): Either[Cli.Error, ArchiveItem] = c.call(AcquireApi.archiveInfo)(id)
  def archiveImport(req: ArchiveImportRequest): Either[Cli.Error, Ingest.Report] =
    c.call(AcquireApi.archiveImport)(req)
  def calibreGaps(limit: Option[com.histmersion.cli.AcquireClient.CalibreGapsLimit]): Either[Cli.Error, CalibreGaps] = c.call(AcquireApi.calibreGaps)(limit)
  def calibreMeta(citekey: com.histmersion.cli.AcquireClient.CalibreMetaCitekey): Either[Cli.Error, CalibreGap] = c.call(AcquireApi.calibreMeta)(citekey)
  def calibrePushed(req: CalibrePushRequest): Either[Cli.Error, CalibrePushResult] =
    c.call(AcquireApi.calibrePushed)(req)

object AcquireClient:
  type DiscoverQ = DiscoverQ.T
  object DiscoverQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverSince = DiscoverSince.T
  object DiscoverSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverSort = DiscoverSort.T
  object DiscoverSort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverLimit = DiscoverLimit.T
  object DiscoverLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverNotInDb = DiscoverNotInDb.T
  object DiscoverNotInDb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaId = MetaId.T
  object MetaId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikirefsUrl = WikirefsUrl.T
  object WikirefsUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveSearchQ = ArchiveSearchQ.T
  object ArchiveSearchQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveSearchRows = ArchiveSearchRows.T
  object ArchiveSearchRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveInfoId = ArchiveInfoId.T
  object ArchiveInfoId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreGapsLimit = CalibreGapsLimit.T
  object CalibreGapsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreMetaCitekey = CalibreMetaCitekey.T
  object CalibreMetaCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
