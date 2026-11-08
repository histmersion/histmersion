package com.histmersion
package domain.library.search

import com.histmersion.api.json.NewtypeCodecs.given
import chimp.server.*
import com.histmersion.api.library.Query
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure

/** The read-only query's MCP tools: `query` (its description generated from the live schema) and `schema`. */
object QueryTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class QueryInput(sql: QueryInput.Sql) derives KindlingsCodecAsObject
  object QueryInput:
    type Sql = Sql.T
    object Sql extends Newtype[String, Pure]

    given Schema[QueryInput] = KindlingsSchema
      .derived[QueryInput]
      .schema
      .modify(_.sql)(_.description("One read-only SQL statement (Postgres refuses writes)"))

  final case class SchemaInput() derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  def apply(queries: QueryControllers) =
    val query =
      tool("query")
        .description(queries.toolDescription.value)
        .input[QueryInput]
        .handle { in =>
          queries.run(Query.QueryRequest(Query.QueryRequest.Sql(in.sql.value))) match
            case Right(r) => ToolResult.text(Query.markdown(r).value)
            // hand the live schema back with the error so the next attempt can be right
            case Left(e) =>
              ToolResult.text(s"${McpOut.refusal(e)}\n\n${Query.schemaMarkdown(queries.schemaTables)}")
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
