package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** The Claude plan pool: [[PlanPoolConfig]]'s two, and how it takes a fresh reading when a run wants the pool and its
  * last one is too old to decide on (docs/research-plan-quotas.md §8). `refresh` is `auto` (a zero-cost read if the CLI
  * has one — 2.1.289 has none — else the probe), `probe`, or `off` (wait for a run's own reading, as before); the probe
  * is one `claude -p` turn on `probeModel` with at most `probeMaxTokens` output tokens, given up after
  * `probeTimeoutSeconds`. `refreshEveryMinutes` also reads it on a schedule while the autopilot has queued work for it.
  */
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
  object Floors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxCallsInFlight = MaxCallsInFlight.T
  object MaxCallsInFlight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refresh = Refresh.T
  object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProbeModel = ProbeModel.T
  object ProbeModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProbeMaxTokens = ProbeMaxTokens.T
  object ProbeMaxTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProbeTimeoutSeconds = ProbeTimeoutSeconds.T
  object ProbeTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
