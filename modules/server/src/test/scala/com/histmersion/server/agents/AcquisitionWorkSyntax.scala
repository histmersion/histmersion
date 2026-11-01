package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.AgentsHandlers

/** The watch kind's acquisition work as the agent suites reach it: through the engine they built. */
extension (a: AgentsHandlers)
  def attach(u: AgentUpstream): Unit = TransitionWorkflows.work(a).attach(u)
  def acquireRecheck: AcquireRecheckService = TransitionWorkflows.work(a).acquireRecheck
  def oaBacklog: OaBacklog = TransitionWorkflows.work(a).oaBacklog
