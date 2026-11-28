package com.histmersion
package api.automation.approvaldigest

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class BulkResult(
    decision: BulkResult.Decision,
    dryRun: BulkResult.DryRun = BulkResult.DryRun.Apply,
    matched: BulkResult.Matched = BulkResult.Matched(0),
    decided: List[BulkResult.Decided] = Nil,
    failed: List[BulkFailed] = Nil,
    left: BulkResult.Left = BulkResult.Left(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object BulkResult:
  type Decision = Decision.T
  object Decision extends Newtype[String, Pure]
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
  type Matched = Matched.T
  object Matched extends Newtype[Int, Pure]
  type Decided = Decided.T
  object Decided extends Newtype[Long, Pure]
  type Left = Left.T
  object Left extends Newtype[Int, Pure]
