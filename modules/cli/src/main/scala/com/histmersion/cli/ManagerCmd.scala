package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.config.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.{
  AgentMode,
  AgentScheduler,
  AgentSchedulerMarkdown,
  ApprovalDigest,
  ApprovalDigestMarkdown,
  Autopilot,
  AutopilotMarkdown,
  ManagersApi,
  ManagersMarkdown,
  StallsMarkdown
}
import com.histmersion.api.automation.{Goal, ItemStatus}
import com.histmersion.api.library.RepairAcquire
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.views.{AutopilotApi, AutopilotStatus}
import com.histmersion.util.console.Console
import com.histmersion.api.library.AcquisitionWorkApi
import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.Stalls
import com.histmersion.api.automation.Managers

/** `histmersion agent managers` and `histmersion agent manager <id> …` — the persistent process managers
  * (docs/research-process-managers.md). The server runs them; this reads and steers them over HTTP.
  */
object ManagerCmd:

  private def config(kvs: List[String]): Either[Cli.Error, ManagerConfig] =
    ManagerConfig
      .parse(kvs.map(ManagerConfig.Spec(_)))
      .left
      .map(m => Cli.Error(m.toNonEmpty.head, exitCode = ExitCode.Usage))

  /** The settings `--config` and `config` take. */
  val SettingKeys: String =
    "max-runs, rounds, max-runs-per-article, no-progress-runs, reopen-limit, cooldown-minutes, " +
      "low-water, refill-batch, max-failures, budget-usd, tags, goal, models, stale-after-days, infer-filter, min-sources, " +
      "max-articles, sweeps, auto-ingest, auto-ingest-per-day, daily-usd (metered spend; 0 = none), auto-approve (its runs' " +
      "plans and drafts decided by code checks), extend-min-new"

  /** `histmersion agent enqueue <review|extend|enrich|seed> <target>` — what the page buttons do. */

  /** `histmersion agent autopilot [status|on|off]` — the one switch (docs/research-process-managers.md "As built: the
    * autopilot").
    */

  /** `histmersion agent scheduler [status|queue|why <item>|strategy <name|default>]` — the central scheduler
    * (docs/research-goal-advancement.md "As built").
    */

  /** `histmersion agent stalls [--days n]` — the goals with no progress, why, and what unblocks them. */

  /** `histmersion agent digest` — everything waiting for a person, by kind (#3762 proposal 3). */

  /** `histmersion agent decide-all <approve|reject> --kind k | --subject s | --id n…` — a group at once. */

  /** `focus` [list | pin <goal> | unpin <goal> | priority <goal> <n> | max <n|default>] → the request (None: just show
    * it); a malformed one is a usage error before anything is sent.
    */
  def focusRequest(args: List[String]): Either[Cli.Error, Option[Autopilot.FocusSet]] =
    def usage(m: String) = Left(Cli.Error(Problem.Unclassified(Legacy.Text(m)), exitCode = ExitCode.Usage))
    def num(s: String) =
      s.trim.toIntOption.toRight(
        Cli.Error(Problem.Unclassified(Legacy.Text(s"'$s' is not a whole number")), exitCode = ExitCode.Usage)
      )
    def by = Some(Autopilot.FocusSet.By("cli"))
    args.map(_.trim) match
      case Nil | List("list")             => Right(None)
      case List(v @ ("pin" | "unpin"), g) =>
        Right(Some(Autopilot.FocusSet(Autopilot.FocusSet.Action(v), Some(Goal.Slug(g)), by = by)))
      case List("priority", g, n) =>
        num(n).map(p =>
          Some(
            Autopilot.FocusSet(
              Autopilot.FocusSet.Action("priority"),
              Some(Goal.Slug(g)),
              Some(Autopilot.FocusSet.Value(p)),
              by
            )
          )
        )
      case List("max", "default") => Right(Some(Autopilot.FocusSet(Autopilot.FocusSet.Action("max"), by = by)))
      case List("max", n)         =>
        num(n).flatMap(m =>
          if m < 0 then usage("focus max takes 0 (no cap) or more, or `default`")
          else
            Right(
              Some(
                Autopilot.FocusSet(Autopilot.FocusSet.Action("max"), value = Some(Autopilot.FocusSet.Value(m)), by = by)
              )
            )
        )
      case other =>
        usage(
          s"focus ${other.mkString(" ")}: use focus [list | pin <goal> | unpin <goal> | priority <goal> <n> | max <n|default>]"
        )

  private def itemPatch(c: Client, id: ManagerInfo.Id, t: ManagerItem.Ref, p: ItemPatch)(using console: Console) =
    c.call(ManagersApi.patchItem)((id, t, p)).map(i => Cli.out.println(ManagersMarkdown.item(i)))

  /** What `agent autopilot` was asked to do. */
  enum AutopilotVerb derives CanEqual:
    case Status
    case On
    case Off
    case Drain
    case Undrain
    case RepairAcquire(dryRun: Boolean)
    case Focus(request: Option[Autopilot.FocusSet])
    case Mode(mode: String)

  /** What `agent manager <id> …` was asked to do. */
  enum ManagerVerb derives CanEqual:
    case Show
    case Status(json: Cli.JsonOutput)
    case Items(status: Option[String], offset: Option[Int], limit: Option[Int], json: Cli.JsonOutput)
    case Events(before: Option[Long], limit: Option[Int])
    case Pause(runs: Boolean)
    case Resume
    case Stop
    case Hint(text: String, item: Option[String], once: Boolean, suggest: Boolean)
    case Enqueue(kind: String, target: String, priority: Option[Int])
    case Hold(target: String, until: Option[String], untilSource: Option[String], reason: Option[String])
    case Release(target: String)
    case Skip(target: String, reason: Option[String])
    case Reopen(target: String)
    case Priority(target: String, priority: Int)
    case Cron(cron: String)
    case Config(settings: List[String])

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Managers(json: Cli.JsonOutput)
    case Enqueue(kind: String, t: String, p: Option[Int], json: Cli.JsonOutput)
    case Stalls(days: Option[Int], json: Cli.JsonOutput)
    case Digest(json: Cli.JsonOutput)
    case Create(
        kind: String,
        goal: Option[String],
        config: List[String],
        cron: Option[String],
        active: Boolean,
        json: Cli.JsonOutput
    )
    case Autopilot(verb: AutopilotVerb, json: Cli.JsonOutput)
    case SchedulerStatus(limit: Option[Int], json: Cli.JsonOutput)
    case SchedulerQueue(limit: Option[Int], json: Cli.JsonOutput)
    case SchedulerWhy(item: String, json: Cli.JsonOutput)
    case SchedulerStrategy(name: String, json: Cli.JsonOutput)
    case Manager(id: String, verb: ManagerVerb)
    case DecideAll(
        d: String,
        kind: Option[String],
        subject: Option[String],
        ids: List[Long],
        note: Option[String],
        limit: Option[Int],
        dry: Boolean,
        json: Cli.JsonOutput
    )

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Managers(json) =>
          c.call(ManagersApi.list)(()).map(ms => Output.emit(json, ms)(ManagersMarkdown.list))
        case Command.Enqueue(kind, t, p, json) =>
          c.call(ManagersApi.enqueue)(
            EnqueueRequest(EnqueueRequest.Kind(kind), EnqueueRequest.Target(t), p.map(EnqueueRequest.Priority(_)))
          ).map(r => Output.emit(json, r)(ManagersMarkdown.enqueued))
        case Command.Stalls(days, json) =>
          c.call(ManagersApi.stalls)(days.map(Stalls.Limit(_))).map(r => Output.emit(json, r)(StallsMarkdown.report))
        case Command.Digest(json) =>
          c.call(ManagersApi.digest)(()).map(d => Output.emit(json, d)(ApprovalDigestMarkdown.digest))
        case Command.DecideAll(d, kind, subject, ids, note, limit, dry, json) =>
          c.call(ManagersApi.decideAll)(
            ApprovalDigest.BulkDecide(
              ApprovalDigest.BulkDecide.Decision(d),
              kind.map(ApprovalDigest.BulkDecide.Kind(_)),
              subject.map(ApprovalDigest.BulkDecide.Subject(_)),
              ids.map(ApprovalDigest.Open.Id(_)),
              note.map(ApprovalDigest.BulkDecide.Note(_)),
              ApprovalDigest.BulkDecide.DryRun(dry),
              limit.map(ApprovalDigest.BulkDecide.Limit(_)),
              Some(ApprovalDigest.BulkDecide.By("cli"))
            )
          ).map(r => Output.emit(json, r)(ApprovalDigestMarkdown.bulk))
        case Command.Create(kind, goal, kvs, cron, active, json) =>
          config(kvs)
            .flatMap(cfg =>
              c.call(ManagersApi.create)(
                ManagerCreate(
                  ManagerCreate.Kind(kind),
                  goal.map(Goal.Slug(_)),
                  cfg,
                  Some(ManagerCreate.Paused(!active)),
                  cron.map(ManagerCreate.Cron(_))
                )
              )
            )
            .map(m => Output.emit(json, m)(ManagersMarkdown.line))
        case Command.Autopilot(verb, json) =>
          def status(call: Either[Cli.Error, AutopilotStatus])(using console: Console) =
            call.map(s => Output.emit(json, s)(_.markdown))
          verb match
            case AutopilotVerb.Status => status(c.call(AutopilotApi.autopilot)(()))
            case AutopilotVerb.On     =>
              status(
                c.call(AutopilotApi.setAutopilot)(
                  Autopilot.AutopilotSet(on = Autopilot.AutopilotSet.On(true), Some(Autopilot.AutopilotSet.By("cli")))
                )
              )
            case AutopilotVerb.Off =>
              status(
                c.call(AutopilotApi.setAutopilot)(
                  Autopilot.AutopilotSet(on = Autopilot.AutopilotSet.On(false), Some(Autopilot.AutopilotSet.By("cli")))
                )
              )
            case AutopilotVerb.Drain =>
              status(
                c.call(AutopilotApi.drain)(
                  Autopilot.DrainSet(on = Autopilot.DrainSet.On(true), Some(Autopilot.DrainSet.By("cli")))
                )
              )
            case AutopilotVerb.Undrain =>
              status(
                c.call(AutopilotApi.drain)(
                  Autopilot.DrainSet(on = Autopilot.DrainSet.On(false), Some(Autopilot.DrainSet.By("cli")))
                )
              )
            case AutopilotVerb.RepairAcquire(dry) =>
              c.call(AcquisitionWorkApi.repairAcquire)(
                RepairAcquire.RepairRequest(RepairAcquire.RepairRequest.DryRun(dry), Some(Activity.Actor("cli")))
              ).map(r => Output.emit(json, r)(RepairAcquire.markdown(_).value))
            case AutopilotVerb.Focus(request) =>
              request
                .fold(c.call(ManagersApi.focus)(()))(r => c.call(ManagersApi.setFocus)(r))
                .map(f => Output.emit(json, f)(AutopilotMarkdown.focus))
            case AutopilotVerb.Mode(m) =>
              status(
                c.call(AutopilotApi.setMode)(
                  AgentMode.ModeSet(AgentMode.ModeSet.Mode(m), Some(AgentMode.ModeSet.By("cli")))
                )
              )
        case Command.SchedulerStatus(limit, json) =>
          c.call(ManagersApi.scheduler)(limit.map(Scheduler.Limit(_)))
            .map(s => Output.emit(json, s)(AgentSchedulerMarkdown.status))
        case Command.SchedulerQueue(limit, json) =>
          c.call(ManagersApi.scheduler)(limit.orElse(Some(200)).map(Scheduler.Limit(_)))
            .map(s =>
              Output.emit(json, s.waiting)(ws =>
                if ws.isEmpty then "_Nothing waits._"
                else
                  (s"# Waiting (${s.waitingTotal}, strategy ${s.strategy})" :: "" :: ws
                    .map(AgentSchedulerMarkdown.waiting)).mkString("\n")
              )
            )
        case Command.SchedulerWhy(item, json) =>
          c.call(ManagersApi.schedulerWhy)(ManagerInfo.Id(item))
            .map(w => Output.emit(json, w)(AgentSchedulerMarkdown.why))
        case Command.SchedulerStrategy(name, json) =>
          c.call(ManagersApi.setScheduler)(
            AgentScheduler
              .SchedulerSet(AgentScheduler.SchedulerSet.Strategy(name), Some(AgentScheduler.SchedulerSet.By("cli")))
          ).map(s => Output.emit(json, s)(AgentSchedulerMarkdown.status))
        case Command.Manager(idText, verb) =>
          val id = ManagerInfo.Id(idText)
          def action(a: String, runs: Option[Boolean]) =
            c.call(ManagersApi.action)((id, ManagersApi.Action(a), runs))
              .map(m => Cli.out.println(ManagersMarkdown.line(m)))
          verb match
            case ManagerVerb.Show => c.call(ManagersApi.get)(id).map(d => Cli.out.println(ManagersMarkdown.detail(d)))
            case ManagerVerb.Status(json) =>
              c.call(ManagersApi.get)(id).map(d => Output.emit(json, d)(ManagersMarkdown.detail))
            case ManagerVerb.Items(st, off, lim, json) =>
              c.call(ManagersApi.items)(
                (id, st.map(ItemStatus.Wire(_)), off.map(ItemPage.Limit(_)), lim.map(UnreadPage.Offset(_)))
              ).map(p => Output.emit(json, p)(ManagersMarkdown.items))
            case ManagerVerb.Events(b, lim) =>
              c.call(ManagersApi.events)((id, b.map(EventPage.Before(_)), lim.map(EventPage.Limit(_))))
                .map(p => Cli.out.println(ManagersMarkdown.events(p)))
            case ManagerVerb.Pause(r)                        => action("pause", Some(r))
            case ManagerVerb.Resume                          => action("resume", None)
            case ManagerVerb.Stop                            => action("stop", None)
            case ManagerVerb.Hint(text, item, once, suggest) =>
              c.call(ManagersApi.addHint)(
                (
                  id,
                  HintAdd(
                    HintAdd.Text(text),
                    item.map(HintAdd.Item(_)),
                    Some(HintAdd.Once(once)),
                    None,
                    Some(HintAdd.Author("cli")),
                    suggest = Some(HintAdd.Suggest(suggest))
                  )
                )
              ).map(h => Cli.out.println(ManagersMarkdown.hint(h)))
            case ManagerVerb.Enqueue(kind, t, p) =>
              c.call(ManagersApi.addItem)(
                (
                  id,
                  EnqueueRequest(EnqueueRequest.Kind(kind), EnqueueRequest.Target(t), p.map(EnqueueRequest.Priority(_)))
                )
              ).map(i => Cli.out.println(ManagersMarkdown.item(i)))
            case ManagerVerb.Hold(t, u, s, r) =>
              itemPatch(
                c,
                id,
                ManagerItem.Ref(t),
                ItemPatch(
                  ItemPatch.Action("hold"),
                  until = u.map(ItemPatch.Until(_)),
                  untilSource = s.map(ItemPatch.UntilSource(_)),
                  reason = r.map(text => Managers.Rationale(text))
                )
              )
            case ManagerVerb.Release(t) => itemPatch(c, id, ManagerItem.Ref(t), ItemPatch(ItemPatch.Action("release")))
            case ManagerVerb.Skip(t, r) =>
              itemPatch(
                c,
                id,
                ManagerItem.Ref(t),
                ItemPatch(ItemPatch.Action("skip"), reason = r.map(text => Managers.Rationale(text)))
              )
            case ManagerVerb.Reopen(t) => itemPatch(c, id, ManagerItem.Ref(t), ItemPatch(ItemPatch.Action("reopen")))
            case ManagerVerb.Priority(t, p) =>
              itemPatch(
                c,
                id,
                ManagerItem.Ref(t),
                ItemPatch(ItemPatch.Action("priority"), priority = Some(ItemPatch.Priority(p)))
              )
            case ManagerVerb.Cron(t) =>
              c.call(ManagersApi.update)((id, ManagerUpdate(cron = Some(ManagerUpdate.Cron(t)))))
                .map(m => Cli.out.println(ManagersMarkdown.line(m)))
            case ManagerVerb.Config(kvs) =>
              config(kvs)
                .flatMap(cfg => c.call(ManagersApi.update)((id, ManagerUpdate(Some(cfg)))))
                .map(m => Cli.out.println(ManagersMarkdown.line(m)))
