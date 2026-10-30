package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

final case class BundleImportResult(
    bundleId: UUID,
    from: BundleInstance,
    dryRun: Boolean,
    applied: Boolean,
    counts: List[BundleKindCounts],
    blobs: Int,
    problems: List[String],
    conflicts: List[String],
    notes: List[String],
    mergeId: Option[UUID] = None,
    issues: List[MergeIssue] = Nil,
    planHash: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def changed: Int = counts.map(_.changed).sum
object BundleImportResult:
  given Schema[BundleImportResult] = summon[KindlingsSchema[BundleImportResult]].schema
