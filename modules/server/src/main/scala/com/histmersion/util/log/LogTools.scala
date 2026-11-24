package com.histmersion
package util.log

import com.histmersion.util.mcp.ChimpJson.given
import com.histmersion.api.json.NewtypeCodecs.given

import chimp.server.*
import com.histmersion.api.Api
import com.histmersion.api.log.{LogEvent, LogPage}
import com.histmersion.util.mcp.McpOut
import com.histmersion.lint.{LintRule, tolerated}

final class LogTools(log: LogControllers):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tools =
    List(
      tool("log")
        .description(
          "The structured log: what happened, step by step — answered requests, MCP tool calls, provider calls, " +
            "problems and every database write statement — each with the trace that links the steps of one request " +
            "or job. Filters: `trace` (an answer's Histmersion-Trace), `job`, `run` (an agent run's id), `source` " +
            "(citekey), `problem` (an id an answer named; alone: every step of the trace it was raised in), `label` " +
            s"(a client's Histmersion-Trace-Label), `kind` (one of ${LogEvent.Kinds.mkString(", ")}), `since` / " +
            "`until` (a date, an ISO instant, or 7d / 12h / 30m ago), `limit` " +
            s"(default ${LogPage.DefaultLimit}, at most ${LogPage.MaxLimit}), `before` (the cursor for the next " +
            "older page), `from` (database, the default, falling back to the file; or file). Use to answer \"what " +
            "happened to job X / source Y / request Z\"."
        )
        .input[LogPage.Filter]
        .handle { in =>
          in.problemAlone
            .fold(log.events(in))(log.problem)
            .left
            .map(Api.Error(_))
            .fold(
              McpOut.error,
              p =>
                ToolResult
                  .text(p.markdown(c => s"_Older: call `log` again with the same filters and `before: \"$c\"`._"))
            )
        }
    )
