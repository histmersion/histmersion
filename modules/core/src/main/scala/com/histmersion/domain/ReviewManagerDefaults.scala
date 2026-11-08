package com.histmersion
package domain

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object MaxRuns extends RefinedType[Int, Pure]
  type Rounds = Rounds.T
  object Rounds extends RefinedType[Int, Pure]
  type MaxRunsPerArticle = MaxRunsPerArticle.T
  object MaxRunsPerArticle extends RefinedType[Int, Pure]
  type NoProgressRuns = NoProgressRuns.T
  object NoProgressRuns extends RefinedType[Int, Pure]
  type ReopenLimit = ReopenLimit.T
  object ReopenLimit extends RefinedType[Int, Pure]
  type CooldownMinutes = CooldownMinutes.T
  object CooldownMinutes extends RefinedType[Int, Pure]
  type LowWater = LowWater.T
  object LowWater extends RefinedType[Int, Pure]
  type RefillBatch = RefillBatch.T
  object RefillBatch extends RefinedType[Int, Pure]
  type StaleAfterDays = StaleAfterDays.T
  object StaleAfterDays extends RefinedType[Int, Pure]
  type InferFilter = InferFilter.T
  object InferFilter extends RefinedType[Boolean, Pure]
  type ExtendMinNew = ExtendMinNew.T
  object ExtendMinNew extends RefinedType[Int, Pure]
