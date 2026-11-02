package com.histmersion.domain.catalogue.events

import com.histmersion.api.Api
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The events' endpoints bound to their controllers, in their old order; `page` is an event's page (get, put and patch
  * one), which shows its pictures and the articles citing its sources beside the catalogue's record.
  */
object EventsRoutes:
  def apply(events: EventsControllers, page: List[ServerEndpoint[Any, Identity]]): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    List(
      Api.events.serverLogicSuccess[Identity](q => events.list(q)),
      Api.eventsGeo.serverLogicSuccess[Identity](q => events.geo(q))
    ) ++ page ++ List(
      Api.deleteEvent.serverLogic[Identity](id => events.delete(me, id))
    )
