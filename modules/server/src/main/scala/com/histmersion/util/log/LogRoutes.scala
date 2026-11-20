package com.histmersion
package util.log

import com.histmersion.api.Api
import com.histmersion.api.log.LogApi
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The structured log's endpoints bound to their controllers. */
object LogRoutes:
  def apply(log: LogControllers): List[ServerEndpoint[Any, Identity]] =
    List(
      LogApi.events.serverLogic[Identity](f => log.events(f).left.map(Api.Error(_))),
      LogApi.problem.serverLogic[Identity](id => log.problem(id).left.map(Api.Error(_)))
    )
