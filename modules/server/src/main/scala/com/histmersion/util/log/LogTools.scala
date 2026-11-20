package com.histmersion
package util.log

import com.histmersion.util.mcp.ChimpJson.given
import com.histmersion.api.json.NewtypeCodecs.given

import chimp.server.*
import com.histmersion.api.Api
import com.histmersion.api.log.{LogEvent, LogMarkdown, LogPage}
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The structured log for agents: one `log` tool, the same filters as `GET /api/log`; with `problem` alone, what
  * happened around that problem (`GET /api/problems/{id}`).
  */
object LogTools:
  object Input:
    given Schema[LogPage.Filter] = KindlingsSchema
      .derived[LogPage.Filter]
      .schema
      .modify(_.trace)(_.description("one trace: every step of one request or job (a response's Histmersion-Trace)"))
      .modify(_.label)(_.description("the label a client gave its calls (Histmersion-Trace-Label)"))
      .modify(_.job)(_.description("what happened to this job (its id)"))
      .modify(_.run)(_.description("what happened to this agent run (its id)"))
      .modify(_.source)(_.description("what happened to this source (its citekey)"))
      .modify(_.problem)(
        _.description("a problem's id (the one an answer named): alone, every step of the trace it was raised in")
      )
      .modify(_.kind)(_.description(s"one of: ${LogEvent.Kinds.mkString(", ")}"))
      .modify(_.since)(_.description("from: an ISO instant, a date, or 7d / 12h / 30m ago"))
      .modify(_.until)(_.description("up to: an ISO instant or a date"))
      .modify(_.limit)(_.description(s"events per page (default ${LogPage.DefaultLimit}, at most ${LogPage.MaxLimit})"))
      .modify(_.before)(_.description("the cursor the last page gave, for the next older one"))
      .modify(_.from)(_.description("database (default; the file when the database cannot answer) or file"))

  def apply(log: LogControllers) =
    import Input.given
    List(
      tool("log")
        .description(
          "The structured log: what happened, step by step — answered requests, provider calls, problems and every " +
            "database write statement — each with the trace that links the steps of one request or job. Filter by " +
            "`trace`, `job`, `run`, `source` (citekey), `problem` (an id an answer named), `label`, `kind`, `since`, " +
            "`until`; newest first, `before` = the cursor for the next older page. Use to answer \"what happened to " +
            "job X / source Y / request Z\"."
        )
        .input[LogPage.Filter]
        .handle { in =>
          val alone = in.problem.filter(_ =>
            List(in.trace, in.label, in.job, in.run, in.source, in.kind, in.since, in.until).forall(_.isEmpty)
          )
          alone
            .fold(log.events(in))(p => log.problem(LogEvent.ProblemId(p.value)))
            .left
            .map(Api.Error(_))
            .fold(
              McpOut.error,
              p =>
                ToolResult.text(
                  LogMarkdown.page(p, c => s"_Older: call `log` again with the same filters and `before: \"$c\"`._")
                )
            )
        }
    )
