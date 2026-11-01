package com.histmersion.domain.automation.managers

import com.histmersion.api.Managers.ManagerConfig

import java.time.Instant

/** A manager as [[ManagerService.InMemory]] holds it. */
final case class ManagerRecord(
    id: String,
    kind: String,
    goal: Option[String],
    state: String,
    pauseRuns: Boolean,
    config: ManagerConfig,
    statusNote: Option[String],
    cron: Option[String],
    nextTickAt: Option[Instant],
    createdAt: Instant,
    updatedAt: Instant
)
