package com.histmersion.domain.catalogue.events

import chimp.server.*
import com.histmersion.api.{Aliases, AliasKindsAdapter, Api, EventGeometry, EventPatch, Filter, Markdown, StrictJson}
import com.histmersion.domain.Caller
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** The events for agents: list or show, and patch one. */
object EventsTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class EventsInput(
      query: Option[String] = None,
      id: Option[String] = None,
      kind: Option[String] = None,
      historicity: Option[String] = None,
      placeId: Option[String] = None,
      from: Option[Long] = None,
      to: Option[Long] = None,
      title: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object EventsInput:
    given Schema[EventsInput] = KindlingsSchema
      .derived[EventsInput]
      .schema
      .modify(_.query)(_.description("Text search over event label and description"))
      .modify(_.id)(_.description("Event id to look up directly (omit for listing)"))
      .modify(_.kind)(
        _.description(
          "Filter: a kind (eruption|earthquake|tsunami|flood|drought|climate_event|epidemic|famine|battle|naval_battle|siege|war|revolt|treaty|polity_formation|collapse|destruction|migration|invasion|technological_change|cultural_shift|era|…) or natural | human"
        )
      )
      .modify(_.historicity)(_.description("Filter: attested|inferred|contested|legendary|mythic"))
      .modify(_.placeId)(_.description("Filter by place id"))
      .modify(_.from)(_.description("Overlap: events whose outer span reaches at or after this signed year"))
      .modify(_.to)(_.description("Overlap: events whose outer span reaches at or before this signed year"))
      .modify(_.title)(_.description("Case-insensitive substring of the event's label"))

  final case class PatchEventInput(
      id: String,
      label: Option[String] = None,
      kind: Option[String] = None,
      historicity: Option[String] = None,
      scale: Option[String] = None,
      placeId: Option[String] = None,
      geometry: Option[String] = None,
      lat: Option[Double] = None,
      lon: Option[Double] = None,
      parentId: Option[String] = None,
      description: Option[String] = None,
      citekey: Option[String] = None,
      wikidata: Option[String] = None
  )
  object PatchEventInput:
    given Schema[PatchEventInput] = KindlingsSchema
      .derived[PatchEventInput]
      .schema
      .modify(_.id)(_.description("Event id"))
      .modify(_.scale)(_.description("local | regional | auto (clear: derived from kind, then place kind)"))
      .modify(_.placeId)(_.description("Gazetteer place id where the event happened (see `places`)"))
      .modify(_.geometry)(_.description("The event's own geometry as GeoJSON (Point or Polygon)"))
      .modify(_.lat)(_.description("The event's own point, latitude (with lon; instead of geometry)"))
      .modify(_.lon)(_.description("The event's own point, longitude (with lat)"))
      .modify(_.historicity)(_.description("attested|inferred|contested|legendary|mythic"))
      .modify(_.wikidata)(_.description("The Wikidata item the event is (Q…); \"\" clears it"))
    given Codec.AsObject[PatchEventInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PatchEventInput]) // #813

  def apply(events: EventsControllers) =
    val listTool =
      tool("events")
        .description(
          "EVENTS database: CIDOC-CRM compatible historical events with four-point time-spans, " +
            "compositional hierarchy (parent_id), causal/temporal edges (Allen interval algebra) " +
            "and participant roles. Without `id` it LISTS events; with `id` it SHOWS one event " +
            "including its datings, edges, participants and scale (local|regional, stated or derived). " +
            "Use `kind` to filter (a kind — eruption, earthquake, battle, siege, war, migration… — or a group: " +
            "natural | human) and `historicity` (attested|inferred|contested|legendary|mythic). An event's " +
            "TIER is `attested` (a source in the corpus attests it) or `catalogue` (known only from a catalogue " +
            "— a research lead, not evidence); `show` lists the catalogues with licence and citation, the " +
            "attesting sources, the findings near it and the sources that may discuss it (see `event_links`)."
        )
        .input[EventsInput]
        .handle { in =>
          in.id.map(_.trim).filter(_.nonEmpty) match
            case Some(id) =>
              events.get(id) match
                case Right(d) => ToolResult.text(Markdown.event(d))
                case Left(e)  => McpOut.error(e)
            case None =>
              val q = Api.EventQuery(
                Filter.of(from = in.from, to = in.to, limit = in.limit, offset = in.offset, title = in.title),
                in.query,
                in.kind,
                in.historicity,
                in.placeId
              )
              ToolResult.text(Markdown.eventList(events.list(q)))
        }

    val patchTool =
      tool("patch_event")
        .description(
          "Update fields of one EVENT without replacing it: label, kind, historicity, description, " +
            "citekey (its primary source), parentId (the event it is part of), placeId (a gazetteer place " +
            "id — where it happened), its own geometry (GeoJSON `geometry`, or a point as `lat`/`lon`), and " +
            "`scale`: local (a battle — one place) | regional (an era, a migration — across a region) | " +
            "auto (forget the stated scale; it is derived from the kind, then the place's kind). The map's " +
            "playback draws a local event as a point and label at its site, a regional one as a label at " +
            "the region's centre with its outline. `wikidata`: the Wikidata item the event is (Q…; \"\" " +
            "clears it) — where `histmersion media import` reads its picture. On an event a CATALOGUE import " +
            "created (GVP, NOAA, Wikidata) every changed field (label, kind, timeSpan, historicity, " +
            "description, geometry) is recorded as a correction and KEPT across re-imports of that catalogue " +
            "(retired once the catalogue agrees, flagged as a conflict when it changes to something else — " +
            "`event_corrections`). Returns the event as `events` with `id` shows it."
        )
        .input[PatchEventInput]
        .handle { in =>
          EventGeometry.of(in.geometry, in.lat, in.lon) match
            case Left(msg)   => McpOut.text(McpOut.refusal(msg))
            case Right(geom) =>
              val p = EventPatch(
                label = in.label,
                kind = in.kind,
                historicity = in.historicity,
                geometry = geom,
                placeId = in.placeId,
                parentId = in.parentId,
                description = in.description,
                citekey = in.citekey,
                scale = in.scale,
                wikidata = in.wikidata
              )
              events.patch(Caller.Local, in.id, p) match
                case Right(d) =>
                  ToolResult.text(Aliases.note(AliasKindsAdapter.EventRecords, in.id, d.event.id)(Markdown.event(d)))
                case Left(e) => McpOut.error(e)
        }
    List(listTool, patchTool)
