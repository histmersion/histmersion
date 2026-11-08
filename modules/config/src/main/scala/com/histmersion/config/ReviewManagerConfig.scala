package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  type StaleScanMinutes = StaleScanMinutes.T
  object StaleScanMinutes extends RefinedType[Int, Pure]
  type ExtendMinNew = ExtendMinNew.T
  object ExtendMinNew extends RefinedType[Int, Pure]
  type ExtendCooldownDays = ExtendCooldownDays.T
  object ExtendCooldownDays extends RefinedType[Int, Pure]
