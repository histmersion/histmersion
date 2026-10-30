package com.histmersion.domain

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
