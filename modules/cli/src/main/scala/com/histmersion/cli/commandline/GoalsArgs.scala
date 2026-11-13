package com.histmersion
package cli
package commandline

import com.histmersion.api.exchange.Bundle
import com.histmersion.api.automation.GoalsMarkdown
import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.automation.Agents.RunRequest
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.{Agents, AgentsApi, AgentsMarkdown}
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.research.ReviewGoal
import com.histmersion.util.console.Console
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.{NonEmptyVector, Problem}
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[GoalsCmd]]. */
object GoalsArgs:
  object GoalsEntry extends CommandLine.Entry:
    val name = "goals"
    def help: String = "Research goals: list, get, update, plan, link, unlink, schedule, schedules, rename"
    def conf(): Subcommand = GroupGoals()

  final private class LeafListing() extends Leaf("histmersion goals", "list", "List research goals") with SharedFlags:
    private val status_ = value[String]("status", s"Filter: ${Goal.Status.all.mkString("|")}")
    private val limit_ = value[Int]("limit", "Max results")
    private val offset_ = value[Int]("offset", "Skip this many (the list's footer names the next offset)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(GoalsCmd.Command.Listing(status_.toOption, limit_.toOption, offset_.toOption, json_()))

  final private class LeafGet() extends Leaf("histmersion goals", "get", "Show one goal: sub-goals, links, schedules, approved plan") with SharedFlags:
    private val slug_ = arg[String]("slug")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(GoalsCmd.Command.Get(slug_(), json_()))

  final private class LeafUpdate() extends Leaf("histmersion goals", "update", "Update goal fields (patch)") with SharedFlags:
    private val slug_ = arg[String]("slug")
    private val title_ = value[String]("title", "New title")
    private val status_ = value[String]("status", s"New status: ${Goal.Status.all.mkString("|")}")
    private val notes_ = value[String]("notes", "Progress notes")
    private val budget_ = value[Double]("budget-usd", "Cost cap of the agent runs started for this goal (0 clears it)")
    private val from_ = value[Long]("from", "Signed year the goal's period begins (BCE negative, e.g. --from=-1200); alone, the end stays as set")
    private val to_ = value[Long]("to", "Signed year the goal's period ends (BCE negative); alone, the start stays as set")

    def command: Either[Problem, Cli.Command] = Right(GoalsCmd.Command.Update(slug_(), title_.toOption, status_.toOption, notes_.toOption, budget_.toOption, from_.toOption, to_.toOption))

  final private class LeafPlan() extends Leaf("histmersion goals", "plan", "Ask the server's planner for sub-goals, search queries and works (DOIs checked upstream); nothing is created until you approve the plan (histmersion agent approve)") with SharedFlags:
    private val slug_ = arg[String]("slug")
    private val model_ = modelFlag()
    private val budget_ = value[Double]("budget-usd", "This run's cost cap in USD (default: the goal's, then agents.budgets.run-usd)")
    private val fol_ = followFlag()

    def command: Either[Problem, Cli.Command] = Right(GoalsCmd.Command.Plan(slug_(), model_(), budget_.toOption, fol_()))

  /** One target, as `--source`, `--article`, `--query` or `--item`: exactly one of them. */
  final private class LeafLink()
      extends Leaf("histmersion goals", "link", "Link a goal to a source, an article, a search query or a queue item")
      with SharedFlags:
    private val slug_ = arg[String]("slug")
    private val source_ = value[String]("source", "A citekey")
    private val article_ = value[String]("article", "An article slug")
    private val query_ =
      value[String]("query", "A literature search query (what the goal's acquisition sweep searches for)")
    private val item_ = value[Int]("item", "A queue item id")
    private val note_ = value[String]("note", "Why it is linked")

    def command: Either[Problem, Cli.Command] =
      val given_ = List(
        source_.toOption.map(LinkKind.Source -> _),
        article_.toOption.map(LinkKind.Article -> _),
        query_.toOption.map(LinkKind.Query -> _),
        item_.toOption.map(i => LinkKind.QueueItem -> i.toString)
      ).flatten
      given_ match
        case List((kind, ref)) => Right(GoalsCmd.Command.Link(slug_(), kind, ref, note_.toOption))
        case _                 =>
          Left(
            Problem.Invalid.Inconsistent(
              NonEmptyVector.of(
                Problem.Field("--source"),
                Problem.Field("--article"),
                Problem.Field("--query"),
                Problem.Field("--item")
              ),
              Problem.Invalid.Exclusion.OneOf
            )
          )

  final private class LeafUnlink() extends Leaf("histmersion goals", "unlink", "Remove one link of a goal") with SharedFlags:
    private val slug_ = arg[String]("slug")
    private given ValueConverter[LinkKind.Wire] = reading(k =>
      LinkKind
        .parse(LinkKind.Wire(k))
        .map(_ => LinkKind.Wire(k))
        .toRight(s"kind is one of ${LinkKind.values.map(_.wire).mkString(", ")}")
    )
    private val kind_ = arg[LinkKind.Wire]("kind")
    private val ref_ = arg[String]("ref")

    def command: Either[Problem, Cli.Command] = Right(GoalsCmd.Command.Unlink(slug_(), kind_(), ref_()))

  /** `--cron … [--off] [--budget-usd …]` sets the schedule, `--remove` deletes it: one or the other. */
  final private class LeafSchedule()
      extends Leaf(
        "histmersion goals",
        "schedule",
        "Set (or --remove) a goal's currency or acquisition schedule: a sweep of its watcher watch:<goal> (created paused — " +
          "resume it with `agent manager watch:<goal> resume`; its one cron is shared by both sweeps); runs only while the server's agents are enabled"
      )
      with SharedFlags:
    private given ValueConverter[ScheduleKind.Wire] = reading(k =>
      ScheduleKind
        .parse(ScheduleKind.Wire(k))
        .map(_ => ScheduleKind.Wire(k))
        .toRight(s"kind is one of ${ScheduleKind.values.map(_.wire).mkString(", ")}")
    )
    private val slug_ = arg[String]("slug")
    private val kind_ = arg[ScheduleKind.Wire]("kind")
    private val remove_ = flag("remove", "Delete the schedule")
    private val cron_ = value[String](
      "cron",
      "Five fields in UTC (minute hour day-of-month month day-of-week), or @daily, @weekly, @monthly"
    )
    private val off_ = flag("off", "Take the sweep out of the watcher (its cron stays)")
    private val budget_ = value[Double]("budget-usd", "Cost cap of each run it starts")

    def command: Either[Problem, Cli.Command] =
      val remove = remove_.getOrElse(false)
      val off = off_.getOrElse(false)
      (remove, cron_.toOption) match
        case (true, None) if !off && budget_.isEmpty =>
          Right(GoalsCmd.Command.Schedule(slug_(), kind_(), GoalsCmd.ScheduleChange.Remove))
        case (false, Some(cron)) =>
          Right(GoalsCmd.Command.Schedule(slug_(), kind_(), GoalsCmd.ScheduleChange.Set(cron, off, budget_.toOption)))
        case (true, _) =>
          Left(
            Problem.Invalid.Inconsistent(
              NonEmptyVector.of(Problem.Field("--remove"), Problem.Field("--cron"), Problem.Field("--off"), Problem.Field("--budget-usd")),
              Problem.Invalid.Exclusion.AtMostOne
            )
          )
        case (false, None) =>
          Left(
            Problem.Invalid.Inconsistent(
              NonEmptyVector.of(Problem.Field("--remove"), Problem.Field("--cron")),
              Problem.Invalid.Exclusion.OneOf
            )
          )

  final private class LeafSchedules() extends Leaf("histmersion goals", "schedules", "Every goal's schedules, the next due first") with SharedFlags:
    def command: Either[Problem, Cli.Command] = Right(GoalsCmd.Command.Schedules())

  final private class GroupGoals() extends Group("histmersion", "goals", "Research goals: list, get, update, plan, link, unlink, schedule, schedules, rename"):
    holds(LeafListing(), LeafGet(), LeafUpdate(), LeafPlan(), LeafLink(), LeafUnlink(), LeafSchedule(), LeafSchedules(), LabelRenameArgs.leaf("histmersion goals", Bundle.Kind.Goal))
