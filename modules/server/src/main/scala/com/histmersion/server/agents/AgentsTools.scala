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
              in.rounds, in.acquireItem)) match
            case Right(r) => ToolResult.text(AgentsMarkdown.runLine(r))
            case Left(e)  => ToolResult.text(s"_${e.message}_")
        }

    val status =
      tool("run_status")
        .description(
          "Embedded agent runs: with `id`, one run's steps, tokens, cost, warnings (and its trace with " +
            "`messages: true`); without, the newest runs (optionally one `status`: queued, running, " +
            "paused, done, failed, cancelled).")
        .input[RunsInput]
        .handle { in =>
          in.id.map(_.trim).filter(_.nonEmpty) match
            case Some(id) => ToolResult.text(a.run(id).fold(e => s"_${e.message}_", AgentsMarkdown.run(_, in.messages)))
            case None     => ToolResult.text(a.runs(in.status, in.limit).fold(e => s"_${e.message}_", AgentsMarkdown.runs))
        }

    val approvals =
      tool("list_approvals")
        .description(
          "Changes embedded agent runs PROPOSED and a person decides: a source's status, or a disagreement " +
            "edge (challenges, refutes, updates, superseded_by), each with the evidence and the verified " +
            "quote it rests on. Default: the open ones; `state` = open, approved, rejected, failed or all.")
        .input[ApprovalsInput]
        .handle { in =>
          ToolResult.text(a.approvalsList(in.state, in.limit).fold(e => s"_${e.message}_", AgentsMarkdown.approvals))
        }

    val decide =
      tool("decide_approval")
        .description(
          "Approve or reject one open approval by id. Approving APPLIES the change through the ordinary " +
            "source patch (status or edge); rejecting keeps it on record. Decide only what the user asked " +
            "you to, or what you have verified against the record yourself.")
        .input[DecideInput]
        .handle { in =>
          ToolResult.text(a.decide(in.id, in.decision, DecisionRequest(in.note))
            .fold(e => s"_${e.message}_", x => AgentsMarkdown.approvalLine(x)))
        }

    List(start, status, approvals, decide)

  final case class RunInput(pipeline: String, target: String, model: Option[String] = None,
      budgetUsd: Option[Double] = None, fix: Boolean = false, rounds: Option[Int] = None,
      acquireItem: Option[Int] = None) derives KindlingsCodecAsObject
  object RunInput:
    given Schema[RunInput] = KindlingsSchema.derived[RunInput].schema
      .modify(_.pipeline)(_.description("The pipeline: enrich-source or review-article"))
      .modify(_.target)(_.description("What it works on: a citekey for enrich-source, an article slug for review-article"))
      .modify(_.model)(_.description("provider:model for every step of this run (default: the configured roles)"))
      .modify(_.budgetUsd)(_.description("This run's cost cap in USD (default: the server's agents.budgets.run-usd)"))
      .modify(_.fix)(_.description("review-article: let the fixer revise the article between rounds"))
      .modify(_.rounds)(_.description("review-article: at most this many panel rounds (default: agents.review.max-rounds)"))
      .modify(_.acquireItem)(_.description("enrich-source: the acquisition item this run fulfils (ticked off at the end)"))

  final case class RunsInput(id: Option[String] = None, status: Option[String] = None, limit: Option[Int] = None,
      messages: Boolean = false) derives KindlingsCodecAsObject
  object RunsInput:
    given Schema[RunsInput] = KindlingsSchema.derived[RunsInput].schema
      .modify(_.id)(_.description("One run's id; omit to list runs"))
      .modify(_.status)(_.description("List only runs in this state"))
      .modify(_.messages)(_.description("With id: include the traced messages and tool calls"))

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
