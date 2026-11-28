package com.histmersion
package server.agents

import com.histmersion.api.automation.autopilot.EngineStatus
import com.histmersion.domain.PriorRun

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.api.automation.Autopilot
import com.histmersion.domain.library.Citekey
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** The library's parts of the engine (the watch kind's acquisition work, the enrich kind's auto-enrich) as the agent
  * suites reach them: through the engine they built.
  */
extension (a: AgentsControllers)
  def attach(u: AgentUpstream): Unit = EngineContributions.work(a).attach(u)
  def acquireRecheck: AcquireRecheckService = EngineContributions.work(a).acquireRecheck
  def oaBacklog: OaBacklog = EngineContributions.work(a).oaBacklog
  def autopilotPage(st: EngineStatus): String =
    EngineContributions.autopilot(a).page(st).markdown
  def autoEnrich(citekey: Citekey): Option[PriorRun.Note] =
    EngineContributions.enrich(a).autoEnrich(citekey)
