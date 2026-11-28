package com.histmersion
package server.agents

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
import com.histmersion.util.data.maintenance.WriteGate

import javax.sql.DataSource
import scala.collection.immutable.ListMap
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome
import com.histmersion.util.time.Timer
import com.histmersion.util.time.Pause
import com.histmersion.config.UserPaths
import com.histmersion.util.plugins.Plugin
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The engine's [[EngineSite]] over the composition: the `Deps` of the server, the domains' contributions
  * ([[EngineContributions]]), the approval effects they contribute (the library's source proposals, research's article
  * filter) and the host over the wiring ([[ServerAutomationHost]]).
  */
@movesTo(FeaturePlace.Server)
final class ServerEngineSite(deps: Deps, w: ServerWiring, digestOn: Option[java.time.Clock] = None) extends EngineSite:
  def stores: Stores = deps.stores
  def resources: Resources = deps.resources
  def jvmHome: JvmHome = deps.jvmHome
  def config: AppConfig = deps.cfg
  def ds: DataSource = deps.ds
  def queues: QueueService = deps.queues
  def workLists: WorkListControllers = deps.workLists
  def pins: Pinning = deps.pins
  def jobStops: JobStops = deps.jobStops
  def maintenance: WriteGate = deps.maintenance
  def clock: java.time.Clock = deps.clock
  def timer: Timer = deps.timer
  def pause: Pause = deps.pause
  override def userPaths: UserPaths = deps.userPaths
  def digestClock: Option[java.time.Clock] = digestOn
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def aiProviders: () => ListMap[Plugin.Id, AiProvider] = () =>
    deps.plugins.registries.aiProviders.map((k, p) => Plugin.Id.assume(k.value) -> p)
  def roster: AgentRoster = EngineContributions.roster
  def contributions(a: AgentsControllers): Contributions = EngineContributions.of(deps, w, a)
  def approvalEffects: ApprovalEffects = ApprovalEffects.of(
    SourceProposalEffect(w.library.sources, domain.research.workflows.ResearchTransformers.proposal),
    List(ArticleFilterApproval(w.research.articles, w.research.articleStore))
  )
  def host(a: AgentsControllers): AutomationHost =
    ServerAutomationHost(w, () => EngineContributions.work(a).acquisition)
