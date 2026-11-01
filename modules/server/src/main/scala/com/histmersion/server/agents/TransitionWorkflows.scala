package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.{AgentRoster, AgentsHandlers}
import com.histmersion.domain.automation.goals.PlanGoalPipeline
import com.histmersion.domain.automation.managers.HintEffectPipeline
import com.histmersion.domain.automation.workflows.PipelineDef

import com.histmersion.api.Agents.{Origin, Pipeline}
import com.histmersion.values.AgentRole
import com.histmersion.domain.automation.workflows.Contributions
import com.histmersion.server.{Deps, Handlers}

/** What today's library and research code registers into the engine until those domains contribute their own: the nine
  * pipelines as workflows ([[PipelineWorkflow]]), the read tools ([[TransitionTools]]) and the four manager kinds.
  */
object TransitionWorkflows:
  def contributions(deps: Deps, h: Handlers, a: AgentsHandlers): Contributions =
    def cfg = deps.cfg.agents
    val ds = deps.ds
    val work = AcquisitionWork(deps, h, a)
    val upstream = () => work.acquisition
    val proposals = SourceProposals(
      a.approvals,
      cfg,
      ck => h.getSource(ck, body = false).toOption.map(_.source.status),
      p => h.patchSource(p.citekey, p.patch).left.map(_.message).map(_ => p.describe)
    )
    val pipelines: List[PipelineDef] = List(
      EnrichSourcePipeline(h, ds, cfg, upstream, proposals),
      ReviewArticlePipeline(h, ds, cfg, proposals, a.definitions),
      PlanGoalPipeline(a.host, deps.queues, deps.workLists, a.goals, ds, cfg),
      CompileArticlePipeline(h, ds, cfg, (req, parent) => a.startRunAs(req, Origin.Run, Some(parent))),
      CurrencyCheckPipeline(h, ds, cfg, upstream, proposals),
      AcquisitionSweepPipeline(h, ds, cfg, upstream, work.oaBacklog),
      InferFilterPipeline(h, ds, cfg),
      ProposeArticlesPipeline(h, ds, cfg),
      HintEffectPipeline(deps.workLists, ds, cfg)
    )
    Contributions(
      pipelines.map(PipelineWorkflow.of(_, a.definitions)),
      TransitionTools(h, upstream, cfg.tools).contributions,
      List(ReviewManagerKind(h), EnrichManagerKind(h, a), SeedManagerKind(h), WatchManagerKind(h, work)),
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
