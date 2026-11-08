package com.histmersion
package domain.library.workflows

import com.histmersion.api.library.AcquisitionWorkApi
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The acquisition list's routes among the managers' (the repair of the open-access backlog and the legal re-check),
  * bound to the library's acquisition work, in their place among the managers' routes.
  */
object AcquisitionWorkRoutes:
  def apply(work: => AcquisitionWork): List[ServerEndpoint[Any, Identity]] = List(
    AcquisitionWorkApi.repairAcquire.serverLogic[Identity](in => Right(work.oaBacklog.repair(in))),
    AcquisitionWorkApi.acquireRecheck.serverLogic[Identity](in => work.acquireRecheck.run(in))
  )
