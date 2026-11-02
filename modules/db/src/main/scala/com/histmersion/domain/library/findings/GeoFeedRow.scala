package com.histmersion.domain.library.findings

import com.histmersion.util.database.given
import com.histmersion.domain.library.Confidence

import com.augustnagro.magnum.*
import com.histmersion.api.library.GeoHit
import com.histmersion.api.library.Source

/** One map-feed row: a [[GeoHit]] and what [[SourceKinds.of]] reads to say what its source is. */
final private[findings] case class GeoFeedRow(
    citekey: String,
    title: String,
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    confidence: Option[Confidence],
    significance: Option[String],
    doi: Option[String],
    venue: Option[String],
    provenance: Option[String],
    tags: List[String],
    topics: List[String],
    hasCover: Boolean
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
