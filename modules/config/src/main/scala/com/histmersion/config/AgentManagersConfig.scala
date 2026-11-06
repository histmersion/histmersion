package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The process managers' supervisor (docs/research-process-managers.md §3). */
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
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TickSeconds = TickSeconds.T
  object TickSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeepEventsDays = KeepEventsDays.T
  object KeepEventsDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffMinutes = BackoffMinutes.T
  object BackoffMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxBackoffMinutes = MaxBackoffMinutes.T
  object MaxBackoffMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxFailures = MaxFailures.T
  object MaxFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DailyUsd = DailyUsd.T
  object DailyUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
