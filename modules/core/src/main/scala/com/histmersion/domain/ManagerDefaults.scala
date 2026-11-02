package com.histmersion.domain

final case class ManagerDefaults(
    maxFailures: Int,
    backoffMinutes: Int,
    maxBackoffMinutes: Int,
    dailyUsd: Double,
    review: ReviewManagerDefaults,
    enrich: ManagerRunDefaults,
    seed: ManagerRunDefaults,
    watch: ManagerRunDefaults
)
