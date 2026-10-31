package com.histmersion.domain.automation.workflows

/** What the domains register into the engine: their workflows and the agent tools they offer. */
final case class Contributions(workflows: List[WorkflowDefinition], tools: List[ToolContribution])
