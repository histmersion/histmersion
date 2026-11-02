package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.places.PlacesMarkdown

import chimp.server.*
import com.histmersion.api.*
import com.histmersion.domain.Caller
import com.histmersion.util.mcp.McpOut
import com.histmersion.values.PlaceKind
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** The gazetteer for agents: `places` (search, or show one place with its hierarchy), `place_names` (a person's names),
  * `place_override` (a person's kind, period and point; merge, remove) and `add_place`.
  */
object PlacesTools:
  import com.histmersion.util.mcp.ToolInputs.given

  /** Input of the `places` tool. */
  final case class PlacesInput(
      query: Option[String] = None,
      name: Option[String] = None,
      id: Option[String] = None,
      asOf: Option[Long] = None,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object PlacesInput:
    given Schema[PlacesInput] = KindlingsSchema
      .derived[PlacesInput]
      .schema
      .modify(_.query)(_.description("Name or label substring (case-insensitive); `name` is an alias"))
      .modify(_.name)(_.description("Alias of `query` — a place NAME to look up (not full-text search)"))
      .modify(_.id)(_.description("Place id to look up directly"))
      .modify(_.asOf)(_.description("Signed year the gazetteer is read at (omit for all rows)"))

  /** Input of the `place_override` tool (V080). */
  final case class PlaceOverrideInput(
      placeId: Option[String] = None,
      kind: Option[String] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      reason: Option[String] = None,
      reset: Option[Boolean] = None,
      mergeInto: Option[String] = None,
      remove: Option[Boolean] = None,
      pointLat: Option[Double] = None,
      pointLon: Option[Double] = None
  )
  object PlaceOverrideInput:
    given Schema[PlaceOverrideInput] = KindlingsSchema
      .derived[PlaceOverrideInput]
      .schema
      .modify(_.placeId)(_.description("The place (an id from `places`); required to set or reset"))
      .modify(_.kind)(_.description(s"The kind it is: one of ${PlaceKind.all.mkString(", ")}"))
      .modify(_.periodStart)(_.description("Signed year it begins (BCE negative)"))
      .modify(_.periodEnd)(_.description("Signed year it ends (BCE negative)"))
      .modify(_.reason)(
        _.description("Why: the evidence the decision rests on (with a point: the gazetteer value it comes from)")
      )
      .modify(_.pointLat)(
        _.description("Latitude of the corrected point (with pointLon) — a place known by a point only")
      )
      .modify(_.pointLon)(_.description("Longitude of the corrected point (with pointLat)"))
      .modify(_.reset)(_.description("true: drop the override and restore what the source says"))
      .modify(_.mergeInto)(_.description("The id of the place this one is the same as: merge it there"))
      .modify(_.remove)(_.description("true: delete this place (only when nothing refers to it)"))
    given Codec.AsObject[PlaceOverrideInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceOverrideInput])

  /** Input of the `place_names` tool (#987). */
  final case class PlaceNamesInput(
      placeId: Option[String] = None,
      name: Option[String] = None,
      lang: Option[String] = None,
      remove: Option[Boolean] = None
  )
  object PlaceNamesInput:
    given Schema[PlaceNamesInput] = KindlingsSchema
      .derived[PlaceNamesInput]
      .schema
      .modify(_.placeId)(_.description("The place (an id from `places`); required with `name`"))
      .modify(_.name)(_.description("The name to add (or remove); omit to list the names added by hand"))
      .modify(_.lang)(_.description("Language tag of the name: en, grc, la, pl …; omit when unknown"))
      .modify(_.remove)(_.description("true: remove this name (only a name added by hand)"))
    given Codec.AsObject[PlaceNamesInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceNamesInput])

  def apply(places: PlacesControllers) =
    val me = Caller.Local
    List(
      /** The gazetteer (plan §1.6a). One tool with two modes so a model does not have to pick between four
        * near-identical ones: no `id` → search, `id` → show that place with its hierarchy.
        */
      tool("places")
        .description(
          "The GAZETTEER: places with geometry, alternate/period names and a temporally scoped " +
            "hierarchy (continent > country > polity > site). Without `id` it SEARCHES by name — use " +
            "it to turn a name a human wrote ('Anatolia', 'Hatti') into a place **id**, then pass that " +
            "id as `places` to `search`/`geo_when`: an id matches every finding inside the place's " +
            "shape AND everything below it in the hierarchy, which a bare name never does. With `id` " +
            "it SHOWS one place: what it is, when it was valid, what it is part of and what it " +
            "contains, as an indented tree. `asOf` is a signed year (BCE negative) and selects the " +
            "hierarchy as it stood then — the same site is under 'Hittite Empire' at -1400 and under " +
            "'Phrygia' at -700. Counts are non-additive: a finding on a border belongs to every place " +
            "that covers it."
        )
        .input[PlacesInput]
        .handle { in =>
          in.id.map(_.trim).filter(_.nonEmpty) match
            case Some(id) =>
              places.get(id, in.asOf) match
                case Right(d) =>
                  val tree = places.tree(id, in.asOf, None).getOrElse(Nil)
                  ToolResult.text(PlacesMarkdown.place(d, tree.toVector))
                case Left(e) => McpOut.error(e)
            case None =>
              ToolResult.text(
                PlacesMarkdown.places(places.search(in.name.orElse(in.query), in.asOf, in.limit).toVector, in.asOf)
              )
        },
      /** A person's names for a place (#987): list, add or remove — the names a re-seed never deletes. */
      tool("place_names")
        .description(
          "Names ADDED BY HAND to gazetteer places — the names every re-seed keeps (an import replaces a " +
            "place's other names). Without `name` it LISTS them (of `placeId`, or of every place). With " +
            "`placeId` and `name` it ADDS that name (`lang`: a language tag such as en, grc, pl; omit when " +
            "unknown) — use it when a well-known name finds no place in `places` (\"Lake Kinneret\" for the " +
            "Sea of Galilee), so the geocoder and `places=` filters know it from then on. With `remove: true` " +
            "it removes a name added by hand (a name the seed gives cannot be removed). Find the place id " +
            "with `places` first."
        )
        .input[PlaceNamesInput]
        .handle { in =>
          val id = in.placeId.map(_.trim).filter(_.nonEmpty)
          (id, in.name.map(_.trim).filter(_.nonEmpty)) match
            case (_, None)       => ToolResult.text(PlacesMarkdown.manualPlaceNames(places.manualNames(id)))
            case (None, Some(_)) =>
              ToolResult.text("_`placeId` is required to add or remove a name — find it with `places`_")
            case (Some(pid), Some(n)) =>
              val r =
                if in.remove.getOrElse(false) then places.removeName(me, pid, PlaceNameRemove(n, in.lang))
                else places.addName(me, pid, PlaceNameAdd(n, in.lang))
              r.fold(McpOut.error, x => ToolResult.text(PlacesMarkdown.placeNameChanged(x)))
        },
      /** A person's kind and period for a place (V080): list, set or reset — kept by every re-import. */
      tool("place_override")
        .description(
          "Curate a gazetteer place by hand, over what its source says — KIND (site | region | " +
            "polity | people | feature) and/or PERIOD (`periodStart`/`periodEnd`, signed years, BCE negative; " +
            "one side alone leaves the other open) and/or POINT (`pointLat`/`pointLon`, for a place known by a point " +
            "whose stored point is wrong; `reason` must name the gazetteer value it comes from — a Pleiades " +
            "representative point, a Wikidata coordinate — never an estimate). Every re-seed and re-import keeps it, and the place's " +
            "findings are matched again at once (a site takes findings within the point tolerance; a region, " +
            "polity or people known only by a point takes only the findings that name it). Use it when a " +
            "source files a place wrongly — Pleiades' Regio XII of Rome as an urban site, one of 14 regiones. " +
            "`reason` says what evidence the decision rests on. With `placeId` alone, or nothing, it LISTS the " +
            "overrides (with what the source says); `reset: true` drops one and restores the source's kind, " +
            "period and point. `mergeInto` (a place id) merges the place into the one it is the same as (findings, " +
            "links and references move; its id resolves there) — for a duplicate a source withdrew; " +
            "`remove: true` deletes a place nothing refers to. Neither comes back with a re-seed. Find the " +
            "place id with `places` first."
        )
        .input[PlaceOverrideInput]
        .handle { in =>
          val id = in.placeId.map(_.trim).filter(_.nonEmpty)
          val into = in.mergeInto.map(_.trim).filter(_.nonEmpty)
          val gone = into.isDefined || in.remove.getOrElse(false)
          val ask = gone || in.kind.exists(_.trim.nonEmpty) || in.periodStart.isDefined || in.periodEnd.isDefined ||
            in.pointLat.isDefined || in.pointLon.isDefined || in.reset.getOrElse(false)
          def text(r: Either[ApiError, String]) = ToolResult.text(r.fold(McpOut.refusal, identity))
          (id, ask) match
            case (_, false)   => ToolResult.text(PlacesMarkdown.placeOverrides(places.overrides(id)))
            case (None, true) =>
              ToolResult.text("_`placeId` is required to set, reset, merge or remove — find it with `places`_")
            case (Some(pid), true) =>
              (into, in.remove.getOrElse(false)) match
                case (Some(_), true)  => ToolResult.text("_`mergeInto` and `remove` exclude each other_")
                case (Some(t), false) =>
                  text(places.merge(me, pid, PlaceMergeIn(t, in.reason)).map(PlacesMarkdown.placeGone))
                case (None, true) =>
                  text(places.remove(me, pid, PlaceRemoveIn(in.reason)).map(PlacesMarkdown.placeGone))
                case (None, false) =>
                  text(
                    places
                      .setOverride(
                        me,
                        pid,
                        PlaceOverrideSet(
                          in.kind,
                          in.periodStart,
                          in.periodEnd,
                          in.reason,
                          in.reset.getOrElse(false),
                          in.pointLat,
                          in.pointLon
                        )
                      )
                      .map(PlacesMarkdown.placeOverrideChanged)
                  )
        },
      /** A place of a person's own (#987). */
      tool("add_place")
        .description(
          "Add a POINT place to the gazetteer that it does not have — only after `places` found nothing " +
            "under any of its names (otherwise give the existing place a name with `place_names`). " +
            "`source` is required and must say where the coordinates come from (a gazetteer or Wikidata " +
            "id, a map, a publication): never estimate a point. `kind`: site | region | feature | polity; " +
            "`names` are further names; `periodStart`/`periodEnd` are signed years (BCE negative) when the " +
            "place existed only then. The place is this collection's own (`local_<label>` unless `id` is " +
            "given), carried in a bundle and never touched by a re-seed. Refused when the id is taken."
        )
        .input[PlaceAdd]
        .handle(in => places.add(me, in).fold(McpOut.error, p => ToolResult.text(PlacesMarkdown.placeAdded(p))))
    )
