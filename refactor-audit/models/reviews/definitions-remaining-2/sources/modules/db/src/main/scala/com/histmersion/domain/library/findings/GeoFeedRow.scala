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
    GeoHit(
      citekey,
      title,
      claim,
      regionName,
      lon,
      lat,
      periodStart,
      periodEnd,
      confidence,
      significance,
      doi,
      Some(Source.Kinds.of(doi, venue, provenance, tags, topics, hasCover))
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
