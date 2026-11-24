package com.histmersion
package domain.library.search

import com.histmersion.api.OpsApi
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.api.Api
import com.histmersion.lint.{LintRule, tolerated}

/** `POST /api/query` and `GET /api/schema/tables`. */
object QueryRoutes:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def apply(queries: QueryControllers): List[ServerEndpoint[Any, Identity]] = List(
    OpsApi.query.serverLogic[Identity](req => queries.run(req).left.map(Api.Error(_))),
    OpsApi.schemaTables.serverLogicSuccess[Identity](_ => queries.schemaTables)
  )
