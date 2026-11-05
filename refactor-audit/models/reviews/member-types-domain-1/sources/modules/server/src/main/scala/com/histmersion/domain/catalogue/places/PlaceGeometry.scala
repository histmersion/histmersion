package com.histmersion.domain.catalogue.places

import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.util.database.Transactor

import scala.annotation.tailrec
import com.histmersion.api.catalogue.Place

/** The gazetteer's geometry operations, a mechanism over PostGIS with no in-memory twin (as the map's tiles): which
  * places each finding belongs to rebuilt from scratch, the hierarchy built from spatial containment, the land the
  * polities are clipped to replaced and every polity clipped again, and the places' checks' repairs (a kind, a grid
  * point, a point-only area).
  */
trait PlaceGeometry:

  /** `finding_places` from scratch: the findings' points, areas and names matched to every place again. */
  def rebuildFindingPlaces(toleranceKm: com.histmersion.domain.catalogue.places.PlaceGeometry.RebuildFindingPlacesToleranceKm): Place.RebuildResult

  /** A `part_of` edge from every point place to each polygon place that contains it. */
  def buildHierarchy(): SpatialHierarchyResult

  /** The land replaced by `geometries` (GeoJSON) in one transaction; the pieces stored. */
  def replaceLand(geometries: List[com.histmersion.domain.catalogue.places.PlaceGeometry.ReplaceLandGeometries]): com.histmersion.domain.catalogue.places.PlaceGeometry.ReplaceLandResult

  /** Every polity clipped to the land again, a batch per transaction; how many are clipped now. */
  def reclip(): com.histmersion.domain.catalogue.places.PlaceGeometry.ReclipResult

  /** `place-kind`'s repair: the places it lists reclassified, the withdrawn ones merged into their replacement, their
    * findings matched again (reclassified, merged).
    */
  def reclassify(toleranceKm: com.histmersion.domain.catalogue.places.PlaceGeometry.ReclassifyToleranceKm): (com.histmersion.domain.catalogue.places.PlaceGeometry.ReclassifyResultPart1, com.histmersion.domain.catalogue.places.PlaceGeometry.ReclassifyResultPart2)

  /** `place-grid-point`'s repair: the places it lists moved from a grid square's centre to their linked record's point;
    * how many moved.
    */
  def relocate(toleranceKm: com.histmersion.domain.catalogue.places.PlaceGeometry.RelocateToleranceKm): com.histmersion.domain.catalogue.places.PlaceGeometry.RelocateResult

  /** `place-imported-point-area`'s repair: the listed places no evidence names removed, `batch` per transaction, until
    * none is left or `budgetMs` is spent (call again while `remaining > 0`).
    */
  def removePointAreas(settings: PointAreaRepo.Settings): PointAreaRepo.Removal

object PlaceGeometry:
  type RebuildFindingPlacesToleranceKm = RebuildFindingPlacesToleranceKm.T
  object RebuildFindingPlacesToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceLandResult = ReplaceLandResult.T
  object ReplaceLandResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceLandGeometries = ReplaceLandGeometries.T
  object ReplaceLandGeometries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReclipResult = ReclipResult.T
  object ReclipResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReclassifyResultPart1 = ReclassifyResultPart1.T
  object ReclassifyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReclassifyResultPart2 = ReclassifyResultPart2.T
  object ReclassifyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReclassifyToleranceKm = ReclassifyToleranceKm.T
  object ReclassifyToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelocateResult = RelocateResult.T
  object RelocateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RelocateToleranceKm = RelocateToleranceKm.T
  object RelocateToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor)(using timer: com.histmersion.util.time.Timer): PlaceGeometry = Implementation(db)

  final private class Implementation(db: Transactor)(using timer: com.histmersion.util.time.Timer) extends PlaceGeometry:
    def rebuildFindingPlaces(toleranceKm: com.histmersion.domain.catalogue.places.PlaceGeometry.Implementation.RebuildFindingPlacesToleranceKm): Place.RebuildResult =
      db.transact(FindingPlaceRepo.rebuildFindingPlaces(toleranceKm))
    def buildHierarchy(): SpatialHierarchyResult = db.transact(PlaceRepo.buildSpatialHierarchy())
    def replaceLand(geometries: List[com.histmersion.domain.catalogue.places.PlaceGeometry.Implementation.ReplaceLandGeometries]): com.histmersion.domain.catalogue.places.PlaceGeometry.Implementation.ReplaceLandResult =
      val _ = db.transact(LandRepo.replace(geometries))
      db.read(LandRepo.analyze())
      db.read(LandRepo.pieces())
    def reclip(): com.histmersion.domain.catalogue.places.PlaceGeometry.Implementation.ReclipResult =
      db.read(LandRepo.polities()).grouped(LandRepo.ReclipBatch).foreach(batch => db.transact(LandRepo.reclip(batch)))
      db.read(LandRepo.clipped())
    def reclassify(toleranceKm: com.histmersion.domain.catalogue.places.PlaceGeometry.Implementation.ReclassifyToleranceKm): (com.histmersion.domain.catalogue.places.PlaceGeometry.Implementation.ReclassifyResultPart1, com.histmersion.domain.catalogue.places.PlaceGeometry.Implementation.ReclassifyResultPart2) =
      val proposals = db.read(PlaceReviewRepo.proposals())
      if !proposals.exists(_.fixable) then (0, 0) else db.transact(PlaceReviewRepo.apply(proposals, toleranceKm))
    def relocate(toleranceKm: com.histmersion.domain.catalogue.places.PlaceGeometry.Implementation.RelocateToleranceKm): com.histmersion.domain.catalogue.places.PlaceGeometry.Implementation.RelocateResult =
      val moves = db.read(PlaceReviewRepo.relocations())
      if moves.isEmpty then 0 else db.transact(PlaceReviewRepo.relocate(moves, toleranceKm))
    def removePointAreas(s: PointAreaRepo.Settings): PointAreaRepo.Removal =
      if s.kinds.isEmpty then PointAreaRepo.Removal(0, 0, 0, 0)
      else
        val kinds = s.kinds
        val deadline = timer.after(scala.concurrent.duration.FiniteDuration(math.max(0L, s.budgetMs), scala.concurrent.duration.MILLISECONDS))
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
              if deadline.reached then acc else go(more, acc._1, acc._2, acc._3)
        val (removed, findings, cks) = go(chunks, 0, 0, Set.empty)
        val remaining = db.read(PointAreaRepo.removableIn(kinds)).size
        PointAreaRepo.Removal(removed, remaining, findings, cks.size)

  object Implementation:
    type RebuildFindingPlacesToleranceKm = RebuildFindingPlacesToleranceKm.T
    object RebuildFindingPlacesToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReplaceLandResult = ReplaceLandResult.T
    object ReplaceLandResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReplaceLandGeometries = ReplaceLandGeometries.T
    object ReplaceLandGeometries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReclipResult = ReclipResult.T
    object ReclipResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReclassifyResultPart1 = ReclassifyResultPart1.T
    object ReclassifyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReclassifyResultPart2 = ReclassifyResultPart2.T
    object ReclassifyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReclassifyToleranceKm = ReclassifyToleranceKm.T
    object ReclassifyToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RelocateResult = RelocateResult.T
    object RelocateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RelocateToleranceKm = RelocateToleranceKm.T
    object RelocateToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
