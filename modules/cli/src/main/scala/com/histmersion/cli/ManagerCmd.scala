package com.histmersion.cli

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
import com.histmersion.api.library.RepairAcquire
import com.histmersion.api.views.{AutopilotApi, AutopilotStatus}
import com.histmersion.util.console.Console

/** `histmersion agent managers` and `histmersion agent manager <id> …` — the persistent process managers
  * (docs/research-process-managers.md). The server runs them; this reads and steers them over HTTP.
  */
object ManagerCmd:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  def managers(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "managers",
    "The process managers: each one's state, items, runs in flight, status line and last event"
  ) {
    Output.jsonFlag.map(json =>
      (c: Client) => c.call(ManagersApi.list)(()).map(ms => Output.emit(json, ms)(ManagersMarkdown.list))
    )
  }

  private def config(kvs: List[com.histmersion.domain.automation.Manager.ConfigEntry]): Either[Cli.Error, ManagerConfig] =
    ManagerConfig.parse(kvs).left.map(m => Cli.Error(m, exitCode = ExitCode.Usage))

  /** The settings `--config` and `config` take. */
  private val SettingKeys =
    "max-runs, rounds, max-runs-per-article, no-progress-runs, reopen-limit, cooldown-minutes, " +
      "low-water, refill-batch, max-failures, budget-usd, tags, goal, models, stale-after-days, infer-filter, min-sources, " +
      "max-articles, sweeps, auto-ingest, auto-ingest-per-day, daily-usd (metered spend; 0 = none), auto-approve (its runs' " +
      "plans and drafts decided by code checks), extend-min-new"

  private def create (using console: Console) = Opts.subcommand(
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
          .flatMap(cfg => c.call(ManagersApi.create)(ManagerCreate(kind, goal, cfg, Some(!active), cron)))
          .map(m => Output.emit(json, m)(ManagersMarkdown.line))
      }
  }

  /** `histmersion agent enqueue <review|extend|enrich|seed> <target>` — what the page buttons do. */
  def enqueue(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
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
      c.call(ManagersApi.enqueue)(EnqueueRequest(kind, t, p)).map(r => Output.emit(json, r)(ManagersMarkdown.enqueued))
    }
  }

  /** `histmersion agent autopilot [status|on|off]` — the one switch (docs/research-process-managers.md "As built: the
    * autopilot").
    */
  def autopilot(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
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
      def status(call: Either[Cli.Error, AutopilotStatus]) (using console: Console) =
        call.map(s => Output.emit(json, s)(_.markdown))
      verb.trim.toLowerCase match
        case "mode" =>
          rest match
            case List(m) => status(c.call(AutopilotApi.setMode)(AgentMode.ModeSet(m, Some("cli"))))
            case _ => Left(Cli.Error("autopilot mode takes one of: working, finish, pause", exitCode = ExitCode.Usage))
        case _ if rest.nonEmpty && verb.trim.toLowerCase != "focus" =>
          Left(
            Cli.Error(s"autopilot $verb takes no further arguments (${rest.mkString(" ")})", exitCode = ExitCode.Usage)
          )
        case "status"  => status(c.call(AutopilotApi.autopilot)(()))
        case "on"      => status(c.call(AutopilotApi.setAutopilot)(Autopilot.AutopilotSet(on = true, Some("cli"))))
        case "off"     => status(c.call(AutopilotApi.setAutopilot)(Autopilot.AutopilotSet(on = false, Some("cli"))))
        case "drain"   => status(c.call(AutopilotApi.drain)(Autopilot.DrainSet(on = true, Some("cli"))))
        case "undrain" => status(c.call(AutopilotApi.drain)(Autopilot.DrainSet(on = false, Some("cli"))))
        case "repair-acquire" =>
          c.call(com.histmersion.api.library.AcquisitionWorkApi.repairAcquire)(
            RepairAcquire.RepairRequest(dry, Some("cli"))
          ).map(r => Output.emit(json, r)(RepairAcquire.markdown))
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
              s"unknown autopilot verb '$other' (status, on, off, drain, undrain, repair-acquire, focus, mode)",
              exitCode = ExitCode.Usage
            )
          )
    }
  }

  /** `histmersion agent scheduler [status|queue|why <item>|strategy <name|default>]` — the central scheduler
    * (docs/research-goal-advancement.md "As built").
    */
  def scheduler(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
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
      def usage(m: com.histmersion.cli.ManagerCmd.Problem) = Left(Cli.Error(m, exitCode = ExitCode.Usage))
      (verb.trim.toLowerCase, rest.map(_.trim).filter(_.nonEmpty)) match
        case ("status", Nil) =>
          c.call(ManagersApi.scheduler)(limit).map(s => Output.emit(json, s)(AgentSchedulerMarkdown.status))
        case ("queue", Nil) =>
          c.call(ManagersApi.scheduler)(limit.orElse(Some(200)))
            .map(s =>
              Output.emit(json, s.waiting)(ws =>
                if ws.isEmpty then "_Nothing waits._"
                else
                  (s"# Waiting (${s.waitingTotal}, strategy ${s.strategy})" :: "" :: ws
                    .map(AgentSchedulerMarkdown.waiting)).mkString("\n")
              )
            )
        case ("why", List(item)) =>
          c.call(ManagersApi.schedulerWhy)(item).map(w => Output.emit(json, w)(AgentSchedulerMarkdown.why))
        case ("strategy", List(name)) =>
          c.call(ManagersApi.setScheduler)(AgentScheduler.SchedulerSet(name, Some("cli")))
            .map(s => Output.emit(json, s)(AgentSchedulerMarkdown.status))
        case ("why", _)      => usage("scheduler why takes one item: its id, or the target of an open item")
        case ("strategy", _) => usage("scheduler strategy takes one name: type-balanced, goal-first or default")
        case (v, _) if Set("status", "queue")(v) => usage(s"scheduler $v takes no further arguments")
        case (other, _) => usage(s"unknown scheduler verb '$other' (status, queue, why, strategy)")
    }
  }

  /** `histmersion agent stalls [--days n]` — the goals with no progress, why, and what unblocks them. */
  def stalls(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "stalls",
    "The unfinished research goals with no progress in N days (default agents.autopilot.stall-days), each with the " +
      "one reason it stands still — draining, waiting for a person, held, paused, a budget, quota, the scheduler's " +
      "queue, a block, a failed item, no candidates, idle — and the command that unblocks it"
  ) {
    (Opts.option[Int]("days", "No progress in this many days").orNone, Output.jsonFlag).mapN {
      (days, json) => (c: Client) =>
        c.call(ManagersApi.stalls)(days).map(r => Output.emit(json, r)(StallsMarkdown.report))
    }
  }

  /** `histmersion agent digest` — everything waiting for a person, by kind (#3762 proposal 3). */
  def digest(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
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
  def decideAll(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
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
      c.call(ManagersApi.decideAll)(ApprovalDigest.BulkDecide(d, kind, subject, ids, note, dry, limit, Some("cli")))
        .map(r => Output.emit(json, r)(ApprovalDigestMarkdown.bulk))
    }
  }

  /** `focus` [list | pin <goal> | unpin <goal> | priority <goal> <n> | max <n|default>] → the request (None: just show
    * it); a malformed one is a usage error before anything is sent.
    */
  def focusRequest(args: List[com.histmersion.domain.automation.Manager.FocusArg]): Either[Cli.Error, Option[Autopilot.FocusSet]] =
    def usage(m: com.histmersion.cli.ManagerCmd.Problem) = Left(Cli.Error(m, exitCode = ExitCode.Usage))
    def num(s: String) = s.trim.toIntOption.toRight(Cli.Error(s"'$s' is not a whole number", exitCode = ExitCode.Usage))
    args.map(_.trim) match
      case Nil | List("list")             => Right(None)
      case List(v @ ("pin" | "unpin"), g) => Right(Some(Autopilot.FocusSet(v, Some(g), by = Some("cli"))))
      case List("priority", g, n)         =>
        num(n).map(p => Some(Autopilot.FocusSet("priority", Some(g), Some(p), Some("cli"))))
      case List("max", "default") => Right(Some(Autopilot.FocusSet("max", by = Some("cli"))))
      case List("max", n)         =>
        num(n).flatMap(m =>
          if m < 0 then usage("focus max takes 0 (no cap) or more, or `default`")
          else Right(Some(Autopilot.FocusSet("max", value = Some(m), by = Some("cli"))))
        )
      case other =>
        usage(
          s"focus ${other.mkString(" ")}: use focus [list | pin <goal> | unpin <goal> | priority <goal> <n> | max <n|default>]"
        )

  /** The verbs of one manager; each takes the manager's id first. */
  private def verb(name: String, help: String)(rest: Opts[(Client, String) => Either[Cli.Error, Unit]]) =
    Opts.subcommand(name, help)(rest)

  private val target = Opts.argument[String]("target")

  private def itemPatch(c: Client, id: com.histmersion.domain.automation.Manager.ItemId, t: com.histmersion.api.automation.Managers.ItemPatch.Action, p: ItemPatch) (using console: Console) =
    c.call(ManagersApi.patchItem)((id, t, p)).map(i => Cli.out.println(ManagersMarkdown.item(i)))

  private def verbs(using console: Console) : Opts[(Client, String) => Either[Cli.Error, Unit]] =
    verb("status", "The manager: items in flight with their runs, hints, newest events")(
      Output.jsonFlag.map(json =>
        (c, id) => c.call(ManagersApi.get)(id).map(d => Output.emit(json, d)(ManagersMarkdown.detail))
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
          c.call(ManagersApi.items)((id, st, off, lim)).map(p => Output.emit(json, p)(ManagersMarkdown.items))
        }
      ) orElse
      verb("events", "Its events, newest first")(
        (
          Opts.option[Long]("before", "Only events older than this id").orNone,
          Opts.option[Int]("limit", "How many").orNone
        ).mapN { (b, lim) => (c, id) =>
          c.call(ManagersApi.events)((id, b, lim)).map(p => Cli.out.println(ManagersMarkdown.events(p)))
        }
      ) orElse
      verb("pause", "Start no new runs (its runs in flight go on, unless --runs)")(
        Opts
          .flag("runs", "Pause its runs in flight too")
          .orFalse
          .map(r =>
            (c, id) => c.call(ManagersApi.action)((id, "pause", Some(r))).map(m => Cli.out.println(ManagersMarkdown.line(m)))
          )
      ) orElse
      verb("resume", "Start runs again (and resume the runs a pause --runs paused)")(
        Opts.unit.map(_ =>
          (c, id) => c.call(ManagersApi.action)((id, "resume", None)).map(m => Cli.out.println(ManagersMarkdown.line(m)))
        )
      ) orElse
      verb("stop", "Cancel its runs in flight and its open items")(
        Opts.unit.map(_ =>
          (c, id) => c.call(ManagersApi.action)((id, "stop", None)).map(m => Cli.out.println(ManagersMarkdown.line(m)))
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
              (id, HintAdd(text, item, Some(once), None, Some("cli"), suggest = Some(suggest)))
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
            c.call(ManagersApi.addItem)((id, EnqueueRequest(kind, t, p))).map(i => Cli.out.println(ManagersMarkdown.item(i)))
          }
      ) orElse
      verb("hold", "Hold an item: until a date, until a source (DOI or citekey) is in the corpus, or until released")(
        (
          target,
          Opts.option[String]("until", "A date (2026-11-01) or an instant").orNone,
          Opts.option[String]("until-source", "A DOI or citekey").orNone,
          Opts.option[String]("reason", "Why").orNone
        ).mapN { (t, u, s, r) => (c, id) =>
          itemPatch(c, id, t, ItemPatch("hold", until = u, untilSource = s, reason = r))
        }
      ) orElse
      verb("release", "Release a held item")(
        target.map(t => (c, id) => itemPatch(c, id, t, ItemPatch("release")))
      ) orElse
      verb("skip", "Stop an item as skipped (its run, if any, is cancelled)")(
        (target, Opts.option[String]("reason", "Why").orNone)
          .mapN((t, r) => (c, id) => itemPatch(c, id, t, ItemPatch("skip", reason = r)))
      ) orElse
      verb("reopen", "Bring a settled item back (its runs counted from zero)")(
        target.map(t => (c, id) => itemPatch(c, id, t, ItemPatch("reopen")))
      ) orElse
      verb("priority", "Set an item's priority (higher first)")(
        (target, Opts.argument[Int]("priority")).mapN((t, p) =>
          (c, id) => itemPatch(c, id, t, ItemPatch("priority", priority = Some(p)))
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
              c.call(ManagersApi.update)((id, ManagerUpdate(cron = Some(t))))
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
                .flatMap(cfg => c.call(ManagersApi.update)((id, ManagerUpdate(Some(cfg)))))
                .map(m => Cli.out.println(ManagersMarkdown.line(m)))
          )
      )

  def manager(using console: Console) : Opts[Client => Either[Cli.Error, Unit]] = Opts.subcommand(
    "manager",
    "One process manager: new, or <id> status|items|events|pause|resume|stop|hint|enqueue|hold|release|skip|reopen|priority|cron|config"
  ) {
    create orElse (
      Opts.argument[String]("id"),
      verbs.orElse(
        Opts((c: Client, id: String) => c.call(ManagersApi.get)(id).map(d => Cli.out.println(ManagersMarkdown.detail(d))))
      )
    ).mapN((id, f) => (c: Client) => f(c, id))
  }
