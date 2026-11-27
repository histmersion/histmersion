package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** Subscription pools (docs/research-plan-quotas.md §5): a snapshot older than `staleMinutes` is unknown; a window with
  * no floor of its own has `defaultFloor`; a stale reading lets a run go on only with `margin` points to spare;
  * `fallthrough` names the roles whose chain may fall from a plan below its floor to a paid key (the others pause).
  */
@movesTo(FeaturePlace.AutomationAgents)
final case class QuotaConfig(
    staleMinutes: QuotaConfig.StaleMinutes,
    defaultFloor: QuotaConfig.DefaultFloor,
    margin: QuotaConfig.Margin,
    pollSeconds: QuotaConfig.PollSeconds,
    claudePlan: ClaudePlanConfig,
    chatgptPlan: PlanPoolConfig,
    fallthrough: QuotaConfig.Fallthrough
) derives ConfigReader

object QuotaConfig:
  type StaleMinutes = StaleMinutes.T
  object StaleMinutes extends Newtype[Int, Pure]
  type DefaultFloor = DefaultFloor.T
  object DefaultFloor extends Newtype[Int, Pure]
  type Margin = Margin.T
  object Margin extends Newtype[Int, Pure]
  type PollSeconds = PollSeconds.T
  object PollSeconds extends Newtype[Int, Pure]
  type Fallthrough = Fallthrough.T
  object Fallthrough extends Newtype[String, Pure]
