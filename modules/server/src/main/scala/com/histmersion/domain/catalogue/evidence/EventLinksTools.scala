package com.histmersion.domain.catalogue.evidence

import chimp.server.*
import com.histmersion.api.Api
import com.histmersion.api.catalogue.{EventLeads, EventLinksMarkdown, EventLinksRequest}
import com.histmersion.api.library.Filter
import com.histmersion.domain.Caller
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `event_links` for agents: the sources that may discuss an event, their confirmation (an attestation from a passage
  * that names it), and the leads catalogue events raise.
  */
object EventLinksTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class EventLinksInput(
      id: Option[EventLinksInput.Id] = None,
      recompute: EventLinksInput.Recompute = com.histmersion.domain.catalogue.evidence.EventLinksTools.EventLinksInput.(false),
      confirm: List[EventLinksInput.Confirm] = Nil,
      reject: List[EventLinksInput.Reject] = Nil,
      from: Option[EventLinksInput.From] = None,
      to: Option[EventLinksInput.To] = None,
      kind: Option[EventLinksInput.Kind] = None,
      limit: Option[EventLinksInput.Limit] = None
  ) derives KindlingsCodecAsObject
  object EventLinksInput:
    type Id = com.histmersion.domain.catalogue.Event.Id
    val Id: _root_.com.histmersion.domain.catalogue.Event.Id.type = _root_.com.histmersion.domain.catalogue.Event.Id
    type Recompute = Recompute.T
    object Recompute extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Confirm = Confirm.T
    object Confirm extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reject = Reject.T
    object Reject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = com.histmersion.PageSize
    val Limit: _root_.com.histmersion.PageSize.type = _root_.com.histmersion.PageSize

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
          in.id.map(_.trim).filter(_.nonEmpty) match
            case Some(id) =>
              val r =
                if in.recompute || in.confirm.nonEmpty || in.reject.nonEmpty then
                  links
                    .applyLinks(Caller.Local, id, EventLinksRequest(in.recompute, in.confirm, in.reject))
                    .map(EventLinksMarkdown.result)
                else links.links(id).map(EventLinksMarkdown.list)
              ToolResult.text(r.fold(McpOut.refusal, identity))
            case None =>
              val q = Api.EventQuery(Filter.of(from = in.from, to = in.to, limit = in.limit), kind = in.kind)
              ToolResult.text(EventLeads.markdown(links.leads(q)))
        }
    List(linksTool)
