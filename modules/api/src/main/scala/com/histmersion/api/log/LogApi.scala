package com.histmersion
package api.log

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.TapirJson.*
import com.histmersion.api.Api
import sttp.tapir.*

/** The structured log's endpoints: a filtered page, and what happened around one problem (by the id its answer gave).
  */
object LogApi:

  private lazy val base = endpoint.in("api").errorOut(Api.problemOut)

  private def text[T](name: String, description: String)(wrap: String => T, unwrap: T => String) =
    query[Option[String]](name).description(description).map(_.map(wrap))(_.map(unwrap))

  private lazy val filter: EndpointInput[LogPage.Filter] =
    text("trace", "one trace: every step of one request or job")(LogEvent.Trace(_), _.value)
      .and(text("label", "the label a client gave its calls (Histmersion-Trace-Label)")(LogEvent.Label(_), _.value))
      .and(text("job", "steps concerning this job")(LogEvent.Key(_), _.value))
      .and(text("run", "steps concerning this agent run")(LogEvent.Key(_), _.value))
      .and(text("source", "steps concerning this source (its citekey)")(LogEvent.Key(_), _.value))
      .and(text("problem", "steps concerning this problem (its id)")(LogEvent.Key(_), _.value))
      .and(text("kind", s"one of: ${LogEvent.Kinds.mkString(", ")}")(LogEvent.Kind(_), _.value))
      .and(text("since", "from: an ISO instant, a date, or 7d / 12h / 30m ago")(LogEvent.At(_), _.value))
      .and(text("until", "up to: an ISO instant or a date")(LogEvent.At(_), _.value))
      .and(
        query[Option[Int]]("limit")
          .description(s"events per page (default ${LogPage.DefaultLimit}, at most ${LogPage.MaxLimit})")
          .map(_.map(LogPage.Limit(_)))(_.map(_.value))
      )
      .and(text("before", "the next older page: the cursor the last page gave")(LogPage.Cursor(_), _.value))
      .and(
        text("from", "database (the default; the file when the database cannot answer) or file")(
          LogPage.From(_),
          _.value
        )
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
      .in("problems" / path[String]("id").map(LogEvent.ProblemId(_))(_.value))
      .out(jsonBody[LogPage])
      .description("What happened around one problem: every step of the trace it was raised in, oldest first.")

  lazy val all: List[AnyEndpoint] = List(events, problem)
