package com.histmersion
package domain.automation.workflows

import com.histmersion.domain.automation.goals.ObjectiveProgress
import com.histmersion.domain.automation.managers.ManagerKindDefinition

/** What the domains register into the engine: their workflows, the agent tools they offer, the kinds of manager they
  * run and how they measure a goal's progress.
  */
final case class Contributions(
    workflows: List[WorkflowDefinition],
    tools: List[ToolContribution],
    managerKinds: List[ManagerKindDefinition],
    objectives: List[ObjectiveProgress]
)
