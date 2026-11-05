package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The Claude plan pool: [[PlanPoolConfig]]'s two, and how it takes a fresh reading when a run wants the pool and its
  * last one is too old to decide on (docs/research-plan-quotas.md §8). `refresh` is `auto` (a zero-cost read if the CLI
  * has one — 2.1.289 has none — else the probe), `probe`, or `off` (wait for a run's own reading, as before); the probe
  * is one `claude -p` turn on `probeModel` with at most `probeMaxTokens` output tokens, given up after
  * `probeTimeoutSeconds`. `refreshEveryMinutes` also reads it on a schedule while the autopilot has queued work for it.
  */
final case class ClaudePlanConfig(
    floors: String,
    maxCallsInFlight: Int,
    refresh: String,
    probeModel: String,
    probeMaxTokens: Int,
    probeTimeoutSeconds: Int,
    refreshEveryMinutes: ScheduledRefresh = ScheduledRefresh.Off
) derives ConfigReader
