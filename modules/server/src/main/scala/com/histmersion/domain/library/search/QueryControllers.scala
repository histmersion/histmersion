package com.histmersion.domain.library.search

import com.histmersion.api.*
import com.histmersion.api.Query.*
import com.histmersion.config.AppConfig

/** `POST /api/query` and `GET /api/schema/tables` (MCP `query`, `schema`). */
final class QueryControllers(queries: QueryService, cfg: () => AppConfig):

  def run(req: QueryRequest): Either[ApiError, QueryResult] =
    val q = cfg().query
    queries.run(req.sql, q.timeoutMs, q.maxRows, q.maxCellChars).left.map(m => ApiError(m))

  def schemaTables: SchemaTables = SchemaTables(queries.tables)

  /** The `query` tool's description, generated from the live schema. Falls back to a schema-less description if the
    * database cannot be read while the tool list is being built — a server that cannot answer `information_schema` has
    * bigger problems than a thin tool description.
    */
  def toolDescription: String =
    Query.toolDescription(scala.util.Try(schemaTables).getOrElse(SchemaTables(Nil)))
