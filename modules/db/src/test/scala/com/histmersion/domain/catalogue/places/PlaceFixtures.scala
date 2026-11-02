package com.histmersion.domain.catalogue.places

import com.histmersion.api.*
import com.histmersion.util.identity.AliasTablesAdapter
import com.augustnagro.magnum.{connect, transact, DbCon}

import javax.sql.DataSource

/** The places' repository as the db suites seed and read it: each call on a connection (a write in a transaction) of
  * the suite's own `ds`, and the SQL the suites assert on.
  */
object PlaceFixtures:
  export PlaceSql.*
  export PlaceRepo.{geoJsonQuery, geoJsonTimeQuery, rangeShadowedSql, rangeShadowTestSql}
  export FindingPlaceRepo.{placeholderSql, DefaultPlaceholderAreaShare, DefaultPlaceholderMin}
  export Geocoder.{apply as applyMoves, candidates, moves, place, placements, resolve, tierSql}

  def upsertPlaces(ds: DataSource, batch: List[PlaceInput], dataset: Dataset = Dataset.Seed): PlaceImportResult =
    if batch.isEmpty then PlaceImportResult(0, 0, 0, Nil) else transact(ds)(PlaceRepo.upsertPlaces(batch, dataset))

  def search(
      ds: DataSource,
      q: Option[String],
      asOf: Option[Long],
      limit: Int,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): Vector[PlaceHit] = connect(ds)(PlaceRepo.search(q, asOf, limit, modernFrom))

  def get(
      ds: DataSource,
      id: String,
      asOf: Option[Long] = None,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): Option[PlaceDetail] = connect(ds)(PlaceRepo.get(id, asOf, modernFrom))

  def children(
      ds: DataSource,
      id: String,
      asOf: Option[Long],
      limit: Int,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): Vector[PlaceSummary] = connect(ds)(PlaceRepo.children(id, asOf, limit, modernFrom))

  def hierarchy(
      ds: DataSource,
      asOf: Option[Long],
      root: Option[String],
      maxDepth: Int,
      limit: Int,
      depthCap: Int = Int.MaxValue,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): Vector[PlaceTreeRow] = connect(ds)(PlaceRepo.hierarchy(asOf, root, maxDepth, limit, depthCap, modernFrom))

  def placesOf(ds: DataSource, findingId: String): Vector[PlaceSummary] = connect(ds)(PlaceRepo.placesOf(findingId))

  def geoJson(
      ds: DataSource,
      kind: Option[String],
      asOf: Option[Long],
      limit: Int,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): String = connect(ds)(PlaceRepo.geoJson(kind, asOf, limit, modernFrom))

  def geoJsonAt(
      ds: DataSource,
      kind: Option[String],
      time: GeoTime,
      limit: Int,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): String = connect(ds)(PlaceRepo.geoJsonAt(kind, time, limit, modernFrom))

  def scopeFor(ds: DataSource, values: List[String], period: Option[Period]): PlaceScope =
    connect(ds)(PlaceRepo.scopeFor(values, period))

  def footprints(ds: DataSource, placeId: String, asOf: Option[Long]): Vector[PlaceFootprint] =
    connect(ds)(PlaceRepo.footprints(placeId, asOf))

  def links(ds: DataSource, placeId: String): Vector[PlaceLink] = connect(ds)(PlaceRepo.links(placeId))

  def rename(ds: DataSource, oldId: String, newId: String): Either[ApiError, Unit] =
    transact(ds)(PlaceRepo.rename(oldId, newId))

  def renamedTo(ds: DataSource, id: String): Option[String] = AliasTablesAdapter.PlaceAliases.renamedTo(ds, id)

  def addName(ds: DataSource, id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged] =
    transact(ds)(PlaceRepo.addName(id, name, lang))

  def removeName(ds: DataSource, id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged] =
    transact(ds)(PlaceRepo.removeName(id, name, lang))

  def manualNames(ds: DataSource, id: Option[String]): List[ManualPlaceName] = connect(ds)(PlaceRepo.manualNames(id))

  def addPlace(ds: DataSource, in: PlaceInput): Either[ApiError, PlaceSummary] = transact(ds)(PlaceRepo.addPlace(in))

  def count(ds: DataSource): Int = connect(ds)(PlaceRepo.count())

  def rebuildFindingPlaces(ds: DataSource, toleranceKm: Double): PlaceRebuildResult =
    transact(ds)(FindingPlaceRepo.rebuildFindingPlaces(toleranceKm))

  def materialisePlaces(ds: DataSource, placeIds: List[String], toleranceKm: Double): Int =
    transact(ds)(FindingPlaceRepo.materialisePlaces(placeIds, toleranceKm))

  /** The same, on the suite's own transaction. */
  def materialisePlacesIn(placeIds: List[String], toleranceKm: Double)(using DbCon): Int =
    FindingPlaceRepo.materialisePlaces(placeIds, toleranceKm)

  def unresolvedPlaceholders(ds: DataSource): List[(String, String)] =
    connect(ds)(FindingPlaceRepo.unresolvedPlaceholdersIn())

  /** The places of the gazetteer slice the rows of a tree hang off. */
  def gazetteer(ds: DataSource, rows: Seq[Tree.TreeRow]): (List[Tree.RegionNode], List[PlaceRelation]) =
    connect(ds)(TreeRepo.gazetteer(rows.iterator.flatMap(_.placeIds).toList))

  def setOverride(
      ds: DataSource,
      id: String,
      edit: PlaceOverrideEdit,
      toleranceKm: Double
  ): Either[ApiError, PlaceOverrideChanged] = transact(ds)(PlaceOverrideRepo.set(id, edit, toleranceKm))

  def merge(ds: DataSource, from: String, into: String, toleranceKm: Double): Either[ApiError, PlaceGone] =
    transact(ds)(PlaceOverrideRepo.merge(from, into, toleranceKm))

  def remove(ds: DataSource, id: String, reason: Option[String]): Either[ApiError, PlaceGone] =
    transact(ds)(PlaceOverrideRepo.remove(id, reason))

  def overrides(ds: DataSource, id: Option[String]): List[PlaceOverride] = connect(ds)(PlaceOverrideRepo.list(id))

  def replaceLand(ds: DataSource, geometries: List[String]): Int =
    val n = transact(ds)(LandRepo.replace(geometries))
    connect(ds)(LandRepo.analyze())
    n

  def reclip(ds: DataSource): Int =
    connect(ds)(LandRepo.polities()).grouped(LandRepo.ReclipBatch).foreach(b => transact(ds)(LandRepo.reclip(b)))
    connect(ds)(LandRepo.clipped())

  def gazetteerSources(ds: DataSource): List[GazetteerSourceRow] = connect(ds)(GazetteerRepo.sources())

  def proposals(ds: DataSource): List[PlaceReview.Proposal] = connect(ds)(PlaceReviewRepo.proposals())

  def applyProposals(ds: DataSource, ps: List[PlaceReview.Proposal], toleranceKm: Double): (Int, Int) =
    transact(ds)(PlaceReviewRepo.apply(ps, toleranceKm))

  def relocations(ds: DataSource): List[PlaceReviewRepo.Relocation] = connect(ds)(PlaceReviewRepo.relocations())

  def relocate(ds: DataSource, rs: List[PlaceReviewRepo.Relocation], toleranceKm: Double): Int =
    transact(ds)(PlaceReviewRepo.relocate(rs, toleranceKm))
