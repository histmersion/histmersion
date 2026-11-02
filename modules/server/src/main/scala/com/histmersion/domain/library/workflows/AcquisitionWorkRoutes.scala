package com.histmersion.domain.library.workflows

import com.histmersion.api.ManagersApi
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The acquisition list's routes among the managers' (the repair of the open-access backlog and the legal re-check),
  * bound to the library's acquisition work, in their place among the managers' routes.
  */
object AcquisitionWorkRoutes:
  def apply(work: => AcquisitionWork): List[ServerEndpoint[Any, Identity]] = List(
    ManagersApi.repairAcquire.serverLogic[Identity](in => Right(work.oaBacklog.repair(in))),
    ManagersApi.acquireRecheck.serverLogic[Identity](in => work.acquireRecheck.run(in))
  )
