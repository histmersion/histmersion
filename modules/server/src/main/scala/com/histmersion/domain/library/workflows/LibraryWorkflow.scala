package com.histmersion.domain.library.workflows

import com.histmersion.domain.automation.agents.{AgentDefinitions, Halt, OutputComparison, RunCtx, StepAgent}
import com.histmersion.domain.automation.workflows.{PipelineDef, ToolName, WorkflowDefinition, WorkflowId}
import com.histmersion.values.{AgentRole, Pipeline, TargetKind}

/** One of the library's pipelines as the workflow it registers: its identifier, version, target, roles and description
  * are the pipeline's; a role's agents may use the tools the definitions of the pipeline's step agents in that role
  * list; `outputs` compares its runs' outputs.
  */
final class LibraryWorkflow(
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
