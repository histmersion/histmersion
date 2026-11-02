package com.histmersion.server.agents

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.AgentsHandlers

/** The library's parts of the engine (the watch kind's acquisition work, the enrich kind's auto-enrich) as the agent
  * suites reach them: through the engine they built.
  */
extension (a: AgentsHandlers)
  def attach(u: AgentUpstream): Unit = TransitionWorkflows.work(a).attach(u)
  def acquireRecheck: AcquireRecheckService = TransitionWorkflows.work(a).acquireRecheck
  def oaBacklog: OaBacklog = TransitionWorkflows.work(a).oaBacklog
  def autoEnrich(citekey: String): Option[String] = TransitionWorkflows.enrich(a).autoEnrich(citekey)
