package com.histmersion
package cli

import cats.syntax.all.*
import com.monovore.decline.*
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

/** `histmersion agent managers` and `histmersion agent manager <id> …` — the persistent process managers
  * (docs/research-process-managers.md). The server runs them; this reads and steers them over HTTP.
  */
object ManagerCmd:
  def managers(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "managers",
    "The process managers: each one's state, items, runs in flight, status line and last event"
  ) {
    Output.jsonFlag.map(json =>
      (c: Client) => c.call(ManagersApi.list)(()).map(ms => Output.emit(json, ms)(ManagersMarkdown.list))
    )
  }

  private def config(kvs: List[String]): Either[Cli.Error, ManagerConfig] =
    ManagerConfig
      .parse(kvs.map(ManagerConfig.Spec(_)))
      .left
      .map(m => Cli.Error(Problem(m.value), exitCode = ExitCode.Usage))

  /** The settings `--config` and `config` take. */
  private val SettingKeys =
    "max-runs, rounds, max-runs-per-article, no-progress-runs, reopen-limit, cooldown-minutes, " +
      "low-water, refill-batch, max-failures, budget-usd, tags, goal, models, stale-after-days, infer-filter, min-sources, " +
      "max-articles, sweeps, auto-ingest, auto-ingest-per-day, daily-usd (metered spend; 0 = none), auto-approve (its runs' " +
      "plans and drafts decided by code checks), extend-min-new"

  private def create(using console: Console) = Opts.subcommand(
    "new",
    "Create a manager: review (library-wide, id 'review'), enrich (library-wide, id 'enrich'), seed --goal g (id 'seed:g'; " +
      "starts by proposing the area's articles), watch --goal g (id 'watch:g'; an acquisition sweep and a currency check " +
      "on its cron, weekly by default; auto-ingest of legal open access only with --config auto-ingest=on); created paused"
  ) {
    (
      Opts.argument[String]("review|enrich|seed|watch"),
      Opts.option[String]("goal", "The research area (seed, watch)").orNone,
      Opts.options[String]("config", s"A setting key=value (repeatable): $SettingKeys").orEmpty,
      Opts
        .option[String](
          "cron",
          "When it fires: five fields in UTC, or @daily/@weekly/@monthly (watch: default @weekly)"
        )
        .orNone,
      Opts.flag("active", "Start it active (default: paused — nothing is spent until you resume it)").orFalse,
      Output.jsonFlag
    )
      .mapN { (kind, goal, kvs, cron, active, json) => (c: Client) =>
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
      }
  }

  /** `histmersion agent enqueue <review|extend|enrich|seed> <target>` — what the page buttons do. */
  def enqueue(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "enqueue",
    "Hand a target to its manager, as the page buttons do: review|extend <article>, enrich <citekey|article|goal> " +
      "(the unenriched sources it cites or links), seed <goal> (propose its next articles); a missing manager is created paused"
  ) {
    (
      Opts.argument[String]("kind"),
      Opts.argument[String]("target"),
      Opts.option[Int]("priority", "Its priority (default 10: first)").orNone,
      Output.jsonFlag
    ).mapN { (kind, t, p, json) => (c: Client) =>
      c.call(ManagersApi.enqueue)(
        EnqueueRequest(EnqueueRequest.Kind(kind), EnqueueRequest.Target(t), p.map(EnqueueRequest.Priority(_)))
      ).map(r => Output.emit(json, r)(ManagersMarkdown.enqueued))
    }
  }

  /** `histmersion agent autopilot [status|on|off]` — the one switch (docs/research-process-managers.md "As built: the
    * autopilot").
    */
  def autopilot(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "autopilot",
    "The autopilot: status (default) — per research goal its stage, managers, sources, articles, open approvals, last " +
      "activity and pace, and the watchers' ingest backlog; on — every goal gets an active seeding manager and watcher and " +
      "moves from planned to reviewed by itself (within every cap, budget and plan floor); off — the seeding managers and " +
      "watchers it runs are paused; repair-acquire [--dry-run] — move the acquisition items a watcher's sweep created for " +
      "legal open-access works into the watchers' ingest backlog (a person's items are never touched); focus " +
      "[list|pin <goal>|unpin <goal>|priority <goal> <n>|max <n|default>] — at most max-active-goals goals are active for " +
      "model work (finish-first: articles to review, then to compile, then enough sources; depth-first within a tree; a " +
      "person's pins and priority; then the oldest), the others' model-using items wait; ingestion is not capped; " +
      "drain — nothing new starts while the runs in flight finish (the status says DRAINED once none is queued or " +
      "running: safe to stop the server for a deploy); undrain — lift it (turning it on lifts it too); mode " +
      "<working|finish|pause> — the two switches as the web UI's one choice: working, finish current work then pause " +
      "(on and draining), pause now (off)"
  ) {
    (
      Opts.argument[String]("status|on|off|drain|undrain|repair-acquire|focus|mode").withDefault("status"),
      Opts.arguments[String]("focus: list|pin|unpin|priority|max [goal] [n]").orEmpty,
      Opts.flag("dry-run", "repair-acquire: list what it would do, change nothing").orFalse,
      Output.jsonFlag
    ).mapN { (verb, rest, dry, json) => (c: Client) =>
      def status(call: Either[Cli.Error, AutopilotStatus])(using console: Console) =
        call.map(s => Output.emit(json, s)(_.markdown))
      verb.trim.toLowerCase match
        case "mode" =>
          rest match
            case List(m) =>
              status(
                c.call(AutopilotApi.setMode)(
                  AgentMode.ModeSet(AgentMode.ModeSet.Mode(m), Some(AgentMode.ModeSet.By("cli")))
                )
              )
            case _ =>
              Left(
                Cli.Error(
                  Problem("autopilot mode takes one of: working, finish, pause"),
                  exitCode = ExitCode.Usage
                )
              )
        case _ if rest.nonEmpty && verb.trim.toLowerCase != "focus" =>
          Left(
            Cli.Error(
              Problem(s"autopilot $verb takes no further arguments (${rest.mkString(" ")})"),
              exitCode = ExitCode.Usage
            )
          )
        case "status" => status(c.call(AutopilotApi.autopilot)(()))
        case "on"     =>
          status(
            c.call(AutopilotApi.setAutopilot)(
              Autopilot.AutopilotSet(on = Autopilot.AutopilotSet.On(true), Some(Autopilot.AutopilotSet.By("cli")))
            )
          )
        case "off" =>
          status(
            c.call(AutopilotApi.setAutopilot)(
              Autopilot.AutopilotSet(on = Autopilot.AutopilotSet.On(false), Some(Autopilot.AutopilotSet.By("cli")))
            )
          )
        case "drain" =>
          status(
            c.call(AutopilotApi.drain)(
              Autopilot.DrainSet(on = Autopilot.DrainSet.On(true), Some(Autopilot.DrainSet.By("cli")))
            )
          )
        case "undrain" =>
          status(
            c.call(AutopilotApi.drain)(
              Autopilot.DrainSet(on = Autopilot.DrainSet.On(false), Some(Autopilot.DrainSet.By("cli")))
            )
          )
        case "repair-acquire" =>
          c.call(AcquisitionWorkApi.repairAcquire)(
            RepairAcquire.RepairRequest(RepairAcquire.RepairRequest.DryRun(dry), Some(Activity.Actor("cli")))
          ).map(r => Output.emit(json, r)(RepairAcquire.markdown(_).value))
        case "focus" =>
          focusRequest(rest)
            .flatMap {
              case None    => c.call(ManagersApi.focus)(())
              case Some(r) => c.call(ManagersApi.setFocus)(r)
            }
            .map(f => Output.emit(json, f)(AutopilotMarkdown.focus))
        case other =>
          Left(
            Cli.Error(
              Problem(
                s"unknown autopilot verb '$other' (status, on, off, drain, undrain, repair-acquire, focus, mode)"
              ),
              exitCode = ExitCode.Usage
            )
          )
    }
  }

  /** `histmersion agent scheduler [status|queue|why <item>|strategy <name|default>]` — the central scheduler
    * (docs/research-goal-advancement.md "As built").
    */
  def scheduler(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "scheduler",
    "The central scheduler that admits the managers' work across every job type: status (default) — the strategy, " +
      "the runs at once and plan pools, the job types' shares, what the last pass started and the queue with why each " +
      "item waits; queue — only the waiting items, in the order they would start; why <item> — one item (its id, or the " +
      "target of an open item): waiting behind what, running, waiting for a person, held, settled; strategy " +
      "<type-balanced|goal-first|default> — switch it live (default: back to agents.scheduler.strategy)"
  ) {
    (
      Opts.argument[String]("status|queue|why|strategy").withDefault("status"),
      Opts.arguments[String]("why: <item>; strategy: <name|default>").orEmpty,
      Opts.option[Int]("limit", "How many waiting items to show (status: 50, queue: 200)").orNone,
      Output.jsonFlag
    ).mapN { (verb, rest, limit, json) => (c: Client) =>
      def usage(m: String) = Left(Cli.Error(Problem(m), exitCode = ExitCode.Usage))
      (verb.trim.toLowerCase, rest.map(_.trim).filter(_.nonEmpty)) match
        case ("status", Nil) =>
          c.call(ManagersApi.scheduler)(limit.map(Scheduler.Limit(_)))
            .map(s => Output.emit(json, s)(AgentSchedulerMarkdown.status))
        case ("queue", Nil) =>
          c.call(ManagersApi.scheduler)(limit.orElse(Some(200)).map(Scheduler.Limit(_)))
            .map(s =>
              Output.emit(json, s.waiting)(ws =>
                if ws.isEmpty then "_Nothing waits._"
                else
                  (s"# Waiting (${s.waitingTotal}, strategy ${s.strategy})" :: "" :: ws
                    .map(AgentSchedulerMarkdown.waiting)).mkString("\n")
              )
            )
        case ("why", List(item)) =>
          c.call(ManagersApi.schedulerWhy)(ManagerInfo.Id(item))
            .map(w => Output.emit(json, w)(AgentSchedulerMarkdown.why))
        case ("strategy", List(name)) =>
          c.call(ManagersApi.setScheduler)(
            AgentScheduler
              .SchedulerSet(AgentScheduler.SchedulerSet.Strategy(name), Some(AgentScheduler.SchedulerSet.By("cli")))
          ).map(s => Output.emit(json, s)(AgentSchedulerMarkdown.status))
        case ("why", _)      => usage("scheduler why takes one item: its id, or the target of an open item")
        case ("strategy", _) => usage("scheduler strategy takes one name: type-balanced, goal-first or default")
        case (v, _) if Set("status", "queue")(v) => usage(s"scheduler $v takes no further arguments")
        case (other, _) => usage(s"unknown scheduler verb '$other' (status, queue, why, strategy)")
    }
  }

  /** `histmersion agent stalls [--days n]` — the goals with no progress, why, and what unblocks them. */
  def stalls(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "stalls",
    "The unfinished research goals with no progress in N days (default agents.autopilot.stall-days), each with the " +
      "one reason it stands still — draining, waiting for a person, held, paused, a budget, quota, the scheduler's " +
      "queue, a block, a failed item, no candidates, idle — and the command that unblocks it"
  ) {
    (Opts.option[Int]("days", "No progress in this many days").orNone, Output.jsonFlag).mapN {
      (days, json) => (c: Client) =>
        c.call(ManagersApi.stalls)(days.map(Stalls.Limit(_))).map(r => Output.emit(json, r)(StallsMarkdown.report))
    }
  }

  /** `histmersion agent digest` — everything waiting for a person, by kind (#3762 proposal 3). */
  def digest(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "digest",
    "Everything waiting for a person in one view: the open approvals by kind (how many, the oldest, who decides them " +
      "now, the subjects with the most, the ids) and the manager items held for a person; once a day it is also " +
      "written to the worklog (agents.autopilot.digest-hour)"
  ) {
    Output.jsonFlag.map(json =>
      (c: Client) => c.call(ManagersApi.digest)(()).map(d => Output.emit(json, d)(ApprovalDigestMarkdown.digest))
    )
  }

  /** `histmersion agent decide-all <approve|reject> --kind k | --subject s | --id n…` — a group at once. */
  def decideAll(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "decide-all",
    "Approve or reject every OPEN approval that matches at once — of a kind, about a subject, or the ids given (they " +
      "narrow together; one is needed), the oldest first, each through the same decision a single one takes"
  ) {
    (
      Opts.argument[String]("approve|reject"),
      Opts.option[String]("kind", "Only this kind (goal-plan, article-publish, …)").orNone,
      Opts.option[String]("subject", "Only approvals about this goal or article").orNone,
      Opts.options[Long]("id", "Only these approvals (repeatable)").orEmpty,
      Opts.option[String]("note", "Why — kept with every decision").orNone,
      Opts.option[Int]("limit", "At most this many (the oldest first)").orNone,
      Opts.flag("dry-run", "List what it would decide; decide nothing").orFalse,
      Output.jsonFlag
    ).mapN { (d, kind, subject, ids, note, limit, dry, json) => (c: Client) =>
      c.call(ManagersApi.decideAll)(
        ApprovalDigest.BulkDecide(
          ApprovalDigest.BulkDecide.Decision(d),
          kind.map(ApprovalDigest.BulkDecide.Kind(_)),
          subject.map(ApprovalDigest.BulkDecide.Subject(_)),
          ids.map(ApprovalDigest.BulkDecide.Ids(_)),
          note.map(ApprovalDigest.BulkDecide.Note(_)),
          ApprovalDigest.BulkDecide.DryRun(dry),
          limit.map(ApprovalDigest.BulkDecide.Limit(_)),
          Some(ApprovalDigest.BulkDecide.By("cli"))
        )
      ).map(r => Output.emit(json, r)(ApprovalDigestMarkdown.bulk))
    }
  }

  /** `focus` [list | pin <goal> | unpin <goal> | priority <goal> <n> | max <n|default>] → the request (None: just show
    * it); a malformed one is a usage error before anything is sent.
    */
  def focusRequest(args: List[String]): Either[Cli.Error, Option[Autopilot.FocusSet]] =
    def usage(m: String) = Left(Cli.Error(Problem(m), exitCode = ExitCode.Usage))
    def num(s: String) =
      s.trim.toIntOption.toRight(Cli.Error(Problem(s"'$s' is not a whole number"), exitCode = ExitCode.Usage))
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

  /** The verbs of one manager; each takes the manager's id first. */
  private def verb(name: String, help: String)(rest: Opts[(Client, String) => Either[Cli.Error, Unit]]) =
    Opts.subcommand(name, help)(rest)

  private val target = Opts.argument[String]("target")

  private def itemPatch(c: Client, id: ManagerInfo.Id, t: ManagerItem.Ref, p: ItemPatch)(using console: Console) =
    c.call(ManagersApi.patchItem)((id, t, p)).map(i => Cli.out.println(ManagersMarkdown.item(i)))

  private def verbs(using console: Console): Opts[(Client, String) => Either[Cli.Error, Unit]] =
    verb("status", "The manager: items in flight with their runs, hints, newest events")(
      Output.jsonFlag.map(json =>
        (c, id) => c.call(ManagersApi.get)(ManagerInfo.Id(id)).map(d => Output.emit(json, d)(ManagersMarkdown.detail))
      )
    ) orElse
      verb("items", "Its items, the most urgent first")(
        (
          Opts
            .option[String]("status", s"Only items in this state: ${ItemStatus.values.map(_.wire).mkString("|")}")
            .orNone,
          Opts.option[Int]("offset", "Skip this many").orNone,
          Opts.option[Int]("limit", "How many").orNone,
          Output.jsonFlag
        ).mapN { (st, off, lim, json) => (c, id) =>
          c.call(ManagersApi.items)(
            (ManagerInfo.Id(id), st.map(ItemStatus.Wire(_)), off.map(ItemPage.Limit(_)), lim.map(UnreadPage.Offset(_)))
          ).map(p => Output.emit(json, p)(ManagersMarkdown.items))
        }
      ) orElse
      verb("events", "Its events, newest first")(
        (
          Opts.option[Long]("before", "Only events older than this id").orNone,
          Opts.option[Int]("limit", "How many").orNone
        ).mapN { (b, lim) => (c, id) =>
          c.call(ManagersApi.events)((ManagerInfo.Id(id), b.map(EventPage.Before(_)), lim.map(EventPage.Limit(_))))
            .map(p => Cli.out.println(ManagersMarkdown.events(p)))
        }
      ) orElse
      verb("pause", "Start no new runs (its runs in flight go on, unless --runs)")(
        Opts
          .flag("runs", "Pause its runs in flight too")
          .orFalse
          .map(r =>
            (c, id) =>
              c.call(ManagersApi.action)((ManagerInfo.Id(id), ManagersApi.Action("pause"), Some(r)))
                .map(m => Cli.out.println(ManagersMarkdown.line(m)))
          )
      ) orElse
      verb("resume", "Start runs again (and resume the runs a pause --runs paused)")(
        Opts.unit.map(_ =>
          (c, id) =>
            c.call(ManagersApi.action)((ManagerInfo.Id(id), ManagersApi.Action("resume"), None))
              .map(m => Cli.out.println(ManagersMarkdown.line(m)))
        )
      ) orElse
      verb("stop", "Cancel its runs in flight and its open items")(
        Opts.unit.map(_ =>
          (c, id) =>
            c.call(ManagersApi.action)((ManagerInfo.Id(id), ManagersApi.Action("stop"), None))
              .map(m => Cli.out.println(ManagersMarkdown.line(m)))
        )
      ) orElse
      verb(
        "hint",
        "A note its runs' fixer and skeptic read (standing unless --once); --suggest: the server's model proposes " +
          "the action it asks for (hold, prioritise, skip, settings), applied only when you approve it"
      )(
        (
          Opts.argument[String]("text"),
          Opts.option[String]("item", "Only about this target (or item id)").orNone,
          Opts.flag("once", "For the next run only").orFalse,
          Opts
            .flag("suggest", "Let the server's planner model propose a structured action (an approval you decide)")
            .orFalse
        )
          .mapN { (text, item, once, suggest) => (c, id) =>
            c.call(ManagersApi.addHint)(
              (
                ManagerInfo.Id(id),
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
          }
      ) orElse
      verb(
        "enqueue",
        "Drive a target: kind review|filter|extend <article> (review manager), enrich <citekey> (enrich), " +
          "propose <goal> (seed), sweep|currency <goal> (watch); a settled one is re-opened"
      )(
        (Opts.argument[String]("kind"), target, Opts.option[Int]("priority", "Its priority (default 10: first)").orNone)
          .mapN { (kind, t, p) => (c, id) =>
            c.call(ManagersApi.addItem)(
              (
                ManagerInfo.Id(id),
                EnqueueRequest(EnqueueRequest.Kind(kind), EnqueueRequest.Target(t), p.map(EnqueueRequest.Priority(_)))
              )
            ).map(i => Cli.out.println(ManagersMarkdown.item(i)))
          }
      ) orElse
      verb("hold", "Hold an item: until a date, until a source (DOI or citekey) is in the corpus, or until released")(
        (
          target,
          Opts.option[String]("until", "A date (2026-11-01) or an instant").orNone,
          Opts.option[String]("until-source", "A DOI or citekey").orNone,
          Opts.option[String]("reason", "Why").orNone
        ).mapN { (t, u, s, r) => (c, id) =>
          itemPatch(
            c,
            ManagerInfo.Id(id),
            ManagerItem.Ref(t),
            ItemPatch(
              ItemPatch.Action("hold"),
              until = u.map(ItemPatch.Until(_)),
              untilSource = s.map(ItemPatch.UntilSource(_)),
              reason = r.map(api.Problem(_))
            )
          )
        }
      ) orElse
      verb("release", "Release a held item")(
        target.map(t =>
          (c, id) => itemPatch(c, ManagerInfo.Id(id), ManagerItem.Ref(t), ItemPatch(ItemPatch.Action("release")))
        )
      ) orElse
      verb("skip", "Stop an item as skipped (its run, if any, is cancelled)")(
        (target, Opts.option[String]("reason", "Why").orNone)
          .mapN((t, r) =>
            (c, id) =>
              itemPatch(
                c,
                ManagerInfo.Id(id),
                ManagerItem.Ref(t),
                ItemPatch(ItemPatch.Action("skip"), reason = r.map(api.Problem(_)))
              )
          )
      ) orElse
      verb("reopen", "Bring a settled item back (its runs counted from zero)")(
        target.map(t =>
          (c, id) => itemPatch(c, ManagerInfo.Id(id), ManagerItem.Ref(t), ItemPatch(ItemPatch.Action("reopen")))
        )
      ) orElse
      verb("priority", "Set an item's priority (higher first)")(
        (target, Opts.argument[Int]("priority")).mapN((t, p) =>
          (c, id) =>
            itemPatch(
              c,
              ManagerInfo.Id(id),
              ManagerItem.Ref(t),
              ItemPatch(ItemPatch.Action("priority"), priority = Some(ItemPatch.Priority(p)))
            )
        )
      ) orElse
      verb(
        "cron",
        "When it fires (UTC): five fields, or @daily/@weekly/@monthly; \"\" clears it. A watcher re-opens its " +
          "sweeps, the review manager refills, a seeding manager proposes again"
      )(
        Opts
          .argument[String]("cron")
          .map(t =>
            (c, id) =>
              c.call(ManagersApi.update)((ManagerInfo.Id(id), ManagerUpdate(cron = Some(ManagerUpdate.Cron(t)))))
                .map(m => Cli.out.println(ManagersMarkdown.line(m)))
          )
      ) orElse
      verb("config", s"Replace its settings: key=value pairs (none: back to the server's defaults): $SettingKeys")(
        Opts
          .arguments[String]("key=value")
          .orEmpty
          .map(kvs =>
            (c, id) =>
              config(kvs)
                .flatMap(cfg => c.call(ManagersApi.update)((ManagerInfo.Id(id), ManagerUpdate(Some(cfg)))))
                .map(m => Cli.out.println(ManagersMarkdown.line(m)))
          )
      )

  def manager(using console: Console): Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "manager",
    "One process manager: new, or <id> status|items|events|pause|resume|stop|hint|enqueue|hold|release|skip|reopen|priority|cron|config"
  ) {
    create orElse (
      Opts.argument[String]("id"),
      verbs.orElse(
        Opts((c: Client, id: String) =>
          c.call(ManagersApi.get)(ManagerInfo.Id(id)).map(d => Cli.out.println(ManagersMarkdown.detail(d)))
        )
      )
    ).mapN((id, f) => (c: Client) => f(c, id))
  }
