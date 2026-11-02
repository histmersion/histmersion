package com.histmersion.domain.catalogue.places

import com.histmersion.api.Geocode
import com.augustnagro.magnum.DbCon

/** What the places decide for the library: which places a source's findings belong to (`finding_places`), placed again
  * inside the library's own transaction whenever it writes the source, so a moved or new finding is located the moment
  * the write commits. `toleranceKm` is how near a finding's point must be to a place's to be its.
  */
trait FindingPlacement:
  def toleranceKm: Double

  /** The findings of `citekey` placed again; the links written. */
  def placeSource(citekey: String)(using DbCon): Int

object FindingPlacement:

  /** Fallback point tolerance for callers that hold no config — the legacy `SourceRepo.upsert` shape and the tests. It
    * MUST equal `places.point-tolerance-km` in `reference.conf`; `PlaceRepoSuite` asserts exactly that, so the two
    * cannot drift. Every production write passes the configured value explicitly.
    */
  val DefaultToleranceKm: Double = 10.0

  def apply(toleranceKm: Double = DefaultToleranceKm): FindingPlacement = Implementation(toleranceKm)

  /** Every finding on a placeholder point (one standing for a named place), as `lint --rule finding-placeholder` reads
    * them: id, place name, period, citekey and point.
    */
  def placeholderFindingsSql: String = FindingPlaceRepo.placeholderFindingsSql

  /** The findings on a placeholder point whose place name the geocoder does not resolve, or that have none: (citekey,
    * line), by citekey and finding id.
    */
  def unresolvedPlaceholdersIn()(using DbCon): List[(String, String)] = FindingPlaceRepo.unresolvedPlaceholdersIn()

  /** The findings whose stored point is the one the old geocoder chose for their place name, as
    * `lint --rule finding-geocode` reads them.
    */
  def legacyPointsSql: String = Geocoder.legacySql

  /** The findings with a place name and no point, as `lint --rule finding-unplaced` reads them. */
  def unplacedSql: String = Geocoder.unplacedSql

  /** The moves `finding-geocode`'s repair would make: every old-geocoder point the current ranking puts more than
    * `repairKm` away. Read-only.
    */
  def movesIn(s: GeocodeSettings)(using DbCon): List[Geocode.Move] = Geocoder.movesIn(s)

  /** The points `finding-unplaced`'s repair would give: every unplaced finding whose name the gazetteer resolves now.
    * Read-only.
    */
  def placementsIn(s: GeocodeSettings)(using DbCon): List[Geocode.Placement] = Geocoder.placementsIn(s)

  /** The moves applied (compare-and-set), the touched sources' findings placed again; the findings moved. */
  def move(ms: List[Geocode.Move], toleranceKm: Double)(using DbCon): Int = Geocoder.apply(ms, toleranceKm)

  /** The points given (compare-and-set), the touched sources' findings placed again; the findings placed. */
  def place(ps: List[Geocode.Placement], toleranceKm: Double)(using DbCon): Int = Geocoder.place(ps, toleranceKm)

  final private class Implementation(val toleranceKm: Double) extends FindingPlacement:
    def placeSource(citekey: String)(using DbCon): Int = FindingPlaceRepo.materialiseFor(citekey, toleranceKm)
