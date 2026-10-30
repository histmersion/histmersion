package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

/** `POST /api/merge/apply` (§6.4). `problems` are blocking — when there are any nothing was written (and no backup
  * taken); among them every open issue and the stale plan hash.
  */
final case class MergeApplyResult(
    bundleId: UUID,
    from: BundleInstance,
    applied: Boolean,
    planHash: String,
    mergeId: Option[UUID] = None,
    /** The backup taken before anything was written (its file name in `backups.dir`). */
    backup: Option[String] = None,
    counts: List[BundleKindCounts] = Nil,
    tally: MergeTally = MergeTally(),
    /** The articles queued for `/review-article` (merged or changed by a decision). */
    reviews: List[String] = Nil,
    /** The `log` queue entry recording the merge. */
    logEntry: Option[Int] = None,
    /** What planning the same bundle again (with the same resolutions) would change — 0 after a successful apply: the
      * merge is idempotent.
      */
    replanChanges: Option[Int] = None,
    problems: List[String] = Nil,
    issues: List[MergeIssue] = Nil,
    notes: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def changed: Int = counts.map(_.changed).sum
object MergeApplyResult:
  given Schema[MergeApplyResult] = summon[KindlingsSchema[MergeApplyResult]].schema
