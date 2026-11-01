package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.AgentsHandlers
import com.histmersion.domain.automation.goals.PlanGoalPipeline
import com.histmersion.domain.automation.managers.HintEffectPipeline
import com.histmersion.domain.automation.workflows.PipelineDef

import com.histmersion.api.Agents.Origin
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
    val pipelines: List[PipelineDef] = List(
      EnrichSourcePipeline(h, ds, cfg, upstream, a.approvals),
      ReviewArticlePipeline(h, ds, cfg, a.approvals, a.definitions),
      PlanGoalPipeline(h, ds, cfg, upstream),
      CompileArticlePipeline(h, ds, cfg, (req, parent) => a.startRunAs(req, Origin.Run, Some(parent))),
      CurrencyCheckPipeline(h, ds, cfg, upstream, a.approvals),
      AcquisitionSweepPipeline(h, ds, cfg, upstream, work.oaBacklog),
      InferFilterPipeline(h, ds, cfg),
      ProposeArticlesPipeline(h, ds, cfg),
      HintEffectPipeline(h, ds, cfg)
    )
    Contributions(
      pipelines.map(PipelineWorkflow.of(_, a.definitions)),
      TransitionTools(h, upstream, cfg.tools).contributions,
      List(ReviewManagerKind(h), EnrichManagerKind(h), SeedManagerKind(h), WatchManagerKind(h, work))
    )

  /** The acquisition work the watch kind of `a`'s registry holds. */
  def work(a: AgentsHandlers): AcquisitionWork =
    a.workflows.managerKinds
      .collectFirst { case w: WatchManagerKind => w.work }
      .getOrElse(throw IllegalStateException("no watch manager kind is registered"))
