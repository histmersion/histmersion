package com.histmersion.domain

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
  object MaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rounds = Rounds.T
  object Rounds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxRunsPerArticle = MaxRunsPerArticle.T
  object MaxRunsPerArticle extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoProgressRuns = NoProgressRuns.T
  object NoProgressRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReopenLimit = ReopenLimit.T
  object ReopenLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CooldownMinutes = CooldownMinutes.T
  object CooldownMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LowWater = LowWater.T
  object LowWater extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefillBatch = RefillBatch.T
  object RefillBatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleAfterDays = StaleAfterDays.T
  object StaleAfterDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InferFilter = InferFilter.T
  object InferFilter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendMinNew = ExtendMinNew.T
  object ExtendMinNew extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
