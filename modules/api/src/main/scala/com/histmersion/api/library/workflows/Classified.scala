package com.histmersion
package api.library.workflows

import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Source
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Classified(
    summary: Classified.Summary,
    topics: List[Classified.Topic],
    regions: List[Classified.Region],
    tags: List[Classified.Tag],
    period: Option[Period],
    periodName: Option[StatedPeriod.Name],
    status: Source.Status,
    statusEvidence: Classified.StatusEvidence
) derives JsonCodec

object Classified:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type StatusEvidence = StatusEvidence.T
  object StatusEvidence extends Newtype[String, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  type Tag = Tag.T
  object Tag extends Newtype[String, Pure]
  type Topic = Topic.T
  object Topic extends Newtype[String, Pure]
