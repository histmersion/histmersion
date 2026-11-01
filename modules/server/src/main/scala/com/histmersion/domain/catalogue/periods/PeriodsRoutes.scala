package com.histmersion.domain.catalogue.periods

import com.histmersion.api.Api
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The periods' endpoints bound to their controllers, in the order route matching needs: `periods/{id}` after the
  * search, the writes after both.
  */
object PeriodsRoutes:
  def apply(periods: PeriodsControllers): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    List(
      Api.periods.serverLogicSuccess[Identity]((q, region, from, to) => periods.search(q, region, from, to)),
      Api.period.serverLogic[Identity](id => periods.period(id)),
      Api.seedPeriods.serverLogic[Identity](replace => periods.seed(me, replace)),
      Api.importPeriods.serverLogic[Identity]((cols, dataset, body) => periods.importDataset(me, cols, dataset, body)),
      Api.fetchPeriods.serverLogic[Identity]((source, cols) => periods.fetch(me, source, cols))
    )
