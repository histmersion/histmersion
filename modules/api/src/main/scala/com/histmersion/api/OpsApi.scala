package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.automation.Queue
import com.histmersion.api.checks.{FormatCheck, FormatReport, Lint}
import com.histmersion.api.library.Query
import com.histmersion.api.views.QueueListing

object OpsApi:

  // explicit imports: a wildcard would shadow tapir's `query`/`path` with this object's own members
  type LintPart1 = LintPart1.T
  object LintPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LintFixPart1 = LintFixPart1.T
  object LintFixPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueuePatchPart1Part1 = QueuePatchPart1Part1.T
  object QueuePatchPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueDeletePart1 = QueueDeletePart1.T
  object QueueDeletePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListPart1Part1 = QueueListPart1Part1.T
  object QueueListPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListPart1Part2 = QueueListPart1Part2.T
  object QueueListPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListPart1Part3 = QueueListPart1Part3.T
  object QueueListPart1Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueListPart1Part4 = QueueListPart1Part4.T
  object QueueListPart1Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueAddPart1Part1 = QueueAddPart1Part1.T
  object QueueAddPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueExportPart1 = QueueExportPart1.T
  object QueueExportPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueExportPart3 = QueueExportPart3.T
  object QueueExportPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueImportPart1Part1 = QueueImportPart1Part1.T
  object QueueImportPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsExportPart3 = GoalsExportPart3.T
  object GoalsExportPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import com.histmersion.api.checks.Lint.{LintFixResult, LintReport}
  import com.histmersion.api.library.Query.{QueryRequest, QueryResult, SchemaTables}
  import com.histmersion.api.automation.Queue.{QueueAdd, QueueImport, QueueImported, QueueItem, QueuePatch}

  private lazy val base = endpoint.in("api").errorOut(jsonBody[Api.Error])

  private lazy val opsErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[Api.Error])(Api.Error.hasCode(Write.CodeNotFound)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  lazy val lint: PublicEndpoint[List[com.histmersion.api.OpsApi.LintPart1], Api.Error, LintReport, Any] =
    endpoint.get
      .in("api" / "lint")
      .in(sttp.tapir.query[List[String]]("rule"))
      .errorOut(opsErrors)
      .out(jsonBody[LintReport])

  lazy val lintFix: PublicEndpoint[List[com.histmersion.api.OpsApi.LintFixPart1], Api.Error, LintFixResult, Any] =
    base.post.in("lint" / "fix").in(sttp.tapir.query[List[String]]("rule")).out(jsonBody[LintFixResult])

  lazy val lintMarkdown: PublicEndpoint[FormatCheck, Api.Error, FormatReport, Any] =
    base.post.in("lint" / "markdown").in(jsonBody[FormatCheck]).out(jsonBody[FormatReport])

  /** Declared before the `{list}` routes so `items` is never read as a list name. */
  lazy val queuePatch: PublicEndpoint[(com.histmersion.api.OpsApi.QueuePatchPart1Part1, QueuePatch), Api.Error, QueueItem, Any] =
    endpoint.patch
      .in("api" / "queue" / "items" / path[Int]("id"))
      .in(jsonBody[QueuePatch])
      .errorOut(opsErrors)
      .out(jsonBody[QueueItem])

  lazy val queueDelete: PublicEndpoint[com.histmersion.api.OpsApi.QueueDeletePart1, Api.Error, QueueItem, Any] =
    endpoint.delete
      .in("api" / "queue" / "items" / path[Int]("id"))
      .errorOut(opsErrors)
      .out(jsonBody[QueueItem])

  lazy val queueList: PublicEndpoint[(com.histmersion.api.OpsApi.QueueListPart1Part1, com.histmersion.api.OpsApi.QueueListPart1Part2, Option[com.histmersion.api.OpsApi.QueueListPart1Part3], Option[com.histmersion.api.OpsApi.QueueListPart1Part4]), Api.Error, QueueListing, Any] =
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

  lazy val queueAdd: PublicEndpoint[(com.histmersion.api.OpsApi.QueueAddPart1Part1, QueueAdd), Api.Error, QueueItem, Any] =
    endpoint.post
      .in("api" / "queue" / path[String]("list"))
      .in(jsonBody[QueueAdd])
      .errorOut(opsErrors)
      .out(jsonBody[QueueItem])

  lazy val queueExport: PublicEndpoint[com.histmersion.api.OpsApi.QueueExportPart1, Api.Error, com.histmersion.api.OpsApi.QueueExportPart3, Any] =
    endpoint.get
      .in("api" / "queue" / path[String]("list") / "export")
      .errorOut(opsErrors)
      .out(stringBody)

  lazy val queueImport: PublicEndpoint[(com.histmersion.api.OpsApi.QueueImportPart1Part1, QueueImport), Api.Error, QueueImported, Any] =
    endpoint.post
      .in("api" / "queue" / path[String]("list") / "import")
      .in(jsonBody[QueueImport])
      .errorOut(opsErrors)
      .out(jsonBody[QueueImported])

  /** Under `export/` rather than `goals/` so it is never read as a goal slug. */
  lazy val goalsExport: PublicEndpoint[Unit, Api.Error, com.histmersion.api.OpsApi.GoalsExportPart3, Any] =
    base.get.in("export" / "goals").out(stringBody)

  lazy val query: PublicEndpoint[QueryRequest, Api.Error, QueryResult, Any] =
    base.post.in("query").in(jsonBody[QueryRequest]).out(jsonBody[QueryResult])

  lazy val schemaTables: PublicEndpoint[Unit, Api.Error, SchemaTables, Any] =
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
