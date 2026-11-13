package com.histmersion
package api.library
import com.histmersion.api.automation.ManagersApi
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import com.histmersion.api.Api

/** The acquisition list's work the managers' endpoints sit beside: the repair of its ingest backlog and its legal
  * re-check. Their errors are the managers'.
  */
object AcquisitionWorkApi:
  import ManagersApi.errors

  lazy val repairAcquire: PublicEndpoint[RepairAcquire.RepairRequest, Api.Error, RepairAcquire.RepairResult, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "repair-acquire")
      .errorOut(errors)
      .in(jsonBody[RepairAcquire.RepairRequest])
      .out(jsonBody[RepairAcquire.RepairResult])

  lazy val acquireRecheck: PublicEndpoint[Acquire.Checks.RecheckRequest, Api.Error, Acquire.Checks.RecheckResult, Any] =
    endpoint.post
      .in("api" / "queue" / "acquire" / "recheck")
      .errorOut(errors)
      .in(jsonBody[Acquire.Checks.RecheckRequest])
      .out(jsonBody[Acquire.Checks.RecheckResult])

  /** After the autopilot's switches in `Api.all`. */
  lazy val all: List[AnyEndpoint] = List(repairAcquire, acquireRecheck)
