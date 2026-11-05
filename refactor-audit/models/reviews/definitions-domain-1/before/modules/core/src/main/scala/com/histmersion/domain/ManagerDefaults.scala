package com.histmersion.domain

final case class ManagerDefaults(
    maxFailures: Int,
    backoffMinutes: Int,
    maxBackoffMinutes: Int,
    dailyUsd: Double,
    review: ManagerRunDefaults,
    enrich: ManagerRunDefaults,
    seed: ManagerRunDefaults,
    watch: ManagerRunDefaults
)
