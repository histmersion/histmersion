package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** The Claude plan pool: [[PlanPoolConfig]]'s two, and how it takes a fresh reading when a run wants the pool and its
  * last one is too old to decide on (docs/research-plan-quotas.md §8). `refresh` is `auto` (a zero-cost read if the CLI
  * has one — 2.1.289 has none — else the probe), `probe`, or `off` (wait for a run's own reading, as before); the probe
  * is one `claude -p` turn on `probeModel` with at most `probeMaxTokens` output tokens, given up after
  * `probeTimeoutSeconds`. `refreshEveryMinutes` also reads it on a schedule while the autopilot has queued work for it.
  */
@movesTo(FeaturePlace.AutomationAgents)
final case class ClaudePlanConfig(
    floors: ClaudePlanConfig.Floors,
    maxCallsInFlight: ClaudePlanConfig.MaxCallsInFlight,
    refresh: ClaudePlanConfig.Refresh,
    probeModel: ClaudePlanConfig.ProbeModel,
    probeMaxTokens: ClaudePlanConfig.ProbeMaxTokens,
    probeTimeoutSeconds: ClaudePlanConfig.ProbeTimeoutSeconds,
    refreshEveryMinutes: ScheduledRefresh = ScheduledRefresh.Off
) derives ConfigReader

object ClaudePlanConfig:
  type Floors = Floors.T
  object Floors extends Newtype[String, Pure]
  type MaxCallsInFlight = MaxCallsInFlight.T
  object MaxCallsInFlight extends Newtype[Int, Pure]
  type Refresh = Refresh.T
  object Refresh extends Newtype[String, Pure]
  type ProbeModel = ProbeModel.T
  object ProbeModel extends Newtype[String, Pure]
  type ProbeMaxTokens = ProbeMaxTokens.T
  object ProbeMaxTokens extends Newtype[Int, Pure]
  type ProbeTimeoutSeconds = ProbeTimeoutSeconds.T
  object ProbeTimeoutSeconds extends Newtype[Int, Pure]
