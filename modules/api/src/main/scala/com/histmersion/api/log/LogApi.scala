package com.histmersion
package api.log

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.Api
import com.histmersion.api.time.TimeBound
import sttp.tapir.*

/** The structured log's endpoints: a filtered page, and what happened around one problem (by the id its answer gave).
  */
object LogApi:

  private lazy val base = endpoint.in("api").errorOut(Api.problemOut)

  private lazy val filter: EndpointInput[LogPage.Filter] =
    query[Option[LogEvent.Trace]]("trace")
      .description("one trace: every step of one request or job")
      .and(
        query[Option[LogEvent.Label]]("label").description(
          "the label a client gave its calls (Histmersion-Trace-Label)"
        )
      )
      .and(query[Option[LogEvent.Key]]("job").description("steps concerning this job"))
      .and(query[Option[LogEvent.Key]]("run").description("steps concerning this agent run"))
      .and(query[Option[LogEvent.Key]]("source").description("steps concerning this source (its citekey)"))
      .and(query[Option[LogEvent.ProblemId]]("problem").description("steps concerning this problem (its id)"))
      .and(query[Option[LogEvent.Kind]]("kind").description(s"one of: ${LogEvent.Kinds.mkString(", ")}"))
      .and(query[Option[TimeBound]]("since").description("from: an ISO instant, a date, or 7d / 12h / 30m ago"))
      .and(query[Option[TimeBound]]("until").description("up to: an ISO instant or a date"))
      .and(
        query[Option[LogPage.Limit]]("limit")
          .description(s"events per page (default ${LogPage.DefaultLimit}, at most ${LogPage.MaxLimit})")
      )
      .and(query[Option[LogPage.Cursor]]("before").description("the next older page: the cursor the last page gave"))
      .and(
        query[Option[LogPage.From]]("from")
          .description("database (the default; the file when the database cannot answer) or file")
      )
      .mapTo[LogPage.Filter]

  lazy val events: PublicEndpoint[LogPage.Filter, Api.Error, LogPage, Any] =
    base.get
      .in("log")
      .in(filter)
      .out(jsonBody[LogPage])
      .description("The structured log, newest first: requests, provider calls, problems and database writes.")

  lazy val problem: PublicEndpoint[LogEvent.ProblemId, Api.Error, LogPage, Any] =
    base.get
      .in("problems" / path[LogEvent.ProblemId]("id"))
      .out(jsonBody[LogPage])
      .description("What happened around one problem: every step of the trace it was raised in, oldest first.")

  lazy val all: List[AnyEndpoint] = List(events, problem)
