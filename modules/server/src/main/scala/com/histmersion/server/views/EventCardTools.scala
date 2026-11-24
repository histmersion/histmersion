package com.histmersion
package server.views
import com.histmersion.util.mcp.ChimpJson.given
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.catalogue.EventsMarkdown
import com.histmersion.domain.catalogue.events.EventsControllers

import chimp.server.*
import com.histmersion.api.{Api, Markdown, StrictJson, Write}
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.catalogue.{EventGeometry, EventPatch}
import com.histmersion.api.library.Filter
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.Event
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.domain.Creator
import com.histmersion.api.automation.Creation
import com.histmersion.domain.CreatorControllers

/** The events for agents: list or show one (its page, with its pictures and the articles citing its sources), and patch
  * one.
  */
object EventCardTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class EventsInput(
      query: Option[EventsInput.Query] = None,
      id: Option[EventsInput.Id] = None,
      kind: Option[EventsInput.Kind] = None,
      historicity: Option[EventsInput.Historicity] = None,
      placeId: Option[EventsInput.PlaceId] = None,
      from: Option[EventsInput.From] = None,
      to: Option[EventsInput.To] = None,
      title: Option[EventsInput.Title] = None,
      limit: Option[EventsInput.Limit] = None,
      offset: Option[EventsInput.Offset] = None
  ) derives JsonCodec
  object EventsInput:
    export Api.EventQuery.Text as Query
    export com.histmersion.api.catalogue.Event.Id
    export Api.EventQuery.Kind
    export com.histmersion.api.catalogue.Historicity.Wire as Historicity
    export Place.Id as PlaceId
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type From = Year
    val From: Year.type = Year
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type To = Year
    val To: Year.type = Year
    export Filter.{Limit, Offset, Title}

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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
      id: PatchEventInput.Id,
      label: Option[PatchEventInput.Label] = None,
      kind: Option[PatchEventInput.Kind] = None,
      historicity: Option[PatchEventInput.Historicity] = None,
      scale: Option[PatchEventInput.Scale] = None,
      placeId: Option[PatchEventInput.PlaceId] = None,
      geometry: Option[PatchEventInput.Geometry] = None,
      lat: Option[PatchEventInput.Lat] = None,
      lon: Option[PatchEventInput.Lon] = None,
      parentId: Option[PatchEventInput.ParentId] = None,
      description: Option[PatchEventInput.Description] = None,
      citekey: Option[PatchEventInput.Citekey] = None,
      wikidata: Option[PatchEventInput.Wikidata] = None,
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives StrictJson
  object PatchEventInput:
    export com.histmersion.api.catalogue.Event.Id
    export EventSummary.{Historicity, Kind, Label, Scale}
    export Place.Id as PlaceId
    export EventGeometry.Geojson as Geometry
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Lat = Latitude
    val Lat: Latitude.type = Latitude
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Lon = Longitude
    val Lon: Longitude.type = Longitude
    export com.histmersion.api.catalogue.Event.Id as ParentId
    export EventSummary.Description
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    type Citekey = com.histmersion.api.library.Citekey
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    val Citekey: com.histmersion.api.library.Citekey.type = com.histmersion.api.library.Citekey
    export EventPatch.Wikidata

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(events: EventsControllers, page: EventCardView) =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Mcp)
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
          in.id.map(_.value.trim).filter(_.nonEmpty) match
            case Some(raw) =>
              Event.Id
                .option(raw)
                .toRight(
                  Api.Error(
                    Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$raw")),
                    Some(Api.Error.Code(Write.CodeNotFound))
                  )
                )
                .flatMap(page.get) match
                case Right(d) => ToolResult.text(d.markdown.value)
                case Left(e)  => McpOut.error(e)
            case None =>
              val q = Api.EventQuery(
                Filter.of(from = in.from, to = in.to, limit = in.limit, offset = in.offset, title = in.title),
                in.query,
                in.kind,
                in.historicity,
                in.placeId
              )
              ToolResult.text(EventsMarkdown.eventList(events.list(q)))
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
          CreatorControllers.mcp(in) {
            EventGeometry.of(in.geometry, in.lat, in.lon) match
              case Left(msg)   => McpOut.refused(msg)
              case Right(geom) =>
                val p = EventPatch(
                  label = in.label,
                  kind = in.kind,
                  historicity = in.historicity,
                  geometry = geom.map(g => EventPatch.Geometry(g.value)),
                  placeId = in.placeId,
                  parentId = in.parentId,
                  description = in.description,
                  citekey = in.citekey,
                  scale = in.scale,
                  wikidata = in.wikidata
                )
                Event.Id
                  .option(in.id.value)
                  .toRight(
                    Api.Error(
                      Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"${in.id.value}")),
                      Some(Api.Error.Code(Write.CodeNotFound))
                    )
                  )
                  .flatMap(page.patch(Caller.Local, _, p)) match
                  case Right(d) =>
                    ToolResult.text(
                      Aliases
                        .note(AliasKinds.EventRecords, Aliases.Id(in.id.value), Aliases.Id(d.event.id.value))(
                          Aliases.Note(d.markdown.value)
                        )
                        .value
                    )
                  case Left(e) => McpOut.error(e)
          }
        }
    List(listTool, patchTool)
