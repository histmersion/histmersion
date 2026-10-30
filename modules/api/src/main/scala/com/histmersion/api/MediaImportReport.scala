package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class MediaImportReport(
    provider: String,
    dryRun: Boolean,
    targets: Int = 0,
    items: Int = 0,
    statements: Int = 0,
    files: Int = 0,
    byLicence: List[MediaCount] = Nil,
    byRole: List[MediaCount] = Nil,
    rejected: List[MediaCount] = Nil,
    stored: Int = 0,
    updated: Int = 0,
    linked: Int = 0,
    categories: Int = 0,
    revoked: List[String] = Nil,
    failed: List[String] = Nil,
    pending: Int = 0,
    batches: Int = 0,
    samples: List[MediaSample] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaImportReport:
  given Schema[MediaImportReport] = summon[KindlingsSchema[MediaImportReport]].schema

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
      a.targets + b.targets,
      a.items + b.items,
      a.statements + b.statements,
      a.files + b.files,
      add(a.byLicence, b.byLicence),
      add(a.byRole, b.byRole),
      add(a.rejected, b.rejected),
      a.stored + b.stored,
      a.updated + b.updated,
      a.linked + b.linked,
      a.categories + b.categories,
      a.revoked ++ b.revoked,
      a.failed ++ b.failed,
      b.pending,
      a.batches + b.batches,
      (a.samples ++ b.samples).take(SampleCap)
    )

  val SampleCap = 50
