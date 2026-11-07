package com.histmersion.domain.library.search

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.Finding

/** One row of `FilterSql.findingsQuery`. */
final private[search] case class FindingHitRow(
    citekey: com.histmersion.domain.library.Citekey,
    id: FindingHitRow.Id,
    claim: FindingHitRow.Claim,
    regionName: Option[FindingHitRow.RegionName],
    lon: Option[com.histmersion.Longitude],
    lat: Option[com.histmersion.Latitude],
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    periodName: Option[FindingHitRow.PeriodName],
    tags: List[FindingHitRow.Tags],
    /** The gazetteer labels / ids this finding was materialised into (plan §1.6a). */
    places: List[FindingHitRow.Places] = Nil,
    placeIds: List[FindingHitRow.PlaceIds] = Nil
) derives DbCodec:
  def toMatch: Finding.Match =
    Finding.Match(id, claim, regionName, lon, lat, periodStart, periodEnd, periodName, tags, Nil, places)
  def assignedPlaceIds: List[com.histmersion.domain.catalogue.Place.Id] = placeIds

object FindingHitRow:

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Claim = Claim.T
  object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionName = RegionName.T
  object RegionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodName = PeriodName.T
  object PeriodName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Places = Places.T
  object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceIds = PlaceIds.T
  object PlaceIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
