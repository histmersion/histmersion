package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.EventsApi
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The event links' endpoints bound to their controllers, in their old order: matched before `events/{id}`, which would
  * read `events/leads` as an event id.
  */
object EventLinksRoutes:
  def apply(links: EventLinksControllers): List[ServerEndpoint[Any, Identity]] =
    val me = Caller.Local
    List(
      EventsApi.leads.serverLogicSuccess[Identity](q => links.leads(q)),
      EventsApi.candidatesJob.serverLogic[Identity](req => links.candidatesJob(me, req)),
      EventsApi.links.serverLogic[Identity](id => links.links(id)),
      EventsApi.applyLinks.serverLogic[Identity]((id, req) => links.applyLinks(me, id, req))
    )
