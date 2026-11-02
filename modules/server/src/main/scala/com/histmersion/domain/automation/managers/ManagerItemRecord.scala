package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Managers.{Hold, ItemState}

import java.time.Instant

/** A manager's work item as [[ManagerService.InMemory]] holds it. */
final case class ManagerItemRecord(
    id: Long,
    managerId: String,
    kind: String,
    target: String,
    status: String,
    priority: Int,
    attempts: Int,
    failures: Int,
    runId: Option[String],
    evaluatedRun: Option[String],
    notBefore: Option[Instant],
    hold: Option[Hold],
    state: ItemState,
    outcome: Option[String],
    origin: String,
    createdAt: Instant,
    updatedAt: Instant
)
