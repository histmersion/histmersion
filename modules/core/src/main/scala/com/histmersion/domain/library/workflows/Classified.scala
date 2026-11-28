package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.library.Source
import com.histmersion.domain.EnrichSource.Topic
import com.histmersion.domain.EnrichSource.Region
import com.histmersion.domain.EnrichSource.Tag
import io.github.iltotore.iron.constraint.any.Pure

final case class Classified(
    summary: Classified.Summary,
    topics: List[Topic],
    regions: List[Region],
    tags: List[Tag],
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
