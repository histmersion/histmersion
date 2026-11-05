package com.histmersion.domain.automation.agents

import java.time.Instant

/** An approval as [[ApprovalService.InMemory]] holds it: its columns, the payload as written, and its times. */
final case class ApprovalRecord(
    id: Long,
    jobId: Option[String],
    seq: Option[Int],
    kind: String,
    citekey: Option[String],
    status: Option[String],
    current: Option[String],
    edgeKind: Option[String],
    target: Option[String],
    subject: Option[String],
    proposal: String,
    state: String,
    note: Option[String],
    result: Option[String],
    createdAt: Instant,
    decidedAt: Option[Instant]
)
