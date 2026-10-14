package com.histmersion.server.events

import com.histmersion.core.EventsApi
import com.histmersion.server.Handlers
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** REST of the catalogue side of events (#1449). Prepended to the server's routes: `events/leads`,
  * `events/import`… must be matched before `events/{id}` reads them as an event id. */
object EventCatalogRoutes:
  def apply(h: Handlers): List[ServerEndpoint[Any, Identity]] = List(
    EventsApi.importEvents.serverLogic[Identity](req => h.catalog.importJob(req)),
    EventsApi.catalogues.serverLogicSuccess[Identity](_ => h.catalog.catalogues),
    EventsApi.leads.serverLogicSuccess[Identity](q => h.eventLeads(q)),
    EventsApi.candidatesJob.serverLogic[Identity](req => h.catalog.candidatesJob(req)),
    EventsApi.links.serverLogic[Identity](id => h.catalog.links(id)),
    EventsApi.applyLinks.serverLogic[Identity]((id, req) => h.catalog.applyLinks(id, req)),
    EventsApi.refineSiteTypes.serverLogic[Identity](req => h.catalog.refineSiteTypes(req)))
