package com.histmersion
package api.library

import com.histmersion.api.library.repairacquire.RepairRequest
import com.histmersion.api.library.repairacquire.RepairResult
import com.histmersion.api.automation.ManagersApi
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.Api
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The acquisition list's work the managers' endpoints sit beside: the repair of its ingest backlog and its legal
  * re-check. Their errors are the managers'.
  */
@atBoundary(
  BoundaryKind.Http,
  "declares the Tapir endpoints of the acquisition work routes with their request and response bodies"
)
object AcquisitionWorkApi:

  lazy val repairAcquire: PublicEndpoint[RepairRequest, Api.Error, RepairResult, Any] =
    endpoint.post
      .in("api" / "agents" / "autopilot" / "repair-acquire")
      .errorOut(Api.problemOut)
      .in(jsonBody[RepairRequest])
      .out(jsonBody[RepairResult])

  lazy val acquireRecheck: PublicEndpoint[Acquire.Checks.RecheckRequest, Api.Error, Acquire.Checks.RecheckResult, Any] =
    endpoint.post
      .in("api" / "queue" / "acquire" / "recheck")
      .errorOut(Api.problemOut)
      .in(jsonBody[Acquire.Checks.RecheckRequest])
      .out(jsonBody[Acquire.Checks.RecheckResult])

  /** After the autopilot's switches in `Api.all`. */
  lazy val all: List[AnyEndpoint] = List(repairAcquire, acquireRecheck)
