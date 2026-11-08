package com.histmersion
package domain.automation.agents

import com.histmersion.domain.automation.agents.AgentRepo

import java.time.Instant
import com.histmersion.util.jobs.Job
import io.github.iltotore.iron.RefinedType
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
  type Seq = Seq.T
  object Seq extends RefinedType[Int, Pure]
  type Attempt = Attempt.T
  object Attempt extends RefinedType[Int, Pure]
