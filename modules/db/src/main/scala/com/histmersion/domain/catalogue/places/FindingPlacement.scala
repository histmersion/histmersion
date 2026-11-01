package com.histmersion.domain.catalogue.places

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

  final private class Implementation(val toleranceKm: Double) extends FindingPlacement:
    def placeSource(citekey: String)(using DbCon): Int = FindingPlaceRepo.materialiseFor(citekey, toleranceKm)
