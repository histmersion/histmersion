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
  type QueueExportPart3 = QueueExportPart3.T
  object QueueExportPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalsExportPart3 = GoalsExportPart3.T
  object GoalsExportPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import com.histmersion.api.checks.Lint.{LintFixResult, LintReport}
  import com.histmersion.api.library.Query.{QueryRequest, QueryResult, SchemaTables}
  import com.histmersion.api.automation.Queue.{QueueAdd, QueueImport, QueueImported, QueueItem, QueuePatch}

  private lazy val base = endpoint.in("api").errorOut(jsonBody[Api.Error])

  private lazy val opsErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[Api.Error])((Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Write.CodeNotFound))).andThen(refactorResult2 => ((refactorResult2) == (true)))),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  lazy val lint: PublicEndpoint[List[com.histmersion.api.checks.Lint.Wire], Api.Error, LintReport, Any] =
    (endpoint.get
      .in("api" / "lint")
      .in(sttp.tapir.query[List[String]]("rule"))
      .errorOut(opsErrors)
      .out(jsonBody[LintReport])).mapIn[_root_.scala.List[_root_.com.histmersion.api.checks.Lint.Wire]]((refactorDecoded10: _root_.scala.List[_root_.java.lang.String]) => (refactorDecoded10).map(refactorElement9 => _root_.com.histmersion.api.checks.Lint.Wire(refactorElement9)))((refactorEncoded10: _root_.scala.List[_root_.com.histmersion.api.checks.Lint.Wire]) => (refactorEncoded10).map(refactorElement10 => (refactorElement10).value))

  lazy val lintFix: PublicEndpoint[List[com.histmersion.api.checks.Lint.Wire], Api.Error, LintFixResult, Any] =
    (base.post.in("lint" / "fix").in(sttp.tapir.query[List[String]]("rule")).out(jsonBody[LintFixResult])).mapIn[_root_.scala.List[_root_.com.histmersion.api.checks.Lint.Wire]]((refactorDecoded11: _root_.scala.List[_root_.java.lang.String]) => (refactorDecoded11).map(refactorElement11 => _root_.com.histmersion.api.checks.Lint.Wire(refactorElement11)))((refactorEncoded11: _root_.scala.List[_root_.com.histmersion.api.checks.Lint.Wire]) => (refactorEncoded11).map(refactorElement12 => (refactorElement12).value))

  lazy val lintMarkdown: PublicEndpoint[FormatCheck, Api.Error, FormatReport, Any] =
    base.post.in("lint" / "markdown").in(jsonBody[FormatCheck]).out(jsonBody[FormatReport])

  /** Declared before the `{list}` routes so `items` is never read as a list name. */
  lazy val queuePatch: PublicEndpoint[(com.histmersion.api.automation.Queue.Id, QueuePatch), Api.Error, QueueItem, Any] =
    (endpoint.patch
      .in("api" / "queue" / "items" / path[Int]("id"))
      .in(jsonBody[QueuePatch])
      .errorOut(opsErrors)
      .out(jsonBody[QueueItem])).mapIn[(_root_.com.histmersion.api.automation.Queue.Id, _root_.com.histmersion.api.automation.Queue.QueuePatch)]((refactorDecoded12: (_root_.scala.Int, _root_.com.histmersion.api.automation.Queue.QueuePatch)) => (_root_.com.histmersion.api.automation.Queue.Id((refactorDecoded12)._1), (refactorDecoded12)._2))((refactorEncoded12: (_root_.com.histmersion.api.automation.Queue.Id, _root_.com.histmersion.api.automation.Queue.QueuePatch)) => (((refactorEncoded12)._1).value, (refactorEncoded12)._2))

  lazy val queueDelete: PublicEndpoint[com.histmersion.api.automation.Queue.Id, Api.Error, QueueItem, Any] =
    (endpoint.delete
      .in("api" / "queue" / "items" / path[Int]("id"))
      .errorOut(opsErrors)
      .out(jsonBody[QueueItem])).mapIn[_root_.com.histmersion.api.automation.Queue.Id]((refactorDecoded4: _root_.scala.Int) => _root_.com.histmersion.api.automation.Queue.Id(refactorDecoded4))((refactorEncoded4: _root_.com.histmersion.api.automation.Queue.Id) => (refactorEncoded4).value)

  lazy val queueList: PublicEndpoint[(com.histmersion.api.automation.Queue.Wire, Boolean, Option[com.histmersion.api.automation.Queue.Wire], Option[com.histmersion.api.automation.Queue.Wire]), Api.Error, QueueListing, Any] =
    (endpoint.get
      .in("api" / "queue" / path[String]("list"))
      .in(sttp.tapir.query[Boolean]("all").default(false))
      .in(sttp.tapir.query[Option[String]]("goal").description("only the items serving this research goal (its slug)"))
      .in(
        sttp.tapir
          .query[Option[String]]("job")
          .description("only the items this agent run or background job wrote or ticked off (its id)")
      )
      .errorOut(opsErrors)
      .out(jsonBody[QueueListing])).mapIn[(_root_.com.histmersion.api.automation.Queue.Wire, Boolean, _root_.scala.Option[_root_.com.histmersion.api.automation.Queue.Wire], _root_.scala.Option[_root_.com.histmersion.api.automation.Queue.Wire])]((refactorDecoded5: (_root_.java.lang.String, _root_.scala.Boolean, _root_.scala.Option[_root_.java.lang.String], _root_.scala.Option[_root_.java.lang.String])) => (_root_.com.histmersion.api.automation.Queue.Wire((refactorDecoded5)._1), ((refactorDecoded5)._2), ((refactorDecoded5)._3).map(refactorElement5 => _root_.com.histmersion.api.automation.Queue.Wire(refactorElement5)), ((refactorDecoded5)._4).map(refactorElement6 => _root_.com.histmersion.api.automation.Queue.Wire(refactorElement6))))((refactorEncoded5: (_root_.com.histmersion.api.automation.Queue.Wire, Boolean, _root_.scala.Option[_root_.com.histmersion.api.automation.Queue.Wire], _root_.scala.Option[_root_.com.histmersion.api.automation.Queue.Wire])) => (((refactorEncoded5)._1).value, ((refactorEncoded5)._2), ((refactorEncoded5)._3).map(refactorElement7 => (refactorElement7).value), ((refactorEncoded5)._4).map(refactorElement8 => (refactorElement8).value)))

  lazy val queueAdd: PublicEndpoint[(com.histmersion.api.automation.Queue.Wire, QueueAdd), Api.Error, QueueItem, Any] =
    (endpoint.post
      .in("api" / "queue" / path[String]("list"))
      .in(jsonBody[QueueAdd])
      .errorOut(opsErrors)
      .out(jsonBody[QueueItem])).mapIn[(_root_.com.histmersion.api.automation.Queue.Wire, _root_.com.histmersion.api.automation.Queue.QueueAdd)]((refactorDecoded15: (_root_.java.lang.String, _root_.com.histmersion.api.automation.Queue.QueueAdd)) => (_root_.com.histmersion.api.automation.Queue.Wire((refactorDecoded15)._1), (refactorDecoded15)._2))((refactorEncoded15: (_root_.com.histmersion.api.automation.Queue.Wire, _root_.com.histmersion.api.automation.Queue.QueueAdd)) => (((refactorEncoded15)._1).value, (refactorEncoded15)._2))

  lazy val queueExport: PublicEndpoint[com.histmersion.api.automation.Queue.Wire, Api.Error, com.histmersion.api.OpsApi.QueueExportPart3, Any] =
    (endpoint.get
      .in("api" / "queue" / path[String]("list") / "export")
      .errorOut(opsErrors)
      .out(stringBody)).mapIn[_root_.com.histmersion.api.automation.Queue.Wire]((refactorDecoded7: _root_.java.lang.String) => _root_.com.histmersion.api.automation.Queue.Wire(refactorDecoded7))((refactorEncoded7: _root_.com.histmersion.api.automation.Queue.Wire) => (refactorEncoded7).value).mapOut[_root_.com.histmersion.api.OpsApi.QueueExportPart3]((refactorDecoded8: _root_.java.lang.String) => _root_.com.histmersion.api.OpsApi.QueueExportPart3(refactorDecoded8))((refactorEncoded8: _root_.com.histmersion.api.OpsApi.QueueExportPart3) => (refactorEncoded8).value)

  lazy val queueImport: PublicEndpoint[(com.histmersion.api.automation.Queue.Wire, QueueImport), Api.Error, QueueImported, Any] =
    (endpoint.post
      .in("api" / "queue" / path[String]("list") / "import")
      .in(jsonBody[QueueImport])
      .errorOut(opsErrors)
      .out(jsonBody[QueueImported])).mapIn[(_root_.com.histmersion.api.automation.Queue.Wire, _root_.com.histmersion.api.automation.Queue.QueueImport)]((refactorDecoded18: (_root_.java.lang.String, _root_.com.histmersion.api.automation.Queue.QueueImport)) => (_root_.com.histmersion.api.automation.Queue.Wire((refactorDecoded18)._1), (refactorDecoded18)._2))((refactorEncoded18: (_root_.com.histmersion.api.automation.Queue.Wire, _root_.com.histmersion.api.automation.Queue.QueueImport)) => (((refactorEncoded18)._1).value, (refactorEncoded18)._2))

  /** Under `export/` rather than `goals/` so it is never read as a goal slug. */
  lazy val goalsExport: PublicEndpoint[Unit, Api.Error, com.histmersion.api.OpsApi.GoalsExportPart3, Any] =
    (base.get.in("export" / "goals").out(stringBody)).mapOut[_root_.com.histmersion.api.OpsApi.GoalsExportPart3]((refactorDecoded2: _root_.java.lang.String) => _root_.com.histmersion.api.OpsApi.GoalsExportPart3(refactorDecoded2))((refactorEncoded2: _root_.com.histmersion.api.OpsApi.GoalsExportPart3) => (refactorEncoded2).value)

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
