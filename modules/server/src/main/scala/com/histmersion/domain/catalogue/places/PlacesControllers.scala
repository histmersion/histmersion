package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.places.PlaceEntry

import com.histmersion.api.LabelRename
import com.histmersion.api.LabelRenames
import com.histmersion.api.LabelRenamed
import com.histmersion.values.BundleKind
import com.histmersion.util.identity.AliasKinds

import com.histmersion.api.{
  Api,
  ApiError,
  Dataset,
  Geocode,
  GeoPick,
  Lpf,
  ManualPlaceName,
  Period,
  PlaceAdd,
  PlaceEdits,
  PlaceFootprint,
  PlaceGone,
  PlaceHit,
  PlaceImportResult,
  PlaceLink,
  PlaceMergeIn,
  PlaceNameAdd,
  PlaceNameChanged,
  PlaceNameRemove,
  PlaceOverride,
  PlaceOverrideChanged,
  PlaceOverrides,
  PlaceOverrideSet,
  PlaceRebuildResult,
  PlaceRemoveIn,
  PlaceSummary,
  PlaceTreeRow
}
import com.histmersion.config.AppConfig
import com.histmersion.domain.Caller
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.util.identity.AliasKind
import com.histmersion.values.PlaceKind

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
    current: (AliasKind, String) => String,
    cfg: () => AppConfig,
    limit: (Option[Int], Int) => Int,
    maintenance: WriteGate
):
  private def modernFrom: Long = cfg().places.modernFrom
  private def tolKm: Double = cfg().places.pointToleranceKm

  /** A place's id now: an old id (renamed, V055 — e.g. by a merge that followed a rename made in the other collection)
    * resolves to the place it names, as an old citekey does.
    */
  def placeId(id: String): String = current(AliasKinds.PlaceRecords, id)

  private def named(id: Option[String]): Option[String] = id.map(_.trim).filter(_.nonEmpty).map(placeId)

  /** A place NAME → its gazetteer point (#981), for a finding about a place of kind `hint` dated `at`. */
  def geocode(name: String, hint: Geocode.KindHint, at: Option[Period]): Option[Geocode.Resolution] =
    geocoder.resolve(name, hint, at, cfg().places.geocodePlaceholderMin)

  /** `GET /api/places` — name search; `limit <= 0` means the configured `places.search-limit`. */
  def search(q: Option[String], asOf: Option[Long], n: Option[Int]): List[PlaceHit] =
    places.search(q, asOf, limit(n, cfg().places.searchLimit), modernFrom)

  /** `GET /api/places/geo` — the map layers' shapes for the time [[GeoPick.resolve]] makes of the query. */
  def shapes(q: Api.PlacesGeoQuery): Either[ApiError, String] =
    q.pick.map(_.trim).filter(_.nonEmpty) match
      case Some(p) if GeoPick.parse(p).isEmpty =>
        Left(ApiError(s"unknown pick '$p': one of ${GeoPick.values.map(_.wire).mkString(", ")}"))
      case p =>
        val time = GeoPick.resolve(q.asOf, q.from, q.to, p.flatMap(GeoPick.parse).getOrElse(GeoPick.Mid))
        Right(shapes.shapes(q.kind, time, cfg().places.geoLimit(q.limit, 100), modernFrom))

  /** `GET /api/places/{id}` — the row, its GeoJSON geometry and the hierarchy around it at `asOf`. */
  def get(id: String, asOf: Option[Long]): Either[ApiError, PlaceEntry] =
    places.get(placeId(id), asOf, modernFrom).toRight(ApiError(s"No place '$id'"))

  def children(id: String, asOf: Option[Long]): Either[ApiError, List[PlaceSummary]] =
    get(id, asOf).map(d => places.children(d.place.id, asOf, cfg().places.childrenLimit, modernFrom))

  /** The subtree under one place, depth-tagged; `depth <= 0` means the configured `places.tree-depth`. */
  def tree(id: String, asOf: Option[Long], depth: Option[Int]): Either[ApiError, List[PlaceTreeRow]] =
    get(id, asOf).map(d =>
      places.hierarchy(
        asOf,
        Some(d.place.id),
        depth.filter(_ > 0).getOrElse(cfg().places.treeDepth),
        cfg().places.treeLimit,
        cfg().places.treeDepth,
        modernFrom
      )
    )

  def footprints(id: String, asOf: Option[Long]): Either[ApiError, List[PlaceFootprint]] =
    get(id, asOf).map(d => places.footprints(d.place.id, asOf))

  def links(id: String): Either[ApiError, List[PlaceLink]] =
    val now = placeId(id)
    places.get(now, None, modernFrom).map(_ => places.links(now)).toRight(ApiError(s"No place '$id'"))

  /** `POST /api/places/import` — an LPF document or a plain GeoJSON FeatureCollection. Parsing is pure ([[Lpf]]) and
    * all-or-nothing: a malformed document is a 400, never a half import.
    */
  def importPlaces(
      caller: Caller,
      kind: Option[String],
      dataset: Option[String],
      body: String
  ): Either[ApiError, PlaceImportResult] =
    maintenance.guard("places/import"):
      val k = kind.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(PlaceKind.Region)
      if !PlaceKind.all.contains(k) then Left(ApiError(s"unknown kind '$k' (one of: ${PlaceKind.all.mkString(", ")})"))
      else
        for
          set <- Dataset.ofParam(dataset)
          in <- Lpf.parse(body, k).left.map(ApiError(_))
        yield places.importPlaces(in, set)

  /** `POST /api/places/{id}/names` (#987) — a name a person gives a place, stored as MANUAL: no re-seed deletes it. */
  def addName(caller: Caller, id: String, in: PlaceNameAdd): Either[ApiError, PlaceNameChanged] =
    maintenance.guard("places/names/add"):
      for
        n <- PlaceEdits.name(in.name)
        l <- PlaceEdits.lang(in.lang)
        r <- places.addName(placeId(id.trim), n, l)
      yield r

  /** `POST /api/places/{id}/names/remove` — only a name a person added; an imported one is refused. */
  def removeName(caller: Caller, id: String, in: PlaceNameRemove): Either[ApiError, PlaceNameChanged] =
    maintenance.guard("places/names/remove"):
      for
        n <- PlaceEdits.name(in.name)
        l <- PlaceEdits.lang(in.lang)
        r <- places.removeName(placeId(id.trim), n, l)
      yield r

  /** `GET /api/places/names?place=` — the names people added, every place or one. */
  def manualNames(id: Option[String]): List[ManualPlaceName] = places.manualNames(named(id))

  /** `POST /api/places/{id}/override` (V080) — a person's kind, period and/or point for a place, kept by every
    * re-import, or `reset` back to the source's; the place's findings are matched again at once.
    */
  def setOverride(caller: Caller, id: String, in: PlaceOverrideSet): Either[ApiError, PlaceOverrideChanged] =
    maintenance.guard("places/override"):
      PlaceOverrides.edit(in).flatMap(e => places.setOverride(placeId(id.trim), e, tolKm))

  /** `POST /api/places/{id}/merge` (V080) — the place is `into`; its id resolves there from then on. */
  def merge(caller: Caller, id: String, in: PlaceMergeIn): Either[ApiError, PlaceGone] =
    maintenance.guard("places/merge"):
      PlaceOverrides.reason(in.reason).flatMap(_ => places.merge(placeId(id.trim), placeId(in.into.trim), tolKm))

  /** `POST /api/places/{id}/remove` (V080) — only a place nothing refers to; no re-seed brings it back. */
  def remove(caller: Caller, id: String, in: PlaceRemoveIn): Either[ApiError, PlaceGone] =
    maintenance.guard("places/remove"):
      PlaceOverrides.reason(in.reason).flatMap(r => places.remove(placeId(id.trim), r))

  /** `GET /api/places/overrides?place=` — the kinds and periods people set, every place's or one's. */
  def overrides(id: Option[String]): List[PlaceOverride] = places.overrides(named(id))

  /** `POST /api/places` (#987) — a point place of a person's own (`local`, carried in a bundle, never touched by a
    * re-seed). Its findings are matched by the next `places rebuild`.
    */
  def add(caller: Caller, in: PlaceAdd): Either[ApiError, PlaceSummary] =
    maintenance.guard("places/add"):
      PlaceEdits.toInput(in).flatMap(places.add)

  /** `POST /api/places/{id}/rename` — a new gazetteer id; the old one keeps resolving (V055). `validate` is the label
    * renames' shape rule, given the old id; the answer is the old id and the new.
    */
  def rename(
      caller: Caller,
      id: String,
      validate: String => Either[ApiError, String]
  ): Either[ApiError, (String, String)] =
    maintenance.guard("rename_place"):
      val from = placeId(id)
      for
        to <- validate(from)
        _ <- places.rename(from, to)
      yield (from, to)

  /** `POST /api/places/{id}/rename` — a new gazetteer id; the old one keeps resolving (V055), in
    * `GET /api/places/{old}` and in a `place=<old>` filter alike.
    */
  def renamed(id: String, req: LabelRename): Either[ApiError, LabelRenamed] =
    rename(Caller.Local, id, from => LabelRenames.validate(BundleKind.Place, from, req.to))
      .map((from, to) => LabelRenamed(BundleKind.Place, from, to))

  /** `POST /api/places/rebuild` — re-materialise `finding_places`; a rebuild arriving during a restore is refused like
    * every other write.
    */
  def rebuild(caller: Caller): Either[ApiError, PlaceRebuildResult] =
    maintenance.guard("places/rebuild"):
      Right(geometry.rebuildFindingPlaces(tolKm))
