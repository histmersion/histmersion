package com.histmersion
package domain.catalogue.places

import com.histmersion.api.catalogue.EventsApi
import com.histmersion.domain.{Caller, Creator}
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The refinement of the sites' types bound to its controller, in its old place (before `events/{id}`). */
object SiteTypesRoutes:
  private given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)

  def apply(siteTypes: SiteTypesControllers): List[ServerEndpoint[Any, Identity]] =
    List(EventsApi.refineSiteTypes.serverLogic[Identity](req => siteTypes.refineSiteTypes(Caller.Local, req)))
