package com.histmersion.domain

final case class ManagerDefaults(
    maxFailures: Int,
    backoffMinutes: Int,
    maxBackoffMinutes: Int,
    dailyUsd: Double,
    review: ReviewManagerDefaults,
    enrich: ManagerRunDefaults,
    seed: ManagerRunDefaults,
    watch: WatchManagerDefaults
)

final case class ReviewManagerDefaults(
    maxRuns: Int,
    rounds: Int,
    maxRunsPerArticle: Int,
    noProgressRuns: Int,
    reopenLimit: Int,
    cooldownMinutes: Int,
    lowWater: Int,
    refillBatch: Int,
    staleAfterDays: Int,
    inferFilter: Boolean,
    extendMinNew: Int
)

final case class ManagerRunDefaults(maxRuns: Int)

final case class WatchManagerDefaults(maxRuns: Int, autoIngestPerDay: Int)

final case class SeedPlanDefaults(maxArticles: Int, minSources: Int)

final case class SchedulerDefaults(
    strategy: String,
    agingHours: Int,
    maxWaitHours: Int,
    minShare: Double,
    builtInWeights: String,
    typeWeights: String,
    builtInPriorities: String,
    typePriorities: String,
    gatheringSlots: Int
)
