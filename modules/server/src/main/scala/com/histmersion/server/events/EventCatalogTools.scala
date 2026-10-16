package com.histmersion.server.events

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.core.*
import com.histmersion.server.{Handlers, McpTools}

/** The MCP half of #1449: `event_links` — the sources that may discuss an event, their confirmation
  * (an attestation from a passage that names it), and the leads catalogue events raise. The import is
  * not a tool, on purpose (as the media import): it is an operator's decision that sends traffic to a
  * third party — `histmersion events import`. */
object EventCatalogTools:
  import McpTools.given // honour Scala defaults when decoding tool arguments

  final case class EventLinksInput(
      id: Option[String] = None,
      recompute: Boolean = false,
      confirm: List[String] = Nil,
      reject: List[String] = Nil,
      from: Option[Long] = None,
      to: Option[Long] = None,
      kind: Option[String] = None,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object EventLinksInput:
    given Schema[EventLinksInput] = KindlingsSchema.derived[EventLinksInput].schema
      .modify(_.id)(_.description("The event; omit to list LEADS (catalogue events no source discusses)"))
      .modify(_.recompute)(_.description("Recompute the event's proposed sources first"))
      .modify(_.confirm)(_.description("Citekeys to confirm: each becomes an attestation from a passage of its text naming the event (refused when none does)"))
      .modify(_.reject)(_.description("Citekeys to stop proposing"))
      .modify(_.from)(_.description("Leads: events overlapping from this signed year"))
      .modify(_.to)(_.description("Leads: events overlapping up to this signed year"))
      .modify(_.kind)(_.description("Leads: a kind (eruption, battle…) or natural | human"))

  final case class EventCorrectionsInput(
      status: Option[String] = None,
      id: Option[String] = None,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object EventCorrectionsInput:
    given Schema[EventCorrectionsInput] = KindlingsSchema.derived[EventCorrectionsInput].schema
      .modify(_.status)(_.description("active | agreed (retired: the catalogue now agrees) | conflict; default: all but agreed"))
      .modify(_.id)(_.description("Only this event's corrections"))

  def apply(h: Handlers) =
    // The re-derivation (`events rederive`) is not a tool, as the import is not: it rewrites every
    // catalogue event at once, an operator's step after a deploy (docs: README, deploy steps).
    val corrections =
      tool("event_corrections")
        .description(
          "The fields of CATALOGUE events (GVP, NOAA, Wikidata imports) a person corrected by hand with " +
            "`patch_event`: each keeps the person's value over the catalogue's on every re-import. A " +
            "correction is retired automatically (`agreed`) once the catalogue says the same; when the " +
            "catalogue changes the field to something else it stays applied and is flagged `conflict` for " +
            "review (conflicts are listed first). Each line: the field, the person's value, what the " +
            "catalogue said then and, in conflict, what it says now.")
        .input[EventCorrectionsInput]
        .handle { in =>
          ToolResult.text(h.catalog.corrections(in.status, in.id, in.limit, None).fold(e => s"_${e.message}_", Corrections.markdown))
        }

    val links =
      tool("event_links")
        .description(
          "Link EVENTS to the corpus without guessing. With `id`: the sources that MAY discuss the event — " +
            "their findings near it in time and space, passages of their text naming it (label, other " +
            "names, aliases in other languages) — each scored, PROPOSED and unconfirmed. `confirm` writes an " +
            "attestation (quote + character offsets) for a source ONLY from a passage that names the event; " +
            "`reject` stops proposing one. Without `id`: LEADS — catalogue events (eruptions, earthquakes, " +
            "battles… from the Smithsonian GVP, NOAA NCEI, Wikidata) that no source in the corpus discusses, " +
            "with the corpus's findings around them counted and the commands to search the literature. A " +
            "catalogue event is a research lead, never evidence: do not cite it in prose.")
        .input[EventLinksInput]
        .handle { in =>
          in.id.map(_.trim).filter(_.nonEmpty) match
            case Some(id) =>
              val r =
                if in.recompute || in.confirm.nonEmpty || in.reject.nonEmpty then
                  h.catalog.applyLinks(id, EventLinksRequest(in.recompute, in.confirm, in.reject)).map(EventLinksMarkdown.result)
                else h.catalog.links(id).map(EventLinksMarkdown.list)
              ToolResult.text(r.fold(e => s"_${e.message}_", identity))
            case None =>
              val q = Api.EventQuery(Filter.of(from = in.from, to = in.to, limit = in.limit), kind = in.kind)
              ToolResult.text(EventLeads.markdown(h.eventLeads(q)))
        }
    List(links, corrections)
