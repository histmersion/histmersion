package com.histmersion.server.agents

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.AgentsControllers

/** The library's parts of the engine (the watch kind's acquisition work, the enrich kind's auto-enrich) as the agent
  * suites reach them: through the engine they built.
  */
extension (a: AgentsControllers)
  def attach(u: AgentUpstream): Unit = EngineContributions.work(a).attach(u)
  def acquireRecheck: AcquireRecheckService = EngineContributions.work(a).acquireRecheck
  def oaBacklog: OaBacklog = EngineContributions.work(a).oaBacklog
  def autopilotPage(st: com.histmersion.api.automation.Autopilot.EngineStatus): String =
    EngineContributions.autopilot(a).page(st).markdown
  def autoEnrich(citekey: String): Option[String] = EngineContributions.enrich(a).autoEnrich(citekey)
