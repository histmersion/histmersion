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
  def blocked(p: PoolId, model: Option[com.histmersion.domain.automation.agents.PoolSink.BlockedModel]): Option[(com.histmersion.domain.automation.agents.PoolSink.BlockedResultPart1, Option[com.histmersion.domain.automation.agents.PoolSink.BlockedResultPart2])]

  /** Read the ChatGPT plan from the app-server when the last read is old enough (throttled). */
  def refresh(now: com.histmersion.domain.automation.agents.PoolSink.RefreshNow): Unit

object PoolSink:
  type BlockedResultPart1 = BlockedResultPart1.T
  object BlockedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockedResultPart2 = BlockedResultPart2.T
  object BlockedResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockedModel = BlockedModel.T
  object BlockedModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshNow = RefreshNow.T
  object RefreshNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
