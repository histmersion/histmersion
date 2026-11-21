package com.histmersion
package cli

import com.histmersion.api.log.{LogApi, LogPage}
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
          filter.problemAlone
            .fold(c.call(LogApi.events)(filter))(p => c.call(LogApi.problem)(p))
            .map(p =>
              Output.emit(json, p)(_.markdown(cursor => s"Older: `${PageCmd.next("log", options(filter), cursor)}`"))
            )

    /** The filters as the command line gives them (the cursor apart), each one only when set: the next page's command.
      */
    private def options(filter: LogPage.Filter): List[(String, Option[String])] = List(
      "trace" -> filter.trace.map(t => s"$t"),
      "label" -> filter.label.map(l => s"$l"),
      "job" -> filter.job.map(j => s"$j"),
      "run" -> filter.run.map(r => s"$r"),
      "source" -> filter.source.map(s => s"$s"),
      "problem" -> filter.problem.map(p => s"$p"),
      "kind" -> filter.kind.map(k => s"$k"),
      "since" -> filter.since.map(s => s"$s"),
      "until" -> filter.until.map(u => s"$u"),
      "limit" -> filter.limit.map(l => s"$l")
    )
