package com.histmersion
package domain.automation.agents

import com.histmersion.domain.automation.agents.AgentRepo

import java.time.Instant
import com.histmersion.util.jobs.Job

/** One attempt of a run's step as [[RunService.InMemory]] holds it: what was recorded, and when it finished. */
final case class StepRecordState(jobId: Job.Id, step: AgentRepo.StepRecord, finishedAt: Instant)
