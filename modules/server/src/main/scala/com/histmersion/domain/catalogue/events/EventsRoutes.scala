package com.histmersion.domain.catalogue.events

import com.histmersion.api.Api
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The events' endpoints bound to their controllers, in their old order. */
object EventsRoutes:
  def apply(events: EventsControllers): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    List(
      Api.events.serverLogicSuccess[Identity](q => events.list(q)),
      Api.eventsGeo.serverLogicSuccess[Identity](q => events.geo(q)),
      Api.getEvent.serverLogic[Identity](id => events.get(id)),
      Api.putEvent.serverLogic[Identity]((id, in) => events.put(me, id, in)),
      Api.patchEvent.serverLogic[Identity]((id, p) => events.patch(me, id, p)),
      Api.deleteEvent.serverLogic[Identity](id => events.delete(me, id))
    )
