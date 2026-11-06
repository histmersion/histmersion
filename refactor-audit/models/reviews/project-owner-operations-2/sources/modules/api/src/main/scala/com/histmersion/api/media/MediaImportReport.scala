package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaImportReport(
    provider: com.histmersion.api.media.MediaItem.Provider,
    dryRun: com.histmersion.api.media.MediaImportRequest.DryRun,
    targets: MediaImportReport.Targets = _root_.com.histmersion.api.media.MediaImportReport.Targets(0),
    items: MediaImportReport.Items = _root_.com.histmersion.api.media.MediaImportReport.Items(0),
    statements: MediaImportReport.Statements = _root_.com.histmersion.api.media.MediaImportReport.Statements(0),
    files: MediaImportReport.Files = _root_.com.histmersion.api.media.MediaImportReport.Files(0),
    byLicence: List[MediaCount] = Nil,
    byRole: List[MediaCount] = Nil,
    rejected: List[MediaCount] = Nil,
    stored: MediaImportReport.Stored = _root_.com.histmersion.api.media.MediaImportReport.Stored(0),
    updated: MediaImportReport.Updated = _root_.com.histmersion.api.media.MediaImportReport.Updated(0),
    linked: MediaImportReport.Linked = _root_.com.histmersion.api.media.MediaImportReport.Linked(0),
    categories: MediaImportReport.Categories = _root_.com.histmersion.api.media.MediaImportReport.Categories(0),
    revoked: List[MediaImportReport.Revoked] = Nil,
    failed: List[MediaImportReport.Failed] = Nil,
    pending: MediaImportReport.Pending = _root_.com.histmersion.api.media.MediaImportReport.Pending(0),
    batches: MediaImportReport.Batches = _root_.com.histmersion.api.media.MediaImportReport.Batches(0),
    samples: List[MediaSample] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaImportReport:


  type Targets = Targets.T
  object Targets extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Items = Items.T
  object Items extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Statements = Statements.T
  object Statements extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Files = Files.T
  object Files extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Stored = Stored.T
  object Stored extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Updated = Updated.T
  object Updated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Linked = Linked.T
  object Linked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Categories = Categories.T
  object Categories extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Revoked = Revoked.T
  object Revoked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Failed = Failed.T
  object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pending = Pending.T
  object Pending extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Batches = Batches.T
  object Batches extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def combine(a: MediaImportReport, b: MediaImportReport): MediaImportReport =
    def add(x: List[MediaCount], y: List[MediaCount]) =
      (x ++ y)
        .groupBy(_.key)
        .toList
        .map((k, cs) => MediaCount(k, cs.map(_.n).sum, cs.forall(_.allowed)))
        .sortBy(c => (-c.n, c.key))
    MediaImportReport(
      b.provider,
      b.dryRun,
      (a.targets).value + b.targets,
      (a.items).value + b.items,
      (a.statements).value + b.statements,
      (a.files).value + b.files,
      add(a.byLicence, b.byLicence),
      add(a.byRole, b.byRole),
      add(a.rejected, b.rejected),
      (a.stored).value + b.stored,
      (a.updated).value + b.updated,
      (a.linked).value + b.linked,
      (a.categories).value + b.categories,
      a.revoked ++ b.revoked,
      a.failed ++ b.failed,
      b.pending,
      (a.batches).value + b.batches,
      (a.samples ++ b.samples).take(SampleCap)
    )

  val SampleCap = 50
