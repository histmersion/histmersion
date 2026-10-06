package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*

/** `histmersion agent …` — the embedded research agents (docs/research-embedded-agents.md). The server
  * runs them; this only starts, follows and steers runs over HTTP. Off unless the SERVER has
  * `HISTMERSION_AGENTS_ENABLED=true`. */
object AgentCmd:

  /** How often `--follow` polls. This process's own cadence, not a server tunable. */
  private val PollMs = 2000

  private val models = Opts.subcommand("models",
      "Each step role's model chain, the model its next call would use, and every allowance's use per window") {
    val set = Opts.subcommand("set", "Save a role's chain over the config: the models tried in order, first choice first") {
      (Opts.argument[String]("role"), Opts.arguments[String]("provider:model")).mapN { (role, ms) => (c: Client) =>
        c.call(AgentsApi.setChain)((role, ModelChains.ChainSet(ms.toList))).map(r => println(AgentsMarkdown.roleChain(r)))
      }
    }
    val reset = Opts.subcommand("reset", "Drop a role's saved chain: the server's config (agents.roles.<role>) applies again") {
      Opts.argument[String]("role").map { role => (c: Client) =>
        c.call(AgentsApi.resetChain)(role).map(r => println(AgentsMarkdown.roleChain(r)))
      }
    }
    val show = Opts.unit.map(_ => (c: Client) => c.call(AgentsApi.models)(()).map(m => println(AgentsMarkdown.models(m))))
    set orElse reset orElse show
  }

  private val allowance = Opts.subcommand("allowance",
      "Save the allowance of a model (provider:model) or of a provider (provider:*): tokens and/or USD per rolling window and a reserve below which the chain's next model is used; --remove goes back to config") {
    (Opts.argument[String]("target"),
      Opts.options[String]("limit", "A limit: '<amount> tokens/<window>' or '<amount> usd/<window>' (window: 5h, 7d, day, week, month); repeatable; none = unlimited").orEmpty,
      Opts.option[Int]("reserve", "Stop using it once less than this % of any limit is left (0-99)").withDefault(0),
      Opts.flag("remove", "Drop the saved allowance: the server's config (agents.allowances) applies again").orFalse)
      .mapN { (target, limits, reserve, remove) => (c: Client) =>
        if remove then
          if limits.nonEmpty then Left(CliError("--remove takes no --limit", exitCode = 1))
          else c.call(AgentsApi.removeAllowance)(target).map(r =>
            println(if r.removed then s"The saved allowance of $target is removed." else s"No saved allowance for $target."))
        else
          limits.traverse(l => ModelChains.Limit.parse(l)).left.map(m => CliError(m, exitCode = 1)).flatMap { ls =>
            c.call(AgentsApi.setAllowance)(ModelChains.AllowanceSet(target, ls.map(_.spec), reserve))
              .map(a => println(AgentsMarkdown.allowance(a)))
          }
      }
  }

  private val generate = Opts.subcommand("generate",
      "Write the per-runtime agent definitions (Claude Code plugins, Codex home, opencode config) from the server's bundled agents and skills — on the SERVER's disk") {
    (Opts.options[String]("runtime", "Only this runtime: claude-code, codex or opencode (repeatable; default all three)").orEmpty,
      Opts.option[String]("out", "A directory on the server to write to instead of its runtimes directory").orNone).mapN { (rts, out) =>
      (c: Client) => c.call(AgentsApi.generate)(GenerateRequest(rts, out)).map(r => println(AgentsMarkdown.generated(r)))
    }
  }

  private val quota = Opts.subcommand("quota",
      "Subscription pools (Claude plan, ChatGPT plan): each window's use, its floor, reset and reading age; the hold switch; metered allowances") {
    val pools = PlanQuota.PoolId.values.map(_.wire).mkString(", ")
    val hold = Opts.subcommand("hold", s"Hold a plan pool: no agent run draws on it until it is released ($pools)") {
      Opts.argument[String]("pool").map(p => (c: Client) => c.call(AgentsApi.setPool)((p, PlanQuota.PoolSet(hold = Some(true)))).map(x => println(AgentsMarkdown.pool(x))))
    }
    val release = Opts.subcommand("release", "Release a held plan pool") {
      Opts.argument[String]("pool").map(p => (c: Client) => c.call(AgentsApi.setPool)((p, PlanQuota.PoolSet(hold = Some(false)))).map(x => println(AgentsMarkdown.pool(x))))
    }
    val floors = Opts.subcommand("floors",
        "Save a plan pool's floors — the share of each window that must stay left, e.g. 'five_hour=30, seven_day=25' (Codex windows by length: '300m=25, 10080m=20'); --reset goes back to config") {
      (Opts.argument[String]("pool"), Opts.argument[String]("floors").orNone, Opts.flag("reset", "Drop the saved floors").orFalse)
        .mapN { (p, f, reset) => (c: Client) =>
          (f, reset) match
            case (None, false) => Left(CliError("give the floors, or --reset", exitCode = 1))
            case (Some(_), true) => Left(CliError("--reset takes no floors", exitCode = 1))
            case _ => c.call(AgentsApi.setPool)((p, PlanQuota.PoolSet(floors = Some(f.getOrElse(""))))).map(x => println(AgentsMarkdown.pool(x)))
        }
    }
    val fallthrough = Opts.subcommand("fallthrough",
        "Whether a role's chain may fall from a plan below its floor to a PAID key (off: the run pauses until the reset)") {
      (Opts.argument[String]("role"), Opts.argument[String]("on|off")).mapN { (role, v) => (c: Client) =>
        v.trim.toLowerCase match
          case "on" | "off" => c.call(AgentsApi.setFallthrough)((role, PlanQuota.FallthroughSet(v.trim.equalsIgnoreCase("on"))))
                                 .map(q => println(AgentsMarkdown.quota(q)))
          case other => Left(CliError(s"'$other' is neither on nor off", exitCode = 1))
      }
    }
    val refresh = Opts.subcommand("refresh",
        s"Take a fresh reading of a plan pool now, or of every pool ($pools): the ChatGPT plan through codex app-server (spends nothing), the Claude plan through one minimal claude -p turn (spends a little of the plan)") {
      Opts.argument[String]("pool").orNone.map(p => (c: Client) =>
        c.call(AgentsApi.refreshQuota)(PlanQuota.QuotaRefresh(p)).map(r => println(AgentsMarkdown.refreshed(r))))
    }
    val show = Opts.unit.map(_ => (c: Client) => c.call(AgentsApi.quota)(()).map(q => println(AgentsMarkdown.quota(q))))
    hold orElse release orElse floors orElse fallthrough orElse refresh orElse show
  }

  private val doctor = Opts.subcommand("doctor",
      "Check every provider without spending anything: keys, the free model listing, prices, the claude CLI and its login") {
    Opts.flag("offline", "Skip the providers' model listings (no network)").orFalse.map { offline => (c: Client) =>
      c.call(AgentsApi.doctor)(Some(offline)).map(d => println(AgentsMarkdown.doctor(d)))
    }
  }

  private[cli] val modelOpt = Opts.option[String]("model", "provider:model for this call (default: the role's configured model)").orNone

  private val probe = Opts.subcommand("probe",
      "One live model call with one read-only tool (get_source) and a typed answer, checked against the record") {
    (Opts.argument[String]("citekey"), modelOpt).mapN { (ck, model) => (c: Client) =>
      c.call(AgentsApi.probe)(ProbeRequest(ck, model)).map(p => println(AgentsMarkdown.probe(p)))
    }
  }

  private[cli] val followOpt = Opts.flag("follow", "Wait for the run to finish (or pause, or wait for an approval), printing its progress").orFalse

  /** Poll a run until it is no longer queued or running; a failed or cancelled run exits 2. */
  @annotation.tailrec
  private[cli] def follow(c: Client, id: String, last: String): Either[CliError, Unit] =
    c.call(AgentsApi.run)(id) match
      case Left(e) => Left(e)
      case Right(d) =>
        val line = AgentsMarkdown.runLine(d.run)
        if line != last then Console.err.println(line)
        RunState.parse(d.run.status) match
          case Some(RunState.Queued) | Some(RunState.Running) =>
            Thread.sleep(PollMs.toLong)
            follow(c, id, line)
          case Some(RunState.Done) | Some(RunState.Paused) | Some(RunState.WaitingApproval) =>
            println(AgentsMarkdown.run(d, withMessages = false)); Right(())
          case _ =>
            println(AgentsMarkdown.run(d, withMessages = false))
            Left(CliError(s"agent run $id ${d.run.status}: ${d.run.error.getOrElse("")}", exitCode = 2))

  private val run = Opts.subcommand("run", s"Start a pipeline on a target (${Pipeline.values.map(p => s"${p.wire} <${p.targetKind.wire}>").mkString(", ")})") {
    val budgets = (
      Opts.option[Double]("budget-usd", "This run's cost cap in USD (default: agents.budgets.run-usd)").orNone,
      Opts.option[Long]("budget-tokens", "This run's token cap (default: agents.budgets.run-tokens)").orNone).tupled
    val review = (
      Opts.flag("fix", "review-article: let the fixer revise the article between panel rounds").orFalse,
      Opts.option[Int]("rounds", "review-article: at most this many panel rounds (default: agents.review.max-rounds)").orNone).tupled
    val acquire = Opts.option[Int]("acquire-item", "enrich-source: the acquisition item this run fulfils (ticked off when it finishes)").orNone
    val compile = (
      Opts.option[String]("title", "compile-article: the title of a NEW article").orNone,
      Opts.option[String]("kind", s"compile-article: the kind of a new article (${Articles.Kinds.mkString(", ")})").orNone,
      Opts.option[String]("query", "compile-article: free-text filter for the sources to compile from").orNone,
      Opts.options[String]("topic", "compile-article: a topic the sources must have (repeatable)").orEmpty,
      Opts.options[String]("tag", "compile-article: a tag the sources must have (repeatable)").orEmpty).tupled
    val goal = Opts.option[String]("goal", "The goal the run works for (a compiled article is linked to it)").orNone
    (Opts.argument[String]("pipeline"), Opts.argument[String]("target"), modelOpt, budgets, review, acquire, compile, goal,
      followOpt)
      .mapN { (pipeline, target, model, b, rv, item, cp, g, fol) => (c: Client) =>
        c.call(AgentsApi.startRun)(RunRequest(pipeline, target, model, b._1, b._2, Option.when(rv._1)(true), rv._2, item,
            cp._1, cp._2, cp._3, cp._4, cp._5, g))
          .flatMap { r => if fol then follow(c, r.id, "") else Right(println(AgentsMarkdown.runLine(r))) }
      }
  }

  private val approvals = Opts.subcommand("approvals",
      "Changes runs proposed for a person to decide (statuses, disagreement edges, goal plans, articles to publish); the open ones by default") {
    (Opts.option[String]("state", s"Only approvals in this state: ${(ApprovalState.values.map(_.wire) :+ "all").mkString("|")}").orNone,
      Opts.option[Int]("limit", "How many").orNone).mapN { (state, limit) => (c: Client) =>
        c.call(AgentsApi.approvals)((state, limit)).map(as => println(AgentsMarkdown.approvals(as)))
      }
  }

  private def decide(d: Decision, help: String) = Opts.subcommand(d.wire, help) {
    (Opts.argument[Long]("id"), Opts.option[String]("note", "Why — kept with the decision").orNone).mapN { (id, note) =>
      (c: Client) => c.call(AgentsApi.decide)((id, d.wire, DecisionRequest(note))).map(a => println(AgentsMarkdown.approvalLine(a)))
    }
  }

  private val runs = Opts.subcommand("runs", "Agent runs, newest first") {
    (Opts.option[String]("status", s"Only runs in this state: ${RunState.values.map(_.wire).mkString("|")} (waiting = for an approval)").orNone,
      Opts.option[Int]("limit", "How many").orNone,
      Opts.option[String]("goal", "Only the runs for this goal").orNone).mapN { (status, limit, goal) => (c: Client) =>
        c.call(AgentsApi.runs)((status, limit, goal)).map(rs => println(AgentsMarkdown.runs(rs)))
      }
  }

  private val show = Opts.subcommand("show", "One run: its steps, tokens, cost, warnings (and --messages: the trace)") {
    (Opts.argument[String]("id"), Opts.flag("messages", "Include the traced messages and tool calls").orFalse, followOpt)
      .mapN { (id, msgs, fol) => (c: Client) =>
        if fol then follow(c, id, "")
        else c.call(AgentsApi.run)(id).map(d => println(AgentsMarkdown.run(d, msgs)))
      }
  }

  private def control(action: String, help: String) = Opts.subcommand(action, help) {
    val budget = if action == "resume" then
        (Opts.option[Double]("budget-usd", "Raise the run's cost cap before resuming").orNone,
          Opts.option[Long]("budget-tokens", "Raise the run's token cap before resuming").orNone).tupled
      else Opts((Option.empty[Double], Option.empty[Long]))
    (Opts.argument[String]("id"), budget).mapN { (id, b) => (c: Client) =>
      c.call(AgentsApi.control)((id, action, b._1, b._2)).map(r => println(AgentsMarkdown.runLine(r)))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("agent",
        "Embedded research agents: models, allowance, quota, generate, doctor, probe, run, runs, show, pause, resume, cancel, approvals, approve, reject") {
      models orElse allowance orElse quota orElse generate orElse doctor orElse probe orElse run orElse runs orElse show orElse
        control("pause", "Pause a run before its next step") orElse
        control("resume", "Resume a paused run (finished steps are replayed, not re-run)") orElse
        control("cancel", "Cancel a run") orElse approvals orElse
        decide(Decision.Approve, "Approve a proposed change: a source change is applied through the ordinary patch; a plan or an article lets its waiting run go on and apply it") orElse
        decide(Decision.Reject, "Reject a proposed change (it stays on record)")
    }
