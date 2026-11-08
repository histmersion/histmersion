package com.histmersion
package domain.automation.workflows

import com.histmersion.domain.automation.agents.{Halt, OutputComparison, RunCtx}
import com.histmersion.domain.automation.{AgentRole, TargetKind}
import com.histmersion.domain.automation.Pipeline
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A kind of workflow a domain defines and the engine runs: what it is called and works on, the roles its agents take,
  * the tools each role's agents may use, and the run — steps in a fixed order (the step number is the order), each
  * checkpointed by the engine, so a resumed run replays the finished ones.
  */
trait WorkflowDefinition:
  def id: WorkflowId

  /** A run started under another version is not resumed (its checkpoints may not line up). */
  def version: Pipeline.Version
  def target: TargetKind
  def roles: List[AgentRole]
  def describe: WorkflowDefinition.DescribeResult

  /** The ONLY tools this role's agents are shown and may call. */
  def allowedTools(role: AgentRole): Set[ToolName]

  /** One run to its next resting state: a one-line summary for the run's log entry, or why it halted. */
  def run(run: RunCtx): Either[Halt, WorkflowDefinition.RunResultPart2]

  /** How its runs' outputs compare (`agent compare`); none by default. */
  def outputs: OutputComparison = OutputComparison.none

object WorkflowDefinition:
  type DescribeResult = DescribeResult.T
  object DescribeResult extends RefinedType[String, Pure]
  type RunResultPart2 = RunResultPart2.T
  object RunResultPart2 extends RefinedType[String, Pure]
