package com.histmersion
package cli

import com.histmersion.api.Paging
import com.histmersion.api.log.{LogApi, LogMarkdown, LogPage}
import com.histmersion.util.console.Console

/** `histmersion log [--trace t] [--job j] [--run r] [--source ck] [--problem id] [--label l] [--kind k] [--since s]
  * [--until u] [--limit n] [--before c] [--file]` — the structured log: what happened to a request, a job, a source or
  * around a problem, step by step, newest first. `--problem` alone: every step of the trace that problem was raised in,
  * oldest first.
  */
object LogCmd:

  /** What `histmersion log` was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Log(filter: LogPage.Filter, json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out the command against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Log(filter, json) =>
          val opts = List(
            "trace" -> filter.trace.map(_.value),
            "label" -> filter.label.map(_.value),
            "job" -> filter.job.map(_.value),
            "run" -> filter.run.map(_.value),
            "source" -> filter.source.map(_.value),
            "problem" -> filter.problem.map(_.value),
            "kind" -> filter.kind.map(_.value),
            "since" -> filter.since.map(_.value),
            "until" -> filter.until.map(_.value),
            "limit" -> filter.limit.map(l => s"${l.value}")
          )
          filter.problemAlone
            .fold(c.call(LogApi.events)(filter))(p => c.call(LogApi.problem)(p))
            .map(p =>
              Output.emit(json, p)(
                LogMarkdown.page(_, cursor => s"Older: `${PageCmd.next("log", opts, Paging.Cursor(cursor.value))}`")
              )
            )
