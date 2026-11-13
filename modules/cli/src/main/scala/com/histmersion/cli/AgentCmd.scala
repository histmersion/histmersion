package com.histmersion
package cli
import com.histmersion.api.automation.ListFilters
import com.histmersion.cli.AllOrFirst.traverse
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.{AgentRole, AgentsApi, AgentsMarkdown, ModelChains, PlanQuota}
import com.histmersion.api.research.Articles
import com.histmersion.util.console.Console
import com.histmersion.api.research.ReviewGoal
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.Run
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Goal
import com.histmersion.api.Paging
import com.histmersion.api.automation.Approval

/** `histmersion agent …` — the embedded research agents (docs/research-embedded-agents.md). The server runs them; this
  * only starts, follows and steers runs over HTTP. Off unless the SERVER has `HISTMERSION_AGENTS_ENABLED=true`.
  */
object AgentCmd:

  /** How often `--follow` polls. This process's own cadence, not a server tunable. */

  private val PollMs = 2000

  /** Poll a run until it is no longer queued or running; a failed or cancelled run exits 2. */
  @annotation.tailrec
  private[cli] def follow(c: Client, id: Agents.RunInfo.Id, last: String)(using
      console: Console
  ): Either[Cli.Error, Unit] =
    c.call(AgentsApi.run)(id) match
      case Left(e)  => Left(e)
      case Right(d) =>
        val line = AgentsMarkdown.runLine(d.run)
        if line != last then Cli.err.println(line)
        RunState.parse(Run.State.Wire(d.run.status.value)) match
          case Some(RunState.Queued) | Some(RunState.Running) =>
            Thread.sleep(PollMs.toLong)
            follow(c, id, line)
          case Some(RunState.Done) | Some(RunState.Paused) | Some(RunState.WaitingApproval) =>
            Cli.out.println(AgentsMarkdown.run(d, withMessages = false)); Right(())
          case _ =>
            Cli.out.println(AgentsMarkdown.run(d, withMessages = false))
            Left(
              Cli.Error(
                Problem.Unclassified(
                  Legacy.Text(s"agent run $id ${d.run.status}: ${d.run.error.map(_.sentence).getOrElse("")}")
                ),
                exitCode = ExitCode.Refused
              )
            )

  /** One decision per id, in the order given, each through the same endpoint (and checks) as a single one; a refused id
    * does not stop the rest, and makes the command fail at the end.
    */

  /** `agent quota floors <pool>`: the floors to save, or back to config. */
  enum QuotaFloorsChange derives CanEqual:
    case Set(floors: String)
    case Reset

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Allowance(target: String, limits: List[String], reserve: Int, remove: Boolean)
    case Generate(rts: List[String], out: Option[String])
    case Doctor(offline: Boolean)
    case Probe(ck: String, model: Option[String])
    case Runs(
        f: ListFilters.RunFilter,
        limit: Option[Int],
        before: Option[String],
        json: Cli.JsonOutput
    )
    case Show(id: String, msgs: Boolean, fol: Boolean)
    case Compare(ids: List[String], json: Cli.JsonOutput)
    case Approvals(
        f: ListFilters.ApprovalFilter,
        limit: Option[Int],
        before: Option[String],
        json: Cli.JsonOutput
    )
    case Decide(d: Decision, ids: List[Long], note: Option[String])
    case ModelsShow()
    case ModelsSet(role: String, models: List[String])
    case ModelsReset(role: String)
    case QuotaShow()
    case QuotaHold(pool: String)
    case QuotaRelease(pool: String)
    case QuotaFloors(pool: String, floors: QuotaFloorsChange)
    case QuotaFallthrough(role: String, on: Boolean)
    case QuotaRefresh(pool: Option[String])
    case Control(action: Agents.Action, id: String, budgetUsd: Option[Double], budgetTokens: Option[Long])
    case Run(
        pipeline: String,
        target: String,
        model: Option[String],
        budgetUsd: Option[Double],
        budgetTokens: Option[Long],
        fix: Boolean,
        rounds: Option[Int],
        item: Option[Int],
        title: Option[String],
        kind: Option[String],
        query: Option[String],
        topics: List[String],
        tags: List[String],
        goal: Option[String],
        dryRun: Boolean,
        modelFor: List[String],
        follow: Boolean
    )

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Allowance(target, limits, reserve, remove) =>
          if remove then
            if limits.nonEmpty then
              Left(Cli.Error(Problem.Unclassified(Legacy.Text("--remove takes no --limit")), exitCode = ExitCode.Usage))
            else
              c.call(AgentsApi.removeAllowance)(ReviewGoal.Slug(target))
                .map(r =>
                  Cli.out.println(if r.removed.value then s"The saved allowance of $target is removed."
                  else s"No saved allowance for $target.")
                )
          else
            limits
              .traverse(l => ModelChains.Limit.parse(ModelChains.Limit.Spec(l)))
              .left
              .map(m => Cli.Error(m, exitCode = ExitCode.Usage))
              .flatMap { ls =>
                c.call(AgentsApi.setAllowance)(
                  ModelChains.AllowanceSet(
                    ModelChains.AllowanceSet.Target(target),
                    ls.map(_.spec),
                    ModelChains.AllowanceSet.ReservePct(reserve)
                  )
                ).map(a => Cli.out.println(AgentsMarkdown.allowance(a)))
              }
        case Command.Generate(rts, out) =>
          c.call(AgentsApi.generate)(
            GenerateRequest(rts.map(GenerateRequest.Runtimes(_)), out.map(GenerateRequest.Out(_)))
          ).map(r => Cli.out.println(AgentsMarkdown.generated(r)))
        case Command.Doctor(offline) =>
          c.call(AgentsApi.doctor)(Some(DoctorReport.Online(offline)))
            .map(d => Cli.out.println(AgentsMarkdown.doctor(d)))
        case Command.Probe(ck, model) =>
          c.call(AgentsApi.probe)(ProbeRequest(Citekey(ck), model.map(ProbeRequest.Model(_))))
            .map(p => Cli.out.println(AgentsMarkdown.probe(p)))
        case Command.Runs(f, limit, before, json) =>
          c.call(AgentsApi.runs)(
            (f, limit.map(Paging.Limit(_)), before.map(RunInfo.Status(_)), None)
          ).map { (rs, page) =>
            Output.emit(json, rs)(rs =>
              AgentsMarkdown.runs(
                rs,
                page,
                f.qualifier.map(q => RunInfo.Status(q.value)),
                PageCmd
                  .older(
                    page,
                    "agent runs",
                    f.options.map((k, v) => (k.value, v.map(_.value))) ++ List("limit" -> limit.map(_.toString))
                  )
                  .map(t => Paging.Cursor(t.value))
              )
            )
          }
        case Command.Show(id, msgs, fol) =>
          if fol then follow(c, RunInfo.Id(id), "")
          else c.call(AgentsApi.run)(RunInfo.Id(id)).map(d => Cli.out.println(AgentsMarkdown.run(d, msgs)))
        case Command.Compare(ids, json) =>
          c.call(AgentsApi.compare)(ids.toList.map(RunInfo.Id(_)))
            .map(cmp => Output.emit(json, cmp)(AgentsMarkdown.compare))
        case Command.Approvals(f, limit, before, json) =>
          c.call(AgentsApi.approvals)(
            (
              f,
              limit.map(Paging.Limit(_)),
              before.map(Approval.State.Wire(_)),
              None
            )
          ).map { (as, page) =>
            Output.emit(json, as)(as =>
              AgentsMarkdown.approvals(
                as,
                page,
                f.qualifier.map(q => Approval.State.Wire(q.value)),
                PageCmd
                  .older(
                    page,
                    "agent approvals",
                    f.options.map((k, v) => (k.value, v.map(_.value))) ++ List("limit" -> limit.map(_.toString))
                  )
                  .map(t => Paging.Cursor(t.value))
              )
            )
          }
        case Command.Decide(d, ids, note) =>
          val failed = ids.toList.flatMap { id =>
            c.call(AgentsApi.decide)(
              (ApprovalInfo.Id(id), d.wire, DecisionRequest(note.map(DecisionRequest.Note(_))))
            ) match
              case Right(a) => Cli.out.println(AgentsMarkdown.approvalLine(a)); None
              case Left(e)  => Cli.err.println(s"#$id: ${e.message.sentence}"); Some(e)
          }
          failed match
            case Nil        => Right(())
            case List(one)  => Left(one)
            case first :: _ =>
              Left(
                first
                  .modify(_.message)
                  .setTo(
                    Problem.Unclassified(
                      Legacy.Text(s"${failed.size} of ${ids.size} not decided — ${first.message.sentence}")
                    )
                  )
              )
        case Command.ModelsShow() => c.call(AgentsApi.models)(()).map(m => Cli.out.println(AgentsMarkdown.models(m)))
        case Command.ModelsSet(role, ms) =>
          c.call(AgentsApi.setChain)(
            (AgentRole.Wire(role), ModelChains.ChainSet(ms.map(ModelChains.ChainSet.Models(_))))
          ).map(r => Cli.out.println(AgentsMarkdown.roleChain(r)))
        case Command.ModelsReset(role) =>
          c.call(AgentsApi.resetChain)(AgentRole.Wire(role)).map(r => Cli.out.println(AgentsMarkdown.roleChain(r)))
        case Command.QuotaShow()  => c.call(AgentsApi.quota)(()).map(q => Cli.out.println(AgentsMarkdown.quota(q)))
        case Command.QuotaHold(p) =>
          c.call(AgentsApi.setPool)(
            (PlanQuota.PoolIdText(p), PlanQuota.PoolSet(hold = Some(PlanQuota.PoolSet.Hold(true))))
          ).map(x => Cli.out.println(AgentsMarkdown.pool(x)))
        case Command.QuotaRelease(p) =>
          c.call(AgentsApi.setPool)(
            (PlanQuota.PoolIdText(p), PlanQuota.PoolSet(hold = Some(PlanQuota.PoolSet.Hold(false))))
          ).map(x => Cli.out.println(AgentsMarkdown.pool(x)))
        case Command.QuotaFloors(p, change) =>
          val floors = change match
            case QuotaFloorsChange.Set(f) => f
            case QuotaFloorsChange.Reset  => ""
          c.call(AgentsApi.setPool)(
            (PlanQuota.PoolIdText(p), PlanQuota.PoolSet(floors = Some(PlanQuota.PoolSet.Floors(floors))))
          ).map(x => Cli.out.println(AgentsMarkdown.pool(x)))
        case Command.QuotaFallthrough(role, on) =>
          c.call(AgentsApi.setFallthrough)(
            (PlanQuota.PoolIdText(role), PlanQuota.FallthroughSet(PlanQuota.FallthroughSet.Fallthrough(on)))
          ).map(q => Cli.out.println(AgentsMarkdown.quota(q)))
        case Command.QuotaRefresh(p) =>
          c.call(AgentsApi.refreshQuota)(PlanQuota.QuotaRefresh(p.map(PlanQuota.QuotaRefresh.Pool(_))))
            .map(r => Cli.out.println(AgentsMarkdown.refreshed(r)))
        case Command.Control(action, id, usd, tokens) =>
          c.call(AgentsApi.control)(
            (RunInfo.Id(id), action, usd.map(RunRequest.BudgetUsd(_)), tokens.map(RunRequest.BudgetTokens(_)))
          ).map(r => Cli.out.println(AgentsMarkdown.runLine(r)))
        case r: Command.Run =>
          r.modelFor
            .traverse(m => ModelOverride.parse(ModelOverride.Wire(m)))
            .left
            .map(m =>
              Cli.Error(Problem.Unclassified(Legacy.Text(s"--model-for ${m.sentence}")), exitCode = ExitCode.Usage)
            )
            .flatMap { over =>
              c.call(AgentsApi.startRun)(
                RunRequest(
                  RunRequest.Pipeline(r.pipeline),
                  RunRequest.Target(r.target),
                  r.model.map(RunRequest.Model(_)),
                  r.budgetUsd.map(RunRequest.BudgetUsd(_)),
                  r.budgetTokens.map(RunRequest.BudgetTokens(_)),
                  Option.when(r.fix)(RunRequest.Fix(true)),
                  r.rounds.map(RunRequest.Rounds(_)),
                  r.item.map(Queue.QueueItem.Id(_)),
                  r.title.map(RunRequest.Title(_)),
                  r.kind.map(RunRequest.Kind(_)),
                  r.query.map(RunRequest.Query(_)),
                  r.topics.map(RunRequest.Topics(_)),
                  r.tags.map(RunRequest.Tags(_)),
                  r.goal.map(Goal.Slug(_)),
                  Option.when(r.dryRun)(RunRequest.DryRun(true)),
                  over
                )
              ).flatMap(x =>
                if r.follow then follow(c, x.id, "") else Right(Cli.out.println(AgentsMarkdown.runLine(x)))
              )
            }
