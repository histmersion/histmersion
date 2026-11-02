package com.histmersion.domain.catalogue.places

import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.catalogue.PlaceRebuildResult
import com.histmersion.util.database.Transactor

import scala.annotation.tailrec

/** The gazetteer's geometry operations, a mechanism over PostGIS with no in-memory twin (as the map's tiles): which
  * places each finding belongs to rebuilt from scratch, the hierarchy built from spatial containment, the land the
  * polities are clipped to replaced and every polity clipped again, and the places' checks' repairs (a kind, a grid
  * point, a point-only area).
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

  /** `place-kind`'s repair: the places it lists reclassified, the withdrawn ones merged into their replacement, their
    * findings matched again (reclassified, merged).
    */
  def reclassify(toleranceKm: Double): (Int, Int)

  /** `place-grid-point`'s repair: the places it lists moved from a grid square's centre to their linked record's point;
    * how many moved.
    */
  def relocate(toleranceKm: Double): Int

  /** `place-imported-point-area`'s repair: the listed places no evidence names removed, `batch` per transaction, until
    * none is left or `budgetMs` is spent (call again while `remaining > 0`).
    */
  def removePointAreas(settings: PointAreaRepo.Settings): PointAreaRepo.Removal

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
    def reclassify(toleranceKm: Double): (Int, Int) =
      val proposals = db.read(PlaceReviewRepo.proposals())
      if !proposals.exists(_.fixable) then (0, 0) else db.transact(PlaceReviewRepo.apply(proposals, toleranceKm))
    def relocate(toleranceKm: Double): Int =
      val moves = db.read(PlaceReviewRepo.relocations())
      if moves.isEmpty then 0 else db.transact(PlaceReviewRepo.relocate(moves, toleranceKm))
    def removePointAreas(s: PointAreaRepo.Settings): PointAreaRepo.Removal =
      if s.kinds.isEmpty then PointAreaRepo.Removal(0, 0, 0, 0)
      else
        val kinds = s.kinds
        val deadline = System.nanoTime() + math.max(0L, s.budgetMs) * 1000000L
        val todo = db.read(PointAreaRepo.removableIn(kinds))
        val chunks = todo.grouped(math.max(1, s.batch)).toList
        val named = if todo.isEmpty then Map.empty[String, Set[String]] else db.read(PointAreaRepo.nameKeysIn())
        // one pass over the chunks while time is left; at least one chunk per call, so a call progresses
        @tailrec
        def go(rest: List[List[String]], removed: Int, findings: Int, cks: Set[String]): (Int, Int, Set[String]) =
          rest match
            case Nil         => (removed, findings, cks)
            case ids :: more =>
              val (n, f, c) = db.transact(PointAreaRepo.removeIn(ids, kinds, s.toleranceKm, named))
              val acc = (removed + n, findings + f, cks ++ c)
              if System.nanoTime() >= deadline then acc else go(more, acc._1, acc._2, acc._3)
        val (removed, findings, cks) = go(chunks, 0, 0, Set.empty)
        val remaining = db.read(PointAreaRepo.removableIn(kinds)).size
        PointAreaRepo.Removal(removed, remaining, findings, cks.size)
