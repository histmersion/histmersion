package com.histmersion.api.library
import com.histmersion.api.ApiError
import com.histmersion.api.library.{AcquireChecks, RepairAcquire}
import com.histmersion.api.automation.ManagersApi
import sttp.tapir.*
import sttp.tapir.json.circe.*

/** The acquisition list's work the managers' endpoints sit beside: the repair of its ingest backlog and its legal
  * re-check. Their errors are the managers'.
  */
object AcquisitionWorkApi:
  import ManagersApi.errors

  lazy val repairAcquire: PublicEndpoint[RepairAcquire.RepairRequest, ApiError, RepairAcquire.RepairResult, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "repair-acquire")
      .errorOut(errors)
      .in(jsonBody[RepairAcquire.RepairRequest])
      .out(jsonBody[RepairAcquire.RepairResult])

  lazy val acquireRecheck: PublicEndpoint[AcquireChecks.RecheckRequest, ApiError, AcquireChecks.RecheckResult, Any] =
    endpoint.post
      .in("api" / "queue" / "acquire" / "recheck")
      .errorOut(errors)
      .in(jsonBody[AcquireChecks.RecheckRequest])
      .out(jsonBody[AcquireChecks.RecheckResult])

  /** After the autopilot's switches in `Api.all`. */
  lazy val all: List[AnyEndpoint] = List(repairAcquire, acquireRecheck)
