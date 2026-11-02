package com.histmersion.domain.library.collections

import com.histmersion.api.ApiError
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.{
  ArchiveFile,
  ArchiveImportRequest,
  ArchiveItem,
  CalibreGap,
  CalibreGaps,
  CalibrePushRequest,
  CalibrePushResult,
  IngestAccepted,
  IngestParams,
  IngestReport,
  SourcePatch,
  SourcePut,
  SourceWithFindings
}
import com.histmersion.api.media.{MediaCoversReport, MediaCoversRequest, MediaItem, SourceCoverParams}

/** A library a suite's collections write through that refuses everything; a suite overrides what it uses. */
open class RefusingCollectionLibrary extends CollectionLibrary:
  private def no = Left(ApiError("not used in this suite"))
  def source(citekey: String): Either[ApiError, SourceWithFindings] = no
  def create(citekey: String, put: SourcePut): Either[ApiError, SourceWithFindings] = no
  def patch(citekey: String, patch: SourcePatch): Either[ApiError, SourceWithFindings] = no
  def pushMeta(citekey: String): Either[ApiError, CalibreGap] = no
  def pushed(req: CalibrePushRequest): Either[ApiError, CalibrePushResult] = no
  def gaps(limit: Option[Int]): CalibreGaps = CalibreGaps(0, Nil)
  def original(citekey: String): Either[ApiError, StoredOriginal] = no
  def queueItem(id: Int): Either[ApiError, Option[Queue.QueueItem]] = no
  def ingest(params: IngestParams, bytes: Array[Byte]): Either[ApiError, IngestAccepted] = no
  def putCover(params: SourceCoverParams, bytes: Array[Byte]): Either[ApiError, MediaItem] = no
  def openLibraryCover(req: MediaCoversRequest): Either[ApiError, MediaCoversReport] = no
  def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest): Either[ApiError, IngestReport] =
    no
