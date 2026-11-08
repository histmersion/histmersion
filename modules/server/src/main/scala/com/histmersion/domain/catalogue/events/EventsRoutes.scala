package com.histmersion
package domain.catalogue.events

import com.histmersion.api.{Api, Write}
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.Event
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
      Api.deleteEvent.serverLogic[Identity](id =>
        Event.Id
          .option(id.value)
          .toRight(
            Api.Error(Problem(s"No event '${id.value}'"), Some(Api.Error.Code(Write.CodeNotFound)))
          )
          .flatMap(events.delete(me, _))
      )
    )
