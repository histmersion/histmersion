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
  def source(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, Source.WithFindings]
  def create(citekey: com.histmersion.domain.library.Citekey, put: Source.Put): Either[Api.Error, Source.WithFindings]
  def patch(citekey: com.histmersion.domain.library.Citekey, patch: Source.Patch): Either[Api.Error, Source.WithFindings]
  def pushMeta(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, CalibreGap]
  def pushed(req: CalibrePushRequest): Either[Api.Error, CalibrePushResult]
  def gaps(limit: Option[com.histmersion.domain.library.collections.CollectionLibrary.GapsLimit]): CalibreGaps
  def original(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, StoredOriginal]
  def queueItem(id: com.histmersion.domain.library.collections.CollectionLibrary.QueueItemId): Either[Api.Error, Option[Queue.QueueItem]]
  def ingest(params: Ingest.Params, bytes: Array[com.histmersion.domain.library.collections.CollectionLibrary.IngestBytes]): Either[Api.Error, Ingest.Accepted]
  def putCover(params: SourceCoverParams, bytes: Array[com.histmersion.domain.library.collections.CollectionLibrary.PutCoverBytes]): Either[Api.Error, MediaItem]
  def openLibraryCover(req: MediaCoversRequest): Either[Api.Error, MediaCoversReport]
  def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest): Either[Api.Error, Ingest.Report]

object CollectionLibrary:
  type GapsLimit = GapsLimit.T
  object GapsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueItemId = QueueItemId.T
  object QueueItemId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestBytes = IngestBytes.T
  object IngestBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutCoverBytes = PutCoverBytes.T
  object PutCoverBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
