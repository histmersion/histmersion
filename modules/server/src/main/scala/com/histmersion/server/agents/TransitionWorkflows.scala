package com.histmersion.server.agents

import com.histmersion.api.Agents.Origin
import com.histmersion.domain.automation.workflows.Contributions
import com.histmersion.server.{Deps, Handlers}

/** What today's library and research code registers into the engine until those domains contribute their own: the nine
  * pipelines as workflows ([[PipelineWorkflow]]) and the read tools ([[TransitionTools]]).
  */
object TransitionWorkflows:
  def contributions(deps: Deps, h: Handlers, a: AgentsHandlers): Contributions =
    def cfg = deps.cfg.agents
    val ds = deps.ds
    val upstream = () => a.acquisition
    val pipelines: List[PipelineDef] = List(
      EnrichSourcePipeline(h, ds, cfg, upstream, a.approvals),
      ReviewArticlePipeline(h, ds, cfg, a.approvals, a.definitions),
      PlanGoalPipeline(h, ds, cfg, upstream),
      CompileArticlePipeline(h, ds, cfg, (req, parent) => a.startRunAs(req, Origin.Run, Some(parent))),
      CurrencyCheckPipeline(h, ds, cfg, upstream, a.approvals),
      AcquisitionSweepPipeline(h, ds, cfg, upstream, a.oaBacklog),
      InferFilterPipeline(h, ds, cfg),
      ProposeArticlesPipeline(h, ds, cfg),
      HintEffectPipeline(h, ds, cfg)
    )
    Contributions(
      pipelines.map(PipelineWorkflow.of(_, a.definitions)),
      TransitionTools(h, upstream, cfg.tools).contributions
    )
