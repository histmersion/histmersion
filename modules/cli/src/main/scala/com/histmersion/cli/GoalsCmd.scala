package com.histmersion
package cli

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
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@atBoundary(BoundaryKind.CliArgs, "parses `histmersion goals` arguments")
object GoalsCmd:

  /** What `goals schedule` does to the schedule. */
  enum ScheduleChange:
    case Remove
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Set(cron: String, off: Boolean, budget: Option[Double])

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Listing(status: Option[String], limit: Option[Int], offset: Option[Int], json: Cli.JsonOutput)
    case Get(slug: String, json: Cli.JsonOutput)
    case Update(
        slug: String,
        title: Option[String],
        status: Option[String],
        notes: Option[String],
        budget: Option[Double],
        from: Option[Long],
        to: Option[Long]
    )
    @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
    case Plan(slug: String, model: Option[String], budget: Option[Double], fol: Boolean)
    case Link(slug: String, kind: LinkKind, ref: String, note: Option[String])
    case Unlink(slug: String, kind: LinkKind.Wire, ref: String)
    case Schedule(slug: String, kind: ScheduleKind.Wire, change: ScheduleChange)
    case Schedules()

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Listing(status, limit, offset, json) =>
          c.listGoals(
            Api.GoalQuery(
              status.map(Api.GoalQuery.Status(_)),
              limit.map(Api.GoalQuery.Limit(_)),
              offset.map(Api.GoalQuery.Offset(_))
            )
          ).map(r => Output.emit(json, r)(GoalsMarkdown.goalList))
        case Command.Get(slug, json) =>
          c.call(AgentsApi.goalView)(ReviewGoal.Slug(slug)).map(v => Output.emit(json, v)(AgentsMarkdown.goalView))
        case Command.Update(slug, title, status, notes, budget, from, to) =>
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
        case Command.Plan(slug, model, budget, fol) =>
          c.call(AgentsApi.startRun)(
            RunRequest(
              RunRequest.Pipeline(Agents.Pipeline.PlanGoal.wire.value),
              RunRequest.Target(slug),
              model.map(RunRequest.Model(_)),
              budget.map(RunRequest.BudgetUsd(_))
            )
          ).flatMap(r =>
            if fol then AgentCmd.follow(c, r.id, "") else Right(Cli.out.println(AgentsMarkdown.runLine(r)))
          )
        case Command.Unlink(slug, kind, ref) =>
          c.call(AgentsApi.removeGoalLink)((ReviewGoal.Slug(slug), kind, Citekey(ref)))
            .map(r => Cli.out.println(if r.removed.removed then "unlinked" else "there was no such link"))
        case Command.Schedules() =>
          c.call(AgentsApi.schedules)(()).map(ss => Cli.out.println(AgentsMarkdown.schedules(ss)))
        case Command.Link(slug, kind, ref, note) =>
          c.call(AgentsApi.addGoalLink)(
            (ReviewGoal.Slug(slug), LinkAdd(kind, LinkAdd.Ref(ref), note.map(LinkAdd.Note(_))))
          ).map(l => Cli.out.println(AgentsMarkdown.linkLine(l)))
        case Command.Schedule(slug, kind, ScheduleChange.Remove) =>
          c.call(AgentsApi.removeSchedule)((ReviewGoal.Slug(slug), kind))
            .map(r => Cli.out.println(if r.removed.removed then "removed" else "there was no such schedule"))
        case Command.Schedule(slug, kind, ScheduleChange.Set(cron, off, budget)) =>
          c.call(AgentsApi.setSchedule)(
            (
              ReviewGoal.Slug(slug),
              kind,
              ScheduleSet(
                ScheduleSet.Cron(cron),
                enabled = (if !off then ScheduleSet.Enabled.Enabled else ScheduleSet.Enabled.Disabled),
                budget.map(ScheduleSet.BudgetUsd(_))
              )
            )
          ).map(s => Cli.out.println(AgentsMarkdown.scheduleLine(s)))
