package com.histmersion
package cli
package commandline
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.util.problem.{Legacy, Problem}
import hearth.kindlings.optics.syntax.*
import com.histmersion.config.*
import com.histmersion.api.automation.Queue.{QueueAdd, QueueImport, QueuePatch}
import java.nio.file.{Files, Path}
import com.histmersion.api.automation.{GoalsExport, Queue}
import com.histmersion.api.checks.{FormatCheck, FormatField, FormatMarkdown, FormatReport, Lint}
import com.histmersion.api.library.{AcquireChecksMarkdown, Citekey, Query}
import com.histmersion.api.research.ReviewGoal
import com.histmersion.api.library.Acquire
import com.histmersion.util.console.Console
import com.histmersion.api.Api
import com.histmersion.api.library.AcquisitionWorkApi
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.Problem
import org.rogach.scallop.*
import scala.util.chaining.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Creation

/** The command line of [[Ops]]. */
object UpkeepArgs:
  object LintEntry extends CommandLine.Entry:
    val name = "lint"
    def help: String = s"Health-check the corpus (${Lint.all.size} rules: ${Lint.names.mkString(", ")})"
    def conf(): Subcommand = LeafLint()

  object QueryEntry extends CommandLine.Entry:
    val name = "query"
    def help: String = "Run ONE read-only SQL statement (a write is refused by Postgres itself)"
    def conf(): Subcommand = LeafQuery()

  object QueueEntry extends CommandLine.Entry:
    val name = "queue"
    // no `|` in it: tools/gen-cli-docs.sh pastes it into a Markdown table cell
    def help: String =
      "Bookkeeping queues: acquire / tasks / log — show, add, done, edit, rm, export (with the goals as GOALS.md), import; acquire recheck: the legal re-check of the acquisition list (more than one item, or --all for every item past the caps, as a server job it follows)"
    def conf(): Subcommand = GroupQueue()

  object SchemaEntry extends CommandLine.Entry:
    val name = "schema"
    def help: String = "List the tables and columns `query` can read (from information_schema)"
    def conf(): Subcommand = LeafSchema()

  final private class LeafLint()
      extends Leaf(
        "histmersion",
        "lint",
        s"Health-check the corpus (${Lint.all.size} rules: ${Lint.names.mkString(", ")})"
      )
      with SharedFlags:
    private val rules_ = values[String]("rule", "Run only this rule (repeatable)")
    private val fix_ = flag(
      "fix",
      "First apply the safe repairs: resolve short edge targets, reindex stale chunks; with --rule finding-geocode also move the findings it lists, with --rule finding-unplaced give a point to the findings it lists, with --rule place-kind reclassify and merge the places it lists, with --rule place-grid-point move the places it lists, with --rule place-imported-point-area remove the places it lists (with --article/--source/--file: the formatting repairs)"
    )
    private val article_ = value[String]("article", "Check only this article's formatting (a slug)")
    private val source_ =
      value[String]("source", "Check only this source's summary and notes for formatting problems (a citekey)")
    private val file_ =
      value[String]("file", "Check a local Markdown file's formatting before it is stored (--fix rewrites the file)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(
      Ops.Command.Lint(
        rules_.getOrElse(Nil),
        fix_.getOrElse(false),
        article_.toOption,
        source_.toOption,
        file_.toOption,
        json_()
      )
    )

  final private class LeafQuery()
      extends Leaf("histmersion", "query", "Run ONE read-only SQL statement (a write is refused by Postgres itself)")
      with SharedFlags:
    private val sql_ = arg[String]("sql")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Ops.Command.Query(sql_(), json_()))

  final private class LeafSchema()
      extends Leaf("histmersion", "schema", "List the tables and columns `query` can read (from information_schema)")
      with SharedFlags:
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Ops.Command.Schema(json_()))

  // ── queue ─────────────────────────────────────────────────────────────────────────────────────

  private val QueueParent = "histmersion queue"

  final private class GroupQueue() extends Group("histmersion", "queue", QueueEntry.help):
    holds(
      (Queue.Kind.values.toList.map(k => LeafList(k.label)) ++
        List(LeafDone(), LeafEdit(), LeafPatch(), LeafRm(), LeafExport(), LeafImport()))*
    )

  /** `queue <list> [--all]` shows it; `queue <list> add …` and `queue acquire recheck …` are its subcommands. */
  final private class LeafList(list: Queue.Kind.Label)
      extends Leaf(QueueParent, list.value, s"Show the ${list.value} list (add: put an item on it)"):
    private val all_ = flag(
      "all",
      "Include done items and the file's prose blocks"
    )
    private val json_ = json()
    holds(
      LeafAdd(list) :: (if list == Queue.Kind.Acquire.label then List(LeafRecheck(), LeafWhy()) else Nil)*
    )

    def command: Either[Problem, Cli.Command] = Right(Ops.QueueCmd.Show(list, all_.getOrElse(false), json_()))

  final private class LeafAdd(list: Queue.Kind.Label)
      extends Leaf(s"$QueueParent ${list.value}", "add", s"Add an item to the ${list.value} list"):
    private val text_ = arg[String]("text")
    private val topic_ = value[String]("topic", "Group the added item under this heading")
    private val reason_ = value[String]("reason", "Why this item is on the list / what research direction it serves")
    private val byPlugin_ = value[Creation.Plugin](
      "by-plugin",
      "An automated caller: the plugin it runs in (else $HISTMERSION_AGENT_PLUGIN)"
    )
    private val byWorkflow_ =
      value[Creation.Workflow](
        "by-workflow",
        "An automated caller: its workflow (else $HISTMERSION_AGENT_WORKFLOW)"
      )
    private val byAgent_ = value[Creation.Definition](
      "by-agent",
      "The agent's definition, with --by-provider and --by-model (else $HISTMERSION_AGENT_DEFINITION)"
    )
    private val byProvider_ =
      value[Creation.Provider]("by-provider", "The agent's AI provider (else $HISTMERSION_AGENT_PROVIDER)")
    private val byModel_ = value[Creation.Model]("by-model", "The agent's model (else $HISTMERSION_AGENT_MODEL)")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] =
      if text_().trim.isEmpty then Left(Problem.Invalid.Missing.here(Problem.Field("text")))
      else
        Right(
          Ops.QueueCmd.Add(
            list,
            Queue.QueueItem.Text(text_()),
            topic_.toOption.map(Queue.QueueItem.Topic(_)),
            reason_.toOption.map(t => Queue.Rationale(t)),
            AgentIdentity(
              byPlugin_.toOption,
              byWorkflow_.toOption,
              byAgent_.toOption,
              byProvider_.toOption,
              byModel_.toOption
            ),
            json_()
          )
        )

  final private class LeafRecheck()
      extends Leaf(
        s"$QueueParent acquire",
        "recheck",
        "The legal re-check of the acquisition list now: the due items the caps allow, or --all (more than one item runs as a server job it follows)"
      ):
    private val all_ = flag("all", "Every open item with a DOI, due or not, past the caps (a server job)")
    private val writes_ = dryRun("List what it would close and try; no fetcher is asked, nothing stored")
    private val limit_ = value[Int]("limit", "Try at most this many due items (default acquire.recheck.per-pass)")
    private val id_ = value[Int]("id", "This acquisition item only, due or not")
    private val following_ = noWait("Print the job id and return instead of following the job")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] =
      val all = all_.getOrElse(false)
      if limit_.toOption.exists(_ < 0) then
        Left(
          Problem.Invalid.OutOfRange(
            CommandLine.typeName[Int],
            Problem.Raw.of(limit_().toLong),
            Problem.Invalid.Bounds(Some(Problem.Raw("0")), None),
            Vector(Problem.Field("limit"))
          )
        )
      else if all && (id_.isDefined || limit_.isDefined) then
        Left(
          Problem.Invalid.Inconsistent.here(
            NonEmptyVector.of(Problem.Field("--all"), Problem.Field(if id_.isDefined then "--id" else "--limit")),
            Problem.Invalid.Exclusion.AtMostOne
          )
        )
      else
        Right(
          Ops.QueueCmd
            .Recheck(writes_(), limit_.toOption, id_.toOption, all = all, following_(), json_())
        )

  final private class LeafWhy()
      extends Leaf(
        s"$QueueParent acquire",
        "why",
        "Why an acquisition item is where it is: its verdict and what each provider answered at each attempt"
      ):
    private val id_ = arg[Queue.QueueItem.Id]("id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(StateCmd.Command.Why(id_(), json_()))

  final private class LeafDone() extends Leaf(QueueParent, "done", "Mark an item done"):
    private val id_ = arg[Queue.Id]("id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Ops.QueueCmd.Done(id_(), json_()))

  final private class LeafEdit() extends Leaf(QueueParent, "edit", "Replace an item's text"):
    private val id_ = arg[Queue.Id]("id")
    private val text_ = arg[String]("text")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] =
      if text_().trim.isEmpty then Left(Problem.Invalid.Missing.here(Problem.Field("text")))
      else Right(Ops.QueueCmd.Edit(id_(), Queue.QueueItem.Text(text_()), json_()))

  final private class LeafPatch() extends Leaf(QueueParent, "patch", "Change who acts on an item"):
    private val id_ = arg[Queue.Id]("id")
    private val actor_ = value[Creation.Actor]("actor", "Who does the task: person | agents")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Ops.QueueCmd.Patch(id_(), actor_.toOption, json_()))

  final private class LeafRm() extends Leaf(QueueParent, "rm", "Remove an item"):
    private val id_ = arg[Queue.Id]("id")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(Ops.QueueCmd.Rm(id_(), json_()))

  final private class LeafExport()
      extends Leaf(QueueParent, "export", "Write ACQUIRE.md, TASKS.md, LOG.md and GOALS.md into a directory"):
    private val dir_ = arg[String]("dir")

    def command: Either[Problem, Cli.Command] = Right(Ops.QueueCmd.Export(dir_()))

  final private class LeafImport()
      extends Leaf(
        QueueParent,
        "import",
        "Read ACQUIRE.md, TASKS.md and LOG.md back from a directory (replacing the lists)"
      ):
    private val dir_ = arg[String]("dir")

    def command: Either[Problem, Cli.Command] = Right(Ops.QueueCmd.Import(dir_()))
