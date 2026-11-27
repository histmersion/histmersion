package com.histmersion
package api

import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import sttp.model.StatusCode
import com.histmersion.api.automation.Queue
import com.histmersion.api.checks.{FormatCheck, FormatReport, Lint}
import com.histmersion.api.library.Query
import com.histmersion.api.views.QueueListing
import scala.`package`.List
import com.histmersion.api.automation.Goal
import com.histmersion.lint.{atBoundary, movesTo, BoundaryKind, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@atBoundary(BoundaryKind.Http, "declares the Tapir endpoints of the ops routes with their request and response bodies")
@movesTo(FeaturePlace.Api)
object OpsApi:

  // explicit imports: a wildcard would shadow tapir's `query`/`path` with this object's own members

  import com.histmersion.api.checks.Lint.{LintFixResult, LintReport}
  import com.histmersion.api.library.Query.{QueryRequest, QueryResult, SchemaTables}
  import com.histmersion.api.automation.Queue.{QueueAdd, QueueImport, QueueImported, QueueItem, QueuePatch}

  private lazy val base = endpoint.in("api").errorOut(Api.problemOut)

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val lint: PublicEndpoint[List[Lint.Wire], Api.Error, LintReport, Any] =
    (endpoint.get
      .in("api" / "lint")
      .in(sttp.tapir.query[List[String]]("rule"))
      .errorOut(Api.problemOut)
      .out(jsonBody[LintReport]))
      .mapIn[List[Lint.Wire]]((refactorDecoded10: List[String]) =>
        (refactorDecoded10).map(refactorElement9 => Lint.Wire(refactorElement9))
      )((refactorEncoded10: List[Lint.Wire]) => (refactorEncoded10).map(refactorElement10 => (refactorElement10).value))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val lintFix: PublicEndpoint[List[Lint.Wire], Api.Error, LintFixResult, Any] =
    (base.post
      .in("lint" / "fix")
      .in(sttp.tapir.query[List[String]]("rule"))
      .out(jsonBody[LintFixResult]))
      .mapIn[List[Lint.Wire]]((refactorDecoded11: List[String]) =>
        (refactorDecoded11).map(refactorElement11 => Lint.Wire(refactorElement11))
      )((refactorEncoded11: List[Lint.Wire]) => (refactorEncoded11).map(refactorElement12 => (refactorElement12).value))

  lazy val lintMarkdown: PublicEndpoint[FormatCheck, Api.Error, FormatReport, Any] =
    base.post.in("lint" / "markdown").in(jsonBody[FormatCheck]).out(jsonBody[FormatReport])

  /** Declared before the `{list}` routes so `items` is never read as a list name. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val queuePatch: PublicEndpoint[(Queue.Id, QueuePatch), Api.Error, QueueItem, Any] =
    (endpoint.patch
      .in("api" / "queue" / "items" / path[Int]("id"))
      .in(jsonBody[QueuePatch])
      .errorOut(Api.problemOut)
      .out(jsonBody[QueueItem]))
      .mapIn[(Queue.Id, QueuePatch)]((refactorDecoded12: (Int, QueuePatch)) =>
        (Queue.Id((refactorDecoded12)._1), (refactorDecoded12)._2)
      )((refactorEncoded12: (Queue.Id, QueuePatch)) => (((refactorEncoded12)._1).value, (refactorEncoded12)._2))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val queueDelete: PublicEndpoint[Queue.Id, Api.Error, QueueItem, Any] =
    (endpoint.delete
      .in("api" / "queue" / "items" / path[Int]("id"))
      .errorOut(Api.problemOut)
      .out(jsonBody[QueueItem]))
      .mapIn[Queue.Id]((refactorDecoded4: Int) => Queue.Id(refactorDecoded4))((refactorEncoded4: Queue.Id) =>
        (refactorEncoded4).value
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val queueList
      : PublicEndpoint[(Queue.Wire, Boolean, Option[Queue.Wire], Option[Queue.Wire]), Api.Error, QueueListing, Any] =
    (endpoint.get
      .in("api" / "queue" / path[String]("list"))
      .in(sttp.tapir.query[Boolean]("all").default(false))
      .in(sttp.tapir.query[Option[String]]("goal").description("only the items serving this research goal (its slug)"))
      .in(
        sttp.tapir
          .query[Option[String]]("job")
          .description("only the items this agent run or background job wrote or ticked off (its id)")
      )
      .errorOut(Api.problemOut)
      .out(jsonBody[QueueListing]))
      .mapIn[(Queue.Wire, Boolean, Option[Queue.Wire], Option[Queue.Wire])](
        (refactorDecoded5: (String, Boolean, Option[String], Option[String])) =>
          (
            Queue.Wire((refactorDecoded5)._1),
            ((refactorDecoded5)._2),
            ((refactorDecoded5)._3).map(refactorElement5 => Queue.Wire(refactorElement5)),
            ((refactorDecoded5)._4).map(refactorElement6 => Queue.Wire(refactorElement6))
          )
      )((refactorEncoded5: (Queue.Wire, Boolean, Option[Queue.Wire], Option[Queue.Wire])) =>
        (
          ((refactorEncoded5)._1).value,
          ((refactorEncoded5)._2),
          ((refactorEncoded5)._3).map(refactorElement7 => (refactorElement7).value),
          ((refactorEncoded5)._4).map(refactorElement8 => (refactorElement8).value)
        )
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val queueAdd: PublicEndpoint[(Queue.Wire, QueueAdd), Api.Error, QueueItem, Any] =
    (endpoint.post
      .in("api" / "queue" / path[String]("list"))
      .in(jsonBody[QueueAdd])
      .errorOut(Api.problemOut)
      .out(jsonBody[QueueItem]))
      .mapIn[(Queue.Wire, QueueAdd)]((refactorDecoded15: (String, QueueAdd)) =>
        (Queue.Wire((refactorDecoded15)._1), (refactorDecoded15)._2)
      )((refactorEncoded15: (Queue.Wire, QueueAdd)) => (((refactorEncoded15)._1).value, (refactorEncoded15)._2))

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val queueExport: PublicEndpoint[Queue.Wire, Api.Error, Queue.Export, Any] =
    (endpoint.get
      .in("api" / "queue" / path[String]("list") / "export")
      .errorOut(Api.problemOut)
      .out(stringBody))
      .mapIn[Queue.Wire]((refactorDecoded7: String) => Queue.Wire(refactorDecoded7))((refactorEncoded7: Queue.Wire) =>
        (refactorEncoded7).value
      )
      .mapOut[Queue.Export]((refactorDecoded8: String) => Queue.Export(refactorDecoded8))(
        (refactorEncoded8: Queue.Export) => (refactorEncoded8).value
      )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val queueImport: PublicEndpoint[(Queue.Wire, QueueImport), Api.Error, QueueImported, Any] =
    (endpoint.post
      .in("api" / "queue" / path[String]("list") / "import")
      .in(jsonBody[QueueImport])
      .errorOut(Api.problemOut)
      .out(jsonBody[QueueImported]))
      .mapIn[(Queue.Wire, QueueImport)]((refactorDecoded18: (String, QueueImport)) =>
        (Queue.Wire((refactorDecoded18)._1), (refactorDecoded18)._2)
      )((refactorEncoded18: (Queue.Wire, QueueImport)) => (((refactorEncoded18)._1).value, (refactorEncoded18)._2))

  /** Under `export/` rather than `goals/` so it is never read as a goal slug. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val goalsExport: PublicEndpoint[Unit, Api.Error, Goal.Export, Any] =
    (base.get
      .in("export" / "goals")
      .out(stringBody))
      .mapOut[Goal.Export]((refactorDecoded2: String) => Goal.Export(refactorDecoded2))(
        (refactorEncoded2: Goal.Export) => (refactorEncoded2).value
      )

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
