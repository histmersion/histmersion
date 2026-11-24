package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.ResearchWorkflows)
final case class ReviewManagerDefaults(
    maxRuns: ReviewManagerDefaults.MaxRuns,
    rounds: ReviewManagerDefaults.Rounds,
    maxRunsPerArticle: ReviewManagerDefaults.MaxRunsPerArticle,
    noProgressRuns: ReviewManagerDefaults.NoProgressRuns,
    reopenLimit: ReviewManagerDefaults.ReopenLimit,
    cooldownMinutes: ReviewManagerDefaults.CooldownMinutes,
    lowWater: ReviewManagerDefaults.LowWater,
    refillBatch: ReviewManagerDefaults.RefillBatch,
    staleAfterDays: ReviewManagerDefaults.StaleAfterDays,
    inferFilter: ReviewManagerDefaults.InferFilter,
    extendMinNew: ReviewManagerDefaults.ExtendMinNew
)

object ReviewManagerDefaults:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends Newtype[Int, Pure]
  type Rounds = Rounds.T
  object Rounds extends Newtype[Int, Pure]
  type MaxRunsPerArticle = MaxRunsPerArticle.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object MaxRunsPerArticle extends Newtype[Int, Pure]
  type NoProgressRuns = NoProgressRuns.T
  object NoProgressRuns extends Newtype[Int, Pure]
  type ReopenLimit = ReopenLimit.T
  object ReopenLimit extends Newtype[Int, Pure]
  type CooldownMinutes = CooldownMinutes.T
  object CooldownMinutes extends Newtype[Int, Pure]
  type LowWater = LowWater.T
  object LowWater extends Newtype[Int, Pure]
  type RefillBatch = RefillBatch.T
  object RefillBatch extends Newtype[Int, Pure]
  type StaleAfterDays = StaleAfterDays.T
  object StaleAfterDays extends Newtype[Int, Pure]
  type InferFilter = InferFilter.T
  object InferFilter extends Newtype[Boolean, Pure]
  type ExtendMinNew = ExtendMinNew.T
  object ExtendMinNew extends Newtype[Int, Pure]
