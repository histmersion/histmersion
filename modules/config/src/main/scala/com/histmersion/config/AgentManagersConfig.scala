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
    review: ReviewManagerConfig,
    enrich: SimpleManagerConfig,
    seed: SimpleManagerConfig,
    watch: WatchManagerConfig,
    dailyUsd: Double = 0
) derives ConfigReader
