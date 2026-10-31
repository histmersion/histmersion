package com.histmersion.domain.library.collections

import com.histmersion.api.*

/** What the collections' operations read and write of the library, lent by the composition: the records (as their
  * endpoints write them), the Calibre push metadata and gaps, the stored originals, the acquisition list, the ingest
  * queue, a source's cover and the archive.org import.
  */
trait CollectionLibrary:
  def source(citekey: String): Either[ApiError, SourceWithFindings]
  def create(citekey: String, put: SourcePut): Either[ApiError, SourceWithFindings]
  def patch(citekey: String, patch: SourcePatch): Either[ApiError, SourceWithFindings]
  def pushMeta(citekey: String): Either[ApiError, CalibreGap]
  def pushed(req: CalibrePushRequest): Either[ApiError, CalibrePushResult]
  def gaps(limit: Option[Int]): CalibreGaps
  def original(citekey: String): Either[ApiError, StoredOriginal]
  def queueItem(id: Int): Either[ApiError, Option[Queue.QueueItem]]
  def ingest(params: IngestParams, bytes: Array[Byte]): Either[ApiError, IngestAccepted]
  def putCover(params: SourceCoverParams, bytes: Array[Byte]): Either[ApiError, MediaItem]
  def openLibraryCover(req: MediaCoversRequest): Either[ApiError, MediaCoversReport]
  def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest): Either[ApiError, IngestReport]
