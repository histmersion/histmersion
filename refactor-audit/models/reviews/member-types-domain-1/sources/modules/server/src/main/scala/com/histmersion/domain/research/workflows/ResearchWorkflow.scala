package com.histmersion.domain.research.workflows

import com.histmersion.domain.automation.agents.AgentDefinitions
import com.histmersion.domain.automation.agents.Halt
import com.histmersion.domain.automation.agents.OutputComparison
import com.histmersion.domain.automation.agents.RunCtx
import com.histmersion.domain.automation.agents.StepAgent
import com.histmersion.domain.automation.workflows.PipelineDef
import com.histmersion.domain.automation.workflows.ToolName
import com.histmersion.domain.automation.workflows.WorkflowDefinition
import com.histmersion.domain.automation.workflows.WorkflowId
import com.histmersion.domain.automation.AgentRole
import com.histmersion.api.automation.Pipeline
import com.histmersion.domain.automation.TargetKind

/** One of research's pipelines as the workflow it registers: its identifier, version, target, roles and description are
  * the pipeline's; a role's agents may use the tools the definitions of the pipeline's step agents in that role list;
  * `outputs` compares its runs' outputs.
  */
final class ResearchWorkflow(
    impl: PipelineDef,
    agents: List[StepAgent],
    definitions: AgentDefinitions,
    override val outputs: OutputComparison = OutputComparison.none
) extends WorkflowDefinition:
  private val pipeline: Pipeline = impl.pipeline
  val id: WorkflowId = WorkflowId.applyUnsafe(pipeline.wire)
  def version: com.histmersion.domain.research.workflows.ResearchWorkflow.VersionResult = pipeline.version
  def target: TargetKind = io.scalaland.chimney.Transformer.derive[com.histmersion.api.automation.TargetKind, TargetKind].transform(pipeline.targetKind)
  def roles: List[AgentRole] = io.scalaland.chimney.Transformer.derive[List[com.histmersion.api.automation.AgentRole], List[AgentRole]].transform(pipeline.roles)
  def describe: com.histmersion.domain.research.workflows.ResearchWorkflow.DescribeResult = pipeline.describe

  def allowedTools(role: AgentRole): Set[ToolName] =
    agents.flatMap(a => definitions.agent(a).toOption).filter(_.role == io.scalaland.chimney.Transformer.derive[AgentRole, com.histmersion.api.automation.AgentRole].transform(role)).flatMap(_.tools).toSet

  def run(run: RunCtx): Either[Halt, com.histmersion.domain.research.workflows.ResearchWorkflow.RunResultPart2] = impl.run(run)

object ResearchWorkflow:
  type VersionResult = VersionResult.T
  object VersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResultPart2 = RunResultPart2.T
  object RunResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
