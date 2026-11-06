package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class MergeApplyResult(
    bundleId: UUID,
    from: Bundle.Instance,
    applied: MergeApplyResult.Applied,
    planHash: MergeApplyResult.PlanHash,
    mergeId: Option[UUID] = None,
    backup: Option[MergeApplyResult.Backup] = None,
    counts: List[Bundle.KindCounts] = Nil,
    tally: MergeTally = MergeTally(),
    reviews: List[MergeApplyResult.Reviews] = Nil,
    logEntry: Option[MergeApplyResult.LogEntry] = None,
    replanChanges: Option[MergeApplyResult.ReplanChanges] = None,
    problems: List[MergeApplyResult.Problems] = Nil,
    issues: List[MergeIssue] = Nil,
    notes: List[MergeApplyResult.Notes] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def changed: com.histmersion.api.exchange.Bundle.ChangedCount = _root_.com.histmersion.api.exchange.Bundle.ChangedCount(counts.map(c => c.changed.value: Int).sum)

object MergeApplyResult:

  type Applied = Applied.T
  object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanHash = com.histmersion.api.exchange.MergePlan.PlanHash
  val PlanHash: _root_.com.histmersion.api.exchange.MergePlan.PlanHash.type = _root_.com.histmersion.api.exchange.MergePlan.PlanHash
  type Backup = Backup.T
  object Backup extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reviews = Reviews.T
  object Reviews extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogEntry = LogEntry.T
  object LogEntry extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplanChanges = ReplanChanges.T
  object ReplanChanges extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problems = Problems.T
  object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Notes = Notes.T
  object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
