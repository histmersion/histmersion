package com.histmersion
package domain.library.sources.referrers

import com.histmersion.api.library.{DeleteBlocker, DeleteCount}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** What a delete would touch of a referrer's records: what refuses it, how many go, the records whose reasons name it
  * (`coverage`) and the items that mention it and stay (`mentions`).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class DeleteReport(
    blockers: List[DeleteBlocker] = Nil,
    counts: List[DeleteCount] = Nil,
    coverage: List[DeleteReport.Coverage] = Nil,
    mentions: List[DeleteReport.Mentions] = Nil
)

object DeleteReport:
  type Coverage = Coverage.T
  object Coverage extends Newtype[String, Pure]
  type Mentions = Mentions.T
  object Mentions extends Newtype[Int, Pure]
