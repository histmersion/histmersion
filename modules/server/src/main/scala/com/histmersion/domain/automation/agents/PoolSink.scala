package com.histmersion.domain.automation.agents

import com.histmersion.server.agents.PlanPools

import com.histmersion.api.automation.PlanQuota.*
import io.circe.{parser, Json}

import scala.jdk.CollectionConverters.*

/** What a CLI client reports its readings to and asks mid-step: the plan pools ([[PlanPools]]), or a test's stand-in.
  */
trait PoolSink:
  def record(p: PoolId, rs: List[Reading]): Unit

  /** Why `p` may not take another call now, and when it frees; None = it may. */
  def blocked(p: PoolId, model: Option[com.histmersion.domain.automation.agents.AgentsControllers.ModelName]): Option[(com.histmersion.domain.automation.agents.PoolSink.BlockedReason, Option[com.histmersion.domain.automation.agents.PlanQuotaRepo.EpochMillis])]

  /** Read the ChatGPT plan from the app-server when the last read is old enough (throttled). */
  def refresh(now: com.histmersion.domain.automation.agents.PlanQuotaRepo.EpochMillis): Unit

object PoolSink:
  type BlockedReason = BlockedReason.T
  object BlockedReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
