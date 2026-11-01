package com.histmersion.domain.catalogue.events

import com.histmersion.api.EventsApi
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The event catalogues' endpoints bound to their controllers, in their old order: matched before `events/{id}`, which
  * would read `events/import` as an event id.
  */
object EventImportsRoutes:
  def apply(imports: EventImportsControllers): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    List(
      EventsApi.importEvents.serverLogic[Identity](req => imports.importJob(me, req)),
      EventsApi.rederive.serverLogic[Identity](req => imports.rederiveJob(me, req)),
      EventsApi.corrections.serverLogic[Identity]((st, ev, limit, offset) => imports.corrections(st, ev, limit, offset)),
      EventsApi.catalogues.serverLogicSuccess[Identity](_ => imports.catalogues)
    )
