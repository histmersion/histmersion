package com.histmersion
package domain.library.search
import com.histmersion.util.mcp.ChimpJson.given

import com.histmersion.api.json.NewtypeCodecs.given
import chimp.server.*
import com.histmersion.api.library.Query
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.lint.{tolerated, LintRule}

/** The read-only query's MCP tools: `query` (its description generated from the live schema) and `schema`. */
object QueryTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class QueryInput(sql: QueryInput.Sql) derives JsonCodec
  object QueryInput:
    type Sql = Sql.T
    object Sql extends Newtype[String, Pure]

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[QueryInput] = KindlingsSchema
      .derived[QueryInput]
      .schema
      .modify(_.sql)(_.description("One read-only SQL statement (Postgres refuses writes)"))

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class SchemaInput() derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(queries: QueryControllers) =
    val query =
      tool("query")
        .description(queries.toolDescription.value)
        .input[QueryInput]
        .handle { in =>
          queries.run(Query.QueryRequest(Query.QueryRequest.Sql(in.sql.value))).left.map(Api.Error(_)) match
            case Right(r) => ToolResult.text(Query.markdown(r).value)
            // hand the live schema back with the error so the next attempt can be right
            case Left(e) => McpOut.error(e, s"${Query.schemaMarkdown(queries.schemaTables)}")
        }

    val schema =
      tool("schema")
        .description(
          "List the tables, columns and types that `query` can read (from the live " +
            "information_schema). Use to check column names before writing SQL — not for " +
            "searching the corpus."
        )
        .input[SchemaInput]
        .handle(_ => ToolResult.text(Query.schemaMarkdown(queries.schemaTables).value))

    List(query, schema)
