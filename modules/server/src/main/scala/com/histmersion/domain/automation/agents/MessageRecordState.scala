package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.agents.AgentRepo

import java.time.Instant

/** One traced message of a step's attempt as [[RunService.InMemory]] holds it. */
final case class MessageRecordState(
    jobId: com.histmersion.util.jobs.Job.Id,
    seq: MessageRecordState.Seq,
    attempt: MessageRecordState.Attempt,
    message: AgentRepo.MessageRecord,
    createdAt: Instant
)

object MessageRecordState:
  type Seq = Seq.T
  object Seq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Attempt = Attempt.T
  object Attempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
