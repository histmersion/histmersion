package com.histmersion.domain.automation.agents

import com.histmersion.server.agents.PlanPools

import com.histmersion.api.PlanQuota.*
import io.circe.{parser, Json}

import scala.jdk.CollectionConverters.*

/** What a CLI client reports its readings to and asks mid-step: the plan pools ([[PlanPools]]), or a test's stand-in.
  */
trait PoolSink:
  def record(p: PoolId, rs: List[Reading]): Unit

  /** Why `p` may not take another call now, and when it frees; None = it may. */
  def blocked(p: PoolId, model: Option[String]): Option[(String, Option[Long])]

  /** Read the ChatGPT plan from the app-server when the last read is old enough (throttled). */
  def refresh(now: Long): Unit
