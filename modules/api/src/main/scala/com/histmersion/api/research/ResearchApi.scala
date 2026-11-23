package com.histmersion
package api.research

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.Api
import com.histmersion.api.automation.{Goal, Queue}
import sttp.tapir.*

object ResearchApi:

  private lazy val base = endpoint.in("api" / "research" / "state").errorOut(Api.problemOut)

  private lazy val scope: EndpointInput[OpenWork.Scope] =
    query[Option[OpenWork.Terms.Who]]("who")
      .description("owner (what waits for you) or agents (what the agents will handle)")
      .validateOption(Validator.enumeration(OpenWork.Terms.Who.all, w => Some(w.label)))
      .and(query[Option[Goal.Slug]]("goal").description("the items of this goal only"))
      .and(
        query[Option[OpenWork.Terms.Kind]]("kind")
          .description("decision, acquisition, task or run")
          .validateOption(Validator.enumeration(OpenWork.Terms.Kind.all, k => Some(k.label)))
      )
      .mapTo[OpenWork.Scope]

  lazy val state: PublicEndpoint[OpenWork.Scope, Api.Error, OpenWork.State, Any] =
    base.get
      .in(scope)
      .out(jsonBody[OpenWork.State])
      .description(
        "Every open item and who acts next: the decisions, acquisitions, tasks and paused runs that wait for the " +
          "owner, and what the agents will handle."
      )

  lazy val acquisition: PublicEndpoint[Queue.QueueItem.Id, Api.Error, OpenWork.History, Any] =
    base.get
      .in("acquisitions" / path[Queue.QueueItem.Id]("id"))
      .out(jsonBody[OpenWork.History])
      .description("One acquisition's verdict and its kept attempts, with what each provider answered.")

  lazy val all: List[AnyEndpoint] = List(state, acquisition)
