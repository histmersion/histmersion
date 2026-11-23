package com.histmersion
package server.views

import com.histmersion.api.given
import com.histmersion.util.mcp.ChimpJson.given
import com.histmersion.api.json.NewtypeCodecs.given

import chimp.server.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.research.OpenWork
import com.histmersion.util.json.JsonCodec
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.problem.Problem.{Raw, TypeName}
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

final class OpenWorkTools(view: OpenWorkView):
  import OpenWorkTools.WhyInput

  def tools =
    List(
      tool("research_state")
        .description(
          "The research state: every open item and who acts next. Waiting for the owner, grouped by what to do " +
            "(decide, download in the browser, borrow, buy or borrow, add an identifier, look at a stuck provider, " +
            "resume a paused run, do a task); what the agents will handle, grouped by when (now, today, later). " +
            "Filters: `who` (owner | agents), `goal` (a goal's slug), `kind` (decision | acquisition | task | run). " +
            "Use to see what is yours to do and what to leave to the person."
        )
        .input[OpenWork.Scope]
        .handle(in =>
          (in.unknownWho, in.unknownKind) match
            case (Some(w), _) =>
              McpOut.unknown(TypeName.of(OpenWork.Terms.Who), s"$w", OpenWork.Terms.Who.all.map(Raw.of)*)
            case (_, Some(k)) =>
              McpOut.unknown(TypeName.of(OpenWork.Terms.Kind), s"$k", OpenWork.Terms.Kind.all.map(Raw.of)*)
            case _ => ToolResult.text(view.state(in.parsed).markdown)
        ),
      tool("acquire_why")
        .description(
          "Why one acquisition-list item is where it is: its verdict (who acts and why) and its kept attempts, " +
            "with what each provider answered. `id`: the item's id (`#123` in `queue acquire`)."
        )
        .input[WhyInput]
        .handle(in => view.acquisition(in.id).fold(McpOut.error, h => ToolResult.text(h.markdown)))
    )

object OpenWorkTools:
  final case class WhyInput(id: Queue.QueueItem.Id) derives JsonCodec, KindlingsSchema, api.json.Schema
