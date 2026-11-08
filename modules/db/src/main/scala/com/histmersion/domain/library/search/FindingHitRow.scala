package com.histmersion
package domain.library.search

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.Finding
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Place
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One row of `FilterSql.findingsQuery`. */
final private[search] case class FindingHitRow(
    citekey: Citekey,
    id: FindingHitRow.Id,
    claim: FindingHitRow.Claim,
    regionName: Option[FindingHitRow.RegionName],
    lon: Option[Longitude],
    lat: Option[Latitude],
    periodStart: Option[Year],
    periodEnd: Option[Year],
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
      regionName.map(r => Place.Region(r.value)),
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
  object Id extends RefinedType[String, Pure]
  type Claim = Claim.T
  object Claim extends RefinedType[String, Pure]
  type RegionName = RegionName.T
  object RegionName extends RefinedType[String, Pure]
  type PeriodName = PeriodName.T
  object PeriodName extends RefinedType[String, Pure]
  type Tags = Tags.T
  object Tags extends RefinedType[String, Pure]
  type Places = Places.T
  object Places extends RefinedType[String, Pure]
  type PlaceIds = PlaceIds.T
  object PlaceIds extends RefinedType[String, Pure]
