package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*
import com.histmersion.core.Managers.*

/** `histmersion agent managers` and `histmersion agent manager <id> …` — the persistent process managers
  * (docs/research-process-managers.md). The server runs them; this reads and steers them over HTTP. */
object ManagerCmd:

  val managers: Opts[Client => Either[CliError, Unit]] = Opts.subcommand("managers",
      "The process managers: each one's state, items, runs in flight, status line and last event") {
    Output.jsonFlag.map(json => (c: Client) => c.call(ManagersApi.list)(()).map(ms => Output.emit(json, ms)(ManagersMarkdown.list)))
  }

  private def config(kvs: List[String]): Either[CliError, ManagerConfig] =
    ManagerConfig.parse(kvs).left.map(m => CliError(m, exitCode = ExitCode.Usage))

  /** The settings `--config` and `config` take. */
  private val SettingKeys = "max-runs, rounds, max-runs-per-article, no-progress-runs, reopen-limit, cooldown-minutes, " +
    "low-water, refill-batch, max-failures, budget-usd, tags, goal, models, stale-after-days, infer-filter, min-sources, " +
    "max-articles, sweeps, auto-ingest, auto-ingest-per-day, daily-usd (metered spend; 0 = none), auto-approve (its runs' " +
    "plans and drafts decided by code checks), extend-min-new"

  private val create = Opts.subcommand("new",
      "Create a manager: review (library-wide, id 'review'), enrich (library-wide, id 'enrich'), seed --goal g (id 'seed:g'; " +
        "starts by proposing the area's articles), watch --goal g (id 'watch:g'; an acquisition sweep and a currency check " +
        "on its cron, weekly by default; auto-ingest of legal open access only with --config auto-ingest=on); created paused") {
    (Opts.argument[String]("review|enrich|seed|watch"), Opts.option[String]("goal", "The research area (seed, watch)").orNone,
      Opts.options[String]("config", s"A setting key=value (repeatable): $SettingKeys").orEmpty,
      Opts.option[String]("cron", "When it fires: five fields in UTC, or @daily/@weekly/@monthly (watch: default @weekly)").orNone,
      Opts.flag("active", "Start it active (default: paused — nothing is spent until you resume it)").orFalse, Output.jsonFlag)
      .mapN { (kind, goal, kvs, cron, active, json) => (c: Client) =>
        config(kvs).flatMap(cfg => c.call(ManagersApi.create)(ManagerCreate(kind, goal, cfg, Some(!active), cron)))
          .map(m => Output.emit(json, m)(ManagersMarkdown.line))
      }
  }

  /** `histmersion agent enqueue <review|extend|enrich|seed> <target>` — what the page buttons do. */
  val enqueue: Opts[Client => Either[CliError, Unit]] = Opts.subcommand("enqueue",
      "Hand a target to its manager, as the page buttons do: review|extend <article>, enrich <citekey|article|goal> " +
        "(the unenriched sources it cites or links), seed <goal> (propose its next articles); a missing manager is created paused") {
    (Opts.argument[String]("kind"), Opts.argument[String]("target"),
      Opts.option[Int]("priority", "Its priority (default 10: first)").orNone, Output.jsonFlag).mapN { (kind, t, p, json) => (c: Client) =>
      c.call(ManagersApi.enqueue)(EnqueueRequest(kind, t, p)).map(r => Output.emit(json, r)(ManagersMarkdown.enqueued))
    }
  }

  /** `histmersion agent autopilot [status|on|off]` — the one switch (docs/research-process-managers.md
    * "As built: the autopilot"). */
  val autopilot: Opts[Client => Either[CliError, Unit]] = Opts.subcommand("autopilot",
      "The autopilot: status (default) — per research goal its stage, managers, sources, articles, open approvals, last " +
        "activity and pace, and the watchers' ingest backlog; on — every goal gets an active seeding manager and watcher and " +
        "moves from planned to reviewed by itself (within every cap, budget and plan floor); off — the seeding managers and " +
        "watchers it runs are paused; repair-acquire [--dry-run] — move the acquisition items a watcher's sweep created for " +
        "legal open-access works into the watchers' ingest backlog (a person's items are never touched)") {
    (Opts.argument[String]("status|on|off|repair-acquire").withDefault("status"),
      Opts.flag("dry-run", "repair-acquire: list what it would do, change nothing").orFalse, Output.jsonFlag).mapN {
      (verb, dry, json) => (c: Client) =>
        def status(call: Either[CliError, Autopilot.AutopilotStatus]) = call.map(s => Output.emit(json, s)(AutopilotMarkdown.status))
        verb.trim.toLowerCase match
          case "status" => status(c.call(ManagersApi.autopilot)(()))
          case "on"     => status(c.call(ManagersApi.setAutopilot)(Autopilot.AutopilotSet(on = true, Some("cli"))))
          case "off"    => status(c.call(ManagersApi.setAutopilot)(Autopilot.AutopilotSet(on = false, Some("cli"))))
          case "repair-acquire" =>
            c.call(ManagersApi.repairAcquire)(RepairAcquire.RepairRequest(dry, Some("cli")))
              .map(r => Output.emit(json, r)(RepairAcquire.markdown))
          case other => Left(CliError(s"unknown autopilot verb '$other' (status, on, off, repair-acquire)", exitCode = ExitCode.Usage))
    }
  }

  /** The verbs of one manager; each takes the manager's id first. */
  private def verb(name: String, help: String)(rest: Opts[(Client, String) => Either[CliError, Unit]]) =
    Opts.subcommand(name, help)(rest)

  private val target = Opts.argument[String]("target")

  private def itemPatch(c: Client, id: String, t: String, p: ItemPatch) =
    c.call(ManagersApi.patchItem)((id, t, p)).map(i => println(ManagersMarkdown.item(i)))

  private val verbs: Opts[(Client, String) => Either[CliError, Unit]] =
    verb("status", "The manager: items in flight with their runs, hints, newest events")(Output.jsonFlag.map(json => (c, id) =>
      c.call(ManagersApi.get)(id).map(d => Output.emit(json, d)(ManagersMarkdown.detail)))) orElse
    verb("items", "Its items, the most urgent first")((
      Opts.option[String]("status", s"Only items in this state: ${ItemStatus.values.map(_.wire).mkString("|")}").orNone,
      Opts.option[Int]("offset", "Skip this many").orNone, Opts.option[Int]("limit", "How many").orNone, Output.jsonFlag).mapN {
      (st, off, lim, json) => (c, id) => c.call(ManagersApi.items)((id, st, off, lim)).map(p => Output.emit(json, p)(ManagersMarkdown.items))
    }) orElse
    verb("events", "Its events, newest first")((Opts.option[Long]("before", "Only events older than this id").orNone,
      Opts.option[Int]("limit", "How many").orNone).mapN { (b, lim) => (c, id) =>
      c.call(ManagersApi.events)((id, b, lim)).map(p => println(ManagersMarkdown.events(p)))
    }) orElse
    verb("pause", "Start no new runs (its runs in flight go on, unless --runs)")(
      Opts.flag("runs", "Pause its runs in flight too").orFalse.map(r => (c, id) =>
        c.call(ManagersApi.action)((id, "pause", Some(r))).map(m => println(ManagersMarkdown.line(m))))) orElse
    verb("resume", "Start runs again (and resume the runs a pause --runs paused)")(Opts.unit.map(_ => (c, id) =>
      c.call(ManagersApi.action)((id, "resume", None)).map(m => println(ManagersMarkdown.line(m))))) orElse
    verb("stop", "Cancel its runs in flight and its open items")(Opts.unit.map(_ => (c, id) =>
      c.call(ManagersApi.action)((id, "stop", None)).map(m => println(ManagersMarkdown.line(m))))) orElse
    verb("hint", "A note its runs' fixer and skeptic read (standing unless --once); --suggest: the server's model proposes " +
      "the action it asks for (hold, prioritise, skip, settings), applied only when you approve it")((Opts.argument[String]("text"),
      Opts.option[String]("item", "Only about this target (or item id)").orNone,
      Opts.flag("once", "For the next run only").orFalse,
      Opts.flag("suggest", "Let the server's planner model propose a structured action (an approval you decide)").orFalse)
      .mapN { (text, item, once, suggest) => (c, id) =>
      c.call(ManagersApi.addHint)((id, HintAdd(text, item, Some(once), None, Some("cli"), suggest = Some(suggest))))
        .map(h => println(ManagersMarkdown.hint(h)))
    }) orElse
    verb("enqueue", "Drive a target: kind review|filter|extend <article> (review manager), enrich <citekey> (enrich), " +
      "propose <goal> (seed), sweep|currency <goal> (watch); a settled one is re-opened")((
      Opts.argument[String]("kind"), target, Opts.option[Int]("priority", "Its priority (default 10: first)").orNone).mapN {
      (kind, t, p) => (c, id) => c.call(ManagersApi.addItem)((id, EnqueueRequest(kind, t, p))).map(i => println(ManagersMarkdown.item(i)))
    }) orElse
    verb("hold", "Hold an item: until a date, until a source (DOI or citekey) is in the corpus, or until released")((target,
      Opts.option[String]("until", "A date (2026-11-01) or an instant").orNone,
      Opts.option[String]("until-source", "A DOI or citekey").orNone,
      Opts.option[String]("reason", "Why").orNone).mapN { (t, u, s, r) => (c, id) =>
      itemPatch(c, id, t, ItemPatch("hold", until = u, untilSource = s, reason = r))
    }) orElse
    verb("release", "Release a held item")(target.map(t => (c, id) => itemPatch(c, id, t, ItemPatch("release")))) orElse
    verb("skip", "Stop an item as skipped (its run, if any, is cancelled)")((target, Opts.option[String]("reason", "Why").orNone)
      .mapN((t, r) => (c, id) => itemPatch(c, id, t, ItemPatch("skip", reason = r)))) orElse
    verb("reopen", "Bring a settled item back (its runs counted from zero)")(target.map(t => (c, id) =>
      itemPatch(c, id, t, ItemPatch("reopen")))) orElse
    verb("priority", "Set an item's priority (higher first)")((target, Opts.argument[Int]("priority")).mapN((t, p) => (c, id) =>
      itemPatch(c, id, t, ItemPatch("priority", priority = Some(p))))) orElse
    verb("cron", "When it fires (UTC): five fields, or @daily/@weekly/@monthly; \"\" clears it. A watcher re-opens its " +
      "sweeps, the review manager refills, a seeding manager proposes again")(Opts.argument[String]("cron").map(t => (c, id) =>
      c.call(ManagersApi.update)((id, ManagerUpdate(cron = Some(t)))).map(m => println(ManagersMarkdown.line(m))))) orElse
    verb("config", s"Replace its settings: key=value pairs (none: back to the server's defaults): $SettingKeys")(
      Opts.arguments[String]("key=value").orEmpty.map(kvs => (c, id) =>
        config(kvs).flatMap(cfg => c.call(ManagersApi.update)((id, ManagerUpdate(Some(cfg))))).map(m => println(ManagersMarkdown.line(m)))))

  val manager: Opts[Client => Either[CliError, Unit]] = Opts.subcommand("manager",
      "One process manager: new, or <id> status|items|events|pause|resume|stop|hint|enqueue|hold|release|skip|reopen|priority|cron|config") {
    create orElse (Opts.argument[String]("id"), verbs.orElse(Opts((c: Client, id: String) =>
      c.call(ManagersApi.get)(id).map(d => println(ManagersMarkdown.detail(d)))))).mapN { (id, f) => (c: Client) => f(c, id) }
  }
