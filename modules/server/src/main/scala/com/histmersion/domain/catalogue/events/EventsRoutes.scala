package com.histmersion
package domain.catalogue.events
import com.histmersion.util.problem.Problem

import com.histmersion.api.{Api, Write}
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.Event
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.lint.{tolerated, LintRule}

/** The events' endpoints bound to their controllers, in their old order; `page` is an event's page (get, put and patch
  * one), which shows its pictures and the articles citing its sources beside the catalogue's record.
  */
object EventsRoutes:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
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
            Api.Error(
              Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"${id.value}")),
              Some(Api.Error.Code(Write.CodeNotFound))
            )
          )
          .flatMap(events.delete(me, _))
      )
    )
