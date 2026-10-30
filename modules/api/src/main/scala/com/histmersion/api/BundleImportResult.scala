package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

/** `POST /api/bundle/import`. `problems` are blocking — when there are any nothing was written; among them every open
  * issue of the merge plan (`issues` has them whole, to render as `merge plan` does) and every decision only
  * `merge apply` can carry out. `conflicts` are rows changed on BOTH sides since the last import that a resolution
  * decided; `notes` are informational.
  */
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
    /** The plan's issues that hold this import up (open, or decided in a way only `merge apply` can carry out).
      */
    issues: List[MergeIssue] = Nil,
    planHash: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def changed: Int = counts.map(_.changed).sum
object BundleImportResult:
  given Schema[BundleImportResult] = summon[KindlingsSchema[BundleImportResult]].schema
