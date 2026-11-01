package com.histmersion.domain.automation.agents

import com.histmersion.db.AgentRepo

import java.time.Instant

/** One attempt of a run's step as [[RunService.InMemory]] holds it: what was recorded, and when it finished. */
final case class StepRecordState(jobId: String, step: AgentRepo.StepRecord, finishedAt: Instant)
