package com.histmersion
package domain.catalogue.events
import com.histmersion.util.problem.Problem

import com.histmersion.api.{Api, Write}
import com.histmersion.api.catalogue.{Correction, EventsApi}
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.Event
import com.histmersion.{Offset, PageSize}
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.domain.Creator
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{LintRule, tolerated}

/** The event catalogues' endpoints bound to their controllers, in their old order: matched before `events/{id}`, which
  * would read `events/import` as an event id.
  */
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object EventImportsRoutes:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  def apply(imports: EventImportsControllers): List[ServerEndpoint[Any, Identity]] =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)
    val me = Caller.Local
    List(
      EventsApi.importEvents.serverLogic[Identity](req => CreatorControllers.rest(req)(imports.importJob(me, req))),
      EventsApi.rederive.serverLogic[Identity](req => CreatorControllers.rest(req)(imports.rederiveJob(me, req))),
      // the query's own order: status, event, limit, offset
      EventsApi.corrections.serverLogic[Identity]((status, event, limit, offset) =>
        for
          ev <- event match
            case Some(e) =>
              Event.Id
                .parse(e.value)
                .left
                .map(p => Api.Error(p.at(Problem.Field("event")), Some(Api.Error.Code(Write.CodeInvalid))))
                .map(Some(_))
            case None => Right(None)
          off <- offset match
            case Some(o) =>
              Offset
                .parse(o.value)
                .left
                .map(p => Api.Error(p.at(Problem.Field("offset")), Some(Api.Error.Code(Write.CodeInvalid))))
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
