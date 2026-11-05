package com.histmersion.domain.library.sources

import com.histmersion.api.Write
import com.histmersion.domain.Caller
import com.histmersion.domain.Licence
import com.histmersion.domain.library.LibraryState
import com.histmersion.domain.library.sources.referrers.{DeleteReport, SourceReferrers}
import com.histmersion.util.database.Transactor
import com.histmersion.util.ocr.{Chunk, Chunker}
import com.histmersion.api.text.WikiLinks

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.api.library.{CalibreGap, DeleteCount, DeletePart, Edge, Rename, Source, TocEntry}
import com.histmersion.api.Api
import com.histmersion.api.library.Finding

/** The library's sources: a record with its findings and the edges it makes, its old citekeys, its rename and delete
  * (what other domains hold of it follows through the referrers), and its body as read by part.
  */
trait SourceService:

  /** The citekey a source has now: `id` itself, or the source an old citekey was renamed to. */
  def current(id: com.histmersion.domain.library.sources.SourceService.CurrentId): Option[com.histmersion.domain.library.sources.SourceService.CurrentResult]

  /** The source `citekey` was renamed to, when it is an old citekey. */
  def aliasOf(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceService.AliasOfResult]

  /** The old citekeys that name `citekey` now. */
  def aliasesOf(citekey: com.histmersion.domain.library.Citekey): Set[com.histmersion.domain.library.sources.SourceService.AliasesOfResult]

  /** Every old citekey and the source it names now, by old citekey. */
  def aliases: List[Source.Alias]

  def citekeys: List[com.histmersion.domain.library.sources.SourceService.CitekeysResult]

  /** `citekey -> its last write` in epoch milliseconds, for the seed's staleness guard. */
  def updatedAtMillis: Map[com.histmersion.domain.library.sources.SourceService.UpdatedAtMillisResultPart1, com.histmersion.domain.library.sources.SourceService.UpdatedAtMillisResultPart2]

  def calibreId(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceService.CalibreIdResult]

  /** A source as read: the record and its findings, in id order. */
  def get(citekey: com.histmersion.domain.library.Citekey): Option[(Source.Detail, List[Finding.Row])]

  /** A source as written: the record with its findings and edges. */
  def load(citekey: com.histmersion.domain.library.Citekey): Option[Source]

  /** The citation-link view of the stored sources among `citekeys`, in citekey order. */
  def refs(citekeys: List[com.histmersion.domain.library.sources.SourceService.RefsCitekeys]): List[Source.Ref]

  /** The citekey each wikilink target names: exact, an old citekey, or the one source a short form fits. */
  def resolveLinks(targets: List[com.histmersion.domain.library.sources.SourceService.ResolveLinksTargets]): Map[com.histmersion.domain.library.sources.SourceService.ResolveLinksResultPart1, com.histmersion.domain.library.sources.SourceService.ResolveLinksResultPart2]

  def toc(citekey: com.histmersion.domain.library.Citekey): List[TocEntry]
  def bodyText(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceService.BodyTextResult
  def bodyUnderHeading(citekey: com.histmersion.domain.library.Citekey, heading: com.histmersion.domain.library.sources.SourceService.BodyUnderHeadingHeading): com.histmersion.domain.library.sources.SourceService.BodyUnderHeadingResult
  def bodyChunks(citekey: com.histmersion.domain.library.Citekey): List[Chunk]

  /** The source's converted text as stored, where its figures are linked from. */
  def markdown(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceService.MarkdownResult

  /** Replace everything under `source.citekey`; the new last-write token. */
  def put(source: Source, caller: Caller): com.histmersion.domain.library.sources.SourceService.PutResult

  /** Merge `patch` into the source `requested` names (an old citekey follows); the new last-write token. */
  def patch(requested: com.histmersion.domain.library.sources.SourceService.PatchRequested, patch: Source.Patch, caller: Caller): Either[Api.Error, com.histmersion.domain.library.sources.SourceService.PatchResultPart2]

  def rename(from: com.histmersion.domain.library.sources.SourceService.RenameFrom, to: com.histmersion.domain.library.sources.SourceService.RenameTo, caller: Caller): Either[Api.Error, Rename.Counts]

  /** What deleting the source would do; nothing is changed. */
  def deletion(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, SourceDeletePlan]

  /** Delete the source unless something refuses it; `files` names its stored originals, `day` is the log's topic. */
  def delete(
      citekey: com.histmersion.domain.library.Citekey,
      day: com.histmersion.domain.library.sources.SourceService.DeleteDay,
      files: SourceDeletePlan => List[com.histmersion.domain.library.sources.SourceService.DeleteFilesResult],
      caller: Caller
  ): Either[Api.Error, SourceDeletePlan]

  /** Promote the parked edges whose target now names one stored source; how many. */
  def resolveEdges(caller: Caller): com.histmersion.domain.library.sources.SourceService.ResolveEdgesResult

  // ── what acquisition, ingest and the collections read and write of a source ───────────────

  def exists(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceService.ExistsResult

  /** `lower(doi) -> citekey` for the whole corpus. */
  def doiIndex: Map[com.histmersion.domain.library.sources.SourceService.DoiIndexResultPart1, com.histmersion.domain.library.sources.SourceService.DoiIndexResultPart2]

  /** The citekey of the source holding `doi` (the first by citekey), if one does. */
  def citekeyOfDoi(doi: com.histmersion.domain.library.Doi): Option[com.histmersion.domain.library.sources.SourceService.CitekeyOfDoiResult]

  /** Store edges routed by their target (one to a source not stored yet is parked); how many were new. */
  def addEdges(edges: List[Edge], caller: Caller): com.histmersion.domain.library.sources.SourceService.AddEdgesResult

  /** The digest of the source's stored original. */
  def original(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceService.OriginalResult]

  /** The source that holds an original with this digest. */
  def holderOf(sha256: com.histmersion.Sha256): Option[com.histmersion.domain.library.sources.SourceService.HolderOfResult]

  /** The digests of the stored originals of those of `citekeys` that have one. */
  def originals(citekeys: List[com.histmersion.domain.library.sources.SourceService.OriginalsCitekeys]): Map[com.histmersion.domain.library.sources.SourceService.OriginalsResultPart1, com.histmersion.domain.library.sources.SourceService.OriginalsResultPart2]

  def setOriginal(citekey: com.histmersion.domain.library.Citekey, sha256: com.histmersion.Sha256, caller: Caller): Unit

  /** The licence the source is published under, classified as it is read (Q26). */
  def licence(citekey: com.histmersion.domain.library.Citekey): Option[Licence]

  /** `licence` recorded as the source's when it has none; `true` when it was written. */
  def recordLicence(citekey: com.histmersion.domain.library.Citekey, licence: Licence.Id, caller: Caller): com.histmersion.domain.library.sources.SourceService.RecordLicenceResult

  /** The sources not in Calibre yet, in citekey order, at most `limit` (none: all). */
  def calibreGaps(limit: Option[com.histmersion.domain.library.sources.SourceService.CalibreGapsLimit]): List[CalibreGap]

  /** What a push of the source into Calibre names it by. */
  def calibrePushMeta(citekey: com.histmersion.domain.library.Citekey): Option[CalibreGap]

  /** The Calibre id written back after a push. */
  def setCalibreId(citekey: com.histmersion.domain.library.Citekey, calibreId: com.histmersion.domain.library.sources.SourceService.SetCalibreIdCalibreId, caller: Caller): Either[Api.Error, Unit]

object SourceService:

  type CurrentResult = CurrentResult.T
  object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentId = CurrentId.T
  object CurrentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasOfResult = AliasOfResult.T
  object AliasOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasesOfResult = AliasesOfResult.T
  object AliasesOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeysResult = CitekeysResult.T
  object CitekeysResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAtMillisResultPart1 = UpdatedAtMillisResultPart1.T
  object UpdatedAtMillisResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatedAtMillisResultPart2 = UpdatedAtMillisResultPart2.T
  object UpdatedAtMillisResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreIdResult = CalibreIdResult.T
  object CalibreIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsCitekeys = RefsCitekeys.T
  object RefsCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveLinksResultPart1 = ResolveLinksResultPart1.T
  object ResolveLinksResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveLinksResultPart2 = ResolveLinksResultPart2.T
  object ResolveLinksResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveLinksTargets = ResolveLinksTargets.T
  object ResolveLinksTargets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyTextResult = BodyTextResult.T
  object BodyTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyUnderHeadingResult = BodyUnderHeadingResult.T
  object BodyUnderHeadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyUnderHeadingHeading = BodyUnderHeadingHeading.T
  object BodyUnderHeadingHeading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutResult = PutResult.T
  object PutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchResultPart2 = PatchResultPart2.T
  object PatchResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchRequested = PatchRequested.T
  object PatchRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameFrom = RenameFrom.T
  object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameTo = RenameTo.T
  object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteDay = DeleteDay.T
  object DeleteDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteFilesResult = DeleteFilesResult.T
  object DeleteFilesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveEdgesResult = ResolveEdgesResult.T
  object ResolveEdgesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExistsResult = ExistsResult.T
  object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoiIndexResultPart1 = DoiIndexResultPart1.T
  object DoiIndexResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoiIndexResultPart2 = DoiIndexResultPart2.T
  object DoiIndexResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitekeyOfDoiResult = CitekeyOfDoiResult.T
  object CitekeyOfDoiResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddEdgesResult = AddEdgesResult.T
  object AddEdgesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalResult = OriginalResult.T
  object OriginalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HolderOfResult = HolderOfResult.T
  object HolderOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalsResultPart1 = OriginalsResultPart1.T
  object OriginalsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalsResultPart2 = OriginalsResultPart2.T
  object OriginalsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalsCitekeys = OriginalsCitekeys.T
  object OriginalsCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordLicenceResult = RecordLicenceResult.T
  object RecordLicenceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreGapsLimit = CalibreGapsLimit.T
  object CalibreGapsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetCalibreIdCalibreId = SetCalibreIdCalibreId.T
  object SetCalibreIdCalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor, referrers: SourceReferrers, settings: () => SourceSettings): SourceService =
    Implementation(db, referrers, settings)

  final private class Implementation(db: Transactor, referrers: SourceReferrers, settings: () => SourceSettings)
      extends SourceService:
    def current(id: com.histmersion.domain.library.sources.SourceService.Implementation.CurrentId): Option[com.histmersion.domain.library.sources.SourceService.Implementation.CurrentResult] = db.read(SourceRepo.canonical(id))
    def aliasOf(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceService.Implementation.AliasOfResult] = db.read(SourceRepo.aliasOf(citekey))
    def aliasesOf(citekey: com.histmersion.domain.library.Citekey): Set[com.histmersion.domain.library.sources.SourceService.Implementation.AliasesOfResult] = db.read(SourceRepo.aliasesOf(citekey))
    def aliases: List[Source.Alias] = db.read(SourceRepo.aliases)
    def citekeys: List[com.histmersion.domain.library.sources.SourceService.Implementation.CitekeysResult] = db.read(SourceRepo.citekeys()).toList
    def updatedAtMillis: Map[com.histmersion.domain.library.sources.SourceService.Implementation.UpdatedAtMillisResultPart1, com.histmersion.domain.library.sources.SourceService.Implementation.UpdatedAtMillisResultPart2] = db.read(SourceRepo.updatedAtMillis())
    def calibreId(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceService.Implementation.CalibreIdResult] = db.read(SourceRepo.calibreId(citekey))
    def get(citekey: com.histmersion.domain.library.Citekey): Option[(Source.Detail, List[Finding.Row])] =
      db.read(SourceRepo.get(citekey)).map((s, fs) => (s, fs.toList))
    def load(citekey: com.histmersion.domain.library.Citekey): Option[Source] = db.read(SourceRepo.load(citekey))
    def refs(citekeys: List[com.histmersion.domain.library.sources.SourceService.Implementation.RefsCitekeys]): List[Source.Ref] = db.read(SourceRepo.refs(citekeys))
    def resolveLinks(targets: List[com.histmersion.domain.library.sources.SourceService.Implementation.ResolveLinksTargets]): Map[com.histmersion.domain.library.sources.SourceService.Implementation.ResolveLinksResultPart1, com.histmersion.domain.library.sources.SourceService.Implementation.ResolveLinksResultPart2] = db.read(SourceRepo.resolveAliases(targets))
    def toc(citekey: com.histmersion.domain.library.Citekey): List[TocEntry] = db.read(SourceRepo.toc(citekey))
    def bodyText(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceService.Implementation.BodyTextResult = db.read(SourceRepo.bodyText(citekey))
    def bodyUnderHeading(citekey: com.histmersion.domain.library.Citekey, heading: com.histmersion.domain.library.sources.SourceService.Implementation.BodyUnderHeadingHeading): com.histmersion.domain.library.sources.SourceService.Implementation.BodyUnderHeadingResult =
      db.read(SourceRepo.bodyUnderHeading(citekey, heading))
    def bodyChunks(citekey: com.histmersion.domain.library.Citekey): List[Chunk] = db.read(SourceRepo.bodyChunks(citekey))
    def markdown(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceService.Implementation.MarkdownResult = db.read(SourceRepo.markdown(citekey))

    def put(source: Source, caller: Caller): com.histmersion.domain.library.sources.SourceService.Implementation.PutResult =
      val s = settings()
      db.transact(SourceRepo.putIn(source, s.chunkChars, s.toleranceKm))

    def patch(requested: com.histmersion.domain.library.sources.SourceService.Implementation.PatchRequested, patch: Source.Patch, caller: Caller): Either[Api.Error, com.histmersion.domain.library.sources.SourceService.Implementation.PatchResultPart2] =
      val s = settings()
      db.transact(SourceRepo.patchIn(requested, patch, s.chunkChars, s.toleranceKm))

    def rename(from: com.histmersion.domain.library.sources.SourceService.Implementation.RenameFrom, to: com.histmersion.domain.library.sources.SourceService.Implementation.RenameTo, caller: Caller): Either[Api.Error, Rename.Counts] =
      db.transact(SourceRepo.rename(from, to, referrers))

    def deletion(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, SourceDeletePlan] =
      db.read(SourceDeleteRepo.planIn(citekey, referrers))

    def delete(
        citekey: com.histmersion.domain.library.Citekey,
        day: com.histmersion.domain.library.sources.SourceService.Implementation.DeleteDay,
        files: SourceDeletePlan => List[com.histmersion.domain.library.sources.SourceService.Implementation.DeleteFilesResult],
        caller: Caller
    ): Either[Api.Error, SourceDeletePlan] =
      db.transact(SourceDeleteRepo.applyIn(citekey, day, files, referrers))

    def resolveEdges(caller: Caller): com.histmersion.domain.library.sources.SourceService.Implementation.ResolveEdgesResult = db.transact(SourceRepo.resolveEdges())

    def exists(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceService.Implementation.ExistsResult = db.read(SourceRepo.exists(citekey))
    def doiIndex: Map[com.histmersion.domain.library.sources.SourceService.Implementation.DoiIndexResultPart1, com.histmersion.domain.library.sources.SourceService.Implementation.DoiIndexResultPart2] = db.read(SourceRepo.doiIndex())
    def citekeyOfDoi(doi: com.histmersion.domain.library.Doi): Option[com.histmersion.domain.library.sources.SourceService.Implementation.CitekeyOfDoiResult] = db.read(SourceRepo.citekeyOfDoi(doi))
    def addEdges(edges: List[Edge], caller: Caller): com.histmersion.domain.library.sources.SourceService.Implementation.AddEdgesResult =
      if edges.isEmpty then 0 else db.transact(SourceRepo.insertEdges(edges))
    def original(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceService.Implementation.OriginalResult] = db.read(SourceRepo.sha256Of(citekey))
    def holderOf(sha256: com.histmersion.Sha256): Option[com.histmersion.domain.library.sources.SourceService.Implementation.HolderOfResult] = db.read(SourceRepo.citekeyOfSha256(sha256))
    def originals(citekeys: List[com.histmersion.domain.library.sources.SourceService.Implementation.OriginalsCitekeys]): Map[com.histmersion.domain.library.sources.SourceService.Implementation.OriginalsResultPart1, com.histmersion.domain.library.sources.SourceService.Implementation.OriginalsResultPart2] =
      if citekeys.isEmpty then Map.empty else db.read(SourceRepo.sha256s(citekeys))
    def setOriginal(citekey: com.histmersion.domain.library.Citekey, sha256: com.histmersion.Sha256, caller: Caller): Unit =
      db.transact(SourceRepo.setSha256(citekey, sha256))
    def licence(citekey: com.histmersion.domain.library.Citekey): Option[Licence] =
      db.read(SourceRepo.licenceOf(citekey)).map(_.trim).filter(_.nonEmpty).map(Licence.classify)
    def recordLicence(citekey: com.histmersion.domain.library.Citekey, licence: Licence.Id, caller: Caller): com.histmersion.domain.library.sources.SourceService.Implementation.RecordLicenceResult =
      val v = licence.value.trim
      v.nonEmpty && db.transact(SourceRepo.recordLicence(citekey, v))
    def calibreGaps(limit: Option[com.histmersion.domain.library.sources.SourceService.Implementation.CalibreGapsLimit]): List[CalibreGap] = db.read(SourceRepo.calibreGaps(limit))
    def calibrePushMeta(citekey: com.histmersion.domain.library.Citekey): Option[CalibreGap] = db.read(SourceRepo.calibrePushMeta(citekey))
    def setCalibreId(citekey: com.histmersion.domain.library.Citekey, calibreId: com.histmersion.domain.library.sources.SourceService.Implementation.SetCalibreIdCalibreId, caller: Caller): Either[Api.Error, Unit] =
      db.transact:
        if SourceRepo.setCalibreId(citekey, calibreId) == 0 then Left(Write.notFound(citekey)) else Right(())

  object Implementation:
    type CurrentResult = CurrentResult.T
    object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CurrentId = CurrentId.T
    object CurrentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AliasOfResult = AliasOfResult.T
    object AliasOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AliasesOfResult = AliasesOfResult.T
    object AliasesOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitekeysResult = CitekeysResult.T
    object CitekeysResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAtMillisResultPart1 = UpdatedAtMillisResultPart1.T
    object UpdatedAtMillisResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAtMillisResultPart2 = UpdatedAtMillisResultPart2.T
    object UpdatedAtMillisResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CalibreIdResult = CalibreIdResult.T
    object CalibreIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefsCitekeys = RefsCitekeys.T
    object RefsCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveLinksResultPart1 = ResolveLinksResultPart1.T
    object ResolveLinksResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveLinksResultPart2 = ResolveLinksResultPart2.T
    object ResolveLinksResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveLinksTargets = ResolveLinksTargets.T
    object ResolveLinksTargets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BodyTextResult = BodyTextResult.T
    object BodyTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BodyUnderHeadingResult = BodyUnderHeadingResult.T
    object BodyUnderHeadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BodyUnderHeadingHeading = BodyUnderHeadingHeading.T
    object BodyUnderHeadingHeading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkdownResult = MarkdownResult.T
    object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutResult = PutResult.T
    object PutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchResultPart2 = PatchResultPart2.T
    object PatchResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchRequested = PatchRequested.T
    object PatchRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameFrom = RenameFrom.T
    object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameTo = RenameTo.T
    object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteDay = DeleteDay.T
    object DeleteDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteFilesResult = DeleteFilesResult.T
    object DeleteFilesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveEdgesResult = ResolveEdgesResult.T
    object ResolveEdgesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExistsResult = ExistsResult.T
    object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoiIndexResultPart1 = DoiIndexResultPart1.T
    object DoiIndexResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoiIndexResultPart2 = DoiIndexResultPart2.T
    object DoiIndexResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitekeyOfDoiResult = CitekeyOfDoiResult.T
    object CitekeyOfDoiResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddEdgesResult = AddEdgesResult.T
    object AddEdgesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalResult = OriginalResult.T
    object OriginalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HolderOfResult = HolderOfResult.T
    object HolderOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalsResultPart1 = OriginalsResultPart1.T
    object OriginalsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalsResultPart2 = OriginalsResultPart2.T
    object OriginalsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalsCitekeys = OriginalsCitekeys.T
    object OriginalsCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordLicenceResult = RecordLicenceResult.T
    object RecordLicenceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CalibreGapsLimit = CalibreGapsLimit.T
    object CalibreGapsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetCalibreIdCalibreId = SetCalibreIdCalibreId.T
    object SetCalibreIdCalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The sources the library holds in memory, as a database holding only the library's rows answers: no other domain
    * stores anything of a source, so a rename rewrites no other record and nothing refuses a delete; `clock` stands in
    * for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[LibraryState], clock: Clock, settings: () => SourceSettings)
      extends SourceService:

    private val Token = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)
    private def token(at: Instant): com.histmersion.domain.library.sources.SourceService.InMemory.TokenResult = Token.format(at)

    private def currentIn(st: LibraryState, id: com.histmersion.domain.library.sources.SourceService.InMemory.CurrentInId): Option[com.histmersion.domain.library.sources.SourceService.InMemory.CurrentInResult] =
      if st.sources.contains(id) then Some(id) else st.aliases.get(id)

    def current(id: com.histmersion.domain.library.sources.SourceService.InMemory.CurrentId): Option[com.histmersion.domain.library.sources.SourceService.InMemory.CurrentResult] = currentIn(state.get, id)
    def aliasOf(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceService.InMemory.AliasOfResult] = state.get.aliases.get(citekey)
    def aliasesOf(citekey: com.histmersion.domain.library.Citekey): Set[com.histmersion.domain.library.sources.SourceService.InMemory.AliasesOfResult] = state.get.aliases.collect { case (a, ck) if ck == citekey => a }.toSet
    def aliases: List[Source.Alias] = state.get.aliases.toList.sortBy(_._1).map((alias, citekey) => Source.Alias(alias, com.histmersion.api.library.Citekey(citekey)))
    def citekeys: List[com.histmersion.domain.library.sources.SourceService.InMemory.CitekeysResult] = state.get.sources.keys.toList.sorted
    def updatedAtMillis: Map[com.histmersion.domain.library.sources.SourceService.InMemory.UpdatedAtMillisResultPart1, com.histmersion.domain.library.sources.SourceService.InMemory.UpdatedAtMillisResultPart2] = state.get.sources.view.mapValues(_.updatedAt.toEpochMilli).toMap
    def calibreId(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceService.InMemory.CalibreIdResult] = state.get.sources.get(citekey).flatMap(_.source.calibreId)

    def get(citekey: com.histmersion.domain.library.Citekey): Option[(Source.Detail, List[Finding.Row])] =
      state.get.sources.get(citekey).map { st =>
        val s = st.source
        val detail = Source.Detail(
          s.citekey,
          s.title,
          s.authors,
          s.year,
          s.venue,
          s.status,
          s.topics,
          s.regions,
          s.tags,
          s.period.map(_.start),
          s.period.map(_.end),
          s.summary,
          s.doi,
          s.ocrMarkdown,
          s.notes,
          Some(token(st.updatedAt)),
          s.provenance,
          Some(st.uid),
          None
        )
        val findings = s.findings.sortBy(_.id).map { f =>
          Finding.Row(
            f.claim,
            f.region,
            f.geo.map(_.lon),
            f.geo.map(_.lat),
            f.period.map(_.start),
            f.period.map(_.end),
            f.periodName,
            f.tags,
            f.confidence,
            f.significance,
            st.findingUids.get(f.id)
          )
        }
        (detail, findings)
      }

    private def loadIn(st: LibraryState, citekey: com.histmersion.domain.library.Citekey): Option[Source] =
      st.sources.get(citekey).map { stored =>
        val edges = (st.edges.toList ++ st.parked.toList).filter(_.src == citekey).sortBy(e => (e.kind, e.dst))
        stored.source
          .modify(_.findings)
          .using(_.sortBy(_.id))
          .modify(_.edges)
          .setTo(edges)
          .modify(_.updatedAt)
          .setTo(Some(token(stored.updatedAt)))
      }

    def load(citekey: com.histmersion.domain.library.Citekey): Option[Source] = loadIn(state.get, citekey)

    def refs(citekeys: List[com.histmersion.domain.library.sources.SourceService.InMemory.RefsCitekeys]): List[Source.Ref] =
      val st = state.get
      citekeys.distinct.sorted.flatMap(ck =>
        st.sources
          .get(ck)
          .map(x => Source.Ref(com.histmersion.api.library.Citekey(ck), x.source.doi, x.source.authors, x.source.year, x.source.title, x.source.status))
      )

    /** The precedence of the database's resolution: the citekey itself, an old citekey, a short form. */
    private def resolveIn(st: LibraryState, targets: List[com.histmersion.domain.library.sources.SourceService.InMemory.ResolveInTargets]): Map[com.histmersion.domain.library.sources.SourceService.InMemory.ResolveInResultPart1, com.histmersion.domain.library.sources.SourceService.InMemory.ResolveInResultPart2] =
      targets
        .map(_.trim.toLowerCase)
        .filter(_.nonEmpty)
        .distinct
        .flatMap { t =>
          val exact = st.sources.keys.filter(_ == t).map(_ -> 0)
          val short = st.sources.keys.filter(_.startsWith(t + "_")).map(_ -> 2)
          val old = st.aliases.get(t).map(_ -> 1)
          val cands = (exact ++ short ++ old).toList
          if cands.isEmpty then None
          else
            val best = cands.map(_._2).min
            cands.filter(_._2 == best).map(_._1).distinct match
              case one :: Nil => Some(t -> one)
              case _          => None
        }
        .toMap

    def resolveLinks(targets: List[com.histmersion.domain.library.sources.SourceService.InMemory.ResolveLinksTargets]): Map[com.histmersion.domain.library.sources.SourceService.InMemory.ResolveLinksResultPart1, com.histmersion.domain.library.sources.SourceService.InMemory.ResolveLinksResultPart2] = resolveIn(state.get, targets)

    private def chunksOf(citekey: com.histmersion.domain.library.Citekey): List[Chunk] = state.get.sources.get(citekey).fold(Nil)(_.chunks)

    def toc(citekey: com.histmersion.domain.library.Citekey): List[TocEntry] =
      chunksOf(citekey)
        .filter(_.heading.isDefined)
        .groupBy(_.heading.get)
        .toList
        .map((h, cs) => TocEntry(h, cs.map(_.ord).min, cs.map(_.body.length).sum))
        .sortBy(_.ord)

    def bodyText(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceService.InMemory.BodyTextResult =
      chunksOf(citekey) match
        case Nil => markdown(citekey)
        case cs  => cs.sortBy(_.ord).map(_.body).mkString("\n\n")

    def bodyUnderHeading(citekey: com.histmersion.domain.library.Citekey, heading: com.histmersion.domain.library.sources.SourceService.InMemory.BodyUnderHeadingHeading): com.histmersion.domain.library.sources.SourceService.InMemory.BodyUnderHeadingResult =
      val lower = heading.trim.toLowerCase
      chunksOf(citekey)
        .sortBy(_.ord)
        .filter(_.heading.exists(_.toLowerCase.startsWith(lower)))
        .map(_.body)
        .mkString(
          "\n\n"
        )

    def bodyChunks(citekey: com.histmersion.domain.library.Citekey): List[Chunk] =
      chunksOf(citekey) match
        case Nil => Option(markdown(citekey)).filter(_.nonEmpty).map(Chunk(0, None, _)).toList
        case cs  => cs.sortBy(_.ord)

    def markdown(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceService.InMemory.MarkdownResult = state.get.sources.get(citekey).flatMap(_.source.ocrMarkdown).getOrElse("")

    // ── writes ───────────────────────────────────────────────────────────────────────────────

    /** The source written under its citekey: its findings kept by id, its edges routed by their targets, the parked
      * edges that named it promoted.
      */
    private def written(st: LibraryState, s: Source, at: Instant): LibraryState =
      st.aliases.get(s.citekey).foreach { now =>
        throw IllegalStateException(s"'${s.citekey}' was renamed to '$now' — write to '$now' instead")
      }
      val ck = s.citekey
      val before = st.sources.get(ck)
      val ids = s.findings.map(_.id).toSet
      val others = st.sources.view
        .mapValues(o =>
          o.modify(_.source.findings)
            .using(
              _.filterNot(f =>
                ids(f.id) &&
                  o.source.citekey != ck
              )
            )
        )
        .toMap
      val stored = LibraryState.Stored(
        s.modify(_.findings)
          .using(_.map(_.modify(_.citekey).setTo(ck)))
          .modify(_.edges)
          .setTo(Nil)
          .modify(_.updatedAt)
          .setTo(None),
        before.fold(UUID.randomUUID())(_.uid),
        s.findings.map(f => f.id -> before.flatMap(_.findingUids.get(f.id)).getOrElse(UUID.randomUUID())).toMap,
        s.ocrMarkdown.map(Chunker.chunk(_, settings().chunkChars)).getOrElse(Nil),
        at,
        before.flatMap(_.sha256),
        before.flatMap(_.licence)
      )
      val placed = st.modify(_.sources).setTo(others.updated(ck, stored))
      val cleared =
        placed.modify(_.edges).using(_.filterNot(_.src == ck)).modify(_.parked).using(_.filterNot(_.src == ck))
      promoted(edgesInserted(cleared, s.edges.map(_.modify(_.src).setTo(ck)).distinct), ck)

    private def edgesInserted(st: LibraryState, edges: List[Edge]): LibraryState =
      @tailrec def loop(rest: List[Edge], resolved: Set[Edge], parked: Set[Edge]): LibraryState =
        rest match
          case e :: tail =>
            currentIn(st, e.dst) match
              case Some(dst) => loop(tail, resolved + e.modify(_.dst).setTo(dst), parked)
              case None      => loop(tail, resolved, parked + e)
          case Nil => st.modify(_.edges).setTo(resolved).modify(_.parked).setTo(parked)
      loop(edges, st.edges, st.parked)

    private def promoted(st: LibraryState, citekey: com.histmersion.domain.library.Citekey): LibraryState =
      val (waiting, rest) = st.parked.partition(_.dst == citekey)
      st.modify(_.edges).using(_ ++ waiting).modify(_.parked).setTo(rest)

    def put(source: Source, caller: Caller): com.histmersion.domain.library.sources.SourceService.InMemory.PutResult =
      val at = clock.instant()
      val _ = state.updateAndGet(written(_, source, at))
      token(at)

    def patch(requested: com.histmersion.domain.library.sources.SourceService.InMemory.PatchRequested, patch: Source.Patch, caller: Caller): Either[Api.Error, com.histmersion.domain.library.sources.SourceService.InMemory.PatchResultPart2] =
      val at = clock.instant()
      var answer: Either[Api.Error, com.histmersion.domain.library.sources.SourceService.InMemory.PatchAnswerPart2] = Left(Write.notFound(requested))
      val _ = state.updateAndGet { st =>
        val citekey = currentIn(st, requested).getOrElse(requested)
        loadIn(st, citekey) match
          case None           => { answer = Left(Write.notFound(citekey)); st }
          case Some(existing) =>
            Source.Write.precondition(existing, patch) match
              case Left(msg) => { answer = Left(Api.Error(msg, Some(Write.CodePrecondition))); st }
              case Right(_)  =>
                Source.Write.applyPatch(existing, Source.Write.srcAliased(patch, requested, citekey)) match
                  case Left(msg)      => { answer = Left(Api.Error(msg, Some(Write.CodeInvalid))); st }
                  case Right(updated) => { answer = Right(token(at)); written(st, updated, at) }
      }
      answer

    def rename(from: com.histmersion.domain.library.sources.SourceService.InMemory.RenameFrom, to: com.histmersion.domain.library.sources.SourceService.InMemory.RenameTo, caller: Caller): Either[Api.Error, Rename.Counts] =
      Citekeys.renameTo(from, to).left.map(Write.invalid).flatMap { validated =>
        val at = clock.instant()
        var answer: Either[Api.Error, Rename.Counts] = Left(Write.notFound(from))
        val _ = state.updateAndGet { st =>
          renamedIn(st, from, validated.value, at) match
            case Left(e)        => { answer = Left(e); st }
            case Right((n, rc)) => { answer = Right(rc); n }
        }
        answer
      }

    private def renamedIn(
        st: LibraryState,
        from: com.histmersion.domain.library.sources.SourceService.InMemory.RenamedInFrom,
        to: com.histmersion.domain.library.sources.SourceService.InMemory.RenamedInTo,
        at: Instant
    ): Either[Api.Error, (LibraryState, Rename.Counts)] =
      val idTaken = st.sources.values.flatMap(_.source.findings).map(_.id).find(_.startsWith(to + "#"))
      if !st.sources.contains(from) then
        Left(st.aliases.get(from) match
          case Some(now) =>
            Api.Error(s"'$from' is an old citekey of '$now' — rename '$now' instead", Some(Write.CodeInvalid))
          case None => Write.notFound(from))
      else if st.sources.contains(to) then
        Left(Api.Error(s"'$to' is already the citekey of another source", Some(Write.CodeInvalid)))
      else
        st.aliases.get(to) match
          case Some(other) if other != from =>
            Left(
              Api.Error(
                s"'$to' is an old citekey of '$other' and still resolves to it — pick another",
                Some(Write.CodeInvalid)
              )
            )
          case back =>
            idTaken match
              case Some(id) =>
                Left(
                  Api.Error(s"finding id '$id' already exists — '$to' would collide with it", Some(Write.CodeInvalid))
                )
              case None =>
                val unaliased =
                  if back.isDefined then st.modify(_.aliases).using(_ - to).modify(_.aliasTombstones).using(_ + to)
                  else st
                Right(renamedNow(unaliased, from, to, at))

    private def renamedNow(st: LibraryState, from: com.histmersion.domain.library.sources.SourceService.InMemory.RenamedNowFrom, to: com.histmersion.domain.library.sources.SourceService.InMemory.RenamedNowTo, at: Instant): (LibraryState, Rename.Counts) =
      val shorts = resolveIn(st, Rename.shortForms(from)).collect { case (f, ck) if ck == from => f }.toSet
      val prefix = from + "#"
      val stored = st.sources(from)
      val renumbered = stored.source.findings.count(_.id.startsWith(prefix))
      def newId(id: com.histmersion.domain.library.sources.SourceService.InMemory.RenamedNowNewIdId): com.histmersion.domain.library.sources.SourceService.InMemory.RenamedNowNewIdResult = if id.startsWith(prefix) then to + id.drop(from.length) else id
      val moved = stored
        .modify(_.source.citekey)
        .setTo(to)
        .modify(_.source.findings)
        .using(_.map(f => f.modify(_.id).using(newId).modify(_.citekey).setTo(to)))
        .modify(_.findingUids)
        .using(_.map((id, u) => newId(id) -> u))
        .modify(_.updatedAt)
        .setTo(at)
      def follow(e: Edge): Edge =
        e.modify(_.src).using(s => if s == from then to else s).modify(_.dst).using(d => if d == from then to else d)
      val keyed = st
        .modify(_.sources)
        .using(_ - from + (to -> moved))
        .modify(_.aliases)
        .using(_.map((a, ck) => a -> (if ck == from then to else ck)))
        .modify(_.edges)
        .using(_.map(follow))
        .modify(_.parked)
        .using(_.map(e => e.modify(_.src).using(s => if s == from then to else s)))
      val waiting = keyed.parked.map(_.dst).filter(d => d == to || to.startsWith(d + "_")).toList.distinct
      val resolvedNow = resolveIn(keyed, waiting)
      val promotable = waiting.filter(d => resolvedNow.get(d).contains(to)).toSet
      val promoting = keyed.parked.filter(e => promotable(e.dst) && e.src != to)
      val newEdges = promoting.map(_.modify(_.dst).setTo(to)).filterNot(keyed.edges)
      val linked = keyed.modify(_.edges).using(_ ++ newEdges).modify(_.parked).using(_ -- promoting)
      val target: com.histmersion.domain.library.sources.SourceService.InMemory.RenamedNowTargetArg1 => Option[com.histmersion.domain.library.sources.SourceService.InMemory.RenamedNowTargetResult] = t => Option.when(t == from || shorts(t))(to)
      val needle = from.takeWhile(_ != '_')
      val retargeted = linked.sources.toList.sortBy(_._1).flatMap { (ck, x) =>
        val text = (x.source.notes.getOrElse("") + " " + x.source.summary.getOrElse("")).toLowerCase
        if !text.contains(needle) then None
        else
          val n = x.source.notes.map(WikiLinks.retarget(_, target))
          val s = x.source.summary.map(WikiLinks.retarget(_, target))
          val count = n.fold(0)(_._2) + s.fold(0)(_._2)
          Option.when(count > 0)((ck, count, n.map(_._1), s.map(_._1)))
      }
      val rewritten = retargeted.map((ck, _, n, s) =>
        ck -> linked
          .sources(ck)
          .modify(_.source.notes)
          .setTo(n)
          .modify(_.source.summary)
          .setTo(s)
          .modify(_.updatedAt)
          .setTo(at)
      )
      val texts = linked.modify(_.sources).using(_ ++ rewritten)
      val aliased =
        texts.modify(_.aliases).using(_.updated(from, to)).modify(_.aliasTombstones).using(_ - from)
      (
        aliased,
        Rename.Counts(renumbered, Nil, retargeted.map(_._1), retargeted.map(_._2).sum, Nil, newEdges.size)
      )

    // ── delete ───────────────────────────────────────────────────────────────────────────────

    private def doiKey(doi: Option[com.histmersion.domain.library.Doi]): Option[com.histmersion.domain.library.sources.SourceService.InMemory.DoiKeyResult] =
      doi.map(_.trim.toLowerCase.replaceFirst("^(https?://(dx\\.)?doi\\.org/|doi:\\s*)", "")).filter(_.nonEmpty)

    private def planIn(st: LibraryState, requested: com.histmersion.domain.library.sources.SourceService.InMemory.PlanInRequested): Either[Api.Error, SourceDeletePlan] =
      st.sources.get(requested) match
        case None =>
          Left(st.aliases.get(requested) match
            case Some(now) =>
              Api.Error(s"'$requested' is an old citekey of '$now' — delete '$now' instead", Some(Write.CodeInvalid))
            case None => Write.notFound(requested))
        case Some(x) =>
          val ck = requested
          val aliases = st.aliases.collect { case (a, c) if c == ck => a }.toList.sorted
          val edgesIn = st.edges.filter(e => e.dst == ck && e.src != ck).toList.sortBy(e => (e.src, e.kind))
          import com.histmersion.api.library.DeletePart.*
          val counts = List(
            Findings -> x.source.findings.size,
            EdgesOut -> st.edges.count(_.src == ck),
            ParkedOut -> st.parked.count(_.src == ck),
            EdgesIn -> edgesIn.size,
            Chunks -> x.chunks.size,
            Aliases -> aliases.size,
            Identifiers -> doiKey(x.source.doi).size
          ).collect { case (p, rows) if rows > 0 => DeleteCount(p, rows) }
          val d = Source.Deletion(
            citekey = ck,
            title = x.source.title,
            applied = false,
            counts = counts,
            aliases = aliases,
            edgesIn = edgesIn,
            calibreId = x.source.calibreId.map(_.trim).filter(_.nonEmpty)
          )
          Right(SourceDeletePlan(d, None, false, ck :: aliases, DeleteReport(counts = counts)))

    def deletion(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, SourceDeletePlan] = planIn(state.get, citekey)

    def delete(
        citekey: com.histmersion.domain.library.Citekey,
        day: com.histmersion.domain.library.sources.SourceService.InMemory.DeleteDay,
        files: SourceDeletePlan => List[com.histmersion.domain.library.sources.SourceService.InMemory.DeleteFilesResult],
        caller: Caller
    ): Either[Api.Error, SourceDeletePlan] =
      var answer: Either[Api.Error, SourceDeletePlan] = Left(Write.notFound(citekey))
      val _ = state.updateAndGet { st =>
        planIn(st, citekey) match
          case Left(e)   => { answer = Left(e); st }
          case Right(p0) =>
            val p = p0.modify(_.deletion.files).setTo(files(p0)).modify(_.deletion.applied).setTo(true)
            answer = Right(p)
            val ck = p.deletion.citekey
            val incoming = st.edges.filter(e => e.dst == ck && e.src != ck)
            st.modify(_.sources)
              .using(_ - ck)
              .modify(_.aliases)
              .using(_.filterNot(_._2 == ck))
              .modify(_.edges)
              .using(_.filterNot(e => e.src == ck || e.dst == ck))
              .modify(_.parked)
              .using(ps => ps.filterNot(_.src == ck) ++ incoming)
      }
      answer

    def resolveEdges(caller: Caller): com.histmersion.domain.library.sources.SourceService.InMemory.ResolveEdgesResult =
      var moved = 0
      val _ = state.updateAndGet { st =>
        val targets = resolveIn(st, st.parked.toList.map(_.dst))
        val moves = st.parked.toList.flatMap(e => targets.get(e.dst.trim.toLowerCase).filter(_ != e.src).map(e -> _))
        moved = moves.size
        st.modify(_.edges)
          .using(_ ++ moves.map((e, ck) => e.modify(_.dst).setTo(ck)))
          .modify(_.parked)
          .using(_ -- moves.map(_._1))
      }
      moved

    // ── what acquisition, ingest and the collections read and write of a source ─────────────

    def exists(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.SourceService.InMemory.ExistsResult = state.get.sources.contains(citekey)

    def citekeyOfDoi(doi: com.histmersion.domain.library.Doi): Option[com.histmersion.domain.library.sources.SourceService.InMemory.CitekeyOfDoiResult] =
      val d = doi.trim.toLowerCase
      state.get.sources.toList.sortBy(_._1).collectFirst {
        case (ck, x) if x.source.doi.exists(_.toLowerCase == d) => ck
      }

    def doiIndex: Map[com.histmersion.domain.library.sources.SourceService.InMemory.DoiIndexResultPart1, com.histmersion.domain.library.sources.SourceService.InMemory.DoiIndexResultPart2] =
      state.get.sources.toList
        .sortBy(_._1)
        .flatMap((ck, x) => x.source.doi.filter(_.nonEmpty).map(_.toLowerCase -> ck))
        .toMap

    def addEdges(edges: List[Edge], caller: Caller): com.histmersion.domain.library.sources.SourceService.InMemory.AddEdgesResult =
      val es = edges.distinct
      if es.isEmpty then 0
      else
        var added = 0
        val _ = state.updateAndGet { st =>
          val inserted = edgesInserted(st, es)
          added = (inserted.edges -- st.edges).size + (inserted.parked -- st.parked).size
          inserted
        }
        added

    private def stored(citekey: com.histmersion.domain.library.Citekey): Option[LibraryState.Stored] = state.get.sources.get(citekey)

    def original(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourceService.InMemory.OriginalResult] = stored(citekey).flatMap(_.sha256)

    def holderOf(sha256: com.histmersion.Sha256): Option[com.histmersion.domain.library.sources.SourceService.InMemory.HolderOfResult] =
      state.get.sources.toList.sortBy(_._1).collectFirst { case (ck, x) if x.sha256.contains(sha256) => ck }

    def originals(citekeys: List[com.histmersion.domain.library.sources.SourceService.InMemory.OriginalsCitekeys]): Map[com.histmersion.domain.library.sources.SourceService.InMemory.OriginalsResultPart1, com.histmersion.domain.library.sources.SourceService.InMemory.OriginalsResultPart2] =
      citekeys.flatMap(ck => original(ck).map(ck -> _)).toMap

    private def updated(citekey: com.histmersion.domain.library.Citekey)(f: LibraryState.Stored => LibraryState.Stored): com.histmersion.domain.library.sources.SourceService.InMemory.UpdatedResult =
      val before = state.getAndUpdate(st =>
        st.modify(_.sources).using(ss => ss.get(citekey).fold(ss)(x => ss.updated(citekey, f(x))))
      )
      before.sources.contains(citekey)

    def setOriginal(citekey: com.histmersion.domain.library.Citekey, sha256: com.histmersion.Sha256, caller: Caller): Unit =
      val _ = updated(citekey)(_.modify(_.sha256).setTo(Some(sha256)))

    def licence(citekey: com.histmersion.domain.library.Citekey): Option[Licence] =
      stored(citekey).flatMap(_.licence).map(_.trim).filter(_.nonEmpty).map(Licence.classify)

    def recordLicence(citekey: com.histmersion.domain.library.Citekey, licence: Licence.Id, caller: Caller): com.histmersion.domain.library.sources.SourceService.InMemory.RecordLicenceResult =
      val v = licence.value.trim
      val open = stored(citekey).exists(_.licence.forall(_.trim.isEmpty))
      v.nonEmpty && open && updated(citekey)(
        _.modify(_.licence).setTo(Some(v)).modify(_.updatedAt).setTo(clock.instant())
      )

    private def gapOf(x: LibraryState.Stored): CalibreGap =
      val s = x.source
      CalibreGap(
        s.citekey,
        s.title,
        s.authors,
        s.doi,
        s.year,
        s.venue,
        s.summary,
        s.topics,
        s.provenance,
        s.calibreId.filter(_.nonEmpty)
      )

    def calibreGaps(limit: Option[com.histmersion.domain.library.sources.SourceService.InMemory.CalibreGapsLimit]): List[CalibreGap] =
      state.get.sources.toList
        .sortBy(_._1)
        .map(_._2)
        .filter(x => x.source.calibreId.forall(_.isEmpty) && !x.source.provenance.contains("calibre"))
        .take(limit.filter(_ > 0).getOrElse(Int.MaxValue))
        .map(gapOf)

    def calibrePushMeta(citekey: com.histmersion.domain.library.Citekey): Option[CalibreGap] = stored(citekey).map(gapOf)

    def setCalibreId(citekey: com.histmersion.domain.library.Citekey, calibreId: com.histmersion.domain.library.sources.SourceService.InMemory.SetCalibreIdCalibreId, caller: Caller): Either[Api.Error, Unit] =
      if updated(citekey)(
          _.modify(_.source.calibreId).setTo(Some(calibreId)).modify(_.updatedAt).setTo(clock.instant())
        )
      then Right(())
      else Left(Write.notFound(citekey))

  object InMemory:
    type PatchAnswerPart2 = PatchAnswerPart2.T
    object PatchAnswerPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedNowNewIdResult = RenamedNowNewIdResult.T
    object RenamedNowNewIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedNowNewIdId = RenamedNowNewIdId.T
    object RenamedNowNewIdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedNowTargetArg1 = RenamedNowTargetArg1.T
    object RenamedNowTargetArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedNowTargetResult = RenamedNowTargetResult.T
    object RenamedNowTargetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type TokenResult = TokenResult.T
    object TokenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CurrentInResult = CurrentInResult.T
    object CurrentInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CurrentInId = CurrentInId.T
    object CurrentInId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CurrentResult = CurrentResult.T
    object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CurrentId = CurrentId.T
    object CurrentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AliasOfResult = AliasOfResult.T
    object AliasOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AliasesOfResult = AliasesOfResult.T
    object AliasesOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitekeysResult = CitekeysResult.T
    object CitekeysResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAtMillisResultPart1 = UpdatedAtMillisResultPart1.T
    object UpdatedAtMillisResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAtMillisResultPart2 = UpdatedAtMillisResultPart2.T
    object UpdatedAtMillisResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CalibreIdResult = CalibreIdResult.T
    object CalibreIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefsCitekeys = RefsCitekeys.T
    object RefsCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveInResultPart1 = ResolveInResultPart1.T
    object ResolveInResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveInResultPart2 = ResolveInResultPart2.T
    object ResolveInResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveInTargets = ResolveInTargets.T
    object ResolveInTargets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveLinksResultPart1 = ResolveLinksResultPart1.T
    object ResolveLinksResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveLinksResultPart2 = ResolveLinksResultPart2.T
    object ResolveLinksResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveLinksTargets = ResolveLinksTargets.T
    object ResolveLinksTargets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BodyTextResult = BodyTextResult.T
    object BodyTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BodyUnderHeadingResult = BodyUnderHeadingResult.T
    object BodyUnderHeadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BodyUnderHeadingHeading = BodyUnderHeadingHeading.T
    object BodyUnderHeadingHeading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkdownResult = MarkdownResult.T
    object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutResult = PutResult.T
    object PutResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchResultPart2 = PatchResultPart2.T
    object PatchResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchRequested = PatchRequested.T
    object PatchRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameFrom = RenameFrom.T
    object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameTo = RenameTo.T
    object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedInFrom = RenamedInFrom.T
    object RenamedInFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedInTo = RenamedInTo.T
    object RenamedInTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedNowFrom = RenamedNowFrom.T
    object RenamedNowFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedNowTo = RenamedNowTo.T
    object RenamedNowTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoiKeyResult = DoiKeyResult.T
    object DoiKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanInRequested = PlanInRequested.T
    object PlanInRequested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteDay = DeleteDay.T
    object DeleteDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteFilesResult = DeleteFilesResult.T
    object DeleteFilesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveEdgesResult = ResolveEdgesResult.T
    object ResolveEdgesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExistsResult = ExistsResult.T
    object ExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitekeyOfDoiResult = CitekeyOfDoiResult.T
    object CitekeyOfDoiResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoiIndexResultPart1 = DoiIndexResultPart1.T
    object DoiIndexResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoiIndexResultPart2 = DoiIndexResultPart2.T
    object DoiIndexResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AddEdgesResult = AddEdgesResult.T
    object AddEdgesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalResult = OriginalResult.T
    object OriginalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HolderOfResult = HolderOfResult.T
    object HolderOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalsResultPart1 = OriginalsResultPart1.T
    object OriginalsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalsResultPart2 = OriginalsResultPart2.T
    object OriginalsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OriginalsCitekeys = OriginalsCitekeys.T
    object OriginalsCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedResult = UpdatedResult.T
    object UpdatedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordLicenceResult = RecordLicenceResult.T
    object RecordLicenceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CalibreGapsLimit = CalibreGapsLimit.T
    object CalibreGapsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetCalibreIdCalibreId = SetCalibreIdCalibreId.T
    object SetCalibreIdCalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
