package com.histmersion
package domain.library.search

import com.histmersion.api.OpsApi
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.api.Api

/** `POST /api/query` and `GET /api/schema/tables`. */
object QueryRoutes:
  def apply(queries: QueryControllers): List[ServerEndpoint[Any, Identity]] = List(
    OpsApi.query.serverLogic[Identity](req => queries.run(req).left.map(Api.Error(_))),
    OpsApi.schemaTables.serverLogicSuccess[Identity](_ => queries.schemaTables)
  )
