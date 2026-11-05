package com.histmersion.domain.catalogue.places

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.aliases.LabelRename
import com.histmersion.api.aliases.LabelRenames
import com.histmersion.api.aliases.LabelRenamed
import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.{Api, Dataset, GeoPick}
import com.histmersion.api.catalogue.{Geocode, Lpf, ManualPlaceName, Period}
import com.histmersion.config.AppConfig
import com.histmersion.domain.Caller
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind
import com.histmersion.domain.catalogue.Place

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
    current: (AliasKind, PlacesControllers.CurrentArg2) => PlacesControllers.CurrentResult,
    cfg: () => AppConfig,
    limit: (Option[PlacesControllers.LimitArg1], PlacesControllers.LimitArg2) => PlacesControllers.LimitResult,
    maintenance: WriteGate
):
  private def modernFrom: com.histmersion.domain.catalogue.places.PlacesControllers.ModernFromResult = cfg().places.modernFrom
  private def tolKm: com.histmersion.domain.catalogue.places.PlacesControllers.TolKmResult = cfg().places.pointToleranceKm

  /** A place's id now: an old id (renamed, V055 — e.g. by a merge that followed a rename made in the other collection)
    * resolves to the place it names, as an old citekey does.
    */
  def placeId(id: com.histmersion.domain.catalogue.places.PlacesControllers.PlaceIdId): com.histmersion.domain.catalogue.Place.Id = current(AliasKinds.PlaceRecords, id)

  private def named(id: Option[com.histmersion.domain.catalogue.places.PlacesControllers.NamedId]): Option[com.histmersion.domain.catalogue.places.PlacesControllers.NamedResult] = id.map(_.trim).filter(_.nonEmpty).map(placeId)

  /** A place NAME → its gazetteer point (#981), for a finding about a place of kind `hint` dated `at`. */
  def geocode(name: com.histmersion.domain.catalogue.places.PlacesControllers.GeocodeName, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution] =
    geocoder.resolve(name, hint, at, cfg().places.geocodePlaceholderMin)

  /** `GET /api/places` — name search; `limit <= 0` means the configured `places.search-limit`. */
  def search(q: Option[com.histmersion.domain.catalogue.places.PlacesControllers.SearchQ], asOf: Option[com.histmersion.domain.catalogue.places.PlacesControllers.SearchAsOf], n: Option[com.histmersion.domain.catalogue.places.PlacesControllers.SearchN]): List[com.histmersion.api.catalogue.Place.Hit] =
    places.search(q, asOf, limit(n, cfg().places.searchLimit), modernFrom)

  /** `GET /api/places/geo` — the map layers' shapes for the time [[GeoPick.resolve]] makes of the query. */
  def shapes(q: Api.PlacesGeoQuery): Either[Api.Error, com.histmersion.domain.catalogue.places.PlacesControllers.ShapesResultPart2] =
    q.pick.map(_.trim).filter(_.nonEmpty) match
      case Some(p) if GeoPick.parse(p).isEmpty =>
        Left(Api.Error(s"unknown pick '$p': one of ${GeoPick.values.map(_.wire).mkString(", ")}"))
      case p =>
        val time = GeoPick.resolve(q.asOf, q.from, q.to, p.flatMap(GeoPick.parse).getOrElse(GeoPick.Mid))
        Right(shapes.shapes(q.kind, time, cfg().places.geoLimit(q.limit, 100), modernFrom))

  /** `GET /api/places/{id}` — the row, its GeoJSON geometry and the hierarchy around it at `asOf`. */
  def get(id: com.histmersion.domain.catalogue.places.PlacesControllers.GetId, asOf: Option[com.histmersion.domain.catalogue.places.PlacesControllers.GetAsOf]): Either[Api.Error, com.histmersion.api.catalogue.Place.Entry] =
    places.get(placeId(id), asOf, modernFrom).toRight(Api.Error(s"No place '$id'"))

  def children(id: com.histmersion.domain.catalogue.places.PlacesControllers.ChildrenId, asOf: Option[com.histmersion.domain.catalogue.places.PlacesControllers.ChildrenAsOf]): Either[Api.Error, List[com.histmersion.api.catalogue.Place.Summary]] =
    get(id, asOf).map(d => places.children(d.place.id.value, asOf, cfg().places.childrenLimit, modernFrom))

  /** The subtree under one place, depth-tagged; `depth <= 0` means the configured `places.tree-depth`. */
  def tree(
      id: com.histmersion.domain.catalogue.places.PlacesControllers.TreeId,
      asOf: Option[com.histmersion.domain.catalogue.places.PlacesControllers.TreeAsOf],
      depth: Option[com.histmersion.domain.catalogue.places.PlacesControllers.TreeDepth]
  ): Either[Api.Error, List[com.histmersion.api.catalogue.Place.TreeRow]] =
    get(id, asOf).map(d =>
      places.hierarchy(
        asOf,
        Some(d.place.id.value),
        depth.filter(_ > 0).getOrElse(cfg().places.treeDepth),
        cfg().places.treeLimit,
        cfg().places.treeDepth,
        modernFrom
      )
    )

  def footprints(
      id: com.histmersion.domain.catalogue.places.PlacesControllers.FootprintsId,
      asOf: Option[com.histmersion.domain.catalogue.places.PlacesControllers.FootprintsAsOf]
  ): Either[Api.Error, List[com.histmersion.api.catalogue.Place.Footprint]] =
    get(id, asOf).map(d => places.footprints(d.place.id.value, asOf))

  def links(id: com.histmersion.domain.catalogue.places.PlacesControllers.LinksId): Either[Api.Error, List[com.histmersion.api.catalogue.Place.Link]] =
    val now = placeId(id)
    places.get(now, None, modernFrom).map(_ => places.links(now)).toRight(Api.Error(s"No place '$id'"))

  /** `POST /api/places/import` — an LPF document or a plain GeoJSON FeatureCollection. Parsing is pure ([[Lpf]]) and
    * all-or-nothing: a malformed document is a 400, never a half import.
    */
  def importPlaces(
      caller: Caller,
      kind: Option[com.histmersion.domain.catalogue.places.PlacesControllers.ImportPlacesKind],
      dataset: Option[com.histmersion.domain.catalogue.places.PlacesControllers.ImportPlacesDataset],
      body: com.histmersion.domain.catalogue.places.PlacesControllers.ImportPlacesBody
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.ImportResult] =
    maintenance.guard("places/import"):
      val k = kind.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(Place.Kind.Region)
      if !Place.Kind.all.contains(k) then
        Left(Api.Error(s"unknown kind '$k' (one of: ${Place.Kind.all.mkString(", ")})"))
      else
        for
          set <- Dataset.ofParam(dataset)
          in <- Lpf.parse(body, k).left.map(Api.Error(_))
        yield places.importPlaces(in, set)

  /** `POST /api/places/{id}/names` (#987) — a name a person gives a place, stored as MANUAL: no re-seed deletes it. */
  def addName(
      caller: Caller,
      id: com.histmersion.domain.catalogue.places.PlacesControllers.AddNameId,
      in: com.histmersion.api.catalogue.Place.NameAdd
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.NameChanged] =
    maintenance.guard("places/names/add"):
      for
        n <- com.histmersion.api.catalogue.Place.Edits.name(in.name)
        l <- com.histmersion.api.catalogue.Place.Edits.lang(in.lang)
        r <- places.addName(placeId(id.trim), n, l)
      yield r

  /** `POST /api/places/{id}/names/remove` — only a name a person added; an imported one is refused. */
  def removeName(
      caller: Caller,
      id: com.histmersion.domain.catalogue.places.PlacesControllers.RemoveNameId,
      in: com.histmersion.api.catalogue.Place.NameRemove
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.NameChanged] =
    maintenance.guard("places/names/remove"):
      for
        n <- com.histmersion.api.catalogue.Place.Edits.name(in.name)
        l <- com.histmersion.api.catalogue.Place.Edits.lang(in.lang)
        r <- places.removeName(placeId(id.trim), n, l)
      yield r

  /** `GET /api/places/names?place=` — the names people added, every place or one. */
  def manualNames(id: Option[com.histmersion.domain.catalogue.places.PlacesControllers.ManualNamesId]): List[ManualPlaceName] = places.manualNames(named(id))

  /** `POST /api/places/{id}/override` (V080) — a person's kind, period and/or point for a place, kept by every
    * re-import, or `reset` back to the source's; the place's findings are matched again at once.
    */
  def setOverride(
      caller: Caller,
      id: com.histmersion.domain.catalogue.places.PlacesControllers.SetOverrideId,
      in: com.histmersion.api.catalogue.Place.OverrideSet
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.OverrideChanged] =
    maintenance.guard("places/override"):
      com.histmersion.api.catalogue.Place.Overrides
        .edit(in)
        .flatMap(e => places.setOverride(placeId(id.trim), e, tolKm))

  /** `POST /api/places/{id}/merge` (V080) — the place is `into`; its id resolves there from then on. */
  def merge(
      caller: Caller,
      id: com.histmersion.domain.catalogue.places.PlacesControllers.MergeId,
      in: com.histmersion.api.catalogue.Place.MergeIn
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.Gone] =
    maintenance.guard("places/merge"):
      com.histmersion.api.catalogue.Place.Overrides
        .reason(in.reason)
        .flatMap(_ => places.merge(placeId(id.trim), placeId(in.into.trim), tolKm))

  /** `POST /api/places/{id}/remove` (V080) — only a place nothing refers to; no re-seed brings it back. */
  def remove(
      caller: Caller,
      id: com.histmersion.domain.catalogue.places.PlacesControllers.RemoveId,
      in: com.histmersion.api.catalogue.Place.RemoveIn
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.Gone] =
    maintenance.guard("places/remove"):
      com.histmersion.api.catalogue.Place.Overrides.reason(in.reason).flatMap(r => places.remove(placeId(id.trim), r))

  /** `GET /api/places/overrides?place=` — the kinds and periods people set, every place's or one's. */
  def overrides(id: Option[com.histmersion.domain.catalogue.places.PlacesControllers.OverridesId]): List[com.histmersion.api.catalogue.Place.Override] = places.overrides(named(id))

  /** `POST /api/places` (#987) — a point place of a person's own (`local`, carried in a bundle, never touched by a
    * re-seed). Its findings are matched by the next `places rebuild`.
    */
  def add(
      caller: Caller,
      in: com.histmersion.api.catalogue.Place.Add
  ): Either[Api.Error, com.histmersion.api.catalogue.Place.Summary] =
    maintenance.guard("places/add"):
      com.histmersion.api.catalogue.Place.Edits.toInput(in).flatMap(places.add)

  /** `POST /api/places/{id}/rename` — a new gazetteer id; the old one keeps resolving (V055). `validate` is the label
    * renames' shape rule, given the old id; the answer is the old id and the new.
    */
  def rename(
      caller: Caller,
      id: com.histmersion.domain.catalogue.places.PlacesControllers.RenameId,
      validate: com.histmersion.domain.catalogue.places.PlacesControllers.RenameValidateArg1 => Either[Api.Error, com.histmersion.domain.catalogue.places.PlacesControllers.RenameValidateResultPart2]
  ): Either[Api.Error, (com.histmersion.domain.catalogue.places.PlacesControllers.RenameResultPart2Part1, com.histmersion.domain.catalogue.places.PlacesControllers.RenameResultPart2Part2)] =
    maintenance.guard("rename_place"):
      val from = placeId(id)
      for
        to <- validate(from)
        _ <- places.rename(from, to)
      yield (from, to)

  /** `POST /api/places/{id}/rename` — a new gazetteer id; the old one keeps resolving (V055), in
    * `GET /api/places/{old}` and in a `place=<old>` filter alike.
    */
  def renamed(id: com.histmersion.domain.catalogue.places.PlacesControllers.RenamedId, req: LabelRename): Either[Api.Error, LabelRenamed] =
    rename(Caller.Local, id, from => LabelRenames.validate(Bundle.Kind.Place, from, req.to))
      .map((from, to) => LabelRenamed(Bundle.Kind.Place, from, to))

  /** `POST /api/places/rebuild` — re-materialise `finding_places`; a rebuild arriving during a restore is refused like
    * every other write.
    */
  def rebuild(caller: Caller): Either[Api.Error, com.histmersion.api.catalogue.Place.RebuildResult] =
    maintenance.guard("places/rebuild"):
      Right(geometry.rebuildFindingPlaces(tolKm))

object PlacesControllers:
  type CurrentArg2 = CurrentArg2.T
  object CurrentArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentResult = CurrentResult.T
  object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LimitArg1 = LimitArg1.T
  object LimitArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LimitArg2 = LimitArg2.T
  object LimitArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LimitResult = LimitResult.T
  object LimitResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ModernFromResult = ModernFromResult.T
  object ModernFromResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TolKmResult = TolKmResult.T
  object TolKmResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceIdId = PlaceIdId.T
  object PlaceIdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamedResult = NamedResult.T
  object NamedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamedId = NamedId.T
  object NamedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeocodeName = GeocodeName.T
  object GeocodeName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchQ = SearchQ.T
  object SearchQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchAsOf = SearchAsOf.T
  object SearchAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchN = SearchN.T
  object SearchN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShapesResultPart2 = ShapesResultPart2.T
  object ShapesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetAsOf = GetAsOf.T
  object GetAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChildrenId = ChildrenId.T
  object ChildrenId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChildrenAsOf = ChildrenAsOf.T
  object ChildrenAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeId = TreeId.T
  object TreeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeAsOf = TreeAsOf.T
  object TreeAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeDepth = TreeDepth.T
  object TreeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FootprintsId = FootprintsId.T
  object FootprintsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FootprintsAsOf = FootprintsAsOf.T
  object FootprintsAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinksId = LinksId.T
  object LinksId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPlacesKind = ImportPlacesKind.T
  object ImportPlacesKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPlacesDataset = ImportPlacesDataset.T
  object ImportPlacesDataset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportPlacesBody = ImportPlacesBody.T
  object ImportPlacesBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddNameId = AddNameId.T
  object AddNameId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveNameId = RemoveNameId.T
  object RemoveNameId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManualNamesId = ManualNamesId.T
  object ManualNamesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetOverrideId = SetOverrideId.T
  object SetOverrideId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeId = MergeId.T
  object MergeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RemoveId = RemoveId.T
  object RemoveId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverridesId = OverridesId.T
  object OverridesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameResultPart2Part1 = RenameResultPart2Part1.T
  object RenameResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameResultPart2Part2 = RenameResultPart2Part2.T
  object RenameResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameId = RenameId.T
  object RenameId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameValidateArg1 = RenameValidateArg1.T
  object RenameValidateArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameValidateResultPart2 = RenameValidateResultPart2.T
  object RenameValidateResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedId = RenamedId.T
  object RenamedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
