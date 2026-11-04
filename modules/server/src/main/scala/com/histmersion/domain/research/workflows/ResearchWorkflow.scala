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
import com.histmersion.api.automation.AgentRole
import com.histmersion.api.automation.Pipeline
import com.histmersion.api.automation.TargetKind

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
  def version: Int = pipeline.version
  def target: TargetKind = pipeline.targetKind
  def roles: List[AgentRole] = pipeline.roles
  def describe: String = pipeline.describe

  def allowedTools(role: AgentRole): Set[ToolName] =
    agents.flatMap(a => definitions.agent(a).toOption).filter(_.role == role).flatMap(_.tools).toSet

  def run(run: RunCtx): Either[Halt, String] = impl.run(run)
