package com.histmersion.domain.automation.workflows

import com.histmersion.domain.automation.managers.ManagerKindDefinition

/** What the domains register into the engine: their workflows, the agent tools they offer and the kinds of manager they
  * run.
  */
final case class Contributions(
    workflows: List[WorkflowDefinition],
    tools: List[ToolContribution],
    managerKinds: List[ManagerKindDefinition]
)
