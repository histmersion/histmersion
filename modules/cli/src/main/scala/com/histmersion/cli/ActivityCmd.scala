package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.Api
import com.histmersion.api.automation.{Activity, ActivityMarkdown}

/** `histmersion activity [--kind k] [--running] [--since s] [--goal g] [--limit n] [--before c]` — Research › Activity:
  * agent runs, background jobs, worklog lines and a person's decisions, one list, newest first.
  */
object ActivityCmd:

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand(
      "activity",
      "What the agents, the server and you did, one list, newest first: agent runs, " +
        "background jobs, worklog lines and your decisions, each with what it touched"
    ) {
      (
        Opts
          .option[String](
            "kind",
            s"Only: ${Activity.Show.values.filter(_ != Activity.Show.Everything).map(_.wire).mkString(", ")}"
          )
          .orNone,
        Opts.flag("running", "Only what is running now (as --kind running)").orFalse,
        Opts.option[String]("since", "From: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago").orNone,
        Opts.option[String]("goal", "Only what concerns this research goal (its slug)").orNone,
        Opts.option[Int]("limit", s"Entries (default ${Activity.DefaultLimit}, at most ${Activity.MaxLimit})").orNone,
        Opts.option[String]("before", "The next older page: the cursor the last page printed").orNone,
        Output.jsonFlag
      ).mapN { (kind, running, since, goal, limit, before, json) => (c: Client) =>
        val show = if running then Some(Activity.Show.Running.wire) else kind
        val opts = List("kind" -> show, "since" -> since, "goal" -> goal, "limit" -> limit.map(_.toString))
        c.call(Api.activity)((show, since, goal, limit, before))
          .map(p =>
            Output.emit(json, p)(
              ActivityMarkdown.page(_, cursor => s"Older: `${PageCmd.next("activity", opts, cursor)}`")
            )
          )
      }
    }
