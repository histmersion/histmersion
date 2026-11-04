package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.workflows.PipelineDef

import com.histmersion.domain.automation.workflows.ToolName
import com.histmersion.domain.automation.workflows.WorkflowDefinition
import com.histmersion.domain.automation.workflows.WorkflowId
import com.histmersion.domain.automation.AgentRole
import com.histmersion.api.automation.Pipeline
import com.histmersion.domain.automation.TargetKind

/** One of research's and the engine's pipelines registered as a workflow, through this transition adapter until
  * research defines its own (the library registers its own, `LibraryWorkflow`): its identifier, version, target, roles
  * and description are the pipeline's; a role's agents may use the tools the definitions of the pipeline's step agents
  * in that role list.
  */
final class PipelineWorkflow(
    val pipeline: Pipeline,
    agents: List[StepAgent],
    definitions: AgentDefinitions,
    impl: PipelineDef
) extends WorkflowDefinition:
  val id: WorkflowId = WorkflowId.applyUnsafe(pipeline.wire)
  def version: Int = pipeline.version
  def target: TargetKind = io.scalaland.chimney.Transformer.derive[com.histmersion.api.automation.TargetKind, TargetKind].transform(pipeline.targetKind)
  def roles: List[AgentRole] = io.scalaland.chimney.Transformer.derive[List[com.histmersion.api.automation.AgentRole], List[AgentRole]].transform(pipeline.roles)
  def describe: String = pipeline.describe

  def allowedTools(role: AgentRole): Set[ToolName] =
    agents.flatMap(a => definitions.agent(a).toOption).filter(_.role == io.scalaland.chimney.Transformer.derive[AgentRole, com.histmersion.api.automation.AgentRole].transform(role)).flatMap(_.tools).toSet

  def run(run: RunCtx): Either[Halt, String] = impl.run(run)

object PipelineWorkflow:
  /** The step agents each pipeline's steps run as. */
  def agentsOf(p: Pipeline): List[StepAgent] = p match
    case Pipeline.EnrichSource  => List(StepAgent.EnrichClassify, StepAgent.EnrichFindings, StepAgent.EnrichNotes)
    case Pipeline.ReviewArticle =>
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
