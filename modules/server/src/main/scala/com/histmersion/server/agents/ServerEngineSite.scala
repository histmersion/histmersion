package com.histmersion.server.agents

import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.agents.{AgentRoster, AgentsControllers, EngineSite}
import com.histmersion.domain.automation.approvals.ApprovalEffects
import com.histmersion.domain.automation.workflows.{AutomationHost, Contributions}
import com.histmersion.domain.automation.worklists.{QueueService, WorkListControllers}
import com.histmersion.domain.library.workflows.SourceProposalEffect
import com.histmersion.domain.research.workflows.ArticleFilterApproval
import com.histmersion.plugin.AiProvider
import com.histmersion.server.{Deps, ServerWiring}
import com.histmersion.util.jobs.{JobStops, Pinning}

import javax.sql.DataSource
import scala.collection.immutable.ListMap

/** The engine's [[EngineSite]] over the composition: the `Deps` of the server, the domains' contributions
  * ([[EngineContributions]]), the approval effects they contribute (the library's source proposals, research's article
  * filter) and the host over the wiring ([[ServerAutomationHost]]).
  */
final class ServerEngineSite(deps: Deps, w: ServerWiring, digestOn: Option[java.time.Clock] = None) extends EngineSite:
  def config: AppConfig = deps.cfg
  def ds: DataSource = deps.ds
  def queues: QueueService = deps.queues
  def workLists: WorkListControllers = deps.workLists
  def pins: Pinning = deps.pins
  def jobStops: JobStops = deps.jobStops
  def clock: java.time.Clock = deps.clock
  override def userPaths: com.histmersion.config.UserPaths = deps.userPaths
  def digestClock: Option[java.time.Clock] = digestOn
  def aiProviders: () => ListMap[String, AiProvider] = () => deps.plugins.registries.aiProviders
  def roster: AgentRoster = EngineContributions.roster
  def contributions(a: AgentsControllers): Contributions = EngineContributions.of(deps, w, a)
  def approvalEffects: ApprovalEffects = ApprovalEffects.of(
    SourceProposalEffect(w.library.sources),
    List(ArticleFilterApproval(w.research.articles, w.research.articleStore))
  )
  def host(a: AgentsControllers): AutomationHost =
    ServerAutomationHost(w, () => EngineContributions.work(a).acquisition)
