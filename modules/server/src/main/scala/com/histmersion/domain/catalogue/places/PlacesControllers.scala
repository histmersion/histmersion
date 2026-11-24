package com.histmersion
package domain.catalogue.places

import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*
import com.histmersion.api.exchange.Bundle

import com.histmersion.api.aliases.LabelRename
import com.histmersion.api.aliases.LabelRenames
import com.histmersion.api.aliases.LabelRenamed
import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.{Api, Dataset, GeoPick, Write}
import com.histmersion.api.catalogue.{Geocode, Lpf, ManualPlaceName, Period, Year}
import com.histmersion.api.catalogue.Place as ApiPlace
import com.histmersion.config.{AppConfig, PlacesConfig}
import com.histmersion.domain.{Caller, DeepTime}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind
import com.histmersion.domain.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.problem.Problems
import com.histmersion.domain.Creator

/** The gazetteer's operations as REST, MCP and the CLI reach them: search, a place and the hierarchy around it, the
  * map's shapes, imports, a person's names, kinds, periods and points, merges, removals and renames, the matching of
  * findings to places and the geocoder. An old place id answers as the place it was renamed or merged into. `cfg` is
  * read at each call; `limit` gives a list its page size.
  */
final class PlacesControllers(
    places: PlaceService,
    shapes: PlaceMap,
    geometry: PlaceGeometry,
    geocoder: Geocoding,
    current: (AliasKind, PlacesControllers.Alias) => Place.Id,
    cfg: () => AppConfig,
    limit: (Option[PageSize], PageSize) => PageSize,
    maintenance: WriteGate
):
  private def modernFrom: DeepTime.SignedYear = DeepTime.SignedYear(cfg().places.modernFrom.value)
  private def tolKm: GeocodeSettings.ToleranceKm = GeocodeSettings.ToleranceKm(cfg().places.pointToleranceKm.value)
  private def page(raw: Int): PageSize = PageSize.applyUnsafe(math.max(1, raw))
  private def notFound(id: String): Api.Error =
    Api.Error(Problem.NotFound.Entity(Problem.TypeName.of(Place.Id), Problem.Raw(s"$id")))

  /** A place's id now: an old id (renamed, V055 — e.g. by a merge that followed a rename made in the other collection)
    * resolves to the place it names, as an old citekey does.
    */
  def placeId(id: Place.Id): Place.Id = current(AliasKinds.PlaceRecords, PlacesControllers.Alias(id.value))

  private def named(id: Option[Place.Id]): Option[Place.Id] = id.map(_.trimmed).filterNot(_.isBlank).map(placeId)

  /** A place NAME → its gazetteer point (#981), for a finding about a place of kind `hint` dated `at`. */
  def geocode(name: Geocode.Attempt.Name, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution] =
    geocoder.resolve(name, hint, at, GeocodeSettings.PlaceholderMin(cfg().places.geocodePlaceholderMin.value))

  /** `GET /api/places` — name search; `limit <= 0` means the configured `places.search-limit`. */
  def search(
      q: Option[PlaceService.SearchQ],
      asOf: Option[DeepTime.SignedYear],
      n: Option[PageSize]
  ): List[com.histmersion.api.catalogue.Place.Hit] =
    places.search(q, asOf, limit(n, page(cfg().places.searchLimit.value)), modernFrom)

  /** `GET /api/places/geo` — the map layers' shapes for the time [[GeoPick.resolve]] makes of the query. */
  def shapes(q: Api.PlacesGeoQuery): Either[Problems, Api.PlacesGeo.Json] =
    q.pick.map(p => GeoPick.Wire(p.value.trim)).filter(_.value.nonEmpty) match
      case Some(p) if GeoPick.parse(p).isEmpty =>
        Left(
          Problem.Invalid.Unknown(
            Problem.TypeName.of(GeoPick.Wire),
            Problem.Raw.of(p),
            GeoPick.values.toVector.map(v => Problem.Raw.of(v.wire)),
            Vector(Problem.Field("pick"))
          )
        )
      case p =>
        val time = GeoPick.resolve(
          q.asOf.map(y => Year(y.value)),
          q.from.map(y => Year(y.value)),
          q.to.map(y => Year(y.value)),
          p.flatMap(GeoPick.parse).getOrElse(GeoPick.Mid)
        )
        val geoLimit =
          cfg().places.geoLimit(q.limit.map(l => PlacesConfig.GeoLimit(l.value)), PlacesConfig.GeoLimit(100))
        Right(
          Api.PlacesGeo.Json(
            shapes.shapes(q.kind.map(k => Place.Kind.Wire(k.value)), time, page(geoLimit.value), modernFrom).value
          )
        )

  /** `GET /api/places/{id}` — the row, its GeoJSON geometry and the hierarchy around it at `asOf`. */
  def get(
      id: Place.Id,
      asOf: Option[DeepTime.SignedYear]
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.Entry] =
    places.get(placeId(id), asOf, modernFrom).toRight(notFound(id.value))

  def children(
      id: Place.Id,
      asOf: Option[DeepTime.SignedYear]
  ): Either[Api.Error, List[com.histmersion.api.catalogue.Place.Summary]] =
    get(id, asOf).map(_ => places.children(placeId(id), asOf, page(cfg().places.childrenLimit.value), modernFrom))

  /** The subtree under one place, depth-tagged; `depth <= 0` means the configured `places.tree-depth`. */
  def tree(
      id: Place.Id,
      asOf: Option[DeepTime.SignedYear],
      depth: Option[PlaceService.DepthLimit]
  ): Either[Api.Error, List[com.histmersion.api.catalogue.Place.TreeRow]] =
    get(id, asOf).map(_ =>
      places.hierarchy(
        asOf,
        Some(placeId(id)),
        depth.filter(_.value > 0).getOrElse(PlaceService.DepthLimit(cfg().places.treeDepth.value)),
        page(cfg().places.treeLimit.value),
        PlaceService.DepthLimit(cfg().places.treeDepth.value),
        modernFrom
      )
    )

  def footprints(
      id: Place.Id,
      asOf: Option[DeepTime.SignedYear]
  ): Either[Api.Error, List[com.histmersion.api.catalogue.Place.Footprint]] =
    get(id, asOf).map(_ => places.footprints(placeId(id), asOf))

  def links(id: Place.Id): Either[Api.Error, List[com.histmersion.api.catalogue.Place.Link]] =
    val now = placeId(id)
    places.get(now, None, modernFrom).map(_ => places.links(now)).toRight(notFound(id.value))

  /** `POST /api/places/import` — an LPF document or a plain GeoJSON FeatureCollection. Parsing is pure ([[Lpf]]) and
    * all-or-nothing: a malformed document is a 400, never a half import.
    */
  def importPlaces(
      caller: Caller,
      kind: Option[Place.Kind.Wire],
      dataset: Option[Dataset.Wire],
      body: PlacesControllers.DatasetBody
  )(using Creator.New): Either[Api.Error, com.histmersion.api.catalogue.Place.ImportResult] =
    maintenance.guard(WriteGate.OperationName("places/import")):
      val k = kind.map(_.value.trim.toLowerCase).filter(_.nonEmpty).getOrElse(Place.Kind.Region)
      if !Place.Kind.all.contains(Place.Kind.Wire(k)) then
        Left(
          Api.Error(
            Problem.Invalid.Unknown(
              Problem.TypeName.of(Place.Kind.Wire),
              Problem.Raw(k),
              Place.Kind.all.toVector.map(Problem.Raw.of(_)),
              Vector(Problem.Field("kind"))
            )
          )
        )
      else
        for
          set <- Dataset.ofParam(dataset)
          in <- Lpf.parse(body.value, ApiPlace.KindValue(k)).left.map(e => Api.Error(e.input))
        yield places.importPlaces(in, set)

  /** `POST /api/places/{id}/names` (#987) — a name a person gives a place, stored as MANUAL: no re-seed deletes it. */
  def addName(
      caller: Caller,
      id: Place.Id,
      in: com.histmersion.api.catalogue.Place.NameAdd
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.NameChanged] =
    maintenance.guard(WriteGate.OperationName("places/names/add")):
      for
        n <- com.histmersion.api.catalogue.Place.Edits.name(ApiPlace.Name.Value(in.name.value))
        l <- com.histmersion.api.catalogue.Place.Edits.lang(in.lang)
        r <- places.addName(placeId(id), n, l)
      yield r

  /** `POST /api/places/{id}/names/remove` — only a name a person added; an imported one is refused. */
  def removeName(
      caller: Caller,
      id: Place.Id,
      in: com.histmersion.api.catalogue.Place.NameRemove
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.NameChanged] =
    maintenance.guard(WriteGate.OperationName("places/names/remove")):
      for
        n <- com.histmersion.api.catalogue.Place.Edits.name(ApiPlace.Name.Value(in.name.value))
        l <- com.histmersion.api.catalogue.Place.Edits.lang(in.lang)
        r <- places.removeName(placeId(id), n, l)
      yield r

  /** `GET /api/places/names?place=` — the names people added, every place or one. */
  def manualNames(id: Option[Place.Id]): List[ManualPlaceName] = places.manualNames(named(id))

  /** `POST /api/places/{id}/override` (V080) — a person's kind, period and/or point for a place, kept by every
    * re-import, or `reset` back to the source's; the place's findings are matched again at once.
    */
  def setOverride(
      caller: Caller,
      id: Place.Id,
      in: com.histmersion.api.catalogue.Place.OverrideSet
  )(using Creator.New): Either[Api.Error, com.histmersion.api.catalogue.Place.OverrideChanged] =
    maintenance.guard(WriteGate.OperationName("places/override")):
      com.histmersion.api.catalogue.Place.Overrides
        .edit(in)
        .flatMap(e => places.setOverride(placeId(id), e, tolKm))

  /** `POST /api/places/{id}/merge` (V080) — the place is `into`; its id resolves there from then on. */
  def merge(
      caller: Caller,
      id: Place.Id,
      in: com.histmersion.api.catalogue.Place.MergeIn
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.Gone] =
    maintenance.guard(WriteGate.OperationName("places/merge")):
      for
        _ <- ApiPlace.Overrides.reason(in.reason)
        into <- Place.Id.either(in.into.value.trim).left.map(_ => notFound(in.into.value))
        gone <- places.merge(placeId(id), placeId(into), tolKm)
      yield gone

  /** `POST /api/places/{id}/remove` (V080) — only a place nothing refers to; no re-seed brings it back. */
  def remove(
      caller: Caller,
      id: Place.Id,
      in: com.histmersion.api.catalogue.Place.RemoveIn
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.Gone] =
    maintenance.guard(WriteGate.OperationName("places/remove")):
      ApiPlace.Overrides
        .reason(in.reason)
        .flatMap(r => places.remove(placeId(id), r))

  /** `GET /api/places/overrides?place=` — the kinds and periods people set, every place's or one's. */
  def overrides(id: Option[Place.Id]): List[com.histmersion.api.catalogue.Place.Override] = places.overrides(named(id))

  /** `POST /api/places` (#987) — a point place of a person's own (`local`, carried in a bundle, never touched by a
    * re-seed). Its findings are matched by the next `places rebuild`.
    */
  def add(
      caller: Caller,
      in: com.histmersion.api.catalogue.Place.Add
  )(using Creator.New): Either[Api.Error, com.histmersion.api.catalogue.Place.Summary] =
    maintenance.guard(WriteGate.OperationName("places/add")):
      com.histmersion.api.catalogue.Place.Edits.toInput(in).flatMap(places.add)

  /** `POST /api/places/{id}/rename` — a new gazetteer id; the old one keeps resolving (V055). `validate` is the label
    * renames' shape rule, given the old id; the answer is the old id and the new.
    */
  def rename(
      caller: Caller,
      id: Place.Id,
      validate: Place.Id => Either[Api.Error, Place.Id]
  ): Either[Api.Error, (Place.Id, Place.Id)] =
    maintenance.guard(WriteGate.OperationName("rename_place")):
      val from = placeId(id)
      for
        to <- validate(from)
        _ <- places.rename(from, to)
      yield (from, to)

  /** `POST /api/places/{id}/rename` — a new gazetteer id; the old one keeps resolving (V055), in
    * `GET /api/places/{old}` and in a `place=<old>` filter alike.
    */
  def renamed(id: Place.Id, req: LabelRename): Either[Api.Error, LabelRenamed] =
    rename(
      Caller.Local,
      id,
      from =>
        LabelRenames
          .validate(Bundle.Kind.Place, LabelRenames.Label(from.value), LabelRenames.Label(req.to.value))
          .flatMap(to =>
            Place.Id
              .checked(to.value)
              .left
              .map(refusal =>
                Api.Error(
                  Problem.Invalid.malformed(refusal).within(Problem.Field("to")),
                  Some(Api.Error.Code(Write.CodeInvalid))
                )
              )
          )
    ).map((from, to) => LabelRenamed(Bundle.Kind.Place, LabelRenamed.From(from.value), LabelRenamed.To(to.value)))

  /** `POST /api/places/rebuild` — re-materialise `finding_places`; a rebuild arriving during a restore is refused like
    * every other write.
    */
  def rebuild(caller: Caller): Either[Api.Error, com.histmersion.api.catalogue.Place.RebuildResult] =
    maintenance.guard(WriteGate.OperationName("places/rebuild")):
      Right(geometry.rebuildFindingPlaces(tolKm))

object PlacesControllers:
  type Alias = Alias.T
  object Alias extends Newtype[String, Pure]
  type DatasetBody = DatasetBody.T
  object DatasetBody extends Newtype[String, Pure]
