package com.histmersion
package domain.research.workflows

import com.histmersion.domain.automation.agents.AgentDefinitions
import com.histmersion.domain.automation.agents.Halt
import com.histmersion.domain.automation.agents.OutputComparison
import com.histmersion.domain.automation.agents.RunCtx
import com.histmersion.domain.automation.agents.StepAgent
import com.histmersion.domain.automation.workflows.PipelineDef
import com.histmersion.domain.automation.workflows.ToolName
import com.histmersion.domain.automation.workflows.WorkflowDefinition
import com.histmersion.domain.automation.workflows.WorkflowId
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import com.histmersion.domain.Creator
import com.histmersion.domain.automation.AgentRole
import com.histmersion.api.automation.Pipeline
import com.histmersion.domain.automation.TargetKind
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{LintRule, tolerated}

/** One of research's pipelines as the workflow it registers: its identifier, version, target, roles and description are
  * the pipeline's; a role's agents may use the tools the definitions of the pipeline's step agents in that role list;
  * `outputs` compares its runs' outputs.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class ResearchWorkflow(
    impl: PipelineDef,
    agents: List[StepAgent],
    definitions: AgentDefinitions,
    override val outputs: OutputComparison = OutputComparison.none
) extends WorkflowDefinition:
  private val pipeline: Pipeline = impl.pipeline
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val id: WorkflowId = WorkflowId.applyUnsafe(pipeline.wire.value)
  def contributor: Creator.Plugin.Name = InTreeWorkflow.Research
  def stepDefinitions: List[Creator.Definition.Name] =
    agents.flatMap(a => Creator.Definition.Name.from(a.wire).toOption)
  def version: domain.automation.Pipeline.Version =
    domain.automation.Pipeline.Version(pipeline.version.value)
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def target: TargetKind = io.scalaland.chimney.Transformer
    .derive[com.histmersion.api.automation.TargetKind, TargetKind]
    .transform(pipeline.targetKind)
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def roles: List[AgentRole] = io.scalaland.chimney.Transformer
    .derive[List[com.histmersion.api.automation.AgentRole], List[AgentRole]]
    .transform(pipeline.roles)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: WorkflowDefinition.Description = WorkflowDefinition.Description(pipeline.describe.value)

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def run(run: RunCtx)(using WritePermit)(using Creator.New): Either[Halt, WorkflowDefinition.Summary] =
    impl.run(run).map(r => WorkflowDefinition.Summary(r.value))
