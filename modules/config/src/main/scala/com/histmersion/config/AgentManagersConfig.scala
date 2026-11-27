package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** The process managers' supervisor (docs/research-process-managers.md §3). */
@movesTo(FeaturePlace.AutomationManagers)
final case class AgentManagersConfig(
    enabled: AgentManagersConfig.Enabled,
    tickSeconds: AgentManagersConfig.TickSeconds,
    keepEventsDays: AgentManagersConfig.KeepEventsDays,
    backoffMinutes: AgentManagersConfig.BackoffMinutes,
    maxBackoffMinutes: AgentManagersConfig.MaxBackoffMinutes,
    maxFailures: AgentManagersConfig.MaxFailures,
    /** The review manager's run limit; research reads the rest of `agents.managers.review`. */
    review: SimpleManagerConfig,
    enrich: SimpleManagerConfig,
    seed: SimpleManagerConfig,
    /** A watcher's run limit; the library reads the rest of `agents.managers.watch`. */
    watch: SimpleManagerConfig,
    dailyUsd: AgentManagersConfig.DailyUsd = AgentManagersConfig.DailyUsd(0)
) derives ConfigReader

object AgentManagersConfig:
  type Enabled = Enabled.T
  object Enabled extends Newtype[Boolean, Pure]
  type TickSeconds = TickSeconds.T
  object TickSeconds extends Newtype[Int, Pure]
  type KeepEventsDays = KeepEventsDays.T
  object KeepEventsDays extends Newtype[Int, Pure]
  type BackoffMinutes = BackoffMinutes.T
  object BackoffMinutes extends Newtype[Int, Pure]
  type MaxBackoffMinutes = MaxBackoffMinutes.T
  object MaxBackoffMinutes extends Newtype[Int, Pure]
  type MaxFailures = MaxFailures.T
  object MaxFailures extends Newtype[Int, Pure]
  type DailyUsd = DailyUsd.T
  object DailyUsd extends Newtype[Double, Pure]
