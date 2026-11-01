package com.histmersion.server.agents

import com.histmersion.api.ManagersApi
import com.histmersion.server.Handlers
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The acquisition list's routes among the managers' (the repair and the legal re-check), bound to the watch kind's
  * acquisition work by this transition adapter until the library binds its own.
  */
object TransitionRoutes:
  def acquisition(h: Handlers): List[ServerEndpoint[Any, Identity]] =
    def work = TransitionWorkflows.work(h.agents)
    List(
      ManagersApi.repairAcquire.serverLogic[Identity](in => Right(work.oaBacklog.repair(in))),
      ManagersApi.acquireRecheck.serverLogic[Identity](in => work.acquireRecheck.run(in))
    )
