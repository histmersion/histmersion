package com.histmersion.domain.catalogue.places

import com.histmersion.api.catalogue.Geocode
import com.augustnagro.magnum.DbCon

/** What the places decide for the library: which places a source's findings belong to (`finding_places`), placed again
  * inside the library's own transaction whenever it writes the source, so a moved or new finding is located the moment
  * the write commits. `toleranceKm` is how near a finding's point must be to a place's to be its.
  */
trait FindingPlacement:
  def toleranceKm: com.histmersion.domain.catalogue.places.FindingPlacement.ToleranceKmResult

  /** The findings of `citekey` placed again; the links written. */
  def placeSource(citekey: com.histmersion.domain.library.Citekey)(using DbCon): com.histmersion.domain.catalogue.places.FindingPlacement.PlaceSourceResult

object FindingPlacement:

  /** Fallback point tolerance for callers that hold no config — the legacy `SourceRepo.upsert` shape and the tests. It
    * MUST equal `places.point-tolerance-km` in `reference.conf`; `PlaceRepoSuite` asserts exactly that, so the two
    * cannot drift. Every production write passes the configured value explicitly.
    */
  type ToleranceKmResult = ToleranceKmResult.T
  object ToleranceKmResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceSourceResult = PlaceSourceResult.T
  object PlaceSourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultToleranceKm = DefaultToleranceKmValue
  type DefaultToleranceKmValue = DefaultToleranceKmValue.T
  object DefaultToleranceKmValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyToleranceKm = ApplyToleranceKm.T
  object ApplyToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceholderFindingsSqlResult = PlaceholderFindingsSqlResult.T
  object PlaceholderFindingsSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnresolvedPlaceholdersInResultPart1 = UnresolvedPlaceholdersInResultPart1.T
  object UnresolvedPlaceholdersInResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnresolvedPlaceholdersInResultPart2 = UnresolvedPlaceholdersInResultPart2.T
  object UnresolvedPlaceholdersInResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LegacyPointsSqlResult = LegacyPointsSqlResult.T
  object LegacyPointsSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnplacedSqlResult = UnplacedSqlResult.T
  object UnplacedSqlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveResult = MoveResult.T
  object MoveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MoveToleranceKm = MoveToleranceKm.T
  object MoveToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceResult = PlaceResult.T
  object PlaceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceToleranceKm = PlaceToleranceKm.T
  object PlaceToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val DefaultToleranceKm: com.histmersion.domain.catalogue.places.FindingPlacement.DefaultToleranceKm = 10.0

  def apply(toleranceKm: com.histmersion.domain.catalogue.places.FindingPlacement.ApplyToleranceKm = DefaultToleranceKm): FindingPlacement = Implementation(toleranceKm)

  /** Every finding on a placeholder point (one standing for a named place), as `lint --rule finding-placeholder` reads
    * them: id, place name, period, citekey and point.
    */
  def placeholderFindingsSql: com.histmersion.domain.catalogue.places.FindingPlacement.PlaceholderFindingsSqlResult = FindingPlaceRepo.placeholderFindingsSql

  /** The findings on a placeholder point whose place name the geocoder does not resolve, or that have none: (citekey,
    * line), by citekey and finding id.
    */
  def unresolvedPlaceholdersIn()(using DbCon): List[(com.histmersion.domain.catalogue.places.FindingPlacement.UnresolvedPlaceholdersInResultPart1, com.histmersion.domain.catalogue.places.FindingPlacement.UnresolvedPlaceholdersInResultPart2)] = FindingPlaceRepo.unresolvedPlaceholdersIn()

  /** The findings whose stored point is the one the old geocoder chose for their place name, as
    * `lint --rule finding-geocode` reads them.
    */
  def legacyPointsSql: com.histmersion.domain.catalogue.places.FindingPlacement.LegacyPointsSqlResult = Geocoder.legacySql

  /** The findings with a place name and no point, as `lint --rule finding-unplaced` reads them. */
  def unplacedSql: com.histmersion.domain.catalogue.places.FindingPlacement.UnplacedSqlResult = Geocoder.unplacedSql

  /** The moves `finding-geocode`'s repair would make: every old-geocoder point the current ranking puts more than
    * `repairKm` away. Read-only.
    */
  def movesIn(s: GeocodeSettings)(using DbCon): List[Geocode.Move] = Geocoder.movesIn(s)

  /** The points `finding-unplaced`'s repair would give: every unplaced finding whose name the gazetteer resolves now.
    * Read-only.
    */
  def placementsIn(s: GeocodeSettings)(using DbCon): List[Geocode.Placement] = Geocoder.placementsIn(s)

  /** The moves applied (compare-and-set), the touched sources' findings placed again; the findings moved. */
  def move(ms: List[Geocode.Move], toleranceKm: com.histmersion.domain.catalogue.places.FindingPlacement.MoveToleranceKm)(using DbCon): com.histmersion.domain.catalogue.places.FindingPlacement.MoveResult = Geocoder.apply(ms, toleranceKm)

  /** The points given (compare-and-set), the touched sources' findings placed again; the findings placed. */
  def place(ps: List[Geocode.Placement], toleranceKm: com.histmersion.domain.catalogue.places.FindingPlacement.PlaceToleranceKm)(using DbCon): com.histmersion.domain.catalogue.places.FindingPlacement.PlaceResult = Geocoder.place(ps, toleranceKm)

  final private class Implementation(val toleranceKm: Implementation.ToleranceKm) extends FindingPlacement:
    def placeSource(citekey: com.histmersion.domain.library.Citekey)(using DbCon): com.histmersion.domain.catalogue.places.FindingPlacement.Implementation.PlaceSourceResult = FindingPlaceRepo.materialiseFor(citekey, toleranceKm)

  object Implementation:
    type ToleranceKm = ToleranceKm.T
    object ToleranceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type PlaceSourceResult = PlaceSourceResult.T
    object PlaceSourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
