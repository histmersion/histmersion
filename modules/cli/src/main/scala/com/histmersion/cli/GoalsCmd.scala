package com.histmersion.cli

import com.histmersion.api.automation.GoalsMarkdown

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.Api
import com.histmersion.config.*
import com.histmersion.api.automation.Agents.RunRequest
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.api.automation.{Agents, AgentsApi, AgentsMarkdown}
import com.histmersion.domain.automation.Goal
import com.histmersion.util.data.exchange.Bundle

object GoalsCmd:

  private val list = Opts.subcommand("list", "List research goals") {
    (
      Opts.option[String]("status", s"Filter: ${Goal.Status.all.mkString("|")}").orNone,
      Opts.option[Int]("limit", "Max results").orNone,
      Opts.option[Int]("offset", "Skip this many (the list's footer names the next offset)").orNone,
      Output.jsonFlag
    )
      .mapN { (status, limit, offset, json) => (c: Client) =>
        c.listGoals(Api.GoalQuery(status, limit, offset))
          .map(r => Output.emit(json, r)(GoalsMarkdown.goalList))
      }
  }

  private val get = Opts.subcommand("get", "Show one goal: sub-goals, links, schedules, approved plan") {
    (Opts.argument[String]("slug"), Output.jsonFlag).mapN { (slug, json) => (c: Client) =>
      c.call(AgentsApi.goalView)(slug).map(v => Output.emit(json, v)(AgentsMarkdown.goalView))
    }
  }

  private val update = Opts.subcommand("update", "Update goal fields (patch)") {
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
          slug,
          com.histmersion.api.automation.Goal.Patch(
            title = title,
            status = status,
            notes = notes,
            budgetUsd = budget,
            periodStart = from,
            periodEnd = to
          )
        ).map(g => println(GoalsMarkdown.goal(g)))
      }
  }

  private val plan = Opts.subcommand(
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
      c.call(AgentsApi.startRun)(RunRequest(Agents.Pipeline.PlanGoal.wire, slug, model, budget))
        .flatMap(r => if fol then AgentCmd.follow(c, r.id, "") else Right(println(AgentsMarkdown.runLine(r))))
    }
  }

  private val link = Opts.subcommand("link", "Link a goal to a source, an article, a search query or a queue item") {
    val target =
      Opts.option[String]("source", "A citekey").map(LinkKind.Source -> _) orElse
        Opts.option[String]("article", "An article slug").map(LinkKind.Article -> _) orElse
        Opts
          .option[String]("query", "A literature search query (what the goal's acquisition sweep searches for)")
          .map(LinkKind.Query -> _) orElse
        Opts.option[Int]("item", "A queue item id").map(i => LinkKind.QueueItem -> i.toString)
    (Opts.argument[String]("slug"), target, Opts.option[String]("note", "Why it is linked").orNone).mapN {
      (slug, t, note) => (c: Client) =>
        c.call(AgentsApi.addGoalLink)((slug, LinkAdd(t._1, t._2, note))).map(l => println(AgentsMarkdown.linkLine(l)))
    }
  }

  private val unlink = Opts.subcommand("unlink", "Remove one link of a goal") {
    (
      Opts.argument[String]("slug"),
      Opts
        .argument[String]("kind")
        .validate(s"kind is one of ${LinkKind.values.map(_.wire).mkString(", ")}")(k => LinkKind.parse(k).isDefined),
      Opts.argument[String]("ref")
    ).mapN { (slug, kind, ref) => (c: Client) =>
      c.call(AgentsApi.removeGoalLink)((slug, kind, ref))
        .map(r => println(if r.removed then "unlinked" else "there was no such link"))
    }
  }

  private val schedule = Opts.subcommand(
    "schedule",
    "Set (or --remove) a goal's currency or acquisition schedule: a sweep of its watcher watch:<goal> (created paused — " +
      "resume it with `agent manager watch:<goal> resume`; its one cron is shared by both sweeps); runs only while the server's agents are enabled"
  ) {
    val kinds = ScheduleKind.values.map(_.wire).mkString(", ")
    (
      Opts.argument[String]("slug"),
      Opts.argument[String]("kind").validate(s"kind is one of $kinds")(k => ScheduleKind.parse(k).isDefined),
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
            c.call(AgentsApi.removeSchedule)((slug, kind))
              .map(r => println(if r.removed then "removed" else "there was no such schedule"))
          case Some((cron, off, budget)) =>
            c.call(AgentsApi.setSchedule)((slug, kind, ScheduleSet(cron, enabled = !off, budget)))
              .map(s => println(AgentsMarkdown.scheduleLine(s)))
      }
  }

  private val schedules = Opts.subcommand("schedules", "Every goal's schedules, the next due first") {
    Opts.unit.map(_ => (c: Client) => c.call(AgentsApi.schedules)(()).map(ss => println(AgentsMarkdown.schedules(ss))))
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("goals", "Research goals: list, get, update, plan, link, unlink, schedule, schedules, rename") {
      list orElse get orElse update orElse plan orElse link orElse unlink orElse schedule orElse schedules orElse
        LabelRenameCmd(Bundle.Kind.Goal)
    }
