package com.histmersion
package domain.catalogue.places

import com.histmersion.api.Problem
import com.histmersion.api.catalogue.RegionNode
import com.histmersion.api.library.Tree

import com.histmersion.api.Dataset
import com.histmersion.domain.catalogue.places.PlaceAliasTable
import com.augustnagro.magnum.{connect, transact, DbCon}

import javax.sql.DataSource
import com.histmersion.api.catalogue.{GeoTime, ManualPlaceName, Period}
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Place
import com.histmersion.config.PlacesConfig
import com.histmersion.domain.DeepTime.SignedYear
import com.histmersion.util.identity.AliasTable
import com.histmersion.api.library.Finding
import com.histmersion.domain.catalogue.evidence.EventMatcher
import com.histmersion.api.library.Filter
import com.histmersion.domain.library.Citekey

/** The places' repository as the db suites seed and read it: each call on a connection (a write in a transaction) of
  * the suite's own `ds`, and the SQL the suites assert on.
  */
object PlaceFixtures:
  export PlaceSql.*
  export PlaceRepo.{geoJsonQuery, geoJsonTimeQuery, rangeShadowedSql, rangeShadowTestSql}
  export PlaceRepo.{Depth, Lang, Limit, Name, PairsByIndex, SearchText}
  export FindingPlaceRepo.{placeholderSql, DefaultPlaceholderAreaShare, DefaultPlaceholderMin}
  export Geocoder.{apply as applyMoves, candidates, moves, place, placements, resolve, tierSql}

  private def findingTolerance(t: PlacesConfig.PointToleranceKm) = FindingPlacement.Implementation.ToleranceKm(t.value)
  private def areaTolerance(t: PlacesConfig.PointToleranceKm) = PointAreaRepo.Settings.ToleranceKm(t.value)

  def upsertPlaces(ds: DataSource, batch: List[Place.Input], dataset: Dataset = Dataset.Seed): Place.ImportResult =
    if batch.isEmpty then
      Place.ImportResult(
        Place.ImportResult.Places(0),
        Place.ImportResult.Names(0),
        Place.ImportResult.Relations(0),
        Nil
      )
    else transact(ds)(PlaceRepo.upsertPlaces(batch, dataset))

  def search(
      ds: DataSource,
      q: Option[PlaceRepo.SearchText],
      asOf: Option[SignedYear],
      limit: PlaceRepo.Limit,
      modernFrom: SignedYear = PlaceSql.DefaultModernFrom
  ): Vector[Place.Hit] = connect(ds)(PlaceRepo.search(q, asOf, limit, modernFrom))

  def get(
      ds: DataSource,
      id: com.histmersion.domain.catalogue.Place.Id,
      asOf: Option[SignedYear] = None,
      modernFrom: SignedYear = PlaceSql.DefaultModernFrom
  ): Option[Place.Entry] = connect(ds)(PlaceRepo.get(id, asOf, modernFrom))

  def children(
      ds: DataSource,
      id: com.histmersion.domain.catalogue.Place.Id,
      asOf: Option[SignedYear],
      limit: PlaceRepo.Limit,
      modernFrom: SignedYear = PlaceSql.DefaultModernFrom
  ): Vector[Place.Summary] = connect(ds)(PlaceRepo.children(id, asOf, limit, modernFrom))

  def hierarchy(
      ds: DataSource,
      asOf: Option[SignedYear],
      root: Option[com.histmersion.domain.catalogue.Place.Id],
      maxDepth: PlaceRepo.Depth,
      limit: PlaceRepo.Limit,
      depthCap: PlaceRepo.Depth = PlaceRepo.Depth(Int.MaxValue),
      modernFrom: SignedYear = PlaceSql.DefaultModernFrom
  ): Vector[Place.TreeRow] = connect(ds)(PlaceRepo.hierarchy(asOf, root, maxDepth, limit, depthCap, modernFrom))

  def placesOf(ds: DataSource, findingId: Finding.Id): Vector[Place.Summary] =
    connect(ds)(PlaceRepo.placesOf(EventMatcher.NearFinding.Id(findingId.value)))

  def geoJson(
      ds: DataSource,
      kind: Option[com.histmersion.domain.catalogue.Place.Kind.Wire],
      asOf: Option[SignedYear],
      limit: PlaceRepo.Limit,
      modernFrom: SignedYear = PlaceSql.DefaultModernFrom
  ): PlaceRepo.GeoJson = connect(ds)(PlaceRepo.geoJson(kind, asOf, limit, modernFrom))

  def geoJsonAt(
      ds: DataSource,
      kind: Option[com.histmersion.domain.catalogue.Place.Kind.Wire],
      time: GeoTime,
      limit: PlaceRepo.Limit,
      modernFrom: SignedYear = PlaceSql.DefaultModernFrom
  ): PlaceRepo.GeoJson = connect(ds)(PlaceRepo.geoJsonAt(kind, time, limit, modernFrom))

  def scopeFor(ds: DataSource, values: List[Filter.Places], period: Option[Period]): PlaceScope =
    connect(ds)(PlaceRepo.scopeFor(values.map(v => PlaceRepo.ScopeForValues(v.value)), period))

  def footprints(
      ds: DataSource,
      placeId: com.histmersion.domain.catalogue.Place.Id,
      asOf: Option[SignedYear]
  ): Vector[Place.Footprint] =
    connect(ds)(PlaceRepo.footprints(placeId, asOf))

  def links(ds: DataSource, placeId: com.histmersion.domain.catalogue.Place.Id): Vector[Place.Link] =
    connect(ds)(PlaceRepo.links(placeId))

  def rename(
      ds: DataSource,
      oldId: com.histmersion.domain.catalogue.Place.Id,
      newId: com.histmersion.domain.catalogue.Place.Id
  ): Either[Api.Error, Unit] =
    transact(ds)(PlaceRepo.rename(oldId, newId))

  def renamedTo(ds: DataSource, id: AliasTable.Id): Option[AliasTable.Id] = PlaceAliasTable.table.renamedTo(ds, id)

  def addName(
      ds: DataSource,
      id: com.histmersion.domain.catalogue.Place.Id,
      name: PlaceRepo.Name,
      lang: PlaceRepo.Lang
  ): Either[Api.Error, Place.NameChanged] =
    transact(ds)(PlaceRepo.addName(id, name, lang))

  def removeName(
      ds: DataSource,
      id: com.histmersion.domain.catalogue.Place.Id,
      name: PlaceRepo.Name,
      lang: PlaceRepo.Lang
  ): Either[Api.Error, Place.NameChanged] =
    transact(ds)(PlaceRepo.removeName(id, name, lang))

  def manualNames(ds: DataSource, id: Option[com.histmersion.domain.catalogue.Place.Id]): List[ManualPlaceName] =
    connect(ds)(PlaceRepo.manualNames(id))

  def addPlace(ds: DataSource, in: Place.Input): Either[Api.Error, Place.Summary] = transact(ds)(PlaceRepo.addPlace(in))

  def count(ds: DataSource): RowCount = connect(ds)(PlaceRepo.count())

  def rebuildFindingPlaces(ds: DataSource, toleranceKm: PlacesConfig.PointToleranceKm): Place.RebuildResult =
    transact(ds)(FindingPlaceRepo.rebuildFindingPlaces(findingTolerance(toleranceKm)))

  def materialisePlaces(
      ds: DataSource,
      placeIds: List[com.histmersion.domain.catalogue.Place.Id],
      toleranceKm: PlacesConfig.PointToleranceKm
  ): RowCount =
    transact(ds)(
      FindingPlaceRepo.materialisePlaces(placeIds.map(i => BPlaceRow.Id(i.value)), findingTolerance(toleranceKm))
    )

  /** The same, on the suite's own transaction. */
  def materialisePlacesIn(
      placeIds: List[com.histmersion.domain.catalogue.Place.Id],
      toleranceKm: PlacesConfig.PointToleranceKm
  )(using DbCon): RowCount =
    FindingPlaceRepo.materialisePlaces(placeIds.map(i => BPlaceRow.Id(i.value)), findingTolerance(toleranceKm))

  def unresolvedPlaceholders(ds: DataSource): List[(Citekey, FindingPlaceRepo.Placeholder.Detail)] =
    connect(ds)(FindingPlaceRepo.unresolvedPlaceholdersIn())

  /** The places of the gazetteer slice the rows of a tree hang off. */
  def gazetteer(ds: DataSource, rows: Seq[Tree.TreeRow]): (List[RegionNode], List[Place.Relation]) =
    connect(ds)(
      TreeRepo.gazetteer(
        rows.iterator
          .flatMap(_.placeIds)
          .map(i => com.histmersion.domain.catalogue.Place.Id.applyUnsafe(i.value))
          .toList
      )
    )

  def setOverride(
      ds: DataSource,
      id: com.histmersion.domain.catalogue.Place.Id,
      edit: Place.OverrideEdit,
      toleranceKm: PlacesConfig.PointToleranceKm
  ): Either[Api.Error, Place.OverrideChanged] =
    transact(ds)(PlaceOverrideRepo.set(BPlaceRow.Id(id.value), edit, findingTolerance(toleranceKm)))

  def merge(
      ds: DataSource,
      from: com.histmersion.domain.catalogue.Place.Id,
      into: com.histmersion.domain.catalogue.Place.Id,
      toleranceKm: PlacesConfig.PointToleranceKm
  ): Either[Api.Error, Place.Gone] =
    transact(ds)(
      PlaceOverrideRepo.merge(BPlaceRow.Id(from.value), BPlaceRow.Id(into.value), findingTolerance(toleranceKm))
    )

  def remove(
      ds: DataSource,
      id: com.histmersion.domain.catalogue.Place.Id,
      reason: Option[Problem]
  ): Either[Api.Error, Place.Gone] =
    transact(ds)(PlaceOverrideRepo.remove(BPlaceRow.Id(id.value), reason))

  def overrides(ds: DataSource, id: Option[com.histmersion.domain.catalogue.Place.Id]): List[Place.Override] =
    connect(ds)(PlaceOverrideRepo.list(id.map(i => BPlaceRow.Id(i.value))))

  def replaceLand(ds: DataSource, geometries: List[BPlaceRow.Geom]): RowCount =
    val n = transact(ds)(LandRepo.replace(geometries))
    connect(ds)(LandRepo.analyze())
    n

  def reclip(ds: DataSource): RowCount =
    connect(ds)(LandRepo.polities()).grouped(LandRepo.ReclipBatch).foreach(b => transact(ds)(LandRepo.reclip(b)))
    connect(ds)(LandRepo.clipped())

  def gazetteerSources(ds: DataSource): List[GazetteerSourceRow] = connect(ds)(GazetteerRepo.sources())

  def proposals(ds: DataSource): List[PlaceReview.Proposal] = connect(ds)(PlaceReviewRepo.proposals())

  def applyProposals(
      ds: DataSource,
      ps: List[PlaceReview.Proposal],
      toleranceKm: PlacesConfig.PointToleranceKm
  ): (RowCount, RowCount) =
    transact(ds)(PlaceReviewRepo.apply(ps, areaTolerance(toleranceKm)))

  def relocations(ds: DataSource): List[PlaceReviewRepo.Relocation] = connect(ds)(PlaceReviewRepo.relocations())

  def relocate(
      ds: DataSource,
      rs: List[PlaceReviewRepo.Relocation],
      toleranceKm: PlacesConfig.PointToleranceKm
  ): RowCount =
    transact(ds)(PlaceReviewRepo.relocate(rs, areaTolerance(toleranceKm)))
