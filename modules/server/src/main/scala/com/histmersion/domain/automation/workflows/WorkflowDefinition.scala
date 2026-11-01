package com.histmersion.domain.automation.workflows

import com.histmersion.domain.automation.agents.{Halt, RunCtx}
import com.histmersion.values.{AgentRole, TargetKind}

/** A kind of workflow a domain defines and the engine runs: what it is called and works on, the roles its agents take,
  * the tools each role's agents may use, and the run — steps in a fixed order (the step number is the order), each
  * checkpointed by the engine, so a resumed run replays the finished ones.
  */
trait WorkflowDefinition:
  def id: WorkflowId

  /** A run started under another version is not resumed (its checkpoints may not line up). */
  def version: Int
  def target: TargetKind
  def roles: List[AgentRole]
  def describe: String

  /** The ONLY tools this role's agents are shown and may call. */
  def allowedTools(role: AgentRole): Set[ToolName]

  /** One run to its next resting state: a one-line summary for the run's log entry, or why it halted. */
  def run(run: RunCtx): Either[Halt, String]
