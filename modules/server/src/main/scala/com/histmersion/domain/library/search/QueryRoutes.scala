package com.histmersion
package domain.library.search

import com.histmersion.api.OpsApi
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** `POST /api/query` and `GET /api/schema/tables`. */
object QueryRoutes:
  def apply(queries: QueryControllers): List[ServerEndpoint[Any, Identity]] = List(
    OpsApi.query.serverLogic[Identity](req => queries.run(req)),
    OpsApi.schemaTables.serverLogicSuccess[Identity](_ => queries.schemaTables)
  )
