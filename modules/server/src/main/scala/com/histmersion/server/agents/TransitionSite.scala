package com.histmersion.server.agents

import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.agents.{AgentRoster, AgentsHandlers, EngineSite}
import com.histmersion.domain.automation.workflows.{AutomationHost, Contributions}
import com.histmersion.domain.automation.worklists.{QueueService, WorkListControllers}
import com.histmersion.plugin.AiProvider
import com.histmersion.server.{Deps, Handlers}
import com.histmersion.util.jobs.{JobStops, Pinning}

import javax.sql.DataSource
import scala.collection.immutable.ListMap

/** The engine's [[EngineSite]] over the composition's `Deps` and today's handlers. */
final class TransitionSite(deps: Deps, h: Handlers, digestOn: Option[java.time.Clock] = None) extends EngineSite:
  def config: AppConfig = deps.cfg
  def ds: DataSource = deps.ds
  def queues: QueueService = deps.queues
  def workLists: WorkListControllers = deps.workLists
  def pins: Pinning = deps.pins
  def jobStops: JobStops = deps.jobStops
  def clock: java.time.Clock = deps.clock
  def digestClock: Option[java.time.Clock] = digestOn
  def aiProviders: () => ListMap[String, AiProvider] = () => deps.plugins.registries.aiProviders
  def roster: AgentRoster = TransitionWorkflows.roster
  def contributions(a: AgentsHandlers): Contributions = TransitionWorkflows.contributions(deps, h, a)
  def approvalEffects: com.histmersion.domain.automation.approvals.ApprovalEffects =
    TransitionApprovalEffects(h, deps.ds)
  def host(a: AgentsHandlers): AutomationHost = TransitionHost(h, () => TransitionWorkflows.work(a).acquisition)
