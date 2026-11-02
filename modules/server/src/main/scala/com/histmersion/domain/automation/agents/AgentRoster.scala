package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.workflows.ToolName
import com.histmersion.domain.automation.AgentRole

/** What the domains contribute that the agent definitions are held to and the definitions generator renders from: the
  * agent tools they offer, and the roles their workflows' agents take (with the engine's own probe). A definition may
  * list only these tools, and take only these roles.
  */
final case class AgentRoster(tools: List[ToolName], roles: List[AgentRole])

object AgentRoster:
  /** No tools and no roles: for reading the bundled files, never a definition. */
  val none: AgentRoster = AgentRoster(Nil, Nil)
