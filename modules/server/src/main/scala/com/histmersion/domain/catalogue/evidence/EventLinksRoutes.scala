package com.histmersion
package domain.catalogue.evidence
import com.histmersion.util.problem.Problem

import com.histmersion.api.{Api, Write}
import com.histmersion.api.catalogue.EventsApi
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.Event
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.domain.Creator
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{LintRule, tolerated}

/** The event links' endpoints bound to their controllers, in their old order: matched before `events/{id}`, which would
  * read `events/leads` as an event id.
  */
object EventLinksRoutes:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def apply(links: EventLinksControllers): List[ServerEndpoint[Any, Identity]] =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)
    val me = Caller.Local
    // an id that is no event id names no event
    def eventId(id: com.histmersion.api.catalogue.Event.Id) =
      Event.Id
        .option(id.value)
        .toRight(
          Api.Error(
            Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"${id.value}")),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
    List(
      EventsApi.leads.serverLogicSuccess[Identity](q => links.leads(q)),
      EventsApi.candidatesJob.serverLogic[Identity](req => CreatorControllers.rest(req)(links.candidatesJob(me, req))),
      EventsApi.links.serverLogic[Identity](id => eventId(id).flatMap(links.links)),
      EventsApi.applyLinks.serverLogic[Identity]((id, req) =>
        CreatorControllers.rest(req)(eventId(id).flatMap(links.applyLinks(me, _, req)))
      )
    )
