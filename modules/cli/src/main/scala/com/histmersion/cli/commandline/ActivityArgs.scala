package com.histmersion
package cli
package commandline

import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.api.Api
import com.histmersion.api.automation.{Activity, ActivityMarkdown}
import com.histmersion.util.console.Console
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[ActivityCmd]]. */
object ActivityArgs:
  object ActivityEntry extends CommandLine.Entry:
    val name = "activity"
    def help: String = "What the agents, the server and you did, one list, newest first: agent runs, " +
        "background jobs, worklog lines and your decisions, each with what it touched"
    def conf(): Subcommand = LeafActivity()

  final private class LeafActivity() extends Leaf("histmersion", "activity", "What the agents, the server and you did, one list, newest first: agent runs, " +
        "background jobs, worklog lines and your decisions, each with what it touched") with SharedFlags:
    private val kind_ = value[String]("kind", s"Only: ${Activity.Show.values.filter(_ != Activity.Show.Everything).map(_.wire).mkString(", ")}")
    private val running_ = flag("running", "Only what is running now (as --kind running)")
    private val since_ = value[String]("since", "From: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
    private val goal_ = value[String]("goal", "Only what concerns this research goal (its slug)")
    private val limit_ = value[Int]("limit", s"Entries (default ${Activity.DefaultLimit}, at most ${Activity.MaxLimit})")
    private val before_ = value[String]("before", "The next older page: the cursor the last page printed")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(ActivityCmd.Command.Activity(kind_.toOption, running_.getOrElse(false), since_.toOption, goal_.toOption, limit_.toOption, before_.toOption, json_()))
