package com.histmersion
package api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.RepairAcquire
import io.github.iltotore.iron.constraint.any.Pure

final case class MediaImportReport(
    provider: MediaItem.Provider,
    dryRun: RepairAcquire.RepairRequest.DryRun,
    targets: MediaImportReport.Targets = MediaImportReport.Targets(0),
    items: MediaImportReport.Items = MediaImportReport.Items(0),
    statements: MediaImportReport.Statements = MediaImportReport.Statements(0),
    files: MediaImportReport.Files = MediaImportReport.Files(0),
    byLicence: List[MediaCount] = Nil,
    byRole: List[MediaCount] = Nil,
    rejected: List[MediaCount] = Nil,
    stored: MediaImportReport.Stored = MediaImportReport.Stored(0),
    updated: MediaImportReport.Updated = MediaImportReport.Updated(0),
    linked: MediaImportReport.Linked = MediaImportReport.Linked(0),
    categories: MediaImportReport.Categories = MediaImportReport.Categories(0),
    revoked: List[MediaImportReport.Revoked] = Nil,
    failed: List[MediaImportReport.Failed] = Nil,
    pending: MediaImportReport.Pending = MediaImportReport.Pending(0),
    batches: MediaImportReport.Batches = MediaImportReport.Batches(0),
    samples: List[MediaSample] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaImportReport:


  type Targets = Targets.T
  object Targets extends Newtype[Int, Pure]
  type Items = Items.T
  object Items extends Newtype[Int, Pure]
  type Statements = Statements.T
  object Statements extends Newtype[Int, Pure]
  type Files = Files.T
  object Files extends Newtype[Int, Pure]
  type Stored = Stored.T
  object Stored extends Newtype[Int, Pure]
  type Updated = Updated.T
  object Updated extends Newtype[Int, Pure]
  type Linked = Linked.T
  object Linked extends Newtype[Int, Pure]
  type Categories = Categories.T
  object Categories extends Newtype[Int, Pure]
  type Revoked = Revoked.T
  object Revoked extends Newtype[String, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[String, Pure]
  type Pending = Pending.T
  object Pending extends Newtype[Int, Pure]
  type Batches = Batches.T
  object Batches extends Newtype[Int, Pure]

  def combine(a: MediaImportReport, b: MediaImportReport): MediaImportReport =
    def add(x: List[MediaCount], y: List[MediaCount]) =
      (x ++ y)
        .groupBy(_.key)
        .toList
        .map((k, cs) => MediaCount(k, MediaCount.N(cs.map(_.n.value).sum), MediaCount.Allowed(cs.forall((refactorCallback => ((refactorCallback.allowed) == MediaCount.Allowed(true)))))))
        .sortBy[(Int, String)](c => (-c.n.value, c.key.value))
    MediaImportReport(
      b.provider,
      b.dryRun,
      Targets((a.targets).value + (b.targets).value),
      Items((a.items).value + (b.items).value),
      Statements((a.statements).value + (b.statements).value),
      Files((a.files).value + (b.files).value),
      add(a.byLicence, b.byLicence),
      add(a.byRole, b.byRole),
      add(a.rejected, b.rejected),
      Stored((a.stored).value + (b.stored).value),
      Updated((a.updated).value + (b.updated).value),
      Linked((a.linked).value + (b.linked).value),
      Categories((a.categories).value + (b.categories).value),
      a.revoked ++ b.revoked,
      a.failed ++ b.failed,
      b.pending,
      Batches((a.batches).value + (b.batches).value),
      (a.samples ++ b.samples).take(SampleCap)
    )

  val SampleCap = 50
