package com.histmersion.server.views

import com.histmersion.api.{AcquireChecks, AgentMode, ApiError, Autopilot, AutopilotStatus, UpstreamQuota}
import com.histmersion.domain.automation.managers.ManagersHandlers
import io.scalaland.chimney.dsl.*

import java.time.{Clock, Instant}

/** The autopilot's page as the automation and the library show it together: the automation's status (the switches,
  * the rules, the goals, the focus, the drain) and the library's upstreams' quotas and its acquisition list's re-check
  * at the same moment. What `GET` and `POST /api/agents/autopilot`, its mode and its drain answer.
  */
final class AutopilotView(
    managers: ManagersHandlers,
    upstreams: Instant => List[UpstreamQuota],
    recheck: Instant => AcquireChecks.RecheckStatus,
    clock: Clock
):
  def page(st: Autopilot.EngineStatus): AutopilotStatus =
    val now = Instant.now(clock)
    st.into[AutopilotStatus]
      .withFieldConst(_.upstreams, upstreams(now))
      .withFieldConst(_.acquireRecheck, Some(recheck(now)))
      .transform

  def autopilot: AutopilotStatus = page(managers.autopilot)

  def setAutopilot(in: Autopilot.AutopilotSet): Either[ApiError, AutopilotStatus] = managers.setAutopilot(in).map(page)

  def setMode(in: AgentMode.ModeSet): Either[ApiError, AutopilotStatus] = managers.setMode(in).map(page)

  def drain(in: Autopilot.DrainSet): Either[ApiError, AutopilotStatus] = managers.drain(in).map(page)
