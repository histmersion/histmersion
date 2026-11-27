package com.histmersion
package cli
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor

import com.histmersion.api.Api
import com.histmersion.api.automation.{Activity, ActivityMarkdown}
import com.histmersion.util.console.Console
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion activity [--kind k] [--running] [--since s] [--goal g] [--limit n] [--before c]` — Research › Activity:
  * agent runs, background jobs, worklog lines and a person's decisions, one list, newest first.
  */
@atBoundary(BoundaryKind.CliArgs, "parses `histmersion activity` arguments")
object ActivityCmd:

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Activity(
        kind: Option[String],
        running: Boolean,
        since: Option[String],
        goal: Option[String],
        limit: Option[Int],
        before: Option[String],
        json: Cli.JsonOutput
    )

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Activity(kind, running, since, goal, limit, before, json) =>
          val show = if running then Some(Activity.Show.Running.wire.value) else kind
          val opts = List("kind" -> show, "since" -> since, "goal" -> goal, "limit" -> limit.map(_.toString))
          c.call(Api.activity)(
            (
              show.map(Activity.Kind(_)),
              since.map(Activity.Actor(_)),
              goal.map(Activity.Subject(_)),
              limit.map(Activity.Limit(_)),
              before.map(Activity.Cursor(_))
            )
          ).map(p =>
            Output.emit(json, p)(
              ActivityMarkdown.page(_, cursor => s"Older: `${PageCmd.next("activity", opts, cursor)}`")
            )
          )
