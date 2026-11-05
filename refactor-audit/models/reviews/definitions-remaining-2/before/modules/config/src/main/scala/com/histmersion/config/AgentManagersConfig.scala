package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The process managers' supervisor (docs/research-process-managers.md §3). */
final case class AgentManagersConfig(
    enabled: Boolean,
    tickSeconds: Int,
    keepEventsDays: Int,
    backoffMinutes: Int,
    maxBackoffMinutes: Int,
    maxFailures: Int,
    /** The review manager's run limit; research reads the rest of `agents.managers.review`. */
    review: SimpleManagerConfig,
    enrich: SimpleManagerConfig,
    seed: SimpleManagerConfig,
    /** A watcher's run limit; the library reads the rest of `agents.managers.watch`. */
    watch: SimpleManagerConfig,
    dailyUsd: Double = 0
) derives ConfigReader
