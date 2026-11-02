package com.histmersion.server.agents

import com.histmersion.domain.library.workflows.{AcquisitionWork, EnrichManagerKind, LibraryWorkflows, WatchManagerKind}

import com.histmersion.domain.automation.agents.{AgentRoster, AgentsHandlers}
import com.histmersion.domain.automation.goals.PlanGoalPipeline
import com.histmersion.domain.automation.managers.HintEffectPipeline
import com.histmersion.domain.automation.workflows.{PipelineDef, WorkflowDefinition}

import com.histmersion.api.Agents.{Origin, Pipeline}
import com.histmersion.values.AgentRole
import com.histmersion.domain.automation.workflows.Contributions
import com.histmersion.server.{Deps, Handlers}

/** What the domains register into the engine: the library's own contribution ([[LibraryWorkflows]]: its three workflows
  * and the enrich and watch kinds), and today's research code until research contributes its own (its pipelines as
  * workflows through [[PipelineWorkflow]], the review and seed kinds), with the read tools ([[TransitionTools]]) — in
  * the order the engine has always listed them.
  */
object TransitionWorkflows:
  def contributions(deps: Deps, h: Handlers, a: AgentsHandlers): Contributions =
    def cfg = deps.cfg.agents
    def lib = deps.cfg.libraryAgents
    val ds = deps.ds
    val library = LibraryWorkflows(() => deps.cfg, ds, deps.transactor, h.librarySite, a)
    val upstream = () => library.work.acquisition
    val proposals = library.proposals
    def research(impl: PipelineDef): WorkflowDefinition = PipelineWorkflow.of(impl, a.definitions)
    val workflows: List[WorkflowDefinition] = List(
      library.enrichSource,
      research(ReviewArticlePipeline(h, ds, cfg, lib, proposals, a.definitions)),
      research(PlanGoalPipeline(a.host, deps.queues, deps.workLists, a.goals, ds, cfg)),
      research(CompileArticlePipeline(h, ds, cfg, lib, (req, parent) => a.startRunAs(req, Origin.Run, Some(parent)))),
      library.currencyCheck,
      library.acquisitionSweep,
      research(InferFilterPipeline(h, ds, cfg)),
      research(ProposeArticlesPipeline(h, ds, cfg, lib)),
      research(HintEffectPipeline(deps.workLists, a.approvalStore, ds, cfg))
    )
    Contributions(
      workflows,
      TransitionTools(h, upstream, cfg.tools).contributions,
      List(ReviewManagerKind(h), library.enrichKind, SeedManagerKind(h, lib), library.watchKind),
      List(ArticleObjectives(h))
    )

  /** What the agent definitions are held to and the generator renders from: the transition tools, and the roles of the
    * registered pipelines with the engine's own probe.
    */
  val roster: AgentRoster =
    AgentRoster(TransitionTools.names, (Pipeline.values.toList.flatMap(_.roles) :+ AgentRole.Probe).distinct)

  /** The enrich kind of `a`'s registry (its auto-enrich after an ingest). */
  def enrich(a: AgentsHandlers): EnrichManagerKind =
    a.workflows.managerKinds
      .collectFirst { case e: EnrichManagerKind => e }
      .getOrElse(throw IllegalStateException("no enrich manager kind is registered"))

  /** The acquisition work the watch kind of `a`'s registry holds. */
  def work(a: AgentsHandlers): AcquisitionWork =
    a.workflows.managerKinds
      .collectFirst { case w: WatchManagerKind => w.work }
      .getOrElse(throw IllegalStateException("no watch manager kind is registered"))
