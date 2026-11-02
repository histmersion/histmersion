package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*

/** The endpoints that answer the autopilot's page ([[AutopilotStatus]]): read it, switch the autopilot, set the
  * three-way mode, drain. Their errors are the managers'.
  */
object AutopilotApi:
  import ManagersApi.errors

  lazy val autopilot: PublicEndpoint[Unit, ApiError, AutopilotStatus, Any] =
    endpoint.get.in("api" / "agents" / "autopilot").errorOut(errors).out(jsonBody[AutopilotStatus])

  lazy val setAutopilot: PublicEndpoint[Autopilot.AutopilotSet, ApiError, AutopilotStatus, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot")
      .errorOut(errors)
      .in(jsonBody[Autopilot.AutopilotSet])
      .out(jsonBody[AutopilotStatus])

  lazy val setMode: PublicEndpoint[AgentMode.ModeSet, ApiError, AutopilotStatus, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "mode")
      .errorOut(errors)
      .in(jsonBody[AgentMode.ModeSet])
      .out(jsonBody[AutopilotStatus])

  lazy val drain: PublicEndpoint[Autopilot.DrainSet, ApiError, AutopilotStatus, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "drain")
      .errorOut(errors)
      .in(jsonBody[Autopilot.DrainSet])
      .out(jsonBody[AutopilotStatus])

  /** The switches, after the managers' own endpoints in `Api.all`. */
  lazy val switches: List[AnyEndpoint] = List(autopilot, setAutopilot, setMode)

  /** The drain, after the managers' steering in `Api.all`. */
  lazy val draining: List[AnyEndpoint] = List(drain)
