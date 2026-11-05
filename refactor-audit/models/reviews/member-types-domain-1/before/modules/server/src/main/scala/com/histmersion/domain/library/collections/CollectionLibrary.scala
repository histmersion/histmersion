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

/** What the collections' operations read and write of the library, lent by the composition: the records (as their
  * endpoints write them), the Calibre push metadata and gaps, the stored originals, the acquisition list, the ingest
  * queue, a source's cover and the archive.org import.
  */
trait CollectionLibrary:
  def source(citekey: String): Either[Api.Error, Source.WithFindings]
  def create(citekey: String, put: Source.Put): Either[Api.Error, Source.WithFindings]
  def patch(citekey: String, patch: Source.Patch): Either[Api.Error, Source.WithFindings]
  def pushMeta(citekey: String): Either[Api.Error, CalibreGap]
  def pushed(req: CalibrePushRequest): Either[Api.Error, CalibrePushResult]
  def gaps(limit: Option[Int]): CalibreGaps
  def original(citekey: String): Either[Api.Error, StoredOriginal]
  def queueItem(id: Int): Either[Api.Error, Option[Queue.QueueItem]]
  def ingest(params: Ingest.Params, bytes: Array[Byte]): Either[Api.Error, Ingest.Accepted]
  def putCover(params: SourceCoverParams, bytes: Array[Byte]): Either[Api.Error, MediaItem]
  def openLibraryCover(req: MediaCoversRequest): Either[Api.Error, MediaCoversReport]
  def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest): Either[Api.Error, Ingest.Report]
