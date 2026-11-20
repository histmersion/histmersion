package com.histmersion
package domain.catalogue.evidence
import com.histmersion.util.mcp.ChimpJson.given
import com.histmersion.util.problem.Problem

import com.histmersion.api.json.NewtypeCodecs.given
import chimp.server.*
import com.histmersion.api.Api
import com.histmersion.api.catalogue.{EventLeads, EventLinksMarkdown, EventLinksRequest}
import com.histmersion.api.library.Filter
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.Write
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.Year

/** `event_links` for agents: the sources that may discuss an event, their confirmation (an attestation from a passage
  * that names it), and the leads catalogue events raise.
  */
object EventLinksTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class EventLinksInput(
      id: Option[com.histmersion.api.catalogue.Event.Id] = None,
      recompute: EventLinksRequest.Recompute = EventLinksRequest.Recompute(false),
      confirm: List[EventLinksRequest.Confirm] = Nil,
      reject: List[EventLinksRequest.Reject] = Nil,
      from: Option[Year] = None,
      to: Option[Year] = None,
      kind: Option[Api.EventQuery.Kind] = None,
      limit: Option[Filter.Limit] = None
  ) derives JsonCodec
  object EventLinksInput:
    given Schema[EventLinksInput] = KindlingsSchema
      .derived[EventLinksInput]
      .schema
      .modify(_.id)(_.description("The event; omit to list LEADS (catalogue events no source discusses)"))
      .modify(_.recompute)(_.description("Recompute the event's proposed sources first"))
      .modify(_.confirm)(
        _.description(
          "Citekeys to confirm: each becomes an attestation from a passage of its text naming the event (refused when none does)"
        )
      )
      .modify(_.reject)(_.description("Citekeys to stop proposing"))
      .modify(_.from)(_.description("Leads: events overlapping from this signed year"))
      .modify(_.to)(_.description("Leads: events overlapping up to this signed year"))
      .modify(_.kind)(_.description("Leads: a kind (eruption, battle…) or natural | human"))

  def apply(links: EventLinksControllers) =
    val linksTool =
      tool("event_links")
        .description(
          "Link EVENTS to the corpus without guessing. With `id`: the sources that MAY discuss the event — " +
            "their findings near it in time and space, passages of their text naming it (label, other " +
            "names, aliases in other languages) — each scored, PROPOSED and unconfirmed. `confirm` writes an " +
            "attestation (quote + character offsets) for a source ONLY from a passage that names the event; " +
            "`reject` stops proposing one. Without `id`: LEADS — catalogue events (eruptions, earthquakes, " +
            "battles… from the Smithsonian GVP, NOAA NCEI, Wikidata) that no source in the corpus discusses, " +
            "with the corpus's findings around them counted and the commands to search the literature. A " +
            "catalogue event is a research lead, never evidence: do not cite it in prose."
        )
        .input[EventLinksInput]
        .handle { in =>
          in.id.map(_.value.trim).filter(_.nonEmpty) match
            case Some(raw) =>
              val r =
                Event.Id
                  .option(raw)
                  .toRight(
                    Api.Error(
                      Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$raw")),
                      Some(Api.Error.Code(Write.CodeNotFound))
                    )
                  )
                  .flatMap { id =>
                    if in.recompute.value || in.confirm.nonEmpty || in.reject.nonEmpty then
                      links
                        .applyLinks(Caller.Local, id, EventLinksRequest(in.recompute, in.confirm, in.reject))
                        .map(EventLinksMarkdown.result)
                    else links.links(id).map(EventLinksMarkdown.list)
                  }
              McpOut.ofText(r)
            case None =>
              val q = Api.EventQuery(Filter.of(from = in.from, to = in.to, limit = in.limit), kind = in.kind)
              ToolResult.text(EventLeads.markdown(links.leads(q)).value)
        }
    List(linksTool)
