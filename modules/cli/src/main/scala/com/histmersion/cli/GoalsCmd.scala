package com.histmersion
package cli

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.automation.GoalsMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
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

object GoalsCmd:

  private def list(using console: Console) = Opts.subcommand("list", "List research goals") {
    (
      Opts.option[String]("status", s"Filter: ${Goal.Status.all.mkString("|")}").orNone,
      Opts.option[Int]("limit", "Max results").orNone,
      Opts.option[Int]("offset", "Skip this many (the list's footer names the next offset)").orNone,
      Output.jsonFlag
    )
      .mapN { (status, limit, offset, json) => (c: Client) =>
        c.listGoals(
          Api.GoalQuery(
            status.map(Api.GoalQuery.Status(_)),
            limit.map(Api.GoalQuery.Limit(_)),
            offset.map(Api.GoalQuery.Offset(_))
          )
        ).map(r => Output.emit(json, r)(GoalsMarkdown.goalList))
      }
  }

  private def get(using console: Console) =
    Opts.subcommand("get", "Show one goal: sub-goals, links, schedules, approved plan") {
      (Opts.argument[String]("slug"), Output.jsonFlag).mapN { (slug, json) => (c: Client) =>
        c.call(AgentsApi.goalView)(ReviewGoal.Slug(slug)).map(v => Output.emit(json, v)(AgentsMarkdown.goalView))
      }
    }

  private def update(using console: Console) = Opts.subcommand("update", "Update goal fields (patch)") {
    (
      Opts.argument[String]("slug"),
      Opts.option[String]("title", "New title").orNone,
      Opts.option[String]("status", s"New status: ${Goal.Status.all.mkString("|")}").orNone,
      Opts.option[String]("notes", "Progress notes").orNone,
      Opts.option[Double]("budget-usd", "Cost cap of the agent runs started for this goal (0 clears it)").orNone,
      Opts
        .option[Long](
          "from",
          "Signed year the goal's period begins (BCE negative, e.g. --from=-1200); alone, the end stays as set"
        )
        .orNone,
      Opts.option[Long]("to", "Signed year the goal's period ends (BCE negative); alone, the start stays as set").orNone
    )
      .mapN { (slug, title, status, notes, budget, from, to) => (c: Client) =>
        c.patchGoal(
          Goal.Uid(slug),
          Goal.Patch(
            title = title.map(Goal.Title(_)),
            status = status.map(Goal.StatusValue(_)),
            notes = notes.map(Goal.Notes(_)),
            budgetUsd = budget.map(Goal.Patch.BudgetUsd(_)),
            periodStart = from.map(Year(_)),
            periodEnd = to.map(Year(_))
          )
        ).map(g => Cli.out.println(GoalsMarkdown.goal(g)))
      }
  }

  private def plan(using console: Console) = Opts.subcommand(
    "plan",
    "Ask the server's planner for sub-goals, search queries and works (DOIs checked upstream); nothing is created until you approve the plan (histmersion agent approve)"
  ) {
    (
      Opts.argument[String]("slug"),
      AgentCmd.modelOpt,
      Opts
        .option[Double]("budget-usd", "This run's cost cap in USD (default: the goal's, then agents.budgets.run-usd)")
        .orNone,
      AgentCmd.followOpt
    ).mapN { (slug, model, budget, fol) => (c: Client) =>
      c.call(AgentsApi.startRun)(
        RunRequest(
          RunRequest.Pipeline(Agents.Pipeline.PlanGoal.wire.value),
          RunRequest.Target(slug),
          model.map(RunRequest.Model(_)),
          budget.map(RunRequest.BudgetUsd(_))
        )
      ).flatMap(r => if fol then AgentCmd.follow(c, r.id, "") else Right(Cli.out.println(AgentsMarkdown.runLine(r))))
    }
  }

  private def link(using console: Console) =
    Opts.subcommand("link", "Link a goal to a source, an article, a search query or a queue item") {
      val target =
        Opts.option[String]("source", "A citekey").map(LinkKind.Source -> _) orElse
          Opts.option[String]("article", "An article slug").map(LinkKind.Article -> _) orElse
          Opts
            .option[String]("query", "A literature search query (what the goal's acquisition sweep searches for)")
            .map(LinkKind.Query -> _) orElse
          Opts.option[Int]("item", "A queue item id").map(i => LinkKind.QueueItem -> i.toString)
      (Opts.argument[String]("slug"), target, Opts.option[String]("note", "Why it is linked").orNone).mapN {
        (slug, t, note) => (c: Client) =>
          c.call(AgentsApi.addGoalLink)(
            (ReviewGoal.Slug(slug), LinkAdd(t._1, LinkAdd.Ref(t._2), note.map(LinkAdd.Note(_))))
          ).map(l => Cli.out.println(AgentsMarkdown.linkLine(l)))
      }
    }

  private def unlink(using console: Console) = Opts.subcommand("unlink", "Remove one link of a goal") {
    (
      Opts.argument[String]("slug"),
      Opts
        .argument[String]("kind")
        .validate(s"kind is one of ${LinkKind.values.map(_.wire).mkString(", ")}")(k =>
          LinkKind.parse(LinkKind.Wire(k)).isDefined
        ),
      Opts.argument[String]("ref")
    ).mapN { (slug, kind, ref) => (c: Client) =>
      c.call(AgentsApi.removeGoalLink)((ReviewGoal.Slug(slug), LinkKind.Wire(kind), Citekey(ref)))
        .map(r => Cli.out.println(if r.removed.value then "unlinked" else "there was no such link"))
    }
  }

  private def schedule(using console: Console) = Opts.subcommand(
    "schedule",
    "Set (or --remove) a goal's currency or acquisition schedule: a sweep of its watcher watch:<goal> (created paused — " +
      "resume it with `agent manager watch:<goal> resume`; its one cron is shared by both sweeps); runs only while the server's agents are enabled"
  ) {
    val kinds = ScheduleKind.values.map(_.wire).mkString(", ")
    (
      Opts.argument[String]("slug"),
      Opts
        .argument[String]("kind")
        .validate(s"kind is one of $kinds")(k => ScheduleKind.parse(ScheduleKind.Wire(k)).isDefined),
      (Opts.flag("remove", "Delete the schedule").map(_ => None) orElse
        (
          Opts.option[String](
            "cron",
            "Five fields in UTC (minute hour day-of-month month day-of-week), or @daily, @weekly, @monthly"
          ),
          Opts.flag("off", "Take the sweep out of the watcher (its cron stays)").orFalse,
          Opts.option[Double]("budget-usd", "Cost cap of each run it starts").orNone
        ).tupled.map(Some(_)))
    )
      .mapN { (slug, kind, set) => (c: Client) =>
        set match
          case None =>
            c.call(AgentsApi.removeSchedule)((ReviewGoal.Slug(slug), ScheduleKind.Wire(kind)))
              .map(r => Cli.out.println(if r.removed.value then "removed" else "there was no such schedule"))
          case Some((cron, off, budget)) =>
            c.call(AgentsApi.setSchedule)(
              (
                ReviewGoal.Slug(slug),
                ScheduleKind.Wire(kind),
                ScheduleSet(
                  ScheduleSet.Cron(cron),
                  enabled = ScheduleSet.Enabled(!off),
                  budget.map(ScheduleSet.BudgetUsd(_))
                )
              )
            ).map(s => Cli.out.println(AgentsMarkdown.scheduleLine(s)))
      }
  }

  private def schedules(using console: Console) =
    Opts.subcommand("schedules", "Every goal's schedules, the next due first") {
      Opts.unit.map(_ =>
        (c: Client) => c.call(AgentsApi.schedules)(()).map(ss => Cli.out.println(AgentsMarkdown.schedules(ss)))
      )
    }

  def command(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand("goals", "Research goals: list, get, update, plan, link, unlink, schedule, schedules, rename") {
      list orElse get orElse update orElse plan orElse link orElse unlink orElse schedule orElse schedules orElse
        LabelRenameCmd(Bundle.Kind.Goal)
    }
