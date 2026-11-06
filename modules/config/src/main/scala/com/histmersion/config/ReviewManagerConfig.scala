package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The review manager's defaults (a value saved in the manager's config wins). `staleAfterDays`: a converged article is
  * selected again (lowest priority) after this many days, or when its filter gained sources (0 = never); looked for at
  * most every `staleScanMinutes`. `inferFilter`: an article without a coverage filter gets an infer-filter item before
  * its review. `extendMinNew`: an article whose filter returns at least this many sources it neither cites nor gives a
  * reason for gets an `article-extend` item (0 = never), at most every `extendCooldownDays` per article.
  */
final case class ReviewManagerConfig(
    maxRuns: ReviewManagerConfig.MaxRuns,
    rounds: ReviewManagerConfig.Rounds,
    maxRunsPerArticle: ReviewManagerConfig.MaxRunsPerArticle,
    noProgressRuns: ReviewManagerConfig.NoProgressRuns,
    reopenLimit: ReviewManagerConfig.ReopenLimit,
    cooldownMinutes: ReviewManagerConfig.CooldownMinutes,
    lowWater: ReviewManagerConfig.LowWater,
    refillBatch: ReviewManagerConfig.RefillBatch,
    staleAfterDays: ReviewManagerConfig.StaleAfterDays,
    inferFilter: ReviewManagerConfig.InferFilter = ReviewManagerConfig.InferFilter(true),
    staleScanMinutes: ReviewManagerConfig.StaleScanMinutes = ReviewManagerConfig.StaleScanMinutes(60),
    extendMinNew: ReviewManagerConfig.ExtendMinNew = ReviewManagerConfig.ExtendMinNew(0),
    extendCooldownDays: ReviewManagerConfig.ExtendCooldownDays = ReviewManagerConfig.ExtendCooldownDays(14)
) derives ConfigReader

object ReviewManagerConfig:
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
  type StaleScanMinutes = StaleScanMinutes.T
  object StaleScanMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendMinNew = ExtendMinNew.T
  object ExtendMinNew extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtendCooldownDays = ExtendCooldownDays.T
  object ExtendCooldownDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
