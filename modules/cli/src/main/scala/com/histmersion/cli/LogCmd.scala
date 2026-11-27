package com.histmersion
package cli

import com.histmersion.api.log.{LogApi, LogPage}
import com.histmersion.util.console.Console
import com.histmersion.lint.{tolerated, LintRule}

object LogCmd:

  enum Command extends Cli.Command:
    case Log(filter: LogPage.Filter, json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Log(filter, json) =>
          filter.problemAlone
            .fold(c.call(LogApi.events)(filter))(p => c.call(LogApi.problem)(p))
            .map(p =>
              Output.emit(json, p)(_.markdown(cursor => s"Older: `${PageCmd.next("log", options(filter), cursor)}`"))
            )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
