package com.histmersion.domain.automation.workflows

import sttp.ai.core.agent.AgentTool
import sttp.shared.Identity

/** An agent tool a domain offers the engine's toolbox: its name, and the typed tool — description, input schema and a
  * call into the domain's own services — the agent loop and `/mcp/agent/<agent>` expose.
  *
  * Offering a tool grants nothing: a workflow names the tools each of its roles may use
  * ([[WorkflowDefinition.allowedTools]]), the engine shows an agent only those, and a workflow that names a tool nobody
  * contributes fails its registration ([[Workflows.of]]).
  */
trait ToolContribution:
  def name: ToolName
  def agentTool: AgentTool[Identity, ?]
