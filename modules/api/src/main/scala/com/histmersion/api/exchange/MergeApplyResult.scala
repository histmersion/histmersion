package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.problem.Problem
import com.histmersion.lint.{tolerated, LintRule}

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
    problems: List[Problem] = Nil,
    issues: List[MergeIssue] = Nil,
    notes: List[MergeApplyResult.Notes] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def changed: Bundle.ChangedCount = Bundle.ChangedCount(counts.map(c => c.changed.value: Int).sum)

object MergeApplyResult:

  type Applied = Applied.T
  object Applied extends Newtype[Boolean, Pure]
  export MergePlan.PlanHash
  type Backup = Backup.T
  object Backup extends Newtype[String, Pure]
  type Reviews = Reviews.T
  object Reviews extends Newtype[String, Pure]
  type LogEntry = LogEntry.T
  object LogEntry extends Newtype[Int, Pure]
  type ReplanChanges = ReplanChanges.T
  object ReplanChanges extends Newtype[Int, Pure]
  type Notes = Notes.T
  object Notes extends Newtype[String, Pure]
