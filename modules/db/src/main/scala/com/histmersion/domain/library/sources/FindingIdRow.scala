package com.histmersion.domain.library.sources

import com.histmersion.domain.library.findings.FindingColumns.given

import com.histmersion.util.database.given
import com.histmersion.domain.library.Confidence

import com.augustnagro.magnum.*

/** A finding's row with its id, as a write path reads it back. */
final private[sources] case class FindingIdRow(
    id: String,
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[Confidence],
    significance: Option[String]
) derives DbCodec
