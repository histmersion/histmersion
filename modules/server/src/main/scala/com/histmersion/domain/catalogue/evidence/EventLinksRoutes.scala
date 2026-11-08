package com.histmersion
package domain.catalogue.evidence

import com.histmersion.api.{Api, Write}
import com.histmersion.api.catalogue.EventsApi
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.Event
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The event links' endpoints bound to their controllers, in their old order: matched before `events/{id}`, which would
  * read `events/leads` as an event id.
  */
object EventLinksRoutes:
  def apply(links: EventLinksControllers): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    // an id that is no event id names no event
    def eventId(id: com.histmersion.api.catalogue.Event.Id) =
      Event.Id
        .option(id.value)
        .toRight(Api.Error(Problem(s"No event '${id.value}'"), Some(Api.Error.Code(Write.CodeNotFound))))
    List(
      EventsApi.leads.serverLogicSuccess[Identity](q => links.leads(q)),
      EventsApi.candidatesJob.serverLogic[Identity](req => links.candidatesJob(me, req)),
      EventsApi.links.serverLogic[Identity](id => eventId(id).flatMap(links.links)),
      EventsApi.applyLinks.serverLogic[Identity]((id, req) => eventId(id).flatMap(links.applyLinks(me, _, req)))
    )
