package com.histmersion
package api.views
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.automation.{AgentMode, Autopilot, ManagersApi}
import com.histmersion.api.given
import com.histmersion.api.Api

/** The endpoints that answer the autopilot's page ([[AutopilotStatus]]): read it, switch the autopilot, set the
  * three-way mode, drain. Their errors are the managers'.
  */
object AutopilotApi:
  import com.histmersion.api.automation.ManagersApi.errors

  lazy val autopilot: PublicEndpoint[Unit, Api.Error, AutopilotStatus, Any] =
    endpoint.get.in("api" / "agents" / "autopilot").errorOut(errors).out(jsonBody[AutopilotStatus])

  lazy val setAutopilot: PublicEndpoint[Autopilot.AutopilotSet, Api.Error, AutopilotStatus, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot")
      .errorOut(errors)
      .in(jsonBody[Autopilot.AutopilotSet])
      .out(jsonBody[AutopilotStatus])

  lazy val setMode: PublicEndpoint[AgentMode.ModeSet, Api.Error, AutopilotStatus, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "mode")
      .errorOut(errors)
      .in(jsonBody[AgentMode.ModeSet])
      .out(jsonBody[AutopilotStatus])

  lazy val drain: PublicEndpoint[Autopilot.DrainSet, Api.Error, AutopilotStatus, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "drain")
      .errorOut(errors)
      .in(jsonBody[Autopilot.DrainSet])
      .out(jsonBody[AutopilotStatus])

  /** The switches, after the managers' own endpoints in `Api.all`. */
  lazy val switches: List[AnyEndpoint] = List(autopilot, setAutopilot, setMode)

  /** The drain, after the managers' steering in `Api.all`. */
  lazy val draining: List[AnyEndpoint] = List(drain)
