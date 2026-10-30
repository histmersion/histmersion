package com.histmersion.domain

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
