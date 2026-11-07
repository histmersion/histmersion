package com.histmersion.domain.library.search

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.Finding
import com.histmersion.util.database.NewtypeColumns.given

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
    Finding.Match(
      Finding.Id(id.value),
      Finding.Claim(claim.value),
      regionName.map(r => com.histmersion.api.catalogue.Place.Region(r.value)),
      lon.map(l => com.histmersion.api.catalogue.Longitude(l.value)),
      lat.map(l => com.histmersion.api.catalogue.Latitude(l.value)),
      periodStart.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
      periodEnd.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
      periodName.map(n => Finding.PeriodName(n.value)),
      tags.map(t => Finding.Tags(t.value)),
      Nil,
      places.map(p => Finding.Match.Places(p.value))
    )
  def assignedPlaceIds: List[com.histmersion.domain.catalogue.Place.Id] =
    placeIds.map(id => com.histmersion.domain.catalogue.Place.Id.applyUnsafe(id.value))

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
