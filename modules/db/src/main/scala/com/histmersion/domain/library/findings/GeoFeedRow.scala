package com.histmersion
package domain.library.findings

import com.histmersion.util.database.given
import com.histmersion.api.library.Confidence

import com.augustnagro.magnum.*
import com.histmersion.api.library.GeoHit
import com.histmersion.api.library.Source
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.api.library.SourceColumns.given
import com.histmersion.domain.library.Citekey
import com.histmersion.api.catalogue.Place
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One map-feed row: a [[GeoHit]] and what [[SourceKinds.of]] reads to say what its source is. */
final private[findings] case class GeoFeedRow(
    citekey: Citekey,
    title: GeoFeedRow.Title,
    claim: GeoFeedRow.Claim,
    regionName: Option[GeoFeedRow.RegionName],
    lon: Option[Longitude],
    lat: Option[Latitude],
    periodStart: Option[Year],
    periodEnd: Option[Year],
    confidence: Option[Confidence],
    significance: Option[GeoFeedRow.Significance],
    doi: Option[GeoFeedRow.Doi],
    venue: Option[GeoFeedRow.Venue],
    provenance: Option[GeoFeedRow.Provenance],
    tags: List[GeoFeedRow.Tags],
    topics: List[GeoFeedRow.Topics],
    hasCover: GeoFeedRow.HasCover
) derives DbCodec:
  def toHit: GeoHit =
    val apiDoi = doi.map(d => com.histmersion.api.library.Doi(d.value))
    GeoHit(
      com.histmersion.api.library.Citekey(citekey.value),
      GeoHit.Title(title.value),
      GeoHit.Claim(claim.value),
      regionName.map(r => Place.Region(r.value)),
      lon.map(l => com.histmersion.api.catalogue.Longitude(l.value)),
      lat.map(l => com.histmersion.api.catalogue.Latitude(l.value)),
      periodStart.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
      periodEnd.map(y => com.histmersion.api.catalogue.Year(y.value.toLong)),
      confidence,
      significance.map(s => GeoHit.Significance(s.value)),
      apiDoi,
      Some(
        GeoHit.SourceKind(
          Source.Kinds.of(
            apiDoi,
            venue.map(v => Source.Venue(v.value)),
            provenance.map(p => Source.Provenance(p.value)),
            tags.map(t => Source.Tags(t.value)),
            topics.map(t => Source.Topics(t.value)),
            hasCover.value
          ).value
        )
      )
    )

object GeoFeedRow:
  type Doi = Doi.T
  object Doi extends RefinedType[String, Pure]
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  type Claim = Claim.T
  object Claim extends RefinedType[String, Pure]
  type RegionName = RegionName.T
  object RegionName extends RefinedType[String, Pure]
  type Significance = Significance.T
  object Significance extends RefinedType[String, Pure]
  type Venue = Venue.T
  object Venue extends RefinedType[String, Pure]
  type Provenance = Provenance.T
  object Provenance extends RefinedType[String, Pure]
  type Tags = Tags.T
  object Tags extends RefinedType[String, Pure]
  type Topics = Topics.T
  object Topics extends RefinedType[String, Pure]
  type HasCover = HasCover.T
  object HasCover extends RefinedType[Boolean, Pure]
