package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

object OpsApi:

  // explicit imports: a wildcard would shadow tapir's `query`/`path` with this object's own members
  import Lint.{LintFixResult, LintReport}
  import Query.{QueryRequest, QueryResult, SchemaTables}
  import Queue.{QueueAdd, QueueImport, QueueImported, QueueItem, QueuePatch}

  private lazy val base = endpoint.in("api").errorOut(jsonBody[ApiError])

  private lazy val opsErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  lazy val lint: PublicEndpoint[List[String], ApiError, LintReport, Any] =
    endpoint.get
      .in("api" / "lint")
      .in(sttp.tapir.query[List[String]]("rule"))
      .errorOut(opsErrors)
      .out(jsonBody[LintReport])

  lazy val lintFix: PublicEndpoint[List[String], ApiError, LintFixResult, Any] =
    base.post.in("lint" / "fix").in(sttp.tapir.query[List[String]]("rule")).out(jsonBody[LintFixResult])

  lazy val lintMarkdown: PublicEndpoint[FormatCheck, ApiError, FormatReport, Any] =
    base.post.in("lint" / "markdown").in(jsonBody[FormatCheck]).out(jsonBody[FormatReport])

  /** Declared before the `{list}` routes so `items` is never read as a list name. */
  lazy val queuePatch: PublicEndpoint[(Int, QueuePatch), ApiError, QueueItem, Any] =
    endpoint.patch
      .in("api" / "queue" / "items" / path[Int]("id"))
      .in(jsonBody[QueuePatch])
      .errorOut(opsErrors)
      .out(jsonBody[QueueItem])

  lazy val queueDelete: PublicEndpoint[Int, ApiError, QueueItem, Any] =
    endpoint.delete
      .in("api" / "queue" / "items" / path[Int]("id"))
      .errorOut(opsErrors)
      .out(jsonBody[QueueItem])

  lazy val queueList: PublicEndpoint[(String, Boolean, Option[String], Option[String]), ApiError, QueueListing, Any] =
    endpoint.get
      .in("api" / "queue" / path[String]("list"))
      .in(sttp.tapir.query[Boolean]("all").default(false))
      .in(sttp.tapir.query[Option[String]]("goal").description("only the items serving this research goal (its slug)"))
      .in(
        sttp.tapir
          .query[Option[String]]("job")
          .description("only the items this agent run or background job wrote or ticked off (its id)")
      )
      .errorOut(opsErrors)
      .out(jsonBody[QueueListing])

  lazy val queueAdd: PublicEndpoint[(String, QueueAdd), ApiError, QueueItem, Any] =
    endpoint.post
      .in("api" / "queue" / path[String]("list"))
      .in(jsonBody[QueueAdd])
      .errorOut(opsErrors)
      .out(jsonBody[QueueItem])

  lazy val queueExport: PublicEndpoint[String, ApiError, String, Any] =
    endpoint.get
      .in("api" / "queue" / path[String]("list") / "export")
      .errorOut(opsErrors)
      .out(stringBody)

  lazy val queueImport: PublicEndpoint[(String, QueueImport), ApiError, QueueImported, Any] =
    endpoint.post
      .in("api" / "queue" / path[String]("list") / "import")
      .in(jsonBody[QueueImport])
      .errorOut(opsErrors)
      .out(jsonBody[QueueImported])

  /** Under `export/` rather than `goals/` so it is never read as a goal slug. */
  lazy val goalsExport: PublicEndpoint[Unit, ApiError, String, Any] =
    base.get.in("export" / "goals").out(stringBody)

  lazy val query: PublicEndpoint[QueryRequest, ApiError, QueryResult, Any] =
    base.post.in("query").in(jsonBody[QueryRequest]).out(jsonBody[QueryResult])

  lazy val schemaTables: PublicEndpoint[Unit, ApiError, SchemaTables, Any] =
    base.get.in("schema" / "tables").out(jsonBody[SchemaTables])

  /** Order matters: the two `queue/items/{id}` routes precede the `queue/{list}` ones. */
  lazy val all: List[AnyEndpoint] = List(
    lint,
    lintFix,
    lintMarkdown,
    queuePatch,
    queueDelete,
    queueExport,
    queueImport,
    queueList,
    queueAdd,
    goalsExport,
    query,
    schemaTables
  )
