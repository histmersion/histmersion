package com.histmersion.domain.catalogue.places

import com.histmersion.api.{PlaceRebuildResult, SpatialHierarchyResult}
import com.histmersion.util.database.Transactor

/** The gazetteer's geometry operations, a mechanism over PostGIS with no in-memory twin (as the map's tiles): which
  * places each finding belongs to rebuilt from scratch, the hierarchy built from spatial containment, the land the
  * polities are clipped to replaced and every polity clipped again.
  */
trait PlaceGeometry:

  /** `finding_places` from scratch: the findings' points, areas and names matched to every place again. */
  def rebuildFindingPlaces(toleranceKm: Double): PlaceRebuildResult

  /** A `part_of` edge from every point place to each polygon place that contains it. */
  def buildHierarchy(): SpatialHierarchyResult

  /** The land replaced by `geometries` (GeoJSON) in one transaction; the pieces stored. */
  def replaceLand(geometries: List[String]): Int

  /** Every polity clipped to the land again, a batch per transaction; how many are clipped now. */
  def reclip(): Int

object PlaceGeometry:
  def apply(db: Transactor): PlaceGeometry = Implementation(db)

  final private class Implementation(db: Transactor) extends PlaceGeometry:
    def rebuildFindingPlaces(toleranceKm: Double): PlaceRebuildResult =
      db.transact(FindingPlaceRepo.rebuildFindingPlaces(toleranceKm))
    def buildHierarchy(): SpatialHierarchyResult = db.transact(PlaceRepo.buildSpatialHierarchy())
    def replaceLand(geometries: List[String]): Int =
      val _ = db.transact(LandRepo.replace(geometries))
      db.read(LandRepo.analyze())
      db.read(LandRepo.pieces())
    def reclip(): Int =
      db.read(LandRepo.polities()).grouped(LandRepo.ReclipBatch).foreach(batch => db.transact(LandRepo.reclip(batch)))
      db.read(LandRepo.clipped())
