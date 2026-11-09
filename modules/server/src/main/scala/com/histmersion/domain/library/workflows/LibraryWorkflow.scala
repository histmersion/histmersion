package com.histmersion
package domain.library.workflows

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
  val id: WorkflowId = WorkflowId.applyUnsafe(pipeline.wire.value)
  def version: com.histmersion.domain.automation.Pipeline.Version =
    com.histmersion.domain.automation.Pipeline.Version(pipeline.version.value)
  def target: TargetKind = io.scalaland.chimney.Transformer
    .derive[com.histmersion.api.automation.TargetKind, TargetKind]
    .transform(pipeline.targetKind)
  def roles: List[AgentRole] = io.scalaland.chimney.Transformer
    .derive[List[com.histmersion.api.automation.AgentRole], List[AgentRole]]
    .transform(pipeline.roles)
  def describe: WorkflowDefinition.Description = WorkflowDefinition.Description(pipeline.describe.value)

  def allowedTools(role: AgentRole): Set[ToolName] =
    agents
      .flatMap(a => definitions.agent(a).toOption)
      .filter(
        _.role == io.scalaland.chimney.Transformer
          .derive[AgentRole, com.histmersion.api.automation.AgentRole]
          .transform(role)
      )
      .flatMap(_.tools)
      .toSet

  def run(run: RunCtx): Either[Halt, WorkflowDefinition.Summary] =
    impl.run(run).map(r => WorkflowDefinition.Summary(r.value))
