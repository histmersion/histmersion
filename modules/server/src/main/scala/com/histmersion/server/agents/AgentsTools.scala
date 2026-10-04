package com.histmersion.server.agents

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.core.*
import com.histmersion.core.Agents.{DecisionRequest, RunRequest}

/** The MCP half of the embedded agents: an external session can hand a whole pipeline to the server
  * with ONE call (`start_run` — e.g. the whole review panel of an article, instead of spawning four
  * subagents), check on it (`run_status`), and see and decide what runs proposed
  * (`list_approvals`, `decide_approval`). Same rule as every tool: a thin wrapper over
  * [[AgentsHandlers]], Markdown out. */
object AgentsTools:
  import com.histmersion.server.McpTools.given // honour Scala defaults when decoding tool arguments

  def apply(a: AgentsHandlers) =
    val start =
      tool("start_run")
        .description(
          "Start an embedded agent pipeline on the SERVER (off unless the server has agents enabled). " +
            s"Pipelines: ${Agents.Pipeline.values.map(p => s"`${p.wire}` <${p.targetKind.wire}> — ${p.describe}").mkString("; ")}. " +
            "Returns the run id at once; the run is checkpointed, budgeted and resumable. Check it with " +
            "`run_status`. Use instead of doing the same steps yourself when the server's agents are on.")
        .input[RunInput]
        .handle { in =>
          a.startRun(RunRequest(in.pipeline, in.target, in.model, in.budgetUsd, None, Option.when(in.fix)(true),
              in.rounds, in.acquireItem, in.title, in.kind, in.query, in.topics, in.tags, in.goal)) match
            case Right(r) => ToolResult.text(AgentsMarkdown.runLine(r))
            case Left(e)  => ToolResult.text(s"_${e.message}_")
        }

    val status =
      tool("run_status")
        .description(
          "Embedded agent runs: with `id`, one run's steps, tokens, cost, warnings (and its trace with " +
            "`messages: true`); without, the newest runs (optionally one `status`: " +
            s"${Agents.RunState.values.map(_.wire).mkString(", ")} — `waiting` = for an approval; or one `goal`'s).")
        .input[RunsInput]
        .handle { in =>
          in.id.map(_.trim).filter(_.nonEmpty) match
            case Some(id) => ToolResult.text(a.run(id).fold(e => s"_${e.message}_", AgentsMarkdown.run(_, in.messages)))
            case None     => ToolResult.text(a.runs(in.status, in.limit, in.goal).fold(e => s"_${e.message}_", AgentsMarkdown.runs))
        }

    val approvals =
      tool("list_approvals")
        .description(
          "Changes embedded agent runs PROPOSED and a person decides: a source's status, or a disagreement " +
            "edge (challenges, refutes, updates, superseded_by), each with the evidence and the verified " +
            "quote it rests on; a goal's plan (sub-goals, queries, works) or an article a run compiled, " +
            "which the run WAITS for. Default: the open ones; `state` = open, approved, rejected, failed or all.")
        .input[ApprovalsInput]
        .handle { in =>
          ToolResult.text(a.approvalsList(in.state, in.limit).fold(e => s"_${e.message}_", AgentsMarkdown.approvals))
        }

    val decide =
      tool("decide_approval")
        .description(
          "Approve or reject one open approval by id. Approving APPLIES the change through the ordinary " +
            "source patch (status or edge), or lets the waiting run apply its plan or write its article; " +
            "rejecting keeps it on record. Decide only what the user asked you to, or what you have " +
            "verified against the record yourself.")
        .input[DecideInput]
        .handle { in =>
          ToolResult.text(a.decide(in.id, in.decision, DecisionRequest(in.note))
            .fold(e => s"_${e.message}_", x => AgentsMarkdown.approvalLine(x)))
        }

    val links =
      tool("goal_links")
        .description(
          "Link a research goal to what it is about, or unlink it: a source (citekey), an article (slug), a " +
            "literature search query (what the goal's acquisition sweep searches for), or a queue item (id). " +
            "`remove: true` unlinks. Answers the goal with all its links and schedules.")
        .input[GoalLinkInput]
        .handle { in =>
          val kind = GoalPlans.LinkKind.parse(in.kind)
          val done = kind.toRight(ApiError(s"unknown link kind '${in.kind}' (source, article, query, queue-item)")).flatMap { k =>
            if in.remove then a.removeGoalLink(in.goal, k.wire, in.ref).map(_ => ())
            else a.addGoalLink(in.goal, GoalPlans.LinkAdd(k, in.ref, in.note)).map(_ => ())
          }
          ToolResult.text(done.flatMap(_ => a.goalView(in.goal)).fold(e => s"_${e.message}_", AgentsMarkdown.goalView))
        }

    val schedule =
      tool("goal_schedule")
        .description(
          "A research goal's schedules (run by the server only while its agents are enabled): `currency` " +
            "(assess + followups over the goal's sources; disagreement edges proposed for approval) and " +
            "`acquisition` (search the goal's queries; a model picks what to add to the acquisition list). " +
            "With `cron` (five fields in UTC, or @daily/@weekly/@monthly) sets one; `remove: true` deletes " +
            "it; with neither, lists every schedule. The daily/monthly cost caps are checked before each start.")
        .input[ScheduleInput]
        .handle { in =>
          val out = (in.goal, in.kind) match
            case (Some(g), Some(k)) if in.remove => a.removeSchedule(g, k).map(_ => AgentsMarkdown.schedules(a.schedules))
            case (Some(g), Some(k)) =>
              in.cron.toRight(ApiError("give `cron` to set a schedule (or `remove: true`)")).flatMap(c =>
                a.setSchedule(g, k, GoalPlans.ScheduleSet(c, in.enabled, in.budgetUsd)).map(s =>
                  AgentsMarkdown.scheduleLine(s)))
            case (None, None) => Right(AgentsMarkdown.schedules(a.schedules))
            case _            => Left(ApiError("give both `goal` and `kind`, or neither to list the schedules"))
          ToolResult.text(out.fold(e => s"_${e.message}_", identity))
        }

    List(start, status, approvals, decide, links, schedule)

  final case class RunInput(pipeline: String, target: String, model: Option[String] = None,
      budgetUsd: Option[Double] = None, fix: Boolean = false, rounds: Option[Int] = None,
      acquireItem: Option[Int] = None, title: Option[String] = None, kind: Option[String] = None,
      query: Option[String] = None, topics: List[String] = Nil, tags: List[String] = Nil,
      goal: Option[String] = None) derives KindlingsCodecAsObject
  object RunInput:
    given Schema[RunInput] = KindlingsSchema.derived[RunInput].schema
      .modify(_.pipeline)(_.description(s"The pipeline: ${Agents.Pipeline.values.map(_.wire).mkString(", ")}"))
      .modify(_.target)(_.description("What it works on: a citekey (enrich-source), an article slug (review-article; " +
        "compile-article — a new slug creates the article, an existing one is extended), a goal slug (plan-goal, " +
        "currency-check, acquisition-sweep)"))
      .modify(_.title)(_.description("compile-article: the title of a NEW article"))
      .modify(_.kind)(_.description("compile-article: the kind of a new article (concept, myth, culture, region, timeline, report, index)"))
      .modify(_.query)(_.description("compile-article: free-text filter for the sources to compile from"))
      .modify(_.topics)(_.description("compile-article: topics the sources must all have"))
      .modify(_.tags)(_.description("compile-article: tags the sources must all have"))
      .modify(_.goal)(_.description("The goal the run works for (a compiled article is linked to it)"))
      .modify(_.model)(_.description("provider:model for every step of this run (default: the configured roles)"))
      .modify(_.budgetUsd)(_.description("This run's cost cap in USD (default: the server's agents.budgets.run-usd)"))
      .modify(_.fix)(_.description("review-article: let the fixer revise the article between rounds"))
      .modify(_.rounds)(_.description("review-article: at most this many panel rounds (default: agents.review.max-rounds)"))
      .modify(_.acquireItem)(_.description("enrich-source: the acquisition item this run fulfils (ticked off at the end)"))

  final case class RunsInput(id: Option[String] = None, status: Option[String] = None, limit: Option[Int] = None,
      messages: Boolean = false, goal: Option[String] = None) derives KindlingsCodecAsObject
  object RunsInput:
    given Schema[RunsInput] = KindlingsSchema.derived[RunsInput].schema
      .modify(_.id)(_.description("One run's id; omit to list runs"))
      .modify(_.status)(_.description("List only runs in this state"))
      .modify(_.messages)(_.description("With id: include the traced messages and tool calls"))
      .modify(_.goal)(_.description("List only the runs for this goal"))

  final case class GoalLinkInput(goal: String, kind: String, ref: String, note: Option[String] = None,
      remove: Boolean = false) derives KindlingsCodecAsObject
  object GoalLinkInput:
    given Schema[GoalLinkInput] = KindlingsSchema.derived[GoalLinkInput].schema
      .modify(_.goal)(_.description("The goal's slug"))
      .modify(_.kind)(_.description("source | article | query | queue-item"))
      .modify(_.ref)(_.description("A citekey, an article slug, the query text, or a queue item id"))
      .modify(_.note)(_.description("Why it is linked"))
      .modify(_.remove)(_.description("Unlink instead of link"))

  final case class ScheduleInput(goal: Option[String] = None, kind: Option[String] = None, cron: Option[String] = None,
      enabled: Boolean = true, budgetUsd: Option[Double] = None, remove: Boolean = false) derives KindlingsCodecAsObject
  object ScheduleInput:
    given Schema[ScheduleInput] = KindlingsSchema.derived[ScheduleInput].schema
      .modify(_.goal)(_.description("The goal's slug (omit with kind to list every schedule)"))
      .modify(_.kind)(_.description("currency | acquisition"))
      .modify(_.cron)(_.description("Five-field cron in UTC (minute hour day-of-month month day-of-week), or @daily, @weekly, @monthly"))
      .modify(_.enabled)(_.description("false keeps the schedule but stops it firing"))
      .modify(_.budgetUsd)(_.description("The cost cap of each run it starts (default: the goal's, then the server's)"))
      .modify(_.remove)(_.description("Delete the schedule"))

  final case class ApprovalsInput(state: Option[String] = None, limit: Option[Int] = None) derives KindlingsCodecAsObject
  object ApprovalsInput:
    given Schema[ApprovalsInput] = KindlingsSchema.derived[ApprovalsInput].schema
      .modify(_.state)(_.description("open (default), approved, rejected, failed, or all"))

  final case class DecideInput(id: Long, decision: String, note: Option[String] = None) derives KindlingsCodecAsObject
  object DecideInput:
    given Schema[DecideInput] = KindlingsSchema.derived[DecideInput].schema
      .modify(_.id)(_.description("The approval's id (from list_approvals)"))
      .modify(_.decision)(_.description("approve or reject"))
      .modify(_.note)(_.description("Why, kept with the decision"))
