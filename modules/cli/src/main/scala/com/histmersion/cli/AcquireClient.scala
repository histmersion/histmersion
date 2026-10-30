package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*

/** The typed client for the acquisition endpoints — the same `AcquireApi` values the server interprets, so the CLI
  * cannot drift from the REST/MCP surface. It reuses [[Client]]'s transport rather than opening a second one.
  */
final class AcquireClient(c: Client):
  def discover(
      q: String,
      since: Option[Int],
      sort: Option[String],
      limit: Option[Int],
      notInDb: Boolean
  ): Either[CliError, DiscoverResult] = c.call(AcquireApi.discover)((q, since, sort, limit, notInDb))
  def meta(id: String): Either[CliError, WorkRef] = c.call(AcquireApi.meta)(id)
  def wikirefs(url: String): Either[CliError, WikirefsResult] = c.call(AcquireApi.wikirefs)(url)
  def refs(req: RefsRequest): Either[CliError, RefsResult] = c.call(AcquireApi.refs)(req)
  def assess(req: AssessRequest): Either[CliError, Assessment] = c.call(AcquireApi.assess)(req)
  def updateRetractions: Either[CliError, RetractionsUpdate] = c.call(AcquireApi.updateRetractions)(())
  def retractionsStatus: Either[CliError, RetractionsUpdate] = c.call(AcquireApi.retractionsStatus)(())
  def followups(req: FollowupsRequest): Either[CliError, FollowupsResult] = c.call(AcquireApi.followups)(req)
  def ingest(req: IngestRequest): Either[CliError, IngestReport] = c.call(AcquireApi.ingest)(req)
  def archiveSearch(q: String, rows: Option[Int]): Either[CliError, ArchiveSearchResult] =
    c.call(AcquireApi.archiveSearch)((q, rows))
  def archiveInfo(id: String): Either[CliError, ArchiveItem] = c.call(AcquireApi.archiveInfo)(id)
  def archiveImport(req: ArchiveImportRequest): Either[CliError, IngestReport] =
    c.call(AcquireApi.archiveImport)(req)
  def calibreGaps(limit: Option[Int]): Either[CliError, CalibreGaps] = c.call(AcquireApi.calibreGaps)(limit)
  def calibreMeta(citekey: String): Either[CliError, CalibreGap] = c.call(AcquireApi.calibreMeta)(citekey)
  def calibrePushed(req: CalibrePushRequest): Either[CliError, CalibrePushResult] =
    c.call(AcquireApi.calibrePushed)(req)
