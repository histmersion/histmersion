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
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** One map-feed row: a [[GeoHit]] and what [[SourceKinds.of]] reads to say what its source is. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
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
          Source.Kinds
            .of(
              apiDoi,
              venue.map(v => Source.Venue(v.value)),
              provenance.map(p => Source.Provenance(p.value)),
              tags.map(t => Source.Tags(t.value)),
              topics.map(t => Source.Topics(t.value)),
              hasCover.value
            )
            .value
        )
      )
    )

object GeoFeedRow:
  type Doi = Doi.T
  object Doi extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type RegionName = RegionName.T
  object RegionName extends Newtype[String, Pure]
  type Significance = Significance.T
  object Significance extends Newtype[String, Pure]
  type Venue = Venue.T
  object Venue extends Newtype[String, Pure]
  type Provenance = Provenance.T
  object Provenance extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Topics = Topics.T
  object Topics extends Newtype[String, Pure]
  type HasCover = HasCover.T
  object HasCover extends Newtype[Boolean, Pure]
