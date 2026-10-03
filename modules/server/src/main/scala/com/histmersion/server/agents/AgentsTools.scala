package com.histmersion.server.agents

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.core.*
import com.histmersion.core.Agents.RunRequest

/** The MCP half of the embedded agents: an external session can hand a whole pipeline to the server
  * with ONE call (`agent_run`) and check on it (`agent_runs`), instead of doing the work in its own
  * context. Same rule as every tool: a thin wrapper over [[AgentsHandlers]], Markdown out. */
object AgentsTools:
  import com.histmersion.server.McpTools.given // honour Scala defaults when decoding tool arguments

  def apply(a: AgentsHandlers) =
    val run =
      tool("agent_run")
        .description(
          "Start an embedded agent pipeline on the SERVER (off unless the server has agents enabled). " +
            s"Pipelines: ${Agents.Pipeline.values.map(p => s"`${p.wire}` <${p.targetKind}> — ${p.describe}").mkString("; ")}. " +
            "Returns the run id at once; the run is checkpointed, budgeted and resumable. Check it with " +
            "`agent_runs`. Use instead of doing the same steps yourself when the server's agents are on.")
        .input[RunInput]
        .handle { in =>
          a.startRun(RunRequest(in.pipeline, in.target, in.model, in.budgetUsd, None)) match
            case Right(r) => ToolResult.text(AgentsMarkdown.runLine(r))
            case Left(e)  => ToolResult.text(s"_${e.message}_")
        }

    val runs =
      tool("agent_runs")
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

    List(run, runs)

  final case class RunInput(pipeline: String, target: String, model: Option[String] = None,
      budgetUsd: Option[Double] = None) derives KindlingsCodecAsObject
  object RunInput:
    given Schema[RunInput] = KindlingsSchema.derived[RunInput].schema
      .modify(_.pipeline)(_.description("The pipeline, e.g. enrich-source"))
      .modify(_.target)(_.description("What it works on: a citekey for enrich-source"))
      .modify(_.model)(_.description("provider:model for every step of this run (default: the configured roles)"))
      .modify(_.budgetUsd)(_.description("This run's cost cap in USD (default: the server's agents.budgets.run-usd)"))

  final case class RunsInput(id: Option[String] = None, status: Option[String] = None, limit: Option[Int] = None,
      messages: Boolean = false) derives KindlingsCodecAsObject
  object RunsInput:
    given Schema[RunsInput] = KindlingsSchema.derived[RunsInput].schema
      .modify(_.id)(_.description("One run's id; omit to list runs"))
      .modify(_.status)(_.description("List only runs in this state"))
      .modify(_.messages)(_.description("With id: include the traced messages and tool calls"))
