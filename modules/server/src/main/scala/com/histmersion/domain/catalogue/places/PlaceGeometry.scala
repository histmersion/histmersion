package com.histmersion
package domain.catalogue.places

import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.util.database.Transactor

import scala.annotation.tailrec
import com.histmersion.api.catalogue.Place
import com.histmersion.util.time.Timer
import com.histmersion.domain.library.Citekey
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.Unused
import com.histmersion.lint.{tolerated, LintRule}

/** The gazetteer's geometry operations, a mechanism over PostGIS with no in-memory twin (as the map's tiles): which
  * places each finding belongs to rebuilt from scratch, the hierarchy built from spatial containment, the land the
  * polities are clipped to replaced and every polity clipped again, and the places' checks' repairs (a kind, a grid
  * point, a point-only area).
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait PlaceGeometry:

  /** `finding_places` from scratch: the findings' points, areas and names matched to every place again. */
  def rebuildFindingPlaces(toleranceKm: GeocodeSettings.ToleranceKm)(using WritePermit): Place.RebuildResult

  /** A `part_of` edge from every point place to each polygon place that contains it. */
  def buildHierarchy()(using WritePermit): SpatialHierarchyResult

  /** The land replaced by `geometries` (GeoJSON) in one transaction; the pieces stored. */
  def replaceLand(geometries: List[PlaceDetailRow.Geometry])(using WritePermit): RowCount

  /** Every polity clipped to the land again, a batch per transaction; how many are clipped now. */
  def reclip()(using WritePermit): RowCount

  /** `place-kind`'s repair: the places it lists reclassified, the withdrawn ones merged into their replacement, their
    * findings matched again (reclassified, merged).
    */
  def reclassify(toleranceKm: GeocodeSettings.ToleranceKm)(using WritePermit): (RowCount, RowCount)

  /** `place-grid-point`'s repair: the places it lists moved from a grid square's centre to their linked record's point;
    * how many moved.
    */
  def relocate(toleranceKm: GeocodeSettings.ToleranceKm)(using WritePermit): RowCount

  /** `place-imported-point-area`'s repair: the listed places no evidence names removed, `batch` per transaction, until
    * none is left or `budgetMs` is spent (call again while `remaining > 0`).
    */
  def removePointAreas(settings: PointAreaRepo.Settings)(using WritePermit): PointAreaRepo.Removal

object PlaceGeometry:

  def apply(db: Transactor)(using timer: Timer): PlaceGeometry = Implementation(db)

  final private class Implementation(db: Transactor)(using timer: Timer) extends PlaceGeometry:
    def rebuildFindingPlaces(toleranceKm: GeocodeSettings.ToleranceKm)(using WritePermit): Place.RebuildResult =
      db.write(FindingPlaceRepo.rebuildFindingPlaces(toleranceKm))
    def buildHierarchy()(using WritePermit): SpatialHierarchyResult = db.write(PlaceRepo.buildSpatialHierarchy())
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def replaceLand(geometries: List[PlaceDetailRow.Geometry])(using WritePermit): RowCount =
      db.write(Unused(LandRepo.replace(geometries.map(g => BPlaceRow.Geom(g.value))), Unused.Why.Reported))
      db.read(LandRepo.analyze())
      db.read(LandRepo.pieces())
    def reclip()(using WritePermit): RowCount =
      db.read(LandRepo.polities()).grouped(LandRepo.ReclipBatch).foreach(batch => db.write(LandRepo.reclip(batch)))
      db.read(LandRepo.clipped())
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def reclassify(toleranceKm: GeocodeSettings.ToleranceKm)(using WritePermit): (RowCount, RowCount) =
      val proposals = db.read(PlaceReviewRepo.proposals())
      if !proposals.exists(_.fixable) then (RowCount(0), RowCount(0))
      else db.write(PlaceReviewRepo.apply(proposals, PointAreaRepo.Settings.ToleranceKm(toleranceKm.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def relocate(toleranceKm: GeocodeSettings.ToleranceKm)(using WritePermit): RowCount =
      val moves = db.read(PlaceReviewRepo.relocations())
      if moves.isEmpty then RowCount(0)
      else db.write(PlaceReviewRepo.relocate(moves, PointAreaRepo.Settings.ToleranceKm(toleranceKm.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def removePointAreas(s: PointAreaRepo.Settings)(using WritePermit): PointAreaRepo.Removal =
      if s.kinds.isEmpty then
        PointAreaRepo.Removal(
          PointAreaRepo.Removal.Removed(0),
          PointAreaRepo.Removal.Remaining(0),
          PointAreaRepo.Removal.Findings(0),
          PointAreaRepo.Removal.Sources(0)
        )
      else
        val kinds = s.kinds.map(k => domain.catalogue.Place.Kind.Wire(k.value))
        val deadline = timer.after(
          scala.concurrent.duration
            .FiniteDuration(math.max(0L, s.budgetMs.value), scala.concurrent.duration.MILLISECONDS)
        )
        val todo = db.read(PointAreaRepo.removableIn(kinds))
        val chunks = todo.grouped(math.max(1, s.batch.value)).toList
        val named =
          if todo.isEmpty then Map.empty[PointAreaRepo.NameKey, Set[Citekey]] else db.read(PointAreaRepo.nameKeysIn())
        // one pass over the chunks while time is left; at least one chunk per call, so a call progresses
        @tailrec
        def go(
            rest: List[List[domain.catalogue.Place.Id]],
            removed: Int,
            findings: Int,
            cks: Set[Citekey]
        )(using WritePermit): (Int, Int, Set[Citekey]) =
          rest match
            case Nil         => (removed, findings, cks)
            case ids :: more =>
              val (n, f, c) = db.write(PointAreaRepo.removeIn(ids, kinds, s.toleranceKm, named))
              val acc = (removed + n.value, findings + f.value, cks ++ c)
              if deadline.reached then acc else go(more, acc._1, acc._2, acc._3)
        val (removed, findings, cks) = go(chunks, 0, 0, Set.empty)
        val remaining = db.read(PointAreaRepo.removableIn(kinds)).size
        PointAreaRepo.Removal(
          PointAreaRepo.Removal.Removed(removed),
          PointAreaRepo.Removal.Remaining(remaining),
          PointAreaRepo.Removal.Findings(findings),
          PointAreaRepo.Removal.Sources(cks.size)
        )
