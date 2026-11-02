package com.histmersion.server.views

import com.histmersion.api.library.UpstreamQuota
import com.histmersion.api.automation.{AgentMode, Autopilot}
import com.histmersion.api.views.AutopilotStatus
import com.histmersion.domain.automation.managers.ManagersHandlers
import io.scalaland.chimney.dsl.*

import java.time.{Clock, Instant}
import com.histmersion.api.Api
import com.histmersion.api.library.Acquire

/** The autopilot's page as the automation and the library show it together: the automation's status (the switches, the
  * rules, the goals, the focus, the drain) and the library's upstreams' quotas and its acquisition list's re-check at
  * the same moment. What `GET` and `POST /api/agents/autopilot`, its mode and its drain answer.
  */
final class AutopilotView(
    managers: ManagersHandlers,
    upstreams: Instant => List[UpstreamQuota],
    recheck: Instant => Acquire.Checks.RecheckStatus,
    clock: Clock
):
  def page(st: Autopilot.EngineStatus): AutopilotStatus =
    val now = Instant.now(clock)
    st.into[AutopilotStatus]
      .withFieldConst(_.upstreams, upstreams(now))
      .withFieldConst(_.acquireRecheck, Some(recheck(now)))
      .transform

  def autopilot: AutopilotStatus = page(managers.autopilot)

  def setAutopilot(in: Autopilot.AutopilotSet): Either[Api.Error, AutopilotStatus] = managers.setAutopilot(in).map(page)

  def setMode(in: AgentMode.ModeSet): Either[Api.Error, AutopilotStatus] = managers.setMode(in).map(page)

  def drain(in: Autopilot.DrainSet): Either[Api.Error, AutopilotStatus] = managers.drain(in).map(page)
