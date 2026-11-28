package com.histmersion
package domain.automation.agents

import com.histmersion.api.automation.planquota.PoolId
import com.histmersion.api.automation.planquota.Reading
import com.histmersion.api.automation.PlanQuota

import com.histmersion.server.agents.PlanPools

import com.histmersion.api.automation.PlanQuota.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{tolerated, LintRule}

/** What a CLI client reports its readings to and asks mid-step: the plan pools ([[PlanPools]]), or a test's stand-in.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait PoolSink:
  def record(p: PoolId, rs: List[Reading])(using WritePermit): Unit

  /** Why `p` may not take another call now, and when it frees; None = it may. */
  def blocked(
      p: PoolId,
      model: Option[AgentsControllers.ModelName]
  )(using WritePermit): Option[(PlanQuota.Rationale, Option[PlanQuotaRepo.EpochMillis])]

  /** Read the ChatGPT plan from the app-server when the last read is old enough (throttled). */
  def refresh(now: PlanQuotaRepo.EpochMillis)(using WritePermit): Unit
