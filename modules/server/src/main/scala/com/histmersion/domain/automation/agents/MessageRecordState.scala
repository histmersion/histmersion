package com.histmersion
package domain.automation.agents

import com.histmersion.domain.automation.agents.AgentRepo

import java.time.Instant
import com.histmersion.util.jobs.Job
import io.github.iltotore.iron.constraint.any.Pure

/** One traced message of a step's attempt as [[RunService.InMemory]] holds it. */
final case class MessageRecordState(
    jobId: Job.Id,
    seq: MessageRecordState.Seq,
    attempt: MessageRecordState.Attempt,
    message: AgentRepo.MessageRecord,
    createdAt: Instant
)

object MessageRecordState:
  export ApprovalRecord.Seq
  type Attempt = Attempt.T
  object Attempt extends Newtype[Int, Pure]
