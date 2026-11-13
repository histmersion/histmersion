package com.histmersion
package cli
package commandline

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence
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
import com.histmersion.cli.CommandLine.{given, *}
import com.histmersion.util.problem.{NonEmptyVector, Problem}
import org.rogach.scallop.*
import scala.util.chaining.*

/** The command line of [[AgentCmd]]. */
object AgentArgs:
  object AgentEntry extends CommandLine.Entry:
    val name = "agent"
    def help: String = "Embedded research agents: models, allowance, quota, generate, doctor, probe, run, runs, show, compare, pause, resume, cancel, approvals, approve, reject, digest, decide-all, managers, manager, enqueue, autopilot, scheduler, stalls"
    def conf(): Subcommand = GroupAgent()

  /** `agent models` shows; `agent models set|reset` change a role's chain. */
  final private class LeafModels()
      extends Leaf(
        "histmersion agent",
        "models",
        "Each step role's model chain, the model its next call would use, and every allowance's use per window"
      )
      with SharedFlags:
    holds(LeafModelsSet(), LeafModelsReset())
    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.ModelsShow())

  final private class LeafModelsSet()
      extends Leaf(
        "histmersion agent models",
        "set",
        "Save a role's chain over the config: the models tried in order, first choice first"
      ):
    private val role_ = arg[String]("role")
    private val models_ = args[String]("provider:model", required = true)
    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.ModelsSet(role_(), models_()))

  final private class LeafModelsReset()
      extends Leaf(
        "histmersion agent models",
        "reset",
        "Drop a role's saved chain: the server's config (agents.roles.<role>) applies again"
      ):
    private val role_ = arg[String]("role")
    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.ModelsReset(role_()))

  final private class LeafAllowance() extends Leaf("histmersion agent", "allowance", "Save the allowance of a model (provider:model) or of a provider (provider:*): tokens and/or USD per rolling window and a reserve below which the chain's next model is used; --remove goes back to config") with SharedFlags:
    private val target_ = arg[String]("target")
    private val limits_ = values[String]("limit", "A limit: '<amount> tokens/<window>' or '<amount> usd/<window>' (window: 5h, 7d, day, week, month); repeatable; none = unlimited")
    private val reserve_ = value[Int]("reserve", "Stop using it once less than this % of any limit is left (0-99)")
    private val remove_ = flag("remove", "Drop the saved allowance: the server's config (agents.allowances) applies again")

    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.Allowance(target_(), limits_.getOrElse(Nil), reserve_.getOrElse(0), remove_.getOrElse(false)))

  private val Pools = PlanQuota.PoolId.values.map(_.wire).mkString(", ")

  /** `agent quota` shows; `agent quota hold|release|floors|fallthrough|refresh` change or read the pools. */
  final private class LeafQuota()
      extends Leaf(
        "histmersion agent",
        "quota",
        "Subscription pools (Claude plan, ChatGPT plan): each window's use, its floor, reset and reading age; the hold switch; metered allowances"
      )
      with SharedFlags:
    holds(LeafQuotaHold(), LeafQuotaRelease(), LeafQuotaFloors(), LeafQuotaFallthrough(), LeafQuotaRefresh())
    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.QuotaShow())

  final private class LeafQuotaHold()
      extends Leaf(
        "histmersion agent quota",
        "hold",
        s"Hold a plan pool: no agent run draws on it until it is released ($Pools)"
      ):
    private val pool_ = arg[String]("pool")
    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.QuotaHold(pool_()))

  final private class LeafQuotaRelease() extends Leaf("histmersion agent quota", "release", "Release a held plan pool"):
    private val pool_ = arg[String]("pool")
    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.QuotaRelease(pool_()))

  /** The floors, or --reset: exactly one. */
  final private class LeafQuotaFloors()
      extends Leaf(
        "histmersion agent quota",
        "floors",
        "Save a plan pool's floors — the share of each window that must stay left, e.g. 'five_hour=30, seven_day=25' (Codex windows by length: '300m=25, 10080m=20'); --reset goes back to config"
      ):
    private val pool_ = arg[String]("pool")
    private val floors_ = arg[String]("floors", required = false)
    private val reset_ = flag("reset", "Drop the saved floors")
    def command: Either[Problem, Cli.Command] =
      (floors_.toOption, reset_.getOrElse(false)) match
        case (Some(f), false) => Right(AgentCmd.Command.QuotaFloors(pool_(), AgentCmd.QuotaFloorsChange.Set(f)))
        case (None, true)     => Right(AgentCmd.Command.QuotaFloors(pool_(), AgentCmd.QuotaFloorsChange.Reset))
        case _                =>
          Left(
            Problem.Invalid.Inconsistent(
              NonEmptyVector.of(Problem.Field("floors"), Problem.Field("--reset")),
              Problem.Invalid.Exclusion.OneOf
            )
          )

  /** `on|off`: a choice, its converter refusing any other word. */
  final private class LeafQuotaFallthrough()
      extends Leaf(
        "histmersion agent quota",
        "fallthrough",
        "Whether a role's chain may fall from a plan below its floor to a PAID key (off: the run pauses until the reset)"
      ):
    private given ValueConverter[Boolean] = reading(v =>
      v.trim.toLowerCase match
        case "on"  => Right(true)
        case "off" => Right(false)
        case other => Left(s"'$other' is neither on nor off")
    )
    private val role_ = arg[String]("role")
    private val on_ = arg[Boolean]("on|off")
    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.QuotaFallthrough(role_(), on_()))

  final private class LeafQuotaRefresh()
      extends Leaf(
        "histmersion agent quota",
        "refresh",
        s"Take a fresh reading of a plan pool now, or of every pool ($Pools): the ChatGPT plan through codex app-server (spends nothing), the Claude plan through one minimal claude -p turn (spends a little of the plan)"
      ):
    private val pool_ = arg[String]("pool", required = false)
    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.QuotaRefresh(pool_.toOption))

  final private class LeafGenerate() extends Leaf("histmersion agent", "generate", "Write the per-runtime agent definitions (Claude Code plugins, Codex home, opencode config) from the server's bundled agents and skills — on the SERVER's disk") with SharedFlags:
    private val rts_ = values[String]("runtime", "Only this runtime: claude-code, codex or opencode (repeatable; default all three)")
    private val out_ = value[String]("out", "A directory on the server to write to instead of its runtimes directory")

    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.Generate(rts_.getOrElse(Nil), out_.toOption))

  final private class LeafDoctor() extends Leaf("histmersion agent", "doctor", "Check every provider without spending anything: keys, the free model listing, prices, the claude CLI and its login") with SharedFlags:
    private val offline_ = flag("offline", "Skip the providers' model listings (no network)")

    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.Doctor(offline_.getOrElse(false)))

  final private class LeafProbe() extends Leaf("histmersion agent", "probe", "One live model call with one read-only tool (get_source) and a typed answer, checked against the record") with SharedFlags:
    private val ck_ = arg[String]("citekey")
    private val model_ = modelFlag()

    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.Probe(ck_(), model_()))

  final private class LeafRun()
      extends Leaf(
        "histmersion agent",
        "run",
        s"Start a pipeline on a target (${Pipeline.values.map(p => s"${p.wire} <${p.targetKind.wire}>").mkString(", ")})"
      )
      with SharedFlags:
    private val pipeline_ = arg[String]("pipeline")
    private val target_ = arg[String]("target")
    private val model_ = modelFlag()
    private val usd_ = value[Double]("budget-usd", "This run's cost cap in USD (default: agents.budgets.run-usd)")
    private val tokens_ = value[Long]("budget-tokens", "This run's token cap (default: agents.budgets.run-tokens)")
    private val fix_ = flag(
      "fix",
      "review-article: let the fixer revise the article between passes, each fix verified by the reviewers"
    )
    private val rounds_ = value[Int](
      "rounds",
      "review-article: at most this many review passes: a full panel, then a verification pass after each fix (default: agents.review.max-rounds)"
    )
    private val item_ =
      value[Int]("acquire-item", "enrich-source: the acquisition item this run fulfils (ticked off when it finishes)")
    private val title_ = value[String]("title", "compile-article: the title of a NEW article")
    private val kind_ =
      value[String]("kind", s"compile-article: the kind of a new article (${Articles.Kinds.mkString(", ")})")
    private val query_ = value[String]("query", "compile-article: free-text filter for the sources to compile from")
    private val topics_ = values[String]("topic", "compile-article: a topic the sources must have (repeatable)")
    private val tags_ = values[String]("tag", "compile-article: a tag the sources must have (repeatable)")
    private val goal_ = value[String]("goal", "The goal the run works for (a compiled article is linked to it)")
    private val dry_ = flag(
      "dry-run",
      "Do every read and every model step but write nothing to the corpus; each write is recorded on its step (agent show)"
    )
    private val modelFor_ = values[String](
      "model-for",
      s"role=provider:model: one role's model for this run only, over its chain and over --model (repeatable; roles: ${Role.values.map(_.wire).mkString(", ")})"
    )
    private val follow_ = followFlag()

    def command: Either[Problem, Cli.Command] =
      Right(
        AgentCmd.Command.Run(
          pipeline_(),
          target_(),
          model_(),
          usd_.toOption,
          tokens_.toOption,
          fix_.getOrElse(false),
          rounds_.toOption,
          item_.toOption,
          title_.toOption,
          kind_.toOption,
          query_.toOption,
          topics_.getOrElse(Nil),
          tags_.getOrElse(Nil),
          goal_.toOption,
          dry_.getOrElse(false),
          modelFor_.getOrElse(Nil),
          follow_()
        )
      )

  final private class LeafRuns() extends Leaf("histmersion agent", "runs", "Agent runs, newest first, filtered on the server") with SharedFlags:
    private val f_ = runFilter()
    private val limit_ = value[Int]("limit", "How many")
    private val before_ = beforeFlag("run")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.Runs(f_(), limit_.toOption, before_(), json_()))

  final private class LeafShow() extends Leaf("histmersion agent", "show", "One run: its steps — tokens (with the first call's input and the turns), cost — and warnings (and --messages: the trace)") with SharedFlags:
    private val id_ = arg[String]("id")
    private val msgs_ = flag("messages", "Include the traced messages and tool calls")
    private val fol_ = followFlag()

    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.Show(id_(), msgs_.getOrElse(false), fol_()))

  final private class LeafCompare() extends Leaf("histmersion agent", "compare", "Runs of one pipeline on one target side by side: per step the model (configured → reported), tokens, the first call's input and the turns, cost or plan pool, time and attempts; then the outputs (enrich-source: findings, quote check, places, periods, summary and notes, the claims only one run made; review-article: verdicts, findings per reviewer and kind, the major findings runs share)") with SharedFlags:
    private val ids_ = args[String]("run-id", required = true)
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.Compare(ids_(), json_()))

  /** `agent pause|resume|cancel <id>`; resume can raise the caps first. */
  final private class LeafControl(action: Agents.Action, help: String)
      extends Leaf("histmersion agent", action.value, help):
    private val id_ = arg[String]("id")
    private val caps = Option.when(action.value == "resume")(
      (
        value[Double]("budget-usd", "Raise the run's cost cap before resuming"),
        value[Long]("budget-tokens", "Raise the run's token cap before resuming")
      )
    )
    def command: Either[Problem, Cli.Command] =
      Right(
        AgentCmd.Command.Control(action, id_(), caps.flatMap(_._1.toOption), caps.flatMap(_._2.toOption))
      )

  final private class LeafApprovals() extends Leaf("histmersion agent", "approvals", "Changes runs proposed for a person to decide (statuses, disagreement edges, goal plans, articles to publish, …), filtered on the server; the open ones by default") with SharedFlags:
    private val f_ = approvalFilter()
    private val limit_ = value[Int]("limit", "How many")
    private val before_ = beforeFlag("approval")
    private val json_ = json()

    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.Approvals(f_(), limit_.toOption, before_(), json_()))

  /** `agent approve|reject <id>…`. */
  final private class LeafDecide(d: Decision, help: String) extends Leaf("histmersion agent", d.wire.value, help):
    private val ids_ = args[Long]("id", required = true)
    private val note_ = value[String]("note", "Why — kept with the decision (with every id)")
    def command: Either[Problem, Cli.Command] = Right(AgentCmd.Command.Decide(d, ids_(), note_.toOption))

  final private class GroupAgent() extends Group("histmersion", "agent", "Embedded research agents: models, allowance, quota, generate, doctor, probe, run, runs, show, compare, pause, resume, cancel, approvals, approve, reject, digest, decide-all, managers, manager, enqueue, autopilot, scheduler, stalls"):
    holds(
      (List(
        LeafModels(),
        LeafAllowance(),
        LeafQuota(),
        LeafGenerate(),
        LeafDoctor(),
        LeafProbe(),
        LeafRun(),
        LeafRuns(),
        LeafShow(),
        LeafCompare(),
        LeafControl(Agents.Action("pause"), "Pause a run before its next step"),
        LeafControl(Agents.Action("resume"), "Resume a paused run (finished steps are replayed, not re-run)"),
        LeafControl(Agents.Action("cancel"), "Cancel a run"),
        LeafApprovals(),
        LeafDecide(
          Decision.Approve,
          "Approve proposed changes (one or more ids): a source change is applied through the ordinary patch; a plan or an article lets its waiting run go on and apply it"
        ),
        LeafDecide(Decision.Reject, "Reject proposed changes (one or more ids; they stay on record)")
      ) ++ ManagerArgs.leaves())*
    )
