package com.histmersion.server.agents

import com.histmersion.domain.automation.workflows.{ToolName, WorkflowDefinition, WorkflowId}
import com.histmersion.values.{AgentRole, Pipeline, TargetKind}

/** One of today's pipelines registered as a workflow, through this transition adapter until its domain defines its own:
  * its identifier, version, target, roles and description are the pipeline's; a role's agents may use the tools the
  * definitions of the pipeline's step agents in that role list.
  */
final class PipelineWorkflow(
    val pipeline: Pipeline,
    agents: List[StepAgent],
    definitions: AgentDefinitions,
    impl: PipelineDef
) extends WorkflowDefinition:
  val id: WorkflowId = WorkflowId.applyUnsafe(pipeline.wire)
  def version: Int = pipeline.version
  def target: TargetKind = pipeline.targetKind
  def roles: List[AgentRole] = pipeline.roles
  def describe: String = pipeline.describe

  def allowedTools(role: AgentRole): Set[ToolName] =
    agents.flatMap(a => definitions.agent(a).toOption).filter(_.role == role).flatMap(_.tools).toSet

  def run(run: RunCtx): Either[Halt, String] = impl.run(run)

object PipelineWorkflow:
  /** The step agents each pipeline's steps run as. */
  def agentsOf(p: Pipeline): List[StepAgent] = p match
    case Pipeline.EnrichSource     => List(StepAgent.EnrichClassify, StepAgent.EnrichFindings, StepAgent.EnrichNotes)
    case Pipeline.ReviewArticle    =>
      List(StepAgent.RulesReviewer, StepAgent.ColdReader, StepAgent.Skeptic, StepAgent.Fixer)
    case Pipeline.PlanGoal         => List(StepAgent.GoalPlanner)
    case Pipeline.CompileArticle   => List(StepAgent.CompileExtractor, StepAgent.CompileWriter)
    case Pipeline.CurrencyCheck    => Nil
    case Pipeline.AcquisitionSweep => List(StepAgent.AcquisitionRanker)
    case Pipeline.InferFilter      => List(StepAgent.FilterInferrer)
    case Pipeline.HintEffect       => List(StepAgent.HintReader)
    case Pipeline.ProposeArticles  => List(StepAgent.ArticlePlanner)

  /** `impl` registered as the workflow of its pipeline. */
  def of(impl: PipelineDef, definitions: AgentDefinitions): PipelineWorkflow =
    PipelineWorkflow(impl.pipeline, agentsOf(impl.pipeline), definitions, impl)
