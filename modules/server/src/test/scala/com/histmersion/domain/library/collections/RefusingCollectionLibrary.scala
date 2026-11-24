package com.histmersion
package domain.library.collections
import com.histmersion.util.problem.{Legacy, Problem}

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
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** A library a suite's collections write through that refuses everything; a suite overrides what it uses. */
open class RefusingCollectionLibrary extends CollectionLibrary:
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def no = Left(Api.Error(Problem.Unclassified(Legacy.Text("not used in this suite"))))
  def source(citekey: Citekey): Either[Api.Error, Source.WithFindings] = no
  def create(citekey: Citekey, put: Source.Put)(using Creator.New): Either[Api.Error, Source.WithFindings] = no
  def patch(citekey: Citekey, patch: Source.Patch)(using Creator.New): Either[Api.Error, Source.WithFindings] = no
  def pushMeta(citekey: Citekey): Either[Api.Error, CalibreGap] = no
  def pushed(req: CalibrePushRequest): Either[Api.Error, CalibrePushResult] = no
  def gaps(limit: Option[CollectionLibrary.Limit]): CalibreGaps = CalibreGaps(CalibreGaps.Count(0), Nil)
  def original(citekey: Citekey): Either[Api.Error, StoredOriginal] = no
  def queueItem(id: CollectionLibrary.QueueItemId): Either[Api.Error, Option[Queue.QueueItem]] = no
  def ingest(params: Ingest.Params, bytes: CollectionLibrary.Bytes)(using
      Creator.New
  ): Either[Api.Error, Ingest.Accepted] = no
  def putCover(params: SourceCoverParams, bytes: CollectionLibrary.Bytes)(using
      Creator.New
  ): Either[Api.Error, MediaItem] = no
  def openLibraryCover(req: MediaCoversRequest)(using Creator.New): Either[Api.Error, MediaCoversReport] = no
  def archiveImport(item: ArchiveItem, file: ArchiveFile, req: ArchiveImportRequest)(using
      Creator.New
  ): Either[Api.Error, Ingest.Report] =
    no
