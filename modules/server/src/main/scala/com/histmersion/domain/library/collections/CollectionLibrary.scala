package com.histmersion
package domain.library.collections

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
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** What the collections' operations read and write of the library, lent by the composition: the records (as their
  * endpoints write them), the Calibre push metadata and gaps, the stored originals, the acquisition list, the ingest
  * queue, a source's cover and the archive.org import.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait CollectionLibrary:
  def source(citekey: Citekey): Either[Api.Error, Source.WithFindings]
  def create(citekey: Citekey, put: Source.Put)(using Creator.New): Either[Api.Error, Source.WithFindings]
  def patch(citekey: Citekey, patch: Source.Patch)(using Creator.New): Either[Api.Error, Source.WithFindings]
  def pushMeta(citekey: Citekey): Either[Api.Error, CalibreGap]
  def pushed(req: CalibrePushRequest): Either[Api.Error, CalibrePushResult]
  def gaps(limit: Option[CollectionLibrary.Limit]): CalibreGaps
  def original(citekey: Citekey): Either[Api.Error, StoredOriginal]
  def queueItem(id: CollectionLibrary.QueueItemId): Either[Api.Error, Option[Queue.QueueItem]]
  def ingest(params: Ingest.Params, bytes: CollectionLibrary.Bytes)(using
      Creator.New
  ): Either[Api.Error, Ingest.Accepted]
  def putCover(params: SourceCoverParams, bytes: CollectionLibrary.Bytes)(using
      Creator.New
  ): Either[Api.Error, MediaItem]
  def openLibraryCover(req: MediaCoversRequest)(using Creator.New): Either[Api.Error, MediaCoversReport]
  def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest)(using
      Creator.New
  ): Either[Api.Error, Ingest.Report]

object CollectionLibrary:
  type Bytes = Bytes.T
  object Bytes extends Newtype[Array[Byte], Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type QueueItemId = QueueItemId.T
  object QueueItemId extends Newtype[Int, Pure]
