package com.histmersion
package domain.catalogue.events
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.{Api, Write}
import com.histmersion.api.catalogue.{Correction, EventsApi}
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.Event
import com.histmersion.{Offset, PageSize}
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
      // the query's own order: status, event, limit, offset
      EventsApi.corrections.serverLogic[Identity]((status, event, limit, offset) =>
        for
          ev <- event match
            case Some(e) =>
              Event.Id
                .either(e.value)
                .left
                .map(why => Write.invalid(Problem.Unclassified(Legacy.Text(s"event '${e.value}': $why"))))
                .map(Some(_))
            case None => Right(None)
          off <- offset match
            case Some(o) =>
              Offset
                .either(o.value)
                .left
                .map(why => Write.invalid(Problem.Unclassified(Legacy.Text(s"offset '${o.value}': $why"))))
                .map(Some(_))
            case None => Right(None)
          list <- imports.corrections(
            status.map(s => Correction.StatusValue(s.value)),
            ev,
            limit.flatMap(l => PageSize.option(l.value)),
            off
          )
        yield list
      ),
      EventsApi.catalogues.serverLogicSuccess[Identity](_ => imports.catalogues)
    )
