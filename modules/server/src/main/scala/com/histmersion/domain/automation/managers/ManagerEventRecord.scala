package com.histmersion.domain.automation.managers

import java.time.Instant

/** A manager's event as [[ManagerService.InMemory]] holds it. */
final case class ManagerEventRecord(
    id: Long,
    managerId: String,
    itemId: Option[Long],
    runId: Option[String],
    at: Instant,
    kind: String,
    text: String
)
