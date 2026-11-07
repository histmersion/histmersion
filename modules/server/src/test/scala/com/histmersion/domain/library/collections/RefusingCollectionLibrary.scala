package com.histmersion.domain.library.collections

import com.histmersion.api.automation.Queue
import com.histmersion.api.library.{
  ArchiveFile,
  ArchiveImportRequest,
  ArchiveItem,
  CalibreGap,
  CalibreGaps,
  CalibrePushRequest,
  CalibrePushResult
}
import com.histmersion.api.media.{MediaCoversReport, MediaCoversRequest, MediaItem, SourceCoverParams}
import com.histmersion.api.Api
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source

/** A library a suite's collections write through that refuses everything; a suite overrides what it uses. */
open class RefusingCollectionLibrary extends CollectionLibrary:
  private def no = Left(Api.Error(com.histmersion.api.Api.Error.Message("not used in this suite")))
  def source(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, Source.WithFindings] = no
  def create(citekey: com.histmersion.domain.library.Citekey, put: Source.Put): Either[Api.Error, Source.WithFindings] = no
  def patch(citekey: com.histmersion.domain.library.Citekey, patch: Source.Patch): Either[Api.Error, Source.WithFindings] = no
  def pushMeta(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, CalibreGap] = no
  def pushed(req: CalibrePushRequest): Either[Api.Error, CalibrePushResult] = no
  def gaps(limit: Option[CollectionLibrary.Limit]): CalibreGaps = CalibreGaps(com.histmersion.api.library.CalibreGaps.Count(0), Nil)
  def original(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, StoredOriginal] = no
  def queueItem(id: CollectionLibrary.QueueItemId): Either[Api.Error, Option[Queue.QueueItem]] = no
  def ingest(params: Ingest.Params, bytes: CollectionLibrary.Bytes): Either[Api.Error, Ingest.Accepted] = no
  def putCover(params: SourceCoverParams, bytes: CollectionLibrary.Bytes): Either[Api.Error, MediaItem] = no
  def openLibraryCover(req: MediaCoversRequest): Either[Api.Error, MediaCoversReport] = no
  def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest): Either[Api.Error, Ingest.Report] =
    no
