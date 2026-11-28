package com.histmersion
package domain.catalogue.places

import com.histmersion.api.catalogue.geocode.Move
import com.histmersion.api.catalogue.geocode.Placement

import com.histmersion.api.catalogue.Geocode
import com.augustnagro.magnum.{DbCon, Frag}
import com.histmersion.domain.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}

/** What the places decide for the library: which places a source's findings belong to (`finding_places`), placed again
  * inside the library's own transaction whenever it writes the source, so a moved or new finding is located the moment
  * the write commits. `toleranceKm` is how near a finding's point must be to a place's to be its.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait FindingPlacement:
  def toleranceKm: FindingPlacement.Implementation.ToleranceKm

  /** The findings of `citekey` placed again; the links written. */
  def placeSource(citekey: Citekey)(using DbCon): RowCount

object FindingPlacement:

  /** Fallback point tolerance for callers that hold no config — the legacy `SourceRepo.upsert` shape and the tests. It
    * MUST equal `places.point-tolerance-km` in `reference.conf`; `PlaceRepoSuite` asserts exactly that, so the two
    * cannot drift. Every production write passes the configured value explicitly.
    */
  val DefaultToleranceKm: Implementation.ToleranceKm = Implementation.ToleranceKm(10.0)

  def apply(toleranceKm: Implementation.ToleranceKm = DefaultToleranceKm): FindingPlacement = Implementation(
    toleranceKm
  )

  /** Every finding on a placeholder point (one standing for a named place), as `lint --rule finding-placeholder` reads
    * them: id, place name, period, citekey and point.
    */
  def placeholderFindingsSql: Frag = FindingPlaceRepo.placeholderFindingsSql

  /** The findings on a placeholder point whose place name the geocoder does not resolve, or that have none: (citekey,
    * line), by citekey and finding id.
    */
  def unresolvedPlaceholdersIn()(using DbCon): List[(Citekey, FindingPlaceRepo.Placeholder.Detail)] =
    FindingPlaceRepo.unresolvedPlaceholdersIn()

  /** The findings whose stored point is the one the old geocoder chose for their place name, as
    * `lint --rule finding-geocode` reads them.
    */
  def legacyPointsSql: Frag = Geocoder.legacySql

  /** The findings with a place name and no point, as `lint --rule finding-unplaced` reads them. */
  def unplacedSql: Frag = Geocoder.unplacedSql

  /** The moves `finding-geocode`'s repair would make: every old-geocoder point the current ranking puts more than
    * `repairKm` away. Read-only.
    */
  def movesIn(s: GeocodeSettings)(using DbCon): List[Move] = Geocoder.movesIn(s)

  /** The points `finding-unplaced`'s repair would give: every unplaced finding whose name the gazetteer resolves now.
    * Read-only.
    */
  def placementsIn(s: GeocodeSettings)(using DbCon): List[Placement] = Geocoder.placementsIn(s)

  /** The moves applied (compare-and-set), the touched sources' findings placed again; the findings moved. */
  def move(ms: List[Move], toleranceKm: Implementation.ToleranceKm)(using DbCon): RowCount =
    Geocoder.apply(ms, toleranceKm)

  /** The points given (compare-and-set), the touched sources' findings placed again; the findings placed. */
  def place(ps: List[Placement], toleranceKm: Implementation.ToleranceKm)(using DbCon): RowCount =
    Geocoder.place(ps, toleranceKm)

  final private class Implementation(val toleranceKm: Implementation.ToleranceKm) extends FindingPlacement:
    def placeSource(citekey: Citekey)(using DbCon): RowCount = FindingPlaceRepo.materialiseFor(citekey, toleranceKm)

  object Implementation:
    export GeocodeSettings.ToleranceKm
