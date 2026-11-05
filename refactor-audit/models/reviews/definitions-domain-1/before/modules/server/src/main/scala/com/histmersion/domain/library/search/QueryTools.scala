package com.histmersion.domain.library.search

import chimp.server.*
import com.histmersion.api.library.Query
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The read-only query's MCP tools: `query` (its description generated from the live schema) and `schema`. */
object QueryTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class QueryInput(sql: String) derives KindlingsCodecAsObject
  object QueryInput:
    given Schema[QueryInput] = KindlingsSchema
      .derived[QueryInput]
      .schema
      .modify(_.sql)(_.description("One read-only SQL statement (Postgres refuses writes)"))

  final case class SchemaInput() derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  def apply(queries: QueryControllers) =
    val query =
      tool("query")
        .description(queries.toolDescription)
        .input[QueryInput]
        .handle { in =>
          queries.run(Query.QueryRequest(in.sql)) match
            case Right(r) => ToolResult.text(Query.markdown(r))
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
        .handle(_ => ToolResult.text(Query.schemaMarkdown(queries.schemaTables)))

    List(query, schema)
