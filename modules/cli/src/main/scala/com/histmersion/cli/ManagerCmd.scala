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
    Opts.unit.map(_ => (c: Client) => c.call(ManagersApi.list)(()).map(ms => println(ManagersMarkdown.list(ms))))
  }

  private def config(kvs: List[String]): Either[CliError, ManagerConfig] =
    ManagerConfig.parse(kvs).left.map(m => CliError(m, exitCode = ExitCode.Usage))

  private val create = Opts.subcommand("new", "Create a manager: review (library-wide, id 'review'; created paused) — seed and watch come later") {
    (Opts.argument[String]("kind"), Opts.option[String]("goal", "The research area (seed, watch)").orNone,
      Opts.options[String]("config", "A setting key=value (repeatable): max-runs, rounds, max-runs-per-article, no-progress-runs, reopen-limit, cooldown-minutes, low-water, refill-batch, max-failures, budget-usd, tags, goal, models").orEmpty,
      Opts.flag("active", "Start it active (default: paused — nothing is spent until you resume it)").orFalse)
      .mapN { (kind, goal, kvs, active) => (c: Client) =>
        config(kvs).flatMap(cfg => c.call(ManagersApi.create)(ManagerCreate(kind, goal, cfg, Some(!active))))
          .map(m => println(ManagersMarkdown.line(m)))
      }
  }

  /** The verbs of one manager; each takes the manager's id first. */
  private def verb(name: String, help: String)(rest: Opts[(Client, String) => Either[CliError, Unit]]) =
    Opts.subcommand(name, help)(rest)

  private val target = Opts.argument[String]("target")

  private def itemPatch(c: Client, id: String, t: String, p: ItemPatch) =
    c.call(ManagersApi.patchItem)((id, t, p)).map(i => println(ManagersMarkdown.item(i)))

  private val verbs: Opts[(Client, String) => Either[CliError, Unit]] =
    verb("status", "The manager: items in flight with their runs, hints, newest events")(Opts.unit.map(_ => (c, id) =>
      c.call(ManagersApi.get)(id).map(d => println(ManagersMarkdown.detail(d))))) orElse
    verb("items", "Its items, the most urgent first")((
      Opts.option[String]("status", s"Only items in this state: ${ItemStatus.values.map(_.wire).mkString("|")}").orNone,
      Opts.option[Int]("offset", "Skip this many").orNone, Opts.option[Int]("limit", "How many").orNone).mapN {
      (st, off, lim) => (c, id) => c.call(ManagersApi.items)((id, st, off, lim)).map(p => println(ManagersMarkdown.items(p)))
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
    verb("hint", "A note its runs' fixer and skeptic read (standing unless --once)")((Opts.argument[String]("text"),
      Opts.option[String]("item", "Only about this target (or item id)").orNone,
      Opts.flag("once", "For the next run only").orFalse).mapN { (text, item, once) => (c, id) =>
      c.call(ManagersApi.addHint)((id, HintAdd(text, item, Some(once), None, Some("cli")))).map(h => println(ManagersMarkdown.hint(h)))
    }) orElse
    verb("enqueue", "Drive a target (an article slug for the review manager); a settled one is re-opened")((
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
    verb("config", "Replace its settings: key=value pairs (none: back to the server's defaults)")(
      Opts.arguments[String]("key=value").orEmpty.map(kvs => (c, id) =>
        config(kvs).flatMap(cfg => c.call(ManagersApi.update)((id, ManagerUpdate(Some(cfg))))).map(m => println(ManagersMarkdown.line(m)))))

  val manager: Opts[Client => Either[CliError, Unit]] = Opts.subcommand("manager",
      "One process manager: new, or <id> status|items|events|pause|resume|stop|hint|enqueue|hold|release|skip|reopen|priority|config") {
    create orElse (Opts.argument[String]("id"), verbs.orElse(Opts((c: Client, id: String) =>
      c.call(ManagersApi.get)(id).map(d => println(ManagersMarkdown.detail(d)))))).mapN { (id, f) => (c: Client) => f(c, id) }
  }
