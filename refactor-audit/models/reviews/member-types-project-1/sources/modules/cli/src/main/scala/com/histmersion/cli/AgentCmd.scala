package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.config.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.{AgentsApi, AgentsMarkdown, ModelChains, PlanQuota}
import com.histmersion.api.research.Articles
import com.histmersion.util.console.Console

/** `histmersion agent …` — the embedded research agents (docs/research-embedded-agents.md). The server runs them; this
  * only starts, follows and steers runs over HTTP. Off unless the SERVER has `HISTMERSION_AGENTS_ENABLED=true`.
  */
object AgentCmd:

  /** How often `--follow` polls. This process's own cadence, not a server tunable. */
  type FollowId = FollowId.T
  object FollowId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowLast = FollowLast.T
  object FollowLast extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideHelp = DecideHelp.T
  object DecideHelp extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BeforeOptWhat = BeforeOptWhat.T
  object BeforeOptWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlAction = ControlAction.T
  object ControlAction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ControlHelp = ControlHelp.T
  object ControlHelp extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val PollMs = 2000

  private def models(using console: Console) = Opts.subcommand(
    "models",
    "Each step role's model chain, the model its next call would use, and every allowance's use per window"
  ) {
    val set =
      Opts.subcommand("set", "Save a role's chain over the config: the models tried in order, first choice first") {
        (Opts.argument[String]("role"), Opts.arguments[String]("provider:model")).mapN { (role, ms) => (c: Client) =>
          c.call(AgentsApi.setChain)((role, ModelChains.ChainSet(ms.toList)))
            .map(r => Cli.out.println(AgentsMarkdown.roleChain(r)))
        }
      }
    val reset =
      Opts.subcommand("reset", "Drop a role's saved chain: the server's config (agents.roles.<role>) applies again") {
        Opts.argument[String]("role").map { role => (c: Client) =>
          c.call(AgentsApi.resetChain)(role).map(r => Cli.out.println(AgentsMarkdown.roleChain(r)))
        }
      }
    val show =
      Opts.unit.map(_ =>
        (c: Client) => c.call(AgentsApi.models)(()).map(m => Cli.out.println(AgentsMarkdown.models(m)))
      )
    set orElse reset orElse show
  }

  private def allowance(using console: Console) = Opts.subcommand(
    "allowance",
    "Save the allowance of a model (provider:model) or of a provider (provider:*): tokens and/or USD per rolling window and a reserve below which the chain's next model is used; --remove goes back to config"
  ) {
    (
      Opts.argument[String]("target"),
      Opts
        .options[String](
          "limit",
          "A limit: '<amount> tokens/<window>' or '<amount> usd/<window>' (window: 5h, 7d, day, week, month); repeatable; none = unlimited"
        )
        .orEmpty,
      Opts.option[Int]("reserve", "Stop using it once less than this % of any limit is left (0-99)").withDefault(0),
      Opts.flag("remove", "Drop the saved allowance: the server's config (agents.allowances) applies again").orFalse
    )
      .mapN { (target, limits, reserve, remove) => (c: Client) =>
        if remove then
          if limits.nonEmpty then Left(Cli.Error("--remove takes no --limit", exitCode = ExitCode.Usage))
          else
            c.call(AgentsApi.removeAllowance)(target)
              .map(r =>
                Cli.out.println(if r.removed then s"The saved allowance of $target is removed."
                else s"No saved allowance for $target.")
              )
        else
          limits
            .traverse(l => ModelChains.Limit.parse(l))
            .left
            .map(m => Cli.Error(m, exitCode = ExitCode.Usage))
            .flatMap { ls =>
              c.call(AgentsApi.setAllowance)(ModelChains.AllowanceSet(target, ls.map(_.spec), reserve))
                .map(a => Cli.out.println(AgentsMarkdown.allowance(a)))
            }
      }
  }

  private def generate(using console: Console) = Opts.subcommand(
    "generate",
    "Write the per-runtime agent definitions (Claude Code plugins, Codex home, opencode config) from the server's bundled agents and skills — on the SERVER's disk"
  ) {
    (
      Opts
        .options[String]("runtime", "Only this runtime: claude-code, codex or opencode (repeatable; default all three)")
        .orEmpty,
      Opts.option[String]("out", "A directory on the server to write to instead of its runtimes directory").orNone
    ).mapN { (rts, out) => (c: Client) =>
      c.call(AgentsApi.generate)(GenerateRequest(rts, out)).map(r => Cli.out.println(AgentsMarkdown.generated(r)))
    }
  }

  private def quota(using console: Console) = Opts.subcommand(
    "quota",
    "Subscription pools (Claude plan, ChatGPT plan): each window's use, its floor, reset and reading age; the hold switch; metered allowances"
  ) {
    val pools = PlanQuota.PoolId.values.map(_.wire).mkString(", ")
    val hold = Opts.subcommand("hold", s"Hold a plan pool: no agent run draws on it until it is released ($pools)") {
      Opts
        .argument[String]("pool")
        .map(p =>
          (c: Client) =>
            c.call(AgentsApi.setPool)((p, PlanQuota.PoolSet(hold = Some(true))))
              .map(x => Cli.out.println(AgentsMarkdown.pool(x)))
        )
    }
    val release = Opts.subcommand("release", "Release a held plan pool") {
      Opts
        .argument[String]("pool")
        .map(p =>
          (c: Client) =>
            c.call(AgentsApi.setPool)((p, PlanQuota.PoolSet(hold = Some(false))))
              .map(x => Cli.out.println(AgentsMarkdown.pool(x)))
        )
    }
    val floors = Opts.subcommand(
      "floors",
      "Save a plan pool's floors — the share of each window that must stay left, e.g. 'five_hour=30, seven_day=25' (Codex windows by length: '300m=25, 10080m=20'); --reset goes back to config"
    ) {
      (
        Opts.argument[String]("pool"),
        Opts.argument[String]("floors").orNone,
        Opts.flag("reset", "Drop the saved floors").orFalse
      )
        .mapN { (p, f, reset) => (c: Client) =>
          (f, reset) match
            case (None, false)   => Left(Cli.Error("give the floors, or --reset", exitCode = ExitCode.Usage))
            case (Some(_), true) => Left(Cli.Error("--reset takes no floors", exitCode = ExitCode.Usage))
            case _               =>
              c.call(AgentsApi.setPool)((p, PlanQuota.PoolSet(floors = Some(f.getOrElse("")))))
                .map(x => Cli.out.println(AgentsMarkdown.pool(x)))
        }
    }
    val fallthrough = Opts.subcommand(
      "fallthrough",
      "Whether a role's chain may fall from a plan below its floor to a PAID key (off: the run pauses until the reset)"
    ) {
      (Opts.argument[String]("role"), Opts.argument[String]("on|off")).mapN { (role, v) => (c: Client) =>
        v.trim.toLowerCase match
          case "on" | "off" =>
            c.call(AgentsApi.setFallthrough)((role, PlanQuota.FallthroughSet(v.trim.equalsIgnoreCase("on"))))
              .map(q => Cli.out.println(AgentsMarkdown.quota(q)))
          case other => Left(Cli.Error(s"'$other' is neither on nor off", exitCode = ExitCode.Usage))
      }
    }
    val refresh = Opts.subcommand(
      "refresh",
      s"Take a fresh reading of a plan pool now, or of every pool ($pools): the ChatGPT plan through codex app-server (spends nothing), the Claude plan through one minimal claude -p turn (spends a little of the plan)"
    ) {
      Opts
        .argument[String]("pool")
        .orNone
        .map(p =>
          (c: Client) =>
            c.call(AgentsApi.refreshQuota)(PlanQuota.QuotaRefresh(p))
              .map(r => Cli.out.println(AgentsMarkdown.refreshed(r)))
        )
    }
    val show =
      Opts.unit.map(_ => (c: Client) => c.call(AgentsApi.quota)(()).map(q => Cli.out.println(AgentsMarkdown.quota(q))))
    hold orElse release orElse floors orElse fallthrough orElse refresh orElse show
  }

  private def doctor(using console: Console) = Opts.subcommand(
    "doctor",
    "Check every provider without spending anything: keys, the free model listing, prices, the claude CLI and its login"
  ) {
    Opts.flag("offline", "Skip the providers' model listings (no network)").orFalse.map { offline => (c: Client) =>
      c.call(AgentsApi.doctor)(Some(offline)).map(d => Cli.out.println(AgentsMarkdown.doctor(d)))
    }
  }

  private[cli] val modelOpt =
    Opts.option[String]("model", "provider:model for this call (default: the role's configured model)").orNone

  private def probe(using console: Console) = Opts.subcommand(
    "probe",
    "One live model call with one read-only tool (get_source) and a typed answer, checked against the record"
  ) {
    (Opts.argument[String]("citekey"), modelOpt).mapN { (ck, model) => (c: Client) =>
      c.call(AgentsApi.probe)(ProbeRequest(ck, model)).map(p => Cli.out.println(AgentsMarkdown.probe(p)))
    }
  }

  private[cli] val followOpt =
    Opts.flag("follow", "Wait for the run to finish (or pause, or wait for an approval), printing its progress").orFalse

  /** Poll a run until it is no longer queued or running; a failed or cancelled run exits 2. */
  @annotation.tailrec
  private[cli] def follow(c: Client, id: com.histmersion.cli.AgentCmd.FollowId, last: com.histmersion.cli.AgentCmd.FollowLast)(using console: Console): Either[Cli.Error, Unit] =
    c.call(AgentsApi.run)(id) match
      case Left(e)  => Left(e)
      case Right(d) =>
        val line = AgentsMarkdown.runLine(d.run)
        if line != last then Cli.err.println(line)
        RunState.parse(d.run.status) match
          case Some(RunState.Queued) | Some(RunState.Running) =>
            Thread.sleep(PollMs.toLong)
            follow(c, id, line)
          case Some(RunState.Done) | Some(RunState.Paused) | Some(RunState.WaitingApproval) =>
            Cli.out.println(AgentsMarkdown.run(d, withMessages = false)); Right(())
          case _ =>
            Cli.out.println(AgentsMarkdown.run(d, withMessages = false))
            Left(Cli.Error(s"agent run $id ${d.run.status}: ${d.run.error.getOrElse("")}", exitCode = ExitCode.Refused))

  private def run(using console: Console) = Opts.subcommand(
    "run",
    s"Start a pipeline on a target (${Pipeline.values.map(p => s"${p.wire} <${p.targetKind.wire}>").mkString(", ")})"
  ) {
    val budgets = (
      Opts.option[Double]("budget-usd", "This run's cost cap in USD (default: agents.budgets.run-usd)").orNone,
      Opts.option[Long]("budget-tokens", "This run's token cap (default: agents.budgets.run-tokens)").orNone
    ).tupled
    val review = (
      Opts
        .flag(
          "fix",
          "review-article: let the fixer revise the article between passes, each fix verified by the reviewers"
        )
        .orFalse,
      Opts
        .option[Int](
          "rounds",
          "review-article: at most this many review passes: a full panel, then a verification pass after each fix (default: agents.review.max-rounds)"
        )
        .orNone
    ).tupled
    val acquire = Opts
      .option[Int]("acquire-item", "enrich-source: the acquisition item this run fulfils (ticked off when it finishes)")
      .orNone
    val compile = (
      Opts.option[String]("title", "compile-article: the title of a NEW article").orNone,
      Opts
        .option[String]("kind", s"compile-article: the kind of a new article (${Articles.Kinds.mkString(", ")})")
        .orNone,
      Opts.option[String]("query", "compile-article: free-text filter for the sources to compile from").orNone,
      Opts.options[String]("topic", "compile-article: a topic the sources must have (repeatable)").orEmpty,
      Opts.options[String]("tag", "compile-article: a tag the sources must have (repeatable)").orEmpty
    ).tupled
    val goal = Opts.option[String]("goal", "The goal the run works for (a compiled article is linked to it)").orNone
    val eval = (
      Opts
        .flag(
          "dry-run",
          "Do every read and every model step but write nothing to the corpus; each write is recorded on its step (agent show)"
        )
        .orFalse,
      Opts
        .options[String](
          "model-for",
          s"role=provider:model: one role's model for this run only, over its chain and over --model (repeatable; roles: ${Role.values.map(_.wire).mkString(", ")})"
        )
        .orEmpty
    ).tupled
    (
      Opts.argument[String]("pipeline"),
      Opts.argument[String]("target"),
      modelOpt,
      budgets,
      review,
      acquire,
      compile,
      goal,
      eval,
      followOpt
    )
      .mapN { (pipeline, target, model, b, rv, item, cp, g, ev, fol) => (c: Client) =>
        ev._2
          .traverse(ModelOverride.parse)
          .left
          .map(m => Cli.Error(s"--model-for $m", exitCode = ExitCode.Usage))
          .flatMap { over =>
            c.call(AgentsApi.startRun)(
              RunRequest(
                pipeline,
                target,
                model,
                b._1,
                b._2,
                Option.when(rv._1)(true),
                rv._2,
                item,
                cp._1,
                cp._2,
                cp._3,
                cp._4,
                cp._5,
                g,
                Option.when(ev._1)(true),
                over
              )
            ).flatMap(r => if fol then follow(c, r.id, "") else Right(Cli.out.println(AgentsMarkdown.runLine(r))))
          }
      }
  }

  private def approvals(using console: Console) = Opts.subcommand(
    "approvals",
    "Changes runs proposed for a person to decide (statuses, disagreement edges, goal plans, articles to publish, …), filtered on the server; the open ones by default"
  ) {
    (ListOpts.approvals, Opts.option[Int]("limit", "How many").orNone, beforeOpt("approval"), Output.jsonFlag)
      .mapN { (f, limit, before, json) => (c: Client) =>
        c.call(AgentsApi.approvals)((f, limit, before, None)).map { (as, page) =>
          Output.emit(json, as)(as =>
            AgentsMarkdown.approvals(
              as,
              page,
              f.qualifier,
              PageCmd.older(page, "agent approvals", f.options ++ List("limit" -> limit.map(_.toString)))
            )
          )
        }
      }
  }

  /** One decision per id, in the order given, each through the same endpoint (and checks) as a single one; a refused id
    * does not stop the rest, and makes the command fail at the end.
    */
  private def decide(d: Decision, help: com.histmersion.cli.AgentCmd.DecideHelp)(using console: Console) = Opts.subcommand(d.wire, help) {
    (Opts.arguments[Long]("id"), Opts.option[String]("note", "Why — kept with the decision (with every id)").orNone)
      .mapN { (ids, note) => (c: Client) =>
        val failed = ids.toList.flatMap { id =>
          c.call(AgentsApi.decide)((id, d.wire, DecisionRequest(note))) match
            case Right(a) => Cli.out.println(AgentsMarkdown.approvalLine(a)); None
            case Left(e)  => Cli.err.println(s"#$id: ${e.message}"); Some(e)
        }
        failed match
          case Nil        => Right(())
          case List(one)  => Left(one)
          case first :: _ =>
            Left(first.modify(_.message).setTo(s"${failed.size} of ${ids.size} not decided — ${first.message}"))
      }
  }

  private def runs(using console: Console) =
    Opts.subcommand("runs", "Agent runs, newest first, filtered on the server") {
      (ListOpts.runs, Opts.option[Int]("limit", "How many").orNone, beforeOpt("run"), Output.jsonFlag)
        .mapN { (f, limit, before, json) => (c: Client) =>
          c.call(AgentsApi.runs)((f, limit, before, None)).map { (rs, page) =>
            Output.emit(json, rs)(rs =>
              AgentsMarkdown.runs(
                rs,
                page,
                f.qualifier,
                PageCmd.older(page, "agent runs", f.options ++ List("limit" -> limit.map(_.toString)))
              )
            )
          }
        }
    }

  private def beforeOpt(what: com.histmersion.cli.AgentCmd.BeforeOptWhat) =
    Opts
      .option[String]("before", s"The next older page: the id of the last $what shown (the listing prints the command)")
      .orNone

  private def show(using console: Console) = Opts.subcommand(
    "show",
    "One run: its steps — tokens (with the first call's input and the turns), cost — and warnings (and --messages: the trace)"
  ) {
    (
      Opts.argument[String]("id"),
      Opts.flag("messages", "Include the traced messages and tool calls").orFalse,
      followOpt
    )
      .mapN { (id, msgs, fol) => (c: Client) =>
        if fol then follow(c, id, "")
        else c.call(AgentsApi.run)(id).map(d => Cli.out.println(AgentsMarkdown.run(d, msgs)))
      }
  }

  private def compare(using console: Console) = Opts.subcommand(
    "compare",
    "Runs of one pipeline on one target side by side: per step the model (configured → reported), tokens, the first call's input and the turns, cost or plan pool, time and attempts; then the outputs (enrich-source: findings, quote check, places, periods, summary and notes, the claims only one run made; review-article: verdicts, findings per reviewer and kind, the major findings runs share)"
  ) {
    (Opts.arguments[String]("run-id"), Output.jsonFlag).mapN { (ids, json) => (c: Client) =>
      c.call(AgentsApi.compare)(ids.toList).map(cmp => Output.emit(json, cmp)(AgentsMarkdown.compare))
    }
  }

  private def control(action: com.histmersion.cli.AgentCmd.ControlAction, help: com.histmersion.cli.AgentCmd.ControlHelp)(using console: Console) = Opts.subcommand(action, help) {
    val budget = if action == "resume" then
      (
        Opts.option[Double]("budget-usd", "Raise the run's cost cap before resuming").orNone,
        Opts.option[Long]("budget-tokens", "Raise the run's token cap before resuming").orNone
      ).tupled
    else Opts((Option.empty[Double], Option.empty[Long]))
    (Opts.argument[String]("id"), budget).mapN { (id, b) => (c: Client) =>
      c.call(AgentsApi.control)((id, action, b._1, b._2)).map(r => Cli.out.println(AgentsMarkdown.runLine(r)))
    }
  }

  def command(using console: Console): Opts[Client => Either[Cli.Error, Unit]] =
    Opts.subcommand(
      "agent",
      "Embedded research agents: models, allowance, quota, generate, doctor, probe, run, runs, show, compare, pause, resume, cancel, approvals, approve, reject, digest, decide-all, managers, manager, enqueue, autopilot, scheduler, stalls"
    ) {
      models orElse allowance orElse quota orElse generate orElse doctor orElse probe orElse run orElse runs orElse show orElse
        compare orElse
        control("pause", "Pause a run before its next step") orElse
        control("resume", "Resume a paused run (finished steps are replayed, not re-run)") orElse
        control("cancel", "Cancel a run") orElse approvals orElse
        decide(
          Decision.Approve,
          "Approve proposed changes (one or more ids): a source change is applied through the ordinary patch; a plan or an article lets its waiting run go on and apply it"
        ) orElse
        decide(Decision.Reject, "Reject proposed changes (one or more ids; they stay on record)") orElse
        ManagerCmd.digest orElse ManagerCmd.decideAll orElse
        ManagerCmd.managers orElse ManagerCmd.manager orElse ManagerCmd.enqueue orElse ManagerCmd.autopilot orElse
        ManagerCmd.scheduler orElse ManagerCmd.stalls
    }
