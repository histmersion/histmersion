package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.RegionNode
import com.histmersion.api.library.Tree

import com.histmersion.api.Dataset
import com.histmersion.domain.catalogue.places.PlaceAliasTable
import com.augustnagro.magnum.{connect, transact, DbCon}

import javax.sql.DataSource
import com.histmersion.api.catalogue.{GeoTime, ManualPlaceName, Period}
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Place

/** The places' repository as the db suites seed and read it: each call on a connection (a write in a transaction) of
  * the suite's own `ds`, and the SQL the suites assert on.
  */
object PlaceFixtures:
  export PlaceSql.*
  export PlaceRepo.{geoJsonQuery, geoJsonTimeQuery, rangeShadowedSql, rangeShadowTestSql}
  export FindingPlaceRepo.{placeholderSql, DefaultPlaceholderAreaShare, DefaultPlaceholderMin}
  export Geocoder.{apply as applyMoves, candidates, moves, place, placements, resolve, tierSql}

  def upsertPlaces(ds: DataSource, batch: List[Place.Input], dataset: Dataset = Dataset.Seed): Place.ImportResult =
    if batch.isEmpty then Place.ImportResult(com.histmersion.api.catalogue.Place.ImportResult.Places(0), com.histmersion.api.catalogue.Place.ImportResult.Names(0), com.histmersion.api.catalogue.Place.ImportResult.Relations(0), Nil) else transact(ds)(PlaceRepo.upsertPlaces(batch, dataset))

  def search(
      ds: DataSource,
      q: Option[String],
      asOf: Option[Long],
      limit: Int,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): Vector[Place.Hit] = connect(ds)(PlaceRepo.search(q, asOf, limit, modernFrom))

  def get(
      ds: DataSource,
      id: String,
      asOf: Option[Long] = None,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): Option[com.histmersion.api.catalogue.Place.Entry] = connect(ds)(PlaceRepo.get(id, asOf, modernFrom))

  def children(
      ds: DataSource,
      id: String,
      asOf: Option[Long],
      limit: Int,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): Vector[Place.Summary] = connect(ds)(PlaceRepo.children(id, asOf, limit, modernFrom))

  def hierarchy(
      ds: DataSource,
      asOf: Option[Long],
      root: Option[String],
      maxDepth: Int,
      limit: Int,
      depthCap: Int = Int.MaxValue,
      modernFrom: Long = PlaceSql.DefaultModernFrom
  ): Vector[Place.TreeRow] = connect(ds)(PlaceRepo.hierarchy(asOf, root, maxDepth, limit, depthCap, modernFrom))

  def placesOf(ds: DataSource, findingId: String): Vector[Place.Summary] = connect(ds)(PlaceRepo.placesOf(findingId))

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

  def footprints(ds: DataSource, placeId: String, asOf: Option[Long]): Vector[Place.Footprint] =
    connect(ds)(PlaceRepo.footprints(placeId, asOf))

  def links(ds: DataSource, placeId: String): Vector[Place.Link] = connect(ds)(PlaceRepo.links(placeId))

  def rename(ds: DataSource, oldId: String, newId: String): Either[Api.Error, Unit] =
    transact(ds)(PlaceRepo.rename(oldId, newId))

  def renamedTo(ds: DataSource, id: String): Option[String] = PlaceAliasTable.table.renamedTo(ds, id)

  def addName(ds: DataSource, id: String, name: String, lang: String): Either[Api.Error, Place.NameChanged] =
    transact(ds)(PlaceRepo.addName(id, name, lang))

  def removeName(ds: DataSource, id: String, name: String, lang: String): Either[Api.Error, Place.NameChanged] =
    transact(ds)(PlaceRepo.removeName(id, name, lang))

  def manualNames(ds: DataSource, id: Option[String]): List[ManualPlaceName] = connect(ds)(PlaceRepo.manualNames(id))

  def addPlace(ds: DataSource, in: Place.Input): Either[Api.Error, Place.Summary] = transact(ds)(PlaceRepo.addPlace(in))

  def count(ds: DataSource): Int = connect(ds)(PlaceRepo.count())

  def rebuildFindingPlaces(ds: DataSource, toleranceKm: Double): Place.RebuildResult =
    transact(ds)(FindingPlaceRepo.rebuildFindingPlaces(toleranceKm))

  def materialisePlaces(ds: DataSource, placeIds: List[String], toleranceKm: Double): Int =
    transact(ds)(FindingPlaceRepo.materialisePlaces(placeIds, toleranceKm))

  /** The same, on the suite's own transaction. */
  def materialisePlacesIn(placeIds: List[String], toleranceKm: Double)(using DbCon): Int =
    FindingPlaceRepo.materialisePlaces(placeIds, toleranceKm)

  def unresolvedPlaceholders(ds: DataSource): List[(String, String)] =
    connect(ds)(FindingPlaceRepo.unresolvedPlaceholdersIn())

  /** The places of the gazetteer slice the rows of a tree hang off. */
  def gazetteer(ds: DataSource, rows: Seq[Tree.TreeRow]): (List[RegionNode], List[Place.Relation]) =
    connect(ds)(TreeRepo.gazetteer(rows.iterator.flatMap(_.placeIds).toList))

  def setOverride(
      ds: DataSource,
      id: String,
      edit: Place.OverrideEdit,
      toleranceKm: Double
  ): Either[Api.Error, Place.OverrideChanged] = transact(ds)(PlaceOverrideRepo.set(id, edit, toleranceKm))

  def merge(ds: DataSource, from: String, into: String, toleranceKm: Double): Either[Api.Error, Place.Gone] =
    transact(ds)(PlaceOverrideRepo.merge(from, into, toleranceKm))

  def remove(ds: DataSource, id: String, reason: Option[String]): Either[Api.Error, Place.Gone] =
    transact(ds)(PlaceOverrideRepo.remove(id, reason))

  def overrides(ds: DataSource, id: Option[String]): List[Place.Override] = connect(ds)(PlaceOverrideRepo.list(id))

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
