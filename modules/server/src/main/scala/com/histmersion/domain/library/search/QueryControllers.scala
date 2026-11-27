package com.histmersion
package domain.library.search

import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*
import com.histmersion.api.library.Query.*
import com.histmersion.config.AppConfig
import com.histmersion.api.library.Query
import com.histmersion.api.Api
import com.histmersion.util.database.Transactor
import com.histmersion.util.problem.Problems
import com.histmersion.lint.{tolerated, LintRule}

/** `POST /api/query` and `GET /api/schema/tables` (MCP `query`, `schema`). */
final class QueryControllers(queries: QueryService, cfg: () => AppConfig):

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def run(req: QueryRequest): Either[Problems, QueryResult] =
    val q = cfg().query
    queries
      .run(
        QueryRepo.Statement(req.sql.value),
        Transactor.TimeoutMs(q.timeoutMs.value),
        QueryRepo.MaxRows(q.maxRows.value),
        QueryRepo.MaxCellChars(q.maxCellChars.value)
      )

  def schemaTables: SchemaTables = SchemaTables(queries.tables)

  /** The `query` tool's description, generated from the live schema. Falls back to a schema-less description if the
    * database cannot be read while the tool list is being built — a server that cannot answer `information_schema` has
    * bigger problems than a thin tool description.
    */
  def toolDescription: Query.ToolDescription =
    Query.toolDescription(scala.util.Try(schemaTables).getOrElse(SchemaTables(Nil)))
