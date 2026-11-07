package com.histmersion.domain.library.findings

import com.histmersion.util.database.given
import com.histmersion.api.library.Confidence

import com.augustnagro.magnum.*
import com.histmersion.api.library.GeoHit
import com.histmersion.api.library.Source

/** One map-feed row: a [[GeoHit]] and what [[SourceKinds.of]] reads to say what its source is. */
final private[findings] case class GeoFeedRow(
    citekey: com.histmersion.domain.library.Citekey,
    title: GeoFeedRow.Title,
    claim: GeoFeedRow.Claim,
    regionName: Option[GeoFeedRow.RegionName],
    lon: Option[com.histmersion.Longitude],
    lat: Option[com.histmersion.Latitude],
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
    confidence: Option[Confidence],
    significance: Option[GeoFeedRow.Significance],
    doi: Option[com.histmersion.domain.library.Doi],
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
      regionName.map(r => com.histmersion.api.catalogue.Place.Region(r.value)),
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
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Claim = Claim.T
  object Claim extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionName = RegionName.T
  object RegionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Significance = Significance.T
  object Significance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Venue = Venue.T
  object Venue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provenance = Provenance.T
  object Provenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasCover = HasCover.T
  object HasCover extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
