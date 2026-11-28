package com.histmersion
package cli
package commandline

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.problem.{Legacy, Problem, Problems}
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
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The command line of [[ManagerCmd]]. */
@atBoundary(
  BoundaryKind.CliArgs,
  "the scallop adapter of `histmersion manager`: declares its options, parses them into typed commands"
)
object ManagerArgs:
  /** `agent digest|decide-all|managers|manager|enqueue|autopilot|scheduler|stalls`, held by `agent`. */
  def leaves(): List[Subcommand] =
    List(
      LeafDigest(),
      LeafDecideAll(),
      LeafManagers(),
      LeafManager(),
      LeafEnqueue(),
      AutopilotLeaf(),
      SchedulerLeaf(),
      LeafStalls()
    )

  private val Agent = "histmersion agent"

  final private class LeafManagers()
      extends Leaf(
        Agent,
        "managers",
        "The process managers: each one's state, items, runs in flight, status line and last event"
      )
      with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(ManagerCmd.Command.Managers(json_()))

  final private class LeafEnqueue()
      extends Leaf(
        Agent,
        "enqueue",
        "Hand a target to its manager, as the page buttons do: review|extend <article>, enrich <citekey|article|goal> " +
          "(the unenriched sources it cites or links), seed <goal> (propose its next articles); a missing manager is created paused"
      )
      with SharedFlags:
    private val kind_ = arg[String]("kind")
    private val t_ = arg[String]("target")
    private val p_ = value[Int]("priority", "Its priority (default 10: first)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(ManagerCmd.Command.Enqueue(kind_(), t_(), p_.toOption, json_()))

  final private class LeafStalls()
      extends Leaf(
        Agent,
        "stalls",
        "The unfinished research goals with no progress in N days (default agents.autopilot.stall-days), each with the " +
          "one reason it stands still — draining, waiting for a person, held, paused, a budget, quota, the scheduler's " +
          "queue, a block, a failed item, no candidates, idle — and the command that unblocks it"
      )
      with SharedFlags:
    private val days_ = value[Int]("days", "No progress in this many days")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(ManagerCmd.Command.Stalls(days_.toOption, json_()))

  final private class LeafDigest()
      extends Leaf(
        Agent,
        "digest",
        "Everything waiting for a person in one view: the open approvals by kind (how many, the oldest, who decides them " +
          "now, the subjects with the most, the ids) and the manager items held for a person; once a day it is also " +
          "written to the worklog (agents.autopilot.digest-hour)"
      )
      with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(ManagerCmd.Command.Digest(json_()))

  final private class LeafDecideAll()
      extends Leaf(
        Agent,
        "decide-all",
        "Approve or reject every OPEN approval that matches at once — of a kind, about a subject, or the ids given (they " +
          "narrow together; one is needed), the oldest first, each through the same decision a single one takes"
      )
      with SharedFlags:
    private val d_ = arg[String]("approve|reject")
    private val kind_ = value[String]("kind", "Only this kind (goal-plan, article-publish, …)")
    private val subject_ = value[String]("subject", "Only approvals about this goal or article")
    private val ids_ = values[Long]("id", "Only these approvals (repeatable)")
    private val note_ = value[String]("note", "Why — kept with every decision")
    private val limit_ = value[Int]("limit", "At most this many (the oldest first)")
    private val writes_ = dryRun("List what it would decide; decide nothing")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      ManagerCmd.Command.DecideAll(
        d_(),
        kind_.toOption,
        subject_.toOption,
        ids_.getOrElse(Nil),
        note_.toOption,
        limit_.toOption,
        writes_(),
        json_()
      )
    )

  // ── manager ────────────────────────────────────────────────────────────────────────────────────

  /** `agent manager <id>` shows one; `agent manager <id> <verb> …` steers it; `agent manager new …` creates one. */
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  final private class LeafManager()
      extends Leaf(
        Agent,
        "manager",
        "One process manager: new, or <id> status|items|events|pause|resume|stop|hint|enqueue|hold|release|skip|reopen|priority|cron|config"
      ):
    private val id_ = arg[String]("id", required = false)
    private val id = () => id_.toOption
    holds(
      LeafCreate(),
      LeafVerb("status", "The manager: items in flight with their runs, hints, newest events", id)(v =>
        val j = v.json(); () => ManagerCmd.ManagerVerb.Status(j())
      ),
      LeafVerb("items", "Its items, the most urgent first", id)(v =>
        val st = v.value[String]("status", s"Only items in this state: ${ItemStatus.values.map(_.wire).mkString("|")}")
        val off = v.value[Int]("offset", "Skip this many")
        val lim = v.value[Int]("limit", "How many")
        val j = v.json()
        () => ManagerCmd.ManagerVerb.Items(st.toOption, off.toOption, lim.toOption, j())
      ),
      LeafVerb("events", "Its events, newest first", id)(v =>
        val b = v.value[Long]("before", "Only events older than this id")
        val lim = v.value[Int]("limit", "How many")
        () => ManagerCmd.ManagerVerb.Events(b.toOption, lim.toOption)
      ),
      LeafVerb("pause", "Start no new runs (its runs in flight go on, unless --runs)", id)(v =>
        val r = v.flag("runs", "Pause its runs in flight too")
        () => ManagerCmd.ManagerVerb.Pause(r.getOrElse(false))
      ),
      LeafVerb("resume", "Start runs again (and resume the runs a pause --runs paused)", id)(_ =>
        () => ManagerCmd.ManagerVerb.Resume
      ),
      LeafVerb("stop", "Cancel its runs in flight and its open items", id)(_ => () => ManagerCmd.ManagerVerb.Stop),
      LeafVerb(
        "hint",
        "A note its runs' fixer and skeptic read (standing unless --once); --suggest: the server's model proposes " +
          "the action it asks for (hold, prioritise, skip, settings), applied only when you approve it",
        id
      )(v =>
        val text = v.arg[String]("text")
        val item = v.value[String]("item", "Only about this target (or item id)")
        val once = v.flag("once", "For the next run only")
        val suggest =
          v.flag("suggest", "Let the server's planner model propose a structured action (an approval you decide)")
        () => ManagerCmd.ManagerVerb.Hint(text(), item.toOption, once.getOrElse(false), suggest.getOrElse(false))
      ),
      LeafVerb(
        "enqueue",
        "Drive a target: kind review|filter|extend <article> (review manager), enrich <citekey> (enrich), " +
          "propose <goal> (seed), sweep|currency <goal> (watch); a settled one is re-opened",
        id
      )(v =>
        val kind = v.arg[String]("kind")
        val target = v.arg[String]("target")
        val p = v.value[Int]("priority", "Its priority (default 10: first)")
        () => ManagerCmd.ManagerVerb.Enqueue(kind(), target(), p.toOption)
      ),
      LeafVerb(
        "hold",
        "Hold an item: until a date, until a source (DOI or citekey) is in the corpus, or until released",
        id
      )(v =>
        val target = v.arg[String]("target")
        val u = v.value[String]("until", "A date (2026-11-01) or an instant")
        val s = v.value[String]("until-source", "A DOI or citekey")
        val r = v.value[String]("reason", "Why")
        () => ManagerCmd.ManagerVerb.Hold(target(), u.toOption, s.toOption, r.toOption)
      ),
      LeafVerb("release", "Release a held item", id)(v =>
        val target = v.arg[String]("target"); () => ManagerCmd.ManagerVerb.Release(target())
      ),
      LeafVerb("skip", "Stop an item as skipped (its run, if any, is cancelled)", id)(v =>
        val target = v.arg[String]("target")
        val r = v.value[String]("reason", "Why")
        () => ManagerCmd.ManagerVerb.Skip(target(), r.toOption)
      ),
      LeafVerb("reopen", "Bring a settled item back (its runs counted from zero)", id)(v =>
        val target = v.arg[String]("target"); () => ManagerCmd.ManagerVerb.Reopen(target())
      ),
      LeafVerb("priority", "Set an item's priority (higher first)", id)(v =>
        val target = v.arg[String]("target")
        val p = v.arg[Int]("priority")
        () => ManagerCmd.ManagerVerb.Priority(target(), p())
      ),
      LeafVerb(
        "cron",
        "When it fires (UTC): five fields, or @daily/@weekly/@monthly; \"\" clears it. A watcher re-opens its " +
          "sweeps, the review manager refills, a seeding manager proposes again",
        id
      )(v =>
        val cron = v.arg[String]("cron"); () => ManagerCmd.ManagerVerb.Cron(cron())
      ),
      LeafVerb(
        "config",
        s"Replace its settings: key=value pairs (none: back to the server's defaults): ${ManagerCmd.SettingKeys}",
        id
      )(v =>
        val kvs = v.args[String]("key=value")
        () => ManagerCmd.ManagerVerb.Config(kvs.getOrElse(Nil))
      )
    )

    def command: Either[Problem, Cli.Command] =
      id_.toOption
        .toRight(Problem.Invalid.Missing.here(Problem.Field("id")))
        .map(ManagerCmd.Command.Manager(_, ManagerCmd.ManagerVerb.Show))

  /** One verb of `agent manager <id>`: `declare` declares its options and returns the verb to read after parsing. */
  final private class LeafVerb(name: String, help: String, id: () => Option[String])(
      declare: LeafVerb => () => ManagerCmd.ManagerVerb
  ) extends Leaf(s"$Agent manager <id>", name, help):
    private val verb = declare(this)
    def command: Either[Problem, Cli.Command] =
      id().toRight(Problem.Invalid.Missing.here(Problem.Field("id"))).map(ManagerCmd.Command.Manager(_, verb()))

  final private class LeafCreate()
      extends Leaf(
        s"$Agent manager",
        "new",
        "Create a manager: review (library-wide, id 'review'), enrich (library-wide, id 'enrich'), seed --goal g (id 'seed:g'; " +
          "starts by proposing the area's articles), watch --goal g (id 'watch:g'; an acquisition sweep and a currency check " +
          "on its cron, weekly by default; auto-ingest of legal open access only with --config auto-ingest=on); created paused"
      ):
    private val kind_ = arg[String]("review|enrich|seed|watch")
    private val goal_ = value[String]("goal", "The research area (seed, watch)")
    private val config_ = values[String]("config", s"A setting key=value (repeatable): ${ManagerCmd.SettingKeys}")
    private val cron_ =
      value[String]("cron", "When it fires: five fields in UTC, or @daily/@weekly/@monthly (watch: default @weekly)")
    private val active_ = flag("active", "Start it active (default: paused — nothing is spent until you resume it)")
    private val json_ = json()

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def command: Either[Problem, Cli.Command] =
      Right(
        ManagerCmd.Command.Create(
          kind_(),
          goal_.toOption,
          config_.getOrElse(Nil),
          cron_.toOption,
          active_.getOrElse(false),
          json_()
        )
      )

  // ── autopilot ──────────────────────────────────────────────────────────────────────────────────

  /** `agent autopilot` is its status; each other verb is a subcommand. */
  final private class AutopilotLeaf()
      extends Leaf(
        Agent,
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
      ):
    private val json_ = json()
    holds(
      LeafAutopilotVerb(
        "status",
        "Per research goal its stage, managers, sources, articles, approvals, activity and pace"
      )(_ => () => Right(ManagerCmd.AutopilotVerb.Status)),
      LeafAutopilotVerb("on", "Every goal gets an active seeding manager and watcher")(_ =>
        () => Right(ManagerCmd.AutopilotVerb.On)
      ),
      LeafAutopilotVerb("off", "Pause the seeding managers and watchers it runs")(_ =>
        () => Right(ManagerCmd.AutopilotVerb.Off)
      ),
      LeafAutopilotVerb("drain", "Nothing new starts while the runs in flight finish")(_ =>
        () => Right(ManagerCmd.AutopilotVerb.Drain)
      ),
      LeafAutopilotVerb("undrain", "Lift the drain")(_ => () => Right(ManagerCmd.AutopilotVerb.Undrain)),
      LeafAutopilotVerb(
        "repair-acquire",
        "Move the acquisition items a watcher's sweep created for legal open-access works into the ingest backlog"
      )(v =>
        val writes = v.dryRun("List what it would do, change nothing")
        () => Right(ManagerCmd.AutopilotVerb.RepairAcquire(writes()))
      ),
      LeafAutopilotVerb(
        "focus",
        "The goals active for model work: list | pin <goal> | unpin <goal> | priority <goal> <n> | max <n|default>"
      )(v =>
        val rest = v.args[String]("list|pin|unpin|priority|max [goal] [n]")
        () => ManagerCmd.focusRequest(rest.getOrElse(Nil)).left.map(_.problems).map(ManagerCmd.AutopilotVerb.Focus(_))
      ),
      LeafAutopilotVerb("mode", "working, finish (current work, then pause) or pause (now)")(v =>
        val m = v.arg[String]("working|finish|pause")
        () => Right(ManagerCmd.AutopilotVerb.Mode(m()))
      )
    )

    def command: Either[Problem, Cli.Command] = Right(
      ManagerCmd.Command.Autopilot(ManagerCmd.AutopilotVerb.Status, json_())
    )

  final private class LeafAutopilotVerb(name: String, help: String)(
      declare: LeafAutopilotVerb => () => Either[Problems, ManagerCmd.AutopilotVerb]
  ) extends Leaf(s"$Agent autopilot", name, help):
    private val json_ = json()
    private val verb = declare(this)

    def command: Either[Problems, Cli.Command] = verb().map(ManagerCmd.Command.Autopilot(_, json_()))

  // ── scheduler ──────────────────────────────────────────────────────────────────────────────────

  /** `agent scheduler` is its status; queue, why and strategy are subcommands. */
  final private class SchedulerLeaf()
      extends Leaf(
        Agent,
        "scheduler",
        "The central scheduler that admits the managers' work across every job type: status (default) — the strategy, " +
          "the runs at once and plan pools, the job types' shares, what the last pass started and the queue with why each " +
          "item waits; queue — only the waiting items, in the order they would start; why <item> — one item (its id, or the " +
          "target of an open item): waiting behind what, running, waiting for a person, held, settled; strategy " +
          "<type-balanced|goal-first|default> — switch it live (default: back to agents.scheduler.strategy)"
      ):
    private val limit_ = value[Int]("limit", "How many waiting items to show (status: 50, queue: 200)")
    private val json_ = json()
    holds(LeafSchedulerStatus(), LeafSchedulerQueue(), LeafSchedulerWhy(), LeafSchedulerStrategy())

    def command: Either[Problem, Cli.Command] = Right(ManagerCmd.Command.SchedulerStatus(limit_.toOption, json_()))

  final private class LeafSchedulerStatus()
      extends Leaf(s"$Agent scheduler", "status", "The strategy, pools, shares, the last pass and the queue"):
    private val limit_ = value[Int]("limit", "How many waiting items to show (default 50)")
    private val json_ = json()
    def command: Either[Problem, Cli.Command] = Right(ManagerCmd.Command.SchedulerStatus(limit_.toOption, json_()))

  final private class LeafSchedulerQueue()
      extends Leaf(s"$Agent scheduler", "queue", "Only the waiting items, in the order they would start"):
    private val limit_ = value[Int]("limit", "How many waiting items to show (default 200)")
    private val json_ = json()
    def command: Either[Problem, Cli.Command] = Right(ManagerCmd.Command.SchedulerQueue(limit_.toOption, json_()))

  final private class LeafSchedulerWhy()
      extends Leaf(s"$Agent scheduler", "why", "One item (its id, or the target of an open item): why it waits"):
    private val item_ = arg[String]("item")
    private val json_ = json()
    def command: Either[Problem, Cli.Command] = Right(ManagerCmd.Command.SchedulerWhy(item_(), json_()))

  final private class LeafSchedulerStrategy()
      extends Leaf(
        s"$Agent scheduler",
        "strategy",
        "Switch the strategy live: type-balanced, goal-first or default (back to agents.scheduler.strategy)"
      ):
    private val name_ = arg[String]("name")
    private val json_ = json()
    def command: Either[Problem, Cli.Command] = Right(ManagerCmd.Command.SchedulerStrategy(name_(), json_()))
