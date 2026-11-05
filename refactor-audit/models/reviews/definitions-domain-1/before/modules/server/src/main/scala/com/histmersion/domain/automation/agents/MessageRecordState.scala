package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.agents.AgentRepo

import java.time.Instant

/** One traced message of a step's attempt as [[RunService.InMemory]] holds it. */
final case class MessageRecordState(
    jobId: String,
    seq: Int,
    attempt: Int,
    message: AgentRepo.MessageRecord,
    createdAt: Instant
)
