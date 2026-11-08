package com.histmersion
package domain.automation.agents

import com.histmersion.server.agents.PlanPools

import com.histmersion.api.automation.PlanQuota.*
import io.circe.{parser, Json}

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a CLI client reports its readings to and asks mid-step: the plan pools ([[PlanPools]]), or a test's stand-in.
  */
trait PoolSink:
  def record(p: PoolId, rs: List[Reading]): Unit

  /** Why `p` may not take another call now, and when it frees; None = it may. */
  def blocked(p: PoolId, model: Option[AgentsControllers.ModelName]): Option[(PoolSink.BlockedReason, Option[PlanQuotaRepo.EpochMillis])]

  /** Read the ChatGPT plan from the app-server when the last read is old enough (throttled). */
  def refresh(now: PlanQuotaRepo.EpochMillis): Unit

object PoolSink:
  type BlockedReason = BlockedReason.T
  object BlockedReason extends RefinedType[String, Pure]
