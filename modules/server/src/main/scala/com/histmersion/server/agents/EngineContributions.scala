package com.histmersion.server.agents

import com.histmersion.domain.library.workflows.{AcquisitionWork, EnrichManagerKind, LibraryWorkflows, WatchManagerKind}
import com.histmersion.domain.research.workflows.ResearchWorkflows

import com.histmersion.domain.automation.agents.{AgentRoster, AgentsHandlers}
import com.histmersion.domain.automation.goals.PlanGoalPipeline
import com.histmersion.domain.automation.managers.HintEffectPipeline
import com.histmersion.domain.automation.workflows.{PipelineDef, WorkflowDefinition}

import com.histmersion.api.Agents.Pipeline
import com.histmersion.values.AgentRole
import com.histmersion.domain.automation.workflows.Contributions
import com.histmersion.server.{Deps, ServerWiring}

/** What the domains register into the engine, in the order the engine has always listed them: the library's
  * contribution ([[LibraryWorkflows]]: its three workflows, the enrich and watch kinds, its tools), research's
  * ([[ResearchWorkflows]]: its four workflows, the review and seed kinds, its measure of a goal and its tools), and the
  * engine's own pipelines ([[PipelineWorkflow]]); the tools in the toolbox's order ([[ToolboxOrder]]).
  */
object EngineContributions:
  def of(deps: Deps, w: ServerWiring, a: AgentsHandlers): Contributions =
    def cfg = deps.cfg.agents
    val ds = deps.ds
    val library = LibraryWorkflows(() => deps.cfg, ds, deps.transactor, w.librarySite, a)
    val research = ResearchWorkflows(() => deps.cfg, w.researchSite, a, library.proposals)
    def engine(impl: PipelineDef): WorkflowDefinition = PipelineWorkflow.of(impl, a.definitions)
    val workflows: List[WorkflowDefinition] = List(
      library.enrichSource,
      research.reviewArticle,
      engine(PlanGoalPipeline(a.host, deps.queues, deps.workLists, a.goals, ds, cfg)),
      research.compileArticle,
      library.currencyCheck,
      library.acquisitionSweep,
      research.inferFilter,
      research.proposeArticles,
      engine(HintEffectPipeline(deps.workLists, a.approvalStore, ds, cfg))
    )
    Contributions(
      workflows,
      ToolboxOrder.ordered(library.tools ++ research.tools),
      List(research.reviewKind, library.enrichKind, research.seedKind, library.watchKind),
      List(research.objectives)
    )

  /** What the agent definitions are held to and the generator renders from: the toolbox's tools, and the roles of the
    * registered pipelines with the engine's own probe.
    */
  val roster: AgentRoster =
    AgentRoster(ToolboxOrder.names, (Pipeline.values.toList.flatMap(_.roles) :+ AgentRole.Probe).distinct)

  /** The enrich kind of `a`'s registry (its auto-enrich after an ingest). */
  def enrich(a: AgentsHandlers): EnrichManagerKind =
    a.workflows.managerKinds
      .collectFirst { case e: EnrichManagerKind => e }
      .getOrElse(throw IllegalStateException("no enrich manager kind is registered"))

  /** The acquisition work the watch kind of `a`'s registry holds. */
  def work(a: AgentsHandlers): AcquisitionWork = watch(a).work

  /** The watch kind of `a`'s registry. */
  def watch(a: AgentsHandlers): WatchManagerKind =
    a.workflows.managerKinds
      .collectFirst { case w: WatchManagerKind => w }
      .getOrElse(throw IllegalStateException("no watch manager kind is registered"))

  /** The autopilot's page over `a`: its status with the watch kind's upstreams and acquisition list's re-check. */
  def autopilot(a: AgentsHandlers): com.histmersion.server.views.AutopilotView =
    com.histmersion.server.views.AutopilotView(a.managing, watch(a).upstreams, work(a).acquireRecheck.status, a.clock)
