package com.histmersion.domain.catalogue.places

import com.histmersion.api.EventsApi
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The refinement of the sites' types bound to its controller, in its old place (before `events/{id}`). */
object SiteTypesRoutes:
  def apply(siteTypes: SiteTypesControllers): List[ServerEndpoint[Any, Identity]] =
    List(EventsApi.refineSiteTypes.serverLogic[Identity](req => siteTypes.refineSiteTypes(Caller.Local, req)))
